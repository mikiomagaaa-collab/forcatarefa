package app.cristal.cover.data.system

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.UiModeManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Resources
import android.database.ContentObserver
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import app.cristal.cover.core.AppScope
import app.cristal.cover.core.safely
import app.cristal.cover.data.shizuku.ShizukuManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class QuickState(
    val torchAvailable: Boolean = false,
    val torchOn: Boolean = false,
    /** `null` quando o estado não pôde ser lido. */
    val wifiOn: Boolean? = null,
    val bluetoothOn: Boolean? = null,
    val mobileDataOn: Boolean? = null,
    /** `true` quando o toque está em vibrar ou silencioso. */
    val ringerMuted: Boolean = false,
    val mediaVolume: Float = 0f,
    val airplaneOn: Boolean = false,
    val locationOn: Boolean = false,
    /** `null` quando o aparelho não tem NFC. */
    val nfcOn: Boolean? = null,
    val batterySaverOn: Boolean = false,
    val darkModeOn: Boolean = false,
    val autoRotate: Boolean = false,
    /** Brilho percebido, de 0 a 1. */
    val brightness: Float = 0.5f,
    val autoBrightness: Boolean = false,
    val dndOn: Boolean = false,
    /** O usuário liberou "Alterar configurações do sistema" (brilho, rotação). */
    val canWriteSettings: Boolean = false,
    /** O usuário liberou o acesso ao Não perturbe. */
    val dndAccess: Boolean = false,
    val shizukuReady: Boolean = false,
)

/** Resultado de um atalho que pode precisar de um painel do sistema. */
enum class ToggleResult { DONE, NEEDS_SYSTEM_PANEL, NEEDS_PERMISSION, FAILED }

/**
 * Ajustes rápidos que um app comum consegue controlar, usados pela Liquid Control Center.
 *
 * Cada controle segue a mesma ordem: API pública do Android quando existe; Shizuku (comando de
 * shell) quando o Android não deixa apps comuns mudarem; e, sem nenhum dos dois, a UI abre a tela
 * certa do sistema. Lanterna, volume, modo de toque são diretos; brilho e rotação pedem
 * "Alterar configurações do sistema"; Não perturbe pede o acesso ao Não perturbe; Wi-Fi,
 * Bluetooth, dados, avião, localização, NFC, modo escuro e economia de bateria só com Shizuku.
 */
