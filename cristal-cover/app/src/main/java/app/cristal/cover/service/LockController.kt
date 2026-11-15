package app.cristal.cover.service

import android.accessibilityservice.AccessibilityService
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.view.Display
import app.cristal.cover.core.Logx
import app.cristal.cover.core.safely
import app.cristal.cover.data.display.CoverDisplayRepository
import app.cristal.cover.data.display.CoverState
import app.cristal.cover.data.prefs.LockMode
import app.cristal.cover.data.prefs.SettingsRepository
import app.cristal.cover.ui.lock.LockActivity
import app.cristal.cover.ui.lock.LockOverlayContent
import app.cristal.cover.ui.lock.LockPresenter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decide quando e onde mostrar a tela de bloqueio do Cristal.
 *
 * Segurança: em nenhum dos modos o keyguard do sistema é removido ou substituído. O Cristal apenas
 * desenha POR CIMA dele. Para destravar, quem autentica (PIN, padrão, digital) é sempre o sistema,
 * por [KeyguardManager.requestDismissKeyguard]. Se o Cristal travar ou for encerrado, o que aparece
 * é a tela de bloqueio original, intacta.
 */
@Singleton
class LockController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val displayRepository: CoverDisplayRepository,
    private val presenter: LockPresenter,
) {
    private val keyguard: KeyguardManager? = context.getSystemService(KeyguardManager::class.java)
    private var host: WeakReference<AccessibilityService>? = null
    private var overlay: LockOverlay? = null
    @Volatile private var cover: CoverState = CoverState()

    fun attachHost(service: AccessibilityService) {
        host = WeakReference(service)
    }

    fun detachHost() {
        hideOverlay()
        host = null
    }

    fun onCoverState(state: CoverState) {
        cover = state
        val settings = settingsRepository.settings.value
        // Abriu o aparelho com o overlay na tela externa: some com ele.
        if (overlay != null && settings.lockCoverOnly && state.folded == false) hideOverlay()
    }

    fun onScreenOff() {
        // Prepara a tela de bloqueio com a tela apagada, para já estar lá quando acender.
        show()
    }

    fun onScreenOn() {
        if (keyguard?.isKeyguardLocked == true) show()
    }

    fun onUserPresent() {
        hideOverlay()
    }

    /** Chamado pelo overlay quando o usuário desliza para destravar. */
    fun unlockFromOverlay() {
        val displayId = overlay?.displayId ?: Display.DEFAULT_DISPLAY
        hideOverlay()
        startLockActivity(displayId, autoDismiss = true)
    }

    /** Tela onde a tela de bloqueio deve aparecer agora, ou `null` se não deve aparecer. */
    private fun targetDisplayId(): Int? {
        val settings = settingsRepository.settings.value
        val state = cover.takeIf { it.displays.isNotEmpty() } ?: displayRepository.state.value
        val coverDisplay = state.cover ?: displayRepository.currentCover(settings.coverDisplayOverride)
        val closed = state.folded != false
        return when {
            coverDisplay != null && closed -> coverDisplay.id
            settings.lockCoverOnly -> null
            else -> Display.DEFAULT_DISPLAY
        }
    }

    private fun show() {
        val settings = settingsRepository.settings.value
        if (settings.lockMode == LockMode.OFF) return
        val displayId = targetDisplayId() ?: return
        when (settings.lockMode) {
            LockMode.ACTIVITY -> startLockActivity(displayId, autoDismiss = false)
            LockMode.OVERLAY -> showOverlay(displayId)
            LockMode.OFF -> Unit
        }
    }

    private fun startLockActivity(displayId: Int, autoDismiss: Boolean) {
        // O serviço de acessibilidade pode abrir activities em segundo plano; o contexto do app não.
        val starter: Context = host?.get() ?: context
        try {
            val intent = Intent(context, LockActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_USER_ACTION,
                )
                .putExtra(LockActivity.EXTRA_AUTO_DISMISS, autoDismiss)
            starter.startActivity(intent, ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle())
        } catch (t: Throwable) {
            Logx.w("Não foi possível abrir a tela de bloqueio na tela $displayId", t)
        }
    }

    private fun showOverlay(displayId: Int) {
        val service = host?.get() ?: return
        val current = overlay
        if (current != null && current.displayId == displayId && current.isShowing) return
        hideOverlay()
        val display = displayRepository.getDisplay(displayId) ?: return
        val created = LockOverlay(service, display) {
            LockOverlayContent(presenter = presenter, onUnlock = ::unlockFromOverlay, onHide = ::hideOverlay)
        }
        if (created.show()) overlay = created
    }

    private fun hideOverlay() {
        safely("fechar overlay de bloqueio") { overlay?.hide() }
        overlay = null
    }
}
