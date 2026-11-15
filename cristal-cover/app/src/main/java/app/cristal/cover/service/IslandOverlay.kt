package app.cristal.cover.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.cristal.cover.core.Logx
import app.cristal.cover.core.safely
import app.cristal.cover.data.island.IslandRepository
import app.cristal.cover.data.media.MediaRepository
import app.cristal.cover.data.notifications.NotificationRepository
import app.cristal.cover.data.prefs.SettingsRepository
import app.cristal.cover.ui.island.IslandActions
import app.cristal.cover.ui.island.IslandGeometry
import app.cristal.cover.ui.island.IslandSizing
import app.cristal.cover.ui.island.IslandTarget
import app.cristal.cover.ui.island.LiquidIsland
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Janela da Liquid Island: uma janela de acessibilidade pequena, centralizada no furo da câmera da
 * tela interna, que acompanha o tamanho da cápsula (assim ela nunca bloqueia toques na barra de
 * status, a não ser em cima da própria ilha).
 */
internal class IslandOverlay(
    private val service: AccessibilityService,
    private val repository: IslandRepository,
    private val settingsRepository: SettingsRepository,
    private val mediaRepository: MediaRepository,
    private val notificationRepository: NotificationRepository,
) {
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    private var windowManager: WindowManager? = null
    private var params: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pendingShrink: Runnable? = null
    private var density = 1f
    private var cameraCenterX = 0
    private var screenWidth = 0

    val isShowing: Boolean get() = view != null

    fun show(): Boolean {
        if (view != null) return true
        return try {
            val display = service.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY) ?: return false
            val context = service.createDisplayContext(display)
                .createWindowContext(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null)
            val wm = context.getSystemService(WindowManager::class.java) ?: return false
            density = context.resources.displayMetrics.density
            val metrics = wm.currentWindowMetrics
            screenWidth = metrics.bounds.width()
            val cutout = safely("ler o furo da câmera") { metrics.windowInsets.displayCutout?.boundingRectTop }?.takeIf { !it.isEmpty }
            cameraCenterX = cutout?.centerX() ?: (screenWidth / 2)
            val cutoutHeight = cutout?.height() ?: (22 * density).roundToInt()
            val cutoutTop = cutout?.top ?: (8 * density).roundToInt()
            val baseGeometry = geometryFor(cutoutTop, cutoutHeight)

            val newOwner = OverlayOwner().also { it.start() }
            val composeView = ComposeView(context).apply {
                setViewTreeLifecycleOwner(newOwner)
                setViewTreeViewModelStoreOwner(newOwner)
                setViewTreeSavedStateRegistryOwner(newOwner)
                setContent { Host(baseGeometry) }
            }
            val p = WindowManager.LayoutParams(
                1,
                1,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = cameraCenterX
                y = 0
                title = "LiquidIsland"
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            wm.addView(composeView, p)
            view = composeView
            owner = newOwner
            windowManager = wm
            params = p
            true
        } catch (t: Throwable) {
            Logx.w("Não foi possível mostrar a Liquid Island", t)
            owner?.stop()
            owner = null
            view = null
            false
        }
    }

    fun hide() {
        val v = view ?: return
        view = null
        pendingShrink?.let(handler::removeCallbacks)
        pendingShrink = null
        safely("remover a Liquid Island") { windowManager?.removeViewImmediate(v) }
        safely("descartar a composição da ilha") { v.disposeComposition() }
        owner?.stop()
        owner = null
        windowManager = null
        params = null
    }

    /** Medidas em dp a partir do furo da câmera (em px). */
    private fun geometryFor(cutoutTop: Int, cutoutHeight: Int): IslandGeometry {
        val pill = (cutoutHeight / density + 12f).coerceIn(30f, 40f)
        val top = (cutoutTop / density - (pill - cutoutHeight / density) / 2f).coerceAtLeast(0f)
        val screenDp = screenWidth / density
        return IslandGeometry(
            pillHeight = pill.dp,
            idleWidth = (pill * 3.1f).dp,
            compactWidth = (screenDp * 0.6f).coerceIn(210f, 260f).dp,
            wideWidth = (screenDp - 18f).coerceAtMost(420f).dp,
            topPadding = top.dp,
        )
    }

    @Composable
    private fun Host(base: IslandGeometry) {
        val state by repository.state.collectAsState()
        val settings by settingsRepository.settings.collectAsState()
        var expanded by remember { mutableStateOf(false) }
        val geometry = base.copy(topPadding = (base.topPadding + settings.islandOffsetY.dp).coerceAtLeast(0.dp))
        val target = IslandSizing.target(state, geometry, expanded, settings.islandAlwaysVisible)
        LaunchedEffect(target, geometry) { resizeFor(target, geometry) }
        val keyguard = remember { service.getSystemService(KeyguardManager::class.java) }
        val locked = remember(state) { safely("ler o bloqueio") { keyguard?.isKeyguardLocked } ?: false }
        LiquidIsland(
            state = state,
            geometry = geometry,
            alwaysVisible = settings.islandAlwaysVisible,
            concealAlerts = locked,
            expanded = expanded,
            onExpandedChange = { expanded = it },
            actions = IslandActions(
                onMediaOpen = {
                    expanded = false
                    mediaRepository.openPlayer(Display.DEFAULT_DISPLAY)
                },
                onPlayPause = mediaRepository::playPause,
                onNext = mediaRepository::next,
                onPrevious = mediaRepository::previous,
                onOpenNotification = { item ->
                    repository.dismissTransient()
                    notificationRepository.open(item, Display.DEFAULT_DISPLAY)
                },
                onDismiss = repository::dismissTransient,
            ),
        )
    }

    /**
     * Ajusta a janela à cápsula. Crescer é imediato (a animação acontece dentro da janela);
     * encolher espera a animação terminar. Invisível, a janela deixa de receber toques.
     */
    private fun resizeFor(target: IslandTarget, geometry: IslandGeometry) {
        val p = params ?: return
        val wm = windowManager ?: return
        val v = view ?: return
        fun px(dp: Dp) = (dp.value * density).roundToInt()
        val bubble = if (target.withBubble) 2 * (px(geometry.pillHeight) + px(IslandSizing.BUBBLE_GAP)) else 0
        val w = (px(target.width) + bubble + px(8.dp)).coerceAtMost(screenWidth)
        val h = px(geometry.topPadding) + px(target.height) + px(6.dp)
        pendingShrink?.let(handler::removeCallbacks)
        pendingShrink = null

        fun apply(width: Int, height: Int, touchable: Boolean) {
            p.width = width
            p.height = height
            p.x = (cameraCenterX - width / 2).coerceIn(0, (screenWidth - width).coerceAtLeast(0))
            p.flags = if (touchable) {
                p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            } else {
                p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
            safely("redimensionar a Liquid Island") { wm.updateViewLayout(v, p) }
        }

        if (!target.visible) {
            // Some com a animação e depois encolhe a janela até quase nada.
            apply(max(p.width, w), max(p.height, h), touchable = false)
            pendingShrink = Runnable { apply(1, 1, touchable = false) }.also { handler.postDelayed(it, SHRINK_DELAY_MS) }
            return
        }
        val growW = max(p.width, w)
        val growH = max(p.height, h)
        apply(growW, growH, touchable = true)
        if (growW != w || growH != h) {
            pendingShrink = Runnable { apply(w, h, touchable = true) }.also { handler.postDelayed(it, SHRINK_DELAY_MS) }
        }
    }

    private companion object {
        const val SHRINK_DELAY_MS = 650L
    }
}
