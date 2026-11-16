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
import app.cristal.cover.ui.island.IslandSpace
import app.cristal.cover.data.notifications.NotificationItem
import app.cristal.cover.data.island.StatusBarMetricsStore
import app.cristal.cover.data.island.StatusBarMetrics
import app.cristal.cover.ui.island.IslandGeometry
import app.cristal.cover.ui.island.IslandSizing
import app.cristal.cover.ui.island.IslandTarget
import app.cristal.cover.ui.island.LiquidIsland
import app.cristal.cover.ui.island.BriefFlightLayer
import app.cristal.cover.data.brief.BriefFlight
import app.cristal.cover.data.brief.BriefRepository
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
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
    private val briefRepository: BriefRepository,
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
    private var statusBarPx = 0
    private var windowContext: android.content.Context? = null
    private var geometry: IslandGeometry? = null
    private val flights = ArrayList<ComposeView>()

    /** O app em primeiro plano escondeu a barra de status (vídeo, jogo) ou a tela está deitada. */
    private val fullscreen = mutableStateOf(false)

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
            statusBarPx = safely("altura da barra de status") {
                metrics.windowInsets.getInsets(android.view.WindowInsets.Type.statusBars()).top
            } ?: (32 * density).roundToInt()
            val baseGeometry = geometryFor(cutoutTop, cutoutHeight)

            val newOwner = OverlayOwner().also { it.start() }
            val composeView = ComposeView(context).apply {
                setViewTreeLifecycleOwner(newOwner)
                setViewTreeViewModelStoreOwner(newOwner)
                setViewTreeSavedStateRegistryOwner(newOwner)
                setContent { Host(baseGeometry) }
                // Barra de status escondida pelo app da frente = tela cheia: a ilha se recolhe.
                setOnApplyWindowInsetsListener { v, insets ->
                    val landscape = v.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    fullscreen.value = landscape || !insets.isVisible(android.view.WindowInsets.Type.statusBars())
                    insets
                }
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
            windowContext = context
            geometry = baseGeometry
            briefRepository.islandReady.value = true
            // Info Brief: a esfera que vem do teclado voa numa janela própria até a ilha.
            newOwner.lifecycleScope.launch { briefRepository.flights.collect { fly(it) } }
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
        briefRepository.islandReady.value = false
        flights.toList().forEach(::removeFlight)
        pendingShrink?.let(handler::removeCallbacks)
        pendingShrink = null
        safely("remover a Liquid Island") { windowManager?.removeViewImmediate(v) }
        safely("descartar a composição da ilha") { v.disposeComposition() }
        owner?.stop()
        owner = null
        windowManager = null
        params = null
        windowContext = null
    }

    /**
     * Voo da esfera do Info Brief: de onde ela saiu do teclado até a câmera. Uma janela de tela
     * inteira que não recebe toques e some ao chegar; a ilha abre a sugestão no pouso.
     */
    private fun fly(flight: BriefFlight) {
        val context = windowContext
        val wm = windowManager
        val g = geometry
        if (context == null || wm == null || g == null) {
            briefRepository.show(flight.suggestion)
            return
        }
        val target = androidx.compose.ui.geometry.Offset(
            cameraCenterX.toFloat(),
            (g.topPadding.value + g.pillHeight.value / 2f) * density,
        )
        val flightOwner = OverlayOwner().also { it.start() }
        var flightView: ComposeView? = null
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(flightOwner)
            setViewTreeViewModelStoreOwner(flightOwner)
            setViewTreeSavedStateRegistryOwner(flightOwner)
            setContent {
                BriefFlightLayer(
                    origin = androidx.compose.ui.geometry.Offset(flight.originX, flight.originY),
                    target = target,
                    onArrive = { briefRepository.show(flight.suggestion) },
                    onDone = {
                        flightView?.let(::removeFlight)
                        flightOwner.stop()
                    },
                )
            }
        }
        flightView = composeView
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "InfoBriefFlight"
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        val added = safely("mostrar o voo do Info Brief") {
            wm.addView(composeView, p)
            true
        } ?: false
        if (!added) {
            flightOwner.stop()
            briefRepository.show(flight.suggestion)
            return
        }
        flights += composeView
        // Garantia: mesmo se a animação não terminar, a janela some.
        handler.postDelayed({
            if (composeView in flights) {
                removeFlight(composeView)
                flightOwner.stop()
            }
        }, 3_000L)
    }

    private fun removeFlight(v: ComposeView) {
        if (!flights.remove(v)) return
        safely("remover o voo do Info Brief") { windowManager?.removeViewImmediate(v) }
        safely("descartar o voo do Info Brief") { v.disposeComposition() }
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
        val notifications by notificationRepository.items.collectAsState()
        val measured by StatusBarMetricsStore.state.collectAsState()
        val isFullscreen by fullscreen
        val space = remember(notifications, measured, isFullscreen) { spaceFor(notifications, measured, isFullscreen) }
        val target = IslandSizing.target(state, geometry, expanded, settings.islandAlwaysVisible, space)
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
            space = space,
            style = settings.islandStyle,
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
                onDismiss = {
                    expanded = false
                    repository.dismissTransient()
                },
                onBriefAccept = briefRepository::accept,
                onBriefLater = briefRepository::later,
                onAssist = {
                    if (settings.islandLily) app.cristal.cover.data.lily.LilyLink.voice(service, Display.DEFAULT_DISPLAY)
                },
            ),
        )
    }

    /**
     * Espaço livre ao redor da câmera. Com o modo de precisão, usa a posição real dos ícones; sem
     * ele, estima: relógio e um ícone por app com notificação à esquerda; sinal, Wi-Fi, bateria e
     * mais um ícone à direita. Sempre com 8 dp de folga de cada lado.
     */
    private fun spaceFor(notifications: List<NotificationItem>, measured: StatusBarMetrics?, isFullscreen: Boolean): IslandSpace {
        val barDp = (statusBarPx / density).dp
        val cx = cameraCenterX / density
        val (leftEdge, rightStart) = if (measured != null && System.currentTimeMillis() - measured.measuredAt < 60_000) {
            measured.leftEdgePx / density to measured.rightStartPx / density
        } else {
            val icons = notifications
                .filter { it.importance >= android.app.NotificationManager.IMPORTANCE_LOW }
                .map { it.packageName }
                .distinct()
                .size
                .coerceAtMost(6)
            val left = 16f + 44f + if (icons > 0) 6f + icons * 18f else 0f
            val right = screenWidth / density - (16f + 4 * 20f)
            left to right
        }
        val half = minOf(cx - (leftEdge + 8f), (rightStart - 8f) - cx).coerceAtLeast(0f)
        return IslandSpace(available = (half * 2f).dp, fullscreen = isFullscreen, statusBarHeight = barDp)
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
        val w = (px(target.totalWidth) + bubble + px(8.dp)).coerceAtMost(screenWidth)
        // A lente quica um pouco ao inchar: folga maior embaixo enquanto ela está aberta.
        val h = px(geometry.topPadding) + px(target.totalHeight) + px(if (target.lens != null) 14.dp else 6.dp)
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
        const val SHRINK_DELAY_MS = 820L
    }
}
