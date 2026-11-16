package app.cristal.cover.ui.launcher

import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import app.cristal.cover.BuildConfig
import app.cristal.cover.data.control.ControlCenterRepository
import app.cristal.cover.data.control.ControlKind
import app.cristal.cover.data.control.ControlLayout
import app.cristal.cover.data.control.ControlPreset
import app.cristal.cover.data.system.QuickSettingsRepository
import app.cristal.cover.data.system.SystemBridge
import app.cristal.cover.data.system.ToggleResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Para onde vão os pedidos da central que precisam de uma tela do sistema ou de um aviso. */
interface ControlSink {
    fun openIntent(intent: Intent)
    fun message(text: String)
}

/**
 * O que cada módulo da Liquid Control Center faz. Fica fora do launcher para a mesma central
 * funcionar sobre qualquer app (pela sobreposição do serviço de acessibilidade).
 */
class ControlCenterController(
    private val quick: QuickSettingsRepository,
    private val controls: ControlCenterRepository,
    private val systemBridge: SystemBridge,
    private val scope: CoroutineScope,
    private val sink: ControlSink,
) {
    fun toggleTorch() {
        if (!quick.toggleTorch()) sink.message("A lanterna não está disponível agora.")
    }

    fun toggleRinger() {
        if (!quick.toggleRinger()) sink.message("Não foi possível mudar o modo de toque (Não Perturbe ativo?).")
    }

    fun setMediaVolume(fraction: Float) = quick.setMediaVolume(fraction)

    fun toggleWifi() = shellToggle(Settings.Panel.ACTION_WIFI) { quick.toggleWifi() }

    fun toggleBluetooth() = shellToggle(Settings.ACTION_BLUETOOTH_SETTINGS) { quick.toggleBluetooth() }

    fun toggleMobileData() = shellToggle(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) { quick.toggleMobileData() }

    fun saveLayout(onCover: Boolean, layout: ControlLayout) {
        scope.launch { controls.save(onCover, layout) }
    }

    fun applyPreset(onCover: Boolean, preset: ControlPreset) {
        scope.launch { controls.applyPreset(onCover, preset) }
    }

    /** Toque num módulo. Os que mexem na própria tela (captura, busca, papel de parede) ficam com quem chama. */
    fun runControl(kind: ControlKind) {
        when (kind) {
            ControlKind.TORCH -> toggleTorch()
            ControlKind.RINGER -> toggleRinger()
            ControlKind.WIFI -> toggleWifi()
            ControlKind.BLUETOOTH -> toggleBluetooth()
            ControlKind.MOBILE_DATA -> toggleMobileData()
            ControlKind.AIRPLANE -> scope.launch { handle(quick.toggleAirplane(), Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)) }
            ControlKind.LOCATION -> scope.launch { handle(quick.toggleLocation(), Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
            ControlKind.NFC -> scope.launch { handle(quick.toggleNfc(), Intent(Settings.ACTION_NFC_SETTINGS)) }
            ControlKind.BATTERY_SAVER -> scope.launch { handle(quick.toggleBatterySaver(), Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)) }
            ControlKind.DARK_MODE -> scope.launch { handle(quick.toggleDarkMode(), Intent(Settings.ACTION_DISPLAY_SETTINGS)) }
            ControlKind.DND -> scope.launch {
                handle(
                    quick.toggleDnd(),
                    panel = null,
                    permission = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
                    hint = "Libere o Cristal em \"Acesso ao Não perturbe\" para usar este botão.",
                )
            }
            ControlKind.ROTATION -> scope.launch {
                handle(
                    quick.toggleRotation(),
                    panel = null,
                    permission = writeSettingsIntent,
                    hint = "Permita que o Cristal altere as configurações do sistema para mudar a rotação.",
                )
            }
            ControlKind.HOTSPOT -> sink.openIntent(Intent(TETHER_SETTINGS))
            ControlKind.LOCK -> if (!systemBridge.lockScreen()) needsAccessibility("bloquear a tela por aqui")
            ControlKind.RECENTS -> if (!systemBridge.openRecents()) needsAccessibility("abrir os apps recentes")
            ControlKind.SYSTEM_PANEL -> if (!systemBridge.openQuickSettings()) needsAccessibility("abrir o painel do sistema")
            ControlKind.TIMER -> sink.openIntent(Intent(AlarmClock.ACTION_SHOW_TIMERS))
            ControlKind.ALARM -> sink.openIntent(Intent(AlarmClock.ACTION_SHOW_ALARMS))
            ControlKind.CALCULATOR -> sink.openIntent(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR))
            ControlKind.CAMERA -> sink.openIntent(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
            ControlKind.MEDIA, ControlKind.BATTERY, ControlKind.BRIGHTNESS, ControlKind.VOLUME, ControlKind.CONNECTIVITY,
            ControlKind.SCREENSHOT, ControlKind.SEARCH, ControlKind.WALLPAPER, ControlKind.SETTINGS -> Unit
        }
    }

    /** Toque longo num módulo: abre a tela do sistema ligada a ele. */
    fun controlSettings(kind: ControlKind) {
        val action = when (kind) {
            ControlKind.WIFI, ControlKind.CONNECTIVITY -> Settings.ACTION_WIFI_SETTINGS
            ControlKind.BLUETOOTH -> Settings.ACTION_BLUETOOTH_SETTINGS
            ControlKind.MOBILE_DATA -> Settings.ACTION_DATA_ROAMING_SETTINGS
            ControlKind.AIRPLANE -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            ControlKind.HOTSPOT -> TETHER_SETTINGS
            ControlKind.NFC -> Settings.ACTION_NFC_SETTINGS
            ControlKind.LOCATION -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            ControlKind.RINGER, ControlKind.VOLUME -> Settings.ACTION_SOUND_SETTINGS
            ControlKind.DND -> Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS
            ControlKind.ROTATION, ControlKind.DARK_MODE, ControlKind.BRIGHTNESS -> Settings.ACTION_DISPLAY_SETTINGS
            ControlKind.BATTERY_SAVER, ControlKind.BATTERY -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            else -> null
        } ?: return
        sink.openIntent(Intent(action))
    }

    fun setBrightness(fraction: Float) {
        if (quick.setBrightness(fraction) == ToggleResult.NEEDS_PERMISSION) {
            sink.message("Permita que o Cristal altere as configurações do sistema para mudar o brilho por aqui.")
            sink.openIntent(writeSettingsIntent)
        }
    }

    fun endBrightnessDrag() = quick.endBrightnessDrag()

    fun toggleAutoBrightness() {
        scope.launch {
            handle(
                quick.toggleAutoBrightness(),
                panel = null,
                permission = writeSettingsIntent,
                hint = "Permita que o Cristal altere as configurações do sistema para usar o brilho automático.",
            )
        }
    }

    fun openSystemQuickSettings(): Boolean = systemBridge.openQuickSettings()

    fun takeScreenshot() {
        if (!systemBridge.takeScreenshot()) needsAccessibility("capturar a tela por aqui")
    }

    private fun needsAccessibility(what: String) = sink.message("Ative o serviço de acessibilidade do Cristal para $what.")

    private val writeSettingsIntent: Intent
        get() = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${BuildConfig.APPLICATION_ID}"))

    private fun shellToggle(panelAction: String, toggle: suspend () -> ToggleResult) {
        scope.launch { handle(toggle(), Intent(panelAction)) }
    }

    private fun handle(result: ToggleResult, panel: Intent?, permission: Intent? = null, hint: String? = null) {
        when (result) {
            ToggleResult.DONE -> Unit
            ToggleResult.NEEDS_SYSTEM_PANEL, ToggleResult.FAILED -> panel?.let(sink::openIntent)
            ToggleResult.NEEDS_PERMISSION -> {
                hint?.let(sink::message)
                (permission ?: panel)?.let(sink::openIntent)
            }
        }
    }

    companion object {
        const val TETHER_SETTINGS = "android.settings.TETHER_SETTINGS"
    }
}
