package app.cristal.cover.service

import android.accessibilityservice.AccessibilityService
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import app.cristal.cover.core.Logx
import app.cristal.cover.core.safely
import app.cristal.cover.data.display.CoverDisplayRepository
import app.cristal.cover.data.display.CoverState
import app.cristal.cover.data.island.IslandRepository
import app.cristal.cover.data.launch.AppLauncher
import app.cristal.cover.data.media.MediaRepository
import app.cristal.cover.data.notifications.NotificationRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import app.cristal.cover.data.prefs.SettingsRepository
import app.cristal.cover.data.system.SystemBridge
import app.cristal.cover.ui.launcher.LauncherActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Serviço de acessibilidade do Cristal. Ele NÃO lê o conteúdo das telas (canRetrieveWindowContent
 * é falso no XML): só recebe "qual app veio para a frente" e executa ações globais do sistema.
 *
 * Para que serve:
 * 1. Ações globais pedidas pelos gestos: bloquear a tela, abrir notificações, recentes etc.
 * 2. "Assumir a tela externa": quando o launcher da Motorola aparece na tela externa, abre o Cristal.
 * 3. Tela de bloqueio: serviços de acessibilidade podem abrir uma activity com a tela apagada, o
 *    que o Android bloqueia para apps comuns.
 */
@AndroidEntryPoint
class CristalAccessibilityService : AccessibilityService() {

    @Inject lateinit var bridge: SystemBridge
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var displayRepository: CoverDisplayRepository
    @Inject lateinit var lockController: LockController
    @Inject lateinit var appLauncher: AppLauncher
    @Inject lateinit var islandRepository: IslandRepository
    @Inject lateinit var mediaRepository: MediaRepository
    @Inject lateinit var notificationRepository: NotificationRepository
    @Inject lateinit var briefRepository: app.cristal.cover.data.brief.BriefRepository
    @Inject lateinit var quickSettingsRepository: app.cristal.cover.data.system.QuickSettingsRepository
    @Inject lateinit var controlCenterRepository: app.cristal.cover.data.control.ControlCenterRepository
    @Inject lateinit var batteryRepository: app.cristal.cover.data.battery.BatteryRepository

    private var island: IslandOverlay? = null
    private var controlCenter: ControlCenterOverlay? = null
    private var foldTransition: FoldTransitionOverlay? = null

