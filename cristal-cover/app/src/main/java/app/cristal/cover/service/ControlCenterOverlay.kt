package app.cristal.cover.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.cristal.cover.core.Logx
import app.cristal.cover.core.safely
import app.cristal.cover.data.battery.BatteryRepository
import app.cristal.cover.data.control.ControlCenterRepository
import app.cristal.cover.data.control.ControlKind
import app.cristal.cover.data.display.CoverDisplayRepository
import app.cristal.cover.data.media.MediaRepository
import app.cristal.cover.data.notifications.NotificationRepository
import app.cristal.cover.data.prefs.SettingsRepository
import app.cristal.cover.data.system.QuickSettingsRepository
import app.cristal.cover.data.system.SystemBridge
import app.cristal.cover.ui.components.SheetState
import app.cristal.cover.ui.glass.LocalGlassBackdrop
import app.cristal.cover.ui.glass.glassBackdropSource
import app.cristal.cover.ui.glass.rememberGlassBackdrop
import app.cristal.cover.ui.launcher.ControlCenter
import app.cristal.cover.ui.launcher.ControlCenterActions
import app.cristal.cover.ui.launcher.ControlCenterController
import app.cristal.cover.ui.launcher.ControlSink
import app.cristal.cover.ui.launcher.LauncherActivity
import app.cristal.cover.ui.launcher.SafeLayout
import app.cristal.cover.ui.settings.SettingsActivity
import app.cristal.cover.ui.theme.CristalTheme
import app.cristal.cover.ui.widgets.WidgetActions
import app.cristal.cover.ui.widgets.WidgetData
import java.util.concurrent.Executor
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * A Liquid Control Center sobre qualquer app: uma faixa invisível no canto superior direito de
 * cada tela (interna e externa); puxar para baixo dali faz a central descer acompanhando o dedo,
 * como no iPhone. O vidro reflete o app de baixo graças a uma captura da tela feita no toque, que
 * fica só na memória e some quando a central fecha. Não aparece com a tela bloqueada nem em tela
 * cheia (vídeo, jogo).
 */
