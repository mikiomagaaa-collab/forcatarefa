package app.cristal.cover.service

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Display
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.cristal.cover.core.Logx

/**
 * Janela de acessibilidade que mostra a tela de bloqueio do Cristal por cima do keyguard, em uma
 * tela específica. Usada só no modo "Sobreposição" (alternativa para quando o fabricante não deixa
 * uma activity aparecer sobre o bloqueio da tela externa).
 */
internal class LockOverlay(
    private val service: AccessibilityService,
    private val display: Display,
    private val content: @Composable () -> Unit,
) {
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    private var windowManager: WindowManager? = null

    val displayId: Int get() = display.displayId
    val isShowing: Boolean get() = view != null

    fun show(): Boolean {
        if (view != null) return true
        return try {
            val windowContext = service.createDisplayContext(display)
                .createWindowContext(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null)
            val wm = windowContext.getSystemService(WindowManager::class.java) ?: return false
            val newOwner = OverlayOwner().also { it.start() }
            val composeView = ComposeView(windowContext).apply {
                setViewTreeLifecycleOwner(newOwner)
                setViewTreeViewModelStoreOwner(newOwner)
                setViewTreeSavedStateRegistryOwner(newOwner)
                setContent(content)
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT,
            ).apply {
                title = "CristalLock"
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            wm.addView(composeView, params)
            view = composeView
            owner = newOwner
            windowManager = wm
            true
        } catch (t: Throwable) {
            Logx.w("Não foi possível mostrar o overlay de bloqueio", t)
            owner?.stop()
            owner = null
            view = null
            false
        }
    }

    fun hide() {
        val v = view ?: return
        view = null
        try {
            windowManager?.removeViewImmediate(v)
        } catch (t: Throwable) {
            Logx.w("Falha ao remover overlay de bloqueio", t)
        }
        try {
            v.disposeComposition()
        } catch (t: Throwable) {
            Logx.w("Falha ao descartar composição do overlay", t)
        }
        owner?.stop()
        owner = null
        windowManager = null
    }
}

/** Dono de ciclo de vida mínimo para o Compose funcionar fora de uma Activity. */
private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()

    fun start() {
        controller.performAttach()
        controller.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun stop() {
        if (registry.currentState == Lifecycle.State.DESTROYED) return
        if (registry.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        viewModelStore.clear()
    }
}
