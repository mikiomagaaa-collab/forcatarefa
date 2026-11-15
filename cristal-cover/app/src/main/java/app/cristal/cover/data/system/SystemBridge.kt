package app.cristal.cover.data.system

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import app.cristal.cover.core.safely
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Ponte entre a UI e o serviço de acessibilidade. A UI nunca depende do serviço estar ligado:
 * se não estiver, as ações globais simplesmente devolvem `false` e a UI oferece a alternativa.
 */
@Singleton
class SystemBridge @Inject constructor() {
    private var service: WeakReference<AccessibilityService>? = null

    private val _connected = MutableStateFlow(false)
    val accessibilityConnected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _topPackages = MutableStateFlow<Map<Int, String>>(emptyMap())

    /** Último pacote em primeiro plano por tela (só é preenchido com a acessibilidade ligada). */
    val topPackages: StateFlow<Map<Int, String>> = _topPackages.asStateFlow()

    /** Até quando o "assumir a tela externa" fica pausado (relógio [SystemClock.elapsedRealtime]). */
    @Volatile var takeoverPausedUntil: Long = 0L
        private set

    fun attach(accessibilityService: AccessibilityService) {
        service = WeakReference(accessibilityService)
        _connected.value = true
    }

    fun detach() {
        service = null
        _connected.value = false
        _topPackages.value = emptyMap()
    }

    fun onWindowChanged(displayId: Int, packageName: String) {
        val current = _topPackages.value
        if (current[displayId] != packageName) _topPackages.value = current + (displayId to packageName)
    }

    /** Pausa o "assumir a tela externa" por um tempo, para o usuário chegar à tela da Motorola. */
    fun pauseTakeover(millis: Long) {
        takeoverPausedUntil = SystemClock.elapsedRealtime() + millis
    }

    private fun global(action: Int): Boolean =
        safely("ação global de acessibilidade $action") { service?.get()?.performGlobalAction(action) } ?: false

    fun lockScreen(): Boolean = global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
    fun openNotifications(): Boolean = global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
    fun openQuickSettings(): Boolean = global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
    fun openRecents(): Boolean = global(AccessibilityService.GLOBAL_ACTION_RECENTS)
    fun goHome(): Boolean = global(AccessibilityService.GLOBAL_ACTION_HOME)
    fun takeScreenshot(): Boolean = global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
}