internal class ControlCenterOverlay(
    private val service: AccessibilityService,
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val quickSettingsRepository: QuickSettingsRepository,
    private val controlCenterRepository: ControlCenterRepository,
    private val notificationRepository: NotificationRepository,
    private val mediaRepository: MediaRepository,
    private val batteryRepository: BatteryRepository,
    private val systemBridge: SystemBridge,
    private val displayRepository: CoverDisplayRepository,
    private val appLauncher: app.cristal.cover.data.launch.AppLauncher,
) {
    private val main = Handler(Looper.getMainLooper())
    private val handles = HashMap<Int, Pair<WindowManager, View>>()
    private var panel: Panel? = null

    /** Comandos para a central aberta (rodam dentro da composição, com o relógio de quadros dela). */
    private sealed interface Command {
        data class Drag(val fraction: Float) : Command
        data class Settle(val velocity: Float) : Command
        data object Open : Command
        data object Close : Command
    }

    private class Panel(
        val displayId: Int,
        val windowManager: WindowManager,
        val root: FrameLayout,
        val owner: OverlayOwner,
        val commands: MutableSharedFlow<Command>,
        var heightPx: Float,
        screenshot: ImageBitmap?,
    ) {
        /** A captura pode chegar depois de a central começar a descer. */
        var screenshot by mutableStateOf(screenshot)
    }

    val isShowing: Boolean get() = handles.isNotEmpty()

    /** Põe (ou atualiza) as faixas nas telas que existem agora. */
    fun show() {
        val displays = service.getSystemService(DisplayManager::class.java)?.displays.orEmpty()
        val wanted = displays.filter { it.displayId == Display.DEFAULT_DISPLAY || it.displayId == displayRepository.state.value.coverId }
            .map { it.displayId }.toSet()
        (handles.keys - wanted).forEach(::removeHandle)
        for (d in displays) if (d.displayId in wanted && d.displayId !in handles) addHandle(d)
    }

    fun hide() {
        handles.keys.toList().forEach(::removeHandle)
        panel?.let { closeNow(it) }
    }

    // --- Faixa no canto -------------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    private fun addHandle(display: Display) {
        val context = service.createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null)
        val wm = context.getSystemService(WindowManager::class.java) ?: return
        val metrics = wm.currentWindowMetrics
        val density = context.resources.displayMetrics.density
        val top = safely("barra de status") { metrics.windowInsets.getInsets(WindowInsets.Type.statusBars()).top } ?: 0
        val width = (metrics.bounds.width() * HANDLE_WIDTH).toInt()
        val height = maxOf(top, (28 * density).toInt())
        val view = View(context)
        val params = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            title = "ControlCenterHandle"
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        view.setOnTouchListener(HandleTouch(display.displayId, density))
        // Tela cheia (vídeo, jogo) ou deitado: a faixa deixa de pegar toques.
        view.setOnApplyWindowInsetsListener { v, insets ->
            val landscape = v.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val off = landscape || !insets.isVisible(WindowInsets.Type.statusBars())
            val flags = if (off) params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            if (flags != params.flags) {
                params.flags = flags
                safely("atualizar a faixa da central") { wm.updateViewLayout(v, params) }
            }
            insets
        }
        val added = safely("mostrar a faixa da central") {
            wm.addView(view, params)
            true
        } ?: false
        if (added) handles[display.displayId] = wm to view
    }

    private fun removeHandle(displayId: Int) {
        val (wm, view) = handles.remove(displayId) ?: return
        safely("remover a faixa da central") { wm.removeViewImmediate(view) }
    }

    /** Puxar para baixo a partir da faixa: a central acompanha o dedo; soltar decide abrir ou fechar. */
    private inner class HandleTouch(private val displayId: Int, density: Float) : View.OnTouchListener {
        private val slop = 10 * density
        private var downY = 0f
        private var lastY = 0f
        private var dragging = false
        private var shot: Bitmap? = null
        private var velocity: VelocityTracker? = null

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (locked() || panel != null) return false
                    downY = e.rawY
                    lastY = e.rawY
                    dragging = false
                    shot = null
                    velocity = VelocityTracker.obtain().also { it.addMovement(e) }
                    // A captura sai antes da central aparecer (senão ela entraria na foto).
                    capture(displayId) { shot = it }
                }
                MotionEvent.ACTION_MOVE -> {
                    velocity?.addMovement(e)
                    if (!dragging && e.rawY - downY > slop) {
                        dragging = true
                        openPanel(displayId, shot, interactive = true)
                        lastY = e.rawY
                    }
                    if (dragging) {
                        val p = panel ?: return true
                        p.commands.tryEmit(Command.Drag((e.rawY - lastY) / p.heightPx))
                        lastY = e.rawY
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    velocity?.addMovement(e)
                    velocity?.computeCurrentVelocity(1000)
                    val vy = velocity?.yVelocity ?: 0f
                    velocity?.recycle()
                    velocity = null
                    val p = panel
                    if (dragging && p != null) p.commands.tryEmit(Command.Settle(vy / p.heightPx))
                    dragging = false
                }
            }
            return true
        }
    }

    private fun locked(): Boolean = service.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    /** Captura a tela para o vidro (só na memória; nada é gravado). */
    private fun capture(displayId: Int, done: (Bitmap?) -> Unit) {
        val executor = Executor { main.post(it) }
        try {
            service.takeScreenshot(
                displayId,
                executor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        val buffer = result.hardwareBuffer
                        val bitmap = safely("ler a captura") { Bitmap.wrapHardwareBuffer(buffer, result.colorSpace) }
                        buffer.close()
                        done(bitmap)
                        panel?.takeIf { it.displayId == displayId && it.screenshot == null }?.let { p ->
                            p.screenshot = bitmap?.asImageBitmap()
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        Logx.d("Captura para a central indisponível ($errorCode)")
                        done(null)
                    }
                },
            )
        } catch (t: Throwable) {
            Logx.w("Sem captura para a central", t)
            done(null)
        }
    }

    // --- Central aberta -------------------------------------------------------------------------

    private fun openPanel(displayId: Int, shot: Bitmap?, interactive: Boolean) {
        if (panel != null) return
        val display = service.getSystemService(DisplayManager::class.java)?.getDisplay(displayId) ?: return
        val context = service.createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null)
        val wm = context.getSystemService(WindowManager::class.java) ?: return
        val metrics = wm.currentWindowMetrics
        val density = context.resources.displayMetrics.density
        val insets = metrics.windowInsets
        val bars = safely("barras do sistema") { insets.getInsets(WindowInsets.Type.systemBars()) }
        val safe = SafeLayout(
            top = ((bars?.top ?: 0) / density).dp,
            bottom = ((bars?.bottom ?: 0) / density).dp,
        )
        val onCover = displayId != Display.DEFAULT_DISPLAY
        val owner = OverlayOwner().also { it.start() }
        val commands = MutableSharedFlow<Command>(extraBufferCapacity = 64)
        val root = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) commands.tryEmit(Command.Close)
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        val p = Panel(displayId, wm, root, owner, commands, metrics.bounds.height().toFloat(), shot?.asImageBitmap())
        val compose = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent { PanelContent(p, safe, onCover, interactive) }
        }
        root.addView(compose)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "LiquidControlCenter"
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        val added = safely("abrir a central") {
            wm.addView(root, params)
            true
        } ?: false
        if (!added) {
            owner.stop()
            return
        }
        panel = p
        // Gesto de voltar (Android 13+): fecha a central em vez de voltar no app de baixo.
        safely("voltar fecha a central") {
            root.findOnBackInvokedDispatcher()?.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                OnBackInvokedCallback { commands.tryEmit(Command.Close) },
            )
        }
    }

    /** Abre a central já toda aberta (pelo bloco de Ajustes rápidos ou um atalho). */
    fun openAnimated(displayId: Int) {
        if (panel != null || locked()) return
        capture(displayId) { shot ->
            openPanel(displayId, shot, interactive = false)
            panel?.commands?.tryEmit(Command.Open)
        }
    }

    private fun closeNow(p: Panel) {
        if (panel === p) panel = null
        safely("fechar a central") { p.windowManager.removeViewImmediate(p.root) }
        safely("descartar a central") { (p.root.getChildAt(0) as? ComposeView)?.disposeComposition() }
        p.owner.stop()
    }

    private fun startOnDisplay(intent: Intent, displayId: Int) {
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
        runCatching { service.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options.toBundle()) }
            .onFailure { Logx.w("Não foi possível abrir pela central", it) }
    }

    @Composable
    private fun PanelContent(p: Panel, safe: SafeLayout, onCover: Boolean, interactive: Boolean) {
        val settings by settingsRepository.settings.collectAsState()
        val quick by quickSettingsRepository.state.collectAsState()
        val layout by controlCenterRepository.layout(onCover).collectAsState()
        val notifications by notificationRepository.items.collectAsState()
        val access by notificationRepository.connected.collectAsState()
        val media by mediaRepository.state.collectAsState()
        val battery by batteryRepository.state.collectAsState()
        val state = remember { SheetState() }
        val uiScope = rememberCoroutineScope()
        fun close() {
            uiScope.launch { state.close() }
        }
        val controller = remember {
            ControlCenterController(
                quickSettingsRepository,
                controlCenterRepository,
                systemBridge,
                scope,
                object : ControlSink {
                    override fun openIntent(intent: Intent) {
                        close()
                        startOnDisplay(intent, p.displayId)
                    }

                    override fun message(text: String) {
                        Toast.makeText(service, text, Toast.LENGTH_LONG).show()
                    }
                },
            )
        }
        LaunchedEffect(Unit) {
            p.commands.collect { c ->
                when (c) {
                    is Command.Drag -> state.dragBy(c.fraction)
                    is Command.Settle -> state.settle(c.velocity)
                    Command.Open -> state.open()
                    Command.Close -> state.close()
                }
            }
        }
        // Fechou (arrastando para cima, voltar ou ação): tira a janela.
        LaunchedEffect(Unit) {
            if (interactive) snapshotFlow { state.progress.value > 0.01f }.first { it }
            snapshotFlow { state.progress.value <= 0.001f && state.progress.targetValue < 0.5f }.first { it }
            main.post { closeNow(p) }
        }
        val widgetData = WidgetData(battery = battery, media = media, notificationAccess = access, clockFont = settings.lockClockFont)
        val widgetActions = WidgetActions(
            onMediaOpen = {
                close()
                mediaRepository.openPlayer(p.displayId)
            },
            onMediaPlayPause = mediaRepository::playPause,
            onMediaNext = mediaRepository::next,
            onMediaPrevious = mediaRepository::previous,
        )
        CristalTheme(settings = settings) {
            val backdrop = rememberGlassBackdrop()
            val shot = p.screenshot
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val amount = state.progress.value.coerceIn(0f, 1f)
                            renderEffect = if (shot != null && amount > 0.01f) BlurEffect(36f * amount * density, 36f * amount * density, TileMode.Clamp) else null
                        }
                        .glassBackdropSource(backdrop),
                ) {
                    if (shot != null) Image(shot, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = state.progress.value.coerceIn(0f, 1f) }
                        .background(Color.Black.copy(alpha = if (shot != null) 0.22f else 0.6f)),
                )
                CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                    ControlCenter(
                        state = state,
                        safe = safe,
                        quick = quick,
                        layout = layout,
                        widgetData = widgetData,
                        widgetActions = widgetActions,
                        notifications = notifications,
                        notificationAccess = access,
                        onCover = onCover,
                        actions = ControlCenterActions(
                            onControl = { kind ->
                                when (kind) {
                                    ControlKind.SCREENSHOT -> uiScope.launch {
                                        state.close()
                                        main.postDelayed({ controller.takeScreenshot() }, 250)
                                    }
                                    ControlKind.SEARCH -> {
                                        close()
                                        startOnDisplay(Intent(service, LauncherActivity::class.java), p.displayId)
                                    }
                                    ControlKind.WALLPAPER -> {
                                        close()
                                        startOnDisplay(Intent(service, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_PAGE, SettingsActivity.PAGE_WALLPAPER), p.displayId)
                                    }
                                    ControlKind.SETTINGS -> {
                                        close()
                                        startOnDisplay(Intent(service, SettingsActivity::class.java), p.displayId)
                                    }
                                    ControlKind.MEDIA -> {
                                        close()
                                        mediaRepository.openPlayer(p.displayId)
                                    }
                                    ControlKind.LILY -> {
                                        close()
                                        scope.launch {
                                            // Na tela externa, a L.I.L.Y é liberada antes (senão a Motorola a fecha ao abrir).
                                            if (displayRepository.state.value.coverId == p.displayId) {
                                                appLauncher.allowOnCover(app.cristal.cover.data.lily.LilyLink.PACKAGE)
                                            }
                                            if (!app.cristal.cover.data.lily.LilyLink.voice(service, p.displayId)) {
                                                Toast.makeText(service, "Instale a L.I.L.Y AI para falar com ela.", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                    else -> controller.runControl(kind)
                                }
                            },
                            onControlSettings = controller::controlSettings,
                            onBrightness = controller::setBrightness,
                            onBrightnessEnd = controller::endBrightnessDrag,
                            onAutoBrightness = controller::toggleAutoBrightness,
                            onVolume = controller::setMediaVolume,
                            onSaveLayout = { controller.saveLayout(onCover, it) },
                            onPreset = { controller.applyPreset(onCover, it) },
                            onSystemPanel = {
                                close()
                                controller.openSystemQuickSettings()
                            },
                            onOpenNotification = { item ->
                                close()
                                notificationRepository.open(item, p.displayId)
                            },
                            onDismissNotification = notificationRepository::dismiss,
                            onClearAll = notificationRepository::dismissAll,
                            onRequestNotificationAccess = {
                                close()
                                startOnDisplay(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), p.displayId)
                            },
                            onPower = {
                                close()
                                systemBridge.powerDialog()
                            },
                        ),
                    )
                }
            }
        }
    }

    private companion object {
        /** A faixa ocupa este tanto da largura, a partir da direita (como no iPhone). */
        const val HANDLE_WIDTH = 0.34f
    }
}