@Singleton
class QuickSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizuku: ShizukuManager,
    @AppScope private val scope: CoroutineScope,
) {
    private val cameraManager: CameraManager? = context.getSystemService(CameraManager::class.java)
    private val audioManager: AudioManager? = context.getSystemService(AudioManager::class.java)
    private val wifiManager: WifiManager? = context.getSystemService(WifiManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? =
        safely("obter adaptador Bluetooth") { context.getSystemService(BluetoothManager::class.java)?.adapter }
    private val telephonyManager: TelephonyManager? = context.getSystemService(TelephonyManager::class.java)
    private val locationManager: LocationManager? = context.getSystemService(LocationManager::class.java)
    private val powerManager: PowerManager? = context.getSystemService(PowerManager::class.java)
    private val uiModeManager: UiModeManager? = context.getSystemService(UiModeManager::class.java)
    private val notificationManager: NotificationManager? = context.getSystemService(NotificationManager::class.java)
    private val nfcAdapter: NfcAdapter? = safely("obter NFC") { NfcAdapter.getDefaultAdapter(context) }
    private val handler = Handler(Looper.getMainLooper())

    private val torchCameraId: String? by lazy { findTorchCamera() }
    private val torchOn = MutableStateFlow(false)
    private val manualRefresh = MutableStateFlow(0)

    /** Valor do brilho enquanto o dedo arrasta (a escrita no sistema vem logo atrás). */
    private val brightnessOverride = MutableStateFlow<Float?>(null)
    private val brightnessTarget = MutableStateFlow<Float?>(null)

    private val maxBrightness: Int by lazy {
        safely("ler brilho máximo") {
            val id = Resources.getSystem().getIdentifier("config_screenBrightnessSettingMaximum", "integer", "android")
            if (id != 0) Resources.getSystem().getInteger(id) else null
        }?.takeIf { it > 1 } ?: 255
    }

    private val torchFlow = callbackFlow {
        val cm = cameraManager
        val id = torchCameraId
        if (cm == null || id == null) {
            trySend(false)
            awaitClose { }
            return@callbackFlow
        }
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == id) {
                    torchOn.value = enabled
                    trySend(enabled)
                }
            }

            override fun onTorchModeUnavailable(cameraId: String) {
                if (cameraId == id) {
                    torchOn.value = false
                    trySend(false)
                }
            }
        }
        safely("registrar callback da lanterna") { cm.registerTorchCallback(callback, handler) }
        trySend(torchOn.value)
        awaitClose { safely("remover callback da lanterna") { cm.unregisterTorchCallback(callback) } }
    }.conflate()

    private val systemEvents = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(VOLUME_CHANGED_ACTION)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(LocationManager.MODE_CHANGED_ACTION)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(NFC_STATE_CHANGED)
        }
        safely("registrar receiver de ajustes rápidos") {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        }
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        val uris = listOf<Uri>(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE),
            Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
            Settings.Global.getUriFor(Settings.Global.AIRPLANE_MODE_ON),
        )
        for (uri in uris) safely("observar $uri") { context.contentResolver.registerContentObserver(uri, false, observer) }
        trySend(Unit)
        awaitClose {
            safely("remover receiver de ajustes rápidos") { context.unregisterReceiver(receiver) }
            safely("remover observador de ajustes") { context.contentResolver.unregisterContentObserver(observer) }
        }
    }.conflate()

    val state: StateFlow<QuickState> = combine(torchFlow, systemEvents, manualRefresh, brightnessOverride, shizuku.status) {
        torch, _, _, override, _ ->
        read(torch).let { if (override != null) it.copy(brightness = override) else it }
    }.stateIn(scope, SharingStarted.WhileSubscribed(3_000), read(false))

    init {
        // Escreve o brilho no ritmo que o sistema aguenta, sempre com o valor mais recente.
        scope.launch {
            brightnessTarget.collect { target ->
                if (target == null) return@collect
                writeBrightness(target)
                delay(BRIGHTNESS_WRITE_INTERVAL_MS)
            }
        }
    }

    fun refresh() {
        manualRefresh.value++
    }

    // Cada leitura está protegida por `safely`, que trata o SecurityException de permissões negadas.
    @SuppressLint("MissingPermission")
    private fun read(torch: Boolean): QuickState {
        val cr = context.contentResolver
        val rawBrightness = safely("ler brilho") { Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS) } ?: (maxBrightness / 2)
        return QuickState(
            torchAvailable = torchCameraId != null,
            torchOn = torch,
            wifiOn = safely("ler Wi-Fi") { wifiManager?.isWifiEnabled },
            bluetoothOn = safely("ler Bluetooth") { bluetoothAdapter?.isEnabled },
            mobileDataOn = safely("ler dados móveis") { telephonyManager?.isDataEnabled },
            ringerMuted = safely("ler modo de toque") { audioManager?.ringerMode != AudioManager.RINGER_MODE_NORMAL } ?: false,
            mediaVolume = safely("ler volume") {
                val am = audioManager ?: return@safely 0f
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
            } ?: 0f,
            airplaneOn = safely("ler modo avião") { Settings.Global.getInt(cr, Settings.Global.AIRPLANE_MODE_ON, 0) == 1 } ?: false,
            locationOn = safely("ler localização") { locationManager?.isLocationEnabled } ?: false,
            nfcOn = safely("ler NFC") { nfcAdapter?.isEnabled },
            batterySaverOn = safely("ler economia de bateria") { powerManager?.isPowerSaveMode } ?: false,
            darkModeOn = safely("ler modo escuro") { uiModeManager?.nightMode == UiModeManager.MODE_NIGHT_YES } ?: false,
            autoRotate = safely("ler rotação") { Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0) == 1 } ?: false,
            brightness = linearToPerceived(rawBrightness),
            autoBrightness = safely("ler brilho automático") {
                Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            } ?: false,
            dndOn = safely("ler não perturbe") {
                val filter = notificationManager?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL
                filter != NotificationManager.INTERRUPTION_FILTER_ALL && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            } ?: false,
            canWriteSettings = safely("ler permissão de ajustes") { Settings.System.canWrite(context) } ?: false,
            dndAccess = safely("ler acesso ao não perturbe") { notificationManager?.isNotificationPolicyAccessGranted } ?: false,
            shizukuReady = shizuku.isReady,
        )
    }

    private fun findTorchCamera(): String? = safely("procurar câmera com flash") {
        val cm = cameraManager ?: return@safely null
        var fallback: String? = null
        for (id in cm.cameraIdList) {
            val chars = safely("ler características da câmera") { cm.getCameraCharacteristics(id) } ?: continue
            if (chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) != true) continue
            if (chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK) return@safely id
            if (fallback == null) fallback = id
        }
        fallback
    }

    /** Liga ou desliga a lanterna. Devolve `false` se a câmera estiver em uso por outro app. */
    fun toggleTorch(): Boolean {
        val cm = cameraManager ?: return false
        val id = torchCameraId ?: return false
        return safely("alternar lanterna") {
            cm.setTorchMode(id, !torchOn.value)
            true
        } ?: false
    }

    fun setMediaVolume(fraction: Float) {
        safely("ajustar volume") {
            val am = audioManager ?: return@safely
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, (fraction.coerceIn(0f, 1f) * max).roundToInt(), 0)
        }
        refresh()
    }

    /** Alterna entre tocar e vibrar. Falha (sem travar) se o Não Perturbe estiver ativo. */
    fun toggleRinger(): Boolean {
        val result = safely("alternar modo de toque") {
            val am = audioManager ?: return@safely false
            am.ringerMode = if (am.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                AudioManager.RINGER_MODE_VIBRATE
            } else {
                AudioManager.RINGER_MODE_NORMAL
            }
            true
        } ?: false
        refresh()
        return result
    }

    suspend fun toggleWifi(): ToggleResult = shellToggle("svc wifi", state.value.wifiOn)

    suspend fun toggleBluetooth(): ToggleResult = shellToggle("svc bluetooth", state.value.bluetoothOn)

    suspend fun toggleMobileData(): ToggleResult = shellToggle("svc data", state.value.mobileDataOn)

    suspend fun toggleNfc(): ToggleResult = shellToggle("svc nfc", state.value.nfcOn)

    suspend fun toggleAirplane(): ToggleResult =
        shellCommand(if (state.value.airplaneOn) "cmd connectivity airplane-mode disable" else "cmd connectivity airplane-mode enable")

    suspend fun toggleLocation(): ToggleResult =
        shellCommand("cmd location set-location-enabled ${!state.value.locationOn}")

    suspend fun toggleBatterySaver(): ToggleResult =
        shellCommand("cmd power set-mode ${if (state.value.batterySaverOn) 0 else 1}")

    suspend fun toggleDarkMode(): ToggleResult =
        shellCommand("cmd uimode night ${if (state.value.darkModeOn) "no" else "yes"}")

    /** Liga ou desliga o Não perturbe (modo "prioridade"). */
    suspend fun toggleDnd(): ToggleResult {
        val turnOn = !state.value.dndOn
        val nm = notificationManager
        if (nm != null && safely("ler acesso ao não perturbe") { nm.isNotificationPolicyAccessGranted } == true) {
            val ok = safely("alternar não perturbe") {
                nm.setInterruptionFilter(
                    if (turnOn) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL,
                )
                true
            } ?: false
            refreshSoon()
            return if (ok) ToggleResult.DONE else ToggleResult.FAILED
        }
        if (shizuku.isReady) return shellCommand("cmd notification set_dnd ${if (turnOn) "priority" else "off"}")
        return ToggleResult.NEEDS_PERMISSION
    }

    /** Liga ou desliga a rotação automática. */
    suspend fun toggleRotation(): ToggleResult {
        val next = if (state.value.autoRotate) 0 else 1
        if (safely("ler permissão de ajustes") { Settings.System.canWrite(context) } == true) {
            val ok = safely("alternar rotação") {
                Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, next)
            } ?: false
            refresh()
            return if (ok) ToggleResult.DONE else ToggleResult.FAILED
        }
        if (shizuku.isReady) return shellCommand("settings put system accelerometer_rotation $next")
        return ToggleResult.NEEDS_PERMISSION
    }

    /** Muda o brilho (0 a 1, escala percebida). Devolve [ToggleResult.NEEDS_PERMISSION] se não puder. */
    fun setBrightness(fraction: Float): ToggleResult {
        val canWrite = safely("ler permissão de ajustes") { Settings.System.canWrite(context) } == true
        if (!canWrite && !shizuku.isReady) return ToggleResult.NEEDS_PERMISSION
        val value = fraction.coerceIn(0f, 1f)
        brightnessOverride.value = value
        brightnessTarget.value = value
        return ToggleResult.DONE
    }

    /** O dedo saiu do controle: o valor real volta a vir do sistema. */
    fun endBrightnessDrag() {
        scope.launch {
            delay(BRIGHTNESS_WRITE_INTERVAL_MS * 3)
            brightnessOverride.value = null
        }
    }

    suspend fun toggleAutoBrightness(): ToggleResult {
        val next = if (state.value.autoBrightness) {
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        } else {
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        }
        if (safely("ler permissão de ajustes") { Settings.System.canWrite(context) } == true) {
            val ok = safely("alternar brilho automático") {
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, next)
            } ?: false
            refresh()
            return if (ok) ToggleResult.DONE else ToggleResult.FAILED
        }
        if (shizuku.isReady) return shellCommand("settings put system screen_brightness_mode $next")
        return ToggleResult.NEEDS_PERMISSION
    }

    private suspend fun writeBrightness(fraction: Float) {
        val raw = perceivedToLinear(fraction)
        if (safely("ler permissão de ajustes") { Settings.System.canWrite(context) } == true) {
            safely("ajustar brilho") { Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, raw) }
        } else if (shizuku.isReady) {
            shizuku.exec("settings put system screen_brightness $raw")
        }
    }

    /** Converte a posição do controle (percebida) para o valor guardado pelo sistema. */
    private fun perceivedToLinear(fraction: Float): Int =
        (fraction.coerceIn(0f, 1f).pow(BRIGHTNESS_GAMMA) * maxBrightness).roundToInt().coerceIn(1, maxBrightness)

    private fun linearToPerceived(raw: Int): Float =
        (raw.toFloat() / maxBrightness).coerceIn(0f, 1f).pow(1f / BRIGHTNESS_GAMMA)

    private suspend fun shellToggle(command: String, current: Boolean?): ToggleResult =
        shellCommand("$command ${if (current == true) "disable" else "enable"}")

    private suspend fun shellCommand(command: String): ToggleResult {
        if (!shizuku.isReady) return ToggleResult.NEEDS_SYSTEM_PANEL
        val result = shizuku.exec(command)
        refreshSoon()
        return if (result.ok) ToggleResult.DONE else ToggleResult.FAILED
    }

    private fun refreshSoon() {
        scope.launch {
            // O rádio leva um instante para mudar de estado.
            delay(600)
            refresh()
            delay(1_500)
            refresh()
        }
    }

    private companion object {
        const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        const val NFC_STATE_CHANGED = "android.nfc.action.ADAPTER_STATE_CHANGED"
        const val BRIGHTNESS_GAMMA = 2.2f
        const val BRIGHTNESS_WRITE_INTERVAL_MS = 60L
    }
}