    private var scope: CoroutineScope? = null
    private var receiverRegistered = false
    private var coverHomePackages: Set<String> = emptySet()
    private var lastTakeover = 0L
    private var lastFolded: Boolean? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    appLauncher.restoreDisplayScale()
                    lockController.onScreenOff()
                }

                Intent.ACTION_SCREEN_ON -> lockController.onScreenOn()
                Intent.ACTION_USER_PRESENT -> lockController.onUserPresent()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        bridge.attach(this)
        lockController.attachHost(this)
        coverHomePackages = resolveCoverHomePackages()

        scope?.cancel()
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = newScope
        // Mantém o estado da tela externa "quente" para sabermos se o aparelho está fechado.
        val fold = foldTransition ?: FoldTransitionOverlay(this).also { foldTransition = it }
        newScope.launch {
            displayRepository.state.collect { state ->
                lockController.onCoverState(state)
                onFoldChanged(state)
                // Transição de fechamento sobre qualquer app (precisa do sensor da dobradiça).
                fold.onHinge(state.hingeAngle, state.folded, settingsRepository.settings.value.flipTransition)
            }
        }
        // Liquid Island: liga e desliga conforme a configuração.
        val overlay = island ?: IslandOverlay(this, islandRepository, settingsRepository, mediaRepository, notificationRepository, briefRepository)
            .also { island = it }
        newScope.launch {
            settingsRepository.settings.map { it.islandEnabled }.distinctUntilChanged().collect { enabled ->
                if (enabled) overlay.show() else overlay.hide()
            }
        }

        // Liquid Control Center sobre qualquer app (faixa no canto superior direito de cada tela).
        controlCenter?.hide()
        val control = ControlCenterOverlay(
            this, newScope, settingsRepository, quickSettingsRepository, controlCenterRepository,
            notificationRepository, mediaRepository, batteryRepository, bridge, displayRepository, appLauncher,
        ).also { controlCenter = it }
        newScope.launch {
            combine(
                settingsRepository.settings.map { it.controlAnywhere }.distinctUntilChanged(),
                displayRepository.state.map { it.coverId }.distinctUntilChanged(),
            ) { enabled, _ -> enabled }.collect { enabled -> if (enabled) control.show() else control.hide() }
        }

        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            receiverRegistered = safely("registrar receiver de tela") {
                ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
                true
            } ?: false
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val displayId = if (Build.VERSION.SDK_INT >= 34) event.displayId else Display.DEFAULT_DISPLAY
        bridge.onWindowChanged(displayId, pkg)
        maybeTakeOver(displayId, pkg)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        cleanup()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private fun cleanup() {
        island?.hide()
        controlCenter?.hide()
        controlCenter = null
        foldTransition?.hide()
        lockController.detachHost()
        bridge.detach()
        scope?.cancel()
        scope = null
        if (receiverRegistered) {
            safely("remover receiver de tela") { unregisterReceiver(screenReceiver) }
            receiverRegistered = false
        }
    }

    /** Se o launcher da Motorola apareceu na tela externa, abre o Cristal por cima (opcional). */
    private fun maybeTakeOver(displayId: Int, pkg: String) {
        val settings = settingsRepository.settings.value
        if (!settings.coverTakeover || pkg == packageName || pkg !in coverHomePackages) return
        val cover = displayRepository.currentCover(settings.coverDisplayOverride) ?: return
        if (cover.isDefault || displayId != cover.id) return
        val now = SystemClock.elapsedRealtime()
        if (now < bridge.takeoverPausedUntil || now - lastTakeover < TAKEOVER_DEBOUNCE_MS) return
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked == true) return
        lastTakeover = now
        try {
            val intent = Intent(this, LauncherActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            startActivity(intent, ActivityOptions.makeBasic().setLaunchDisplayId(cover.id).toBundle())
        } catch (t: Throwable) {
            Logx.w("Não foi possível assumir a tela externa", t)
        }
    }

    /** Ao fechar o aparelho, abre o Cristal na tela externa (se "Assumir a tela externa" estiver ligado). */
    private fun onFoldChanged(state: CoverState) {
        val folded = state.folded ?: return
        val justClosed = folded && lastFolded == false
        lastFolded = folded
        if (!justClosed || !settingsRepository.settings.value.coverTakeover) return
        val cover = state.cover?.takeIf { !it.isDefault } ?: return
        scope?.launch {
            // Dá um instante para a Motorola terminar a transição para a tela externa.
            delay(650)
            if (SystemClock.elapsedRealtime() < bridge.takeoverPausedUntil) return@launch
            if (getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true) return@launch
            lastTakeover = SystemClock.elapsedRealtime()
            try {
                val intent = Intent(this@CristalAccessibilityService, LauncherActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                startActivity(intent, ActivityOptions.makeBasic().setLaunchDisplayId(cover.id).toBundle())
            } catch (t: Throwable) {
                Logx.w("Não foi possível abrir o Cristal ao fechar o aparelho", t)
            }
        }
    }

    /** Pacotes que respondem como "home" (de qualquer tela), mais os conhecidos da Motorola. */
    private fun resolveCoverHomePackages(): Set<String> {
        val found = HashSet<String>(KNOWN_COVER_HOMES)
        safely("resolver SECONDARY_HOME") {
            for (category in listOf(Intent.CATEGORY_SECONDARY_HOME, Intent.CATEGORY_HOME)) {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
                packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
                    .mapNotNullTo(found) { it.activityInfo?.packageName }
            }
        }
        found.remove(packageName)
        return found
    }

    private companion object {
        const val TAKEOVER_DEBOUNCE_MS = 700L
        val KNOWN_COVER_HOMES = setOf("com.motorola.launcher.secondarydisplay", "com.motorola.launcher3")
    }
}
