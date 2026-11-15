package app.cristal.cover.data.system

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import app.cristal.cover.core.AppScope
import app.cristal.cover.core.safely
import app.cristal.cover.data.shizuku.ShizukuManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
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
)

/** Resultado de um atalho que pode precisar de um painel do sistema. */
enum class ToggleResult { DONE, NEEDS_SYSTEM_PANEL, FAILED }

/**
 * Ajustes rápidos que um app comum consegue controlar.
 *
 * Lanterna, volume e modo vibrar são diretos. Wi-Fi, Bluetooth e dados móveis não podem mais ser
 * alternados por apps desde o Android 10/13: com Shizuku usamos `svc`; sem ele, a UI abre o painel
 * correspondente do sistema.
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
    private val handler = Handler(Looper.getMainLooper())

    private val torchCameraId: String? by lazy { findTorchCamera() }
    private val torchOn = MutableStateFlow(false)
    private val manualRefresh = MutableStateFlow(0)

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
        }
        safely("registrar receiver de ajustes rápidos") {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        }
        trySend(Unit)
        awaitClose { safely("remover receiver de ajustes rápidos") { context.unregisterReceiver(receiver) } }
    }.conflate()

    val state: StateFlow<QuickState> = combine(torchFlow, systemEvents, manualRefresh) { torch, _, _ ->
        read(torch)
    }.stateIn(scope, SharingStarted.WhileSubscribed(3_000), read(false))

    fun refresh() {
        manualRefresh.value++
    }

    // Cada leitura está protegida por `safely`, que trata o SecurityException de permissões negadas.
    @SuppressLint("MissingPermission")
    private fun read(torch: Boolean): QuickState = QuickState(
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
    )

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
            am.setStreamVolume(AudioManager.STREAM_MUSIC, (fraction.coerceIn(0f, 1f) * max).toInt(), 0)
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

    suspend fun toggleWifi(): ToggleResult = shellToggle("wifi", state.value.wifiOn)

    suspend fun toggleBluetooth(): ToggleResult = shellToggle("bluetooth", state.value.bluetoothOn)

    suspend fun toggleMobileData(): ToggleResult = shellToggle("data", state.value.mobileDataOn)

    private suspend fun shellToggle(service: String, current: Boolean?): ToggleResult {
        if (!shizuku.isReady) return ToggleResult.NEEDS_SYSTEM_PANEL
        val target = if (current == true) "disable" else "enable"
        val result = shizuku.exec("svc $service $target")
        scope.launch {
            // O rádio leva um instante para mudar de estado.
            delay(600)
            refresh()
            delay(1_500)
            refresh()
        }
        return if (result.ok) ToggleResult.DONE else ToggleResult.FAILED
    }

    private companion object {
        const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
    }
}
