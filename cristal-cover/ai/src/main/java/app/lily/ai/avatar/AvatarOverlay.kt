package app.lily.ai.avatar

import android.app.ActivityOptions
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.lily.ai.AiGraph
import app.lily.ai.presence.AppVisibility
import app.lily.ai.presence.LilyPresence
import app.lily.ai.presence.Presence
import app.lily.ai.presence.PresenceMode
import app.lily.ai.ui.MainActivity
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * A avatar flutuante por cima de outros apps. Só aparece se a pessoa ligar (Configurações > Avatar),
 * se o Android permitir a sobreposição e quando a própria L.I.L.Y não está na tela. No modo "durante
 * tarefas" ela some quando a L.I.L.Y volta a ficar parada. Tocar abre a L.I.L.Y; segurar esconde até
 * a próxima tarefa; arrastar muda de lugar. Na tela externa do Razr usa a forma compacta.
 */
class AvatarOverlay(private val graph: AiGraph, private val app: Application) {
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    private var params: WindowManager.LayoutParams? = null
    private var windowManager: WindowManager? = null
    private var hiddenUntilNext = false
    private var lastMode = PresenceMode.IDLE

    /** Mensagem para a tela de configurações quando a sobreposição não pode aparecer. */
    val blockedReason: String?
        get() = if (canDraw(app)) null else "O Android ainda não permite que a L.I.L.Y apareça por cima de outros apps."

    fun start() {
        graph.scope.launch {
            combine(
                LilyPresence.state,
                graph.settings.value.map { it.avatar }.distinctUntilChanged(),
                AppVisibility.foreground,
            ) { presence, look, foreground -> Triple(presence, look, foreground) }
                .collect { (presence, look, foreground) -> update(presence, look, foreground) }
        }
    }

    private fun update(presence: Presence, look: AvatarLook, foreground: Boolean) {
        // Uma tarefa nova traz a avatar de volta depois de "esconder até a próxima".
        if (lastMode == PresenceMode.IDLE && presence.mode != PresenceMode.IDLE) hiddenUntilNext = false
        lastMode = presence.mode
        val wanted = look.enabled && look.overlay != OverlayMode.OFF && canDraw(app) && !foreground && !hiddenUntilNext &&
            (look.overlay == OverlayMode.ALWAYS || presence.mode != PresenceMode.IDLE)
        if (wanted && view == null) runCatching { attach(look) }.onFailure { detach() }
        if (!wanted && view != null) detach()
    }

    /** A tela acesa agora: a interna, ou a externa quando o Razr está fechado. */
    private fun activeDisplay(): Display? {
        val dm = app.getSystemService(DisplayManager::class.java) ?: return null
        val on = dm.displays.filter { it.state == Display.STATE_ON }
        return on.firstOrNull { it.displayId == Display.DEFAULT_DISPLAY } ?: on.firstOrNull() ?: dm.getDisplay(Display.DEFAULT_DISPLAY)
    }

    private fun attach(look: AvatarLook) {
        val display = activeDisplay() ?: return
        val context = app.createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        val compact = context.resources.configuration.smallestScreenWidthDp < 480
        val wm = context.getSystemService(WindowManager::class.java)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = when (look.corner) {
                OverlayCorner.TOP_START -> Gravity.TOP or Gravity.START
                OverlayCorner.TOP_END -> Gravity.TOP or Gravity.END
                OverlayCorner.BOTTOM_START -> Gravity.BOTTOM or Gravity.START
                OverlayCorner.BOTTOM_END -> Gravity.BOTTOM or Gravity.END
            }
            val density = context.resources.displayMetrics.density
            x = (16 * density).toInt()
            y = ((if (look.corner.name.startsWith("TOP")) 96 else 120) * density).toInt()
            title = "L.I.L.Y"
        }
        val ownerNow = OverlayOwner().also { it.resume() }
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(ownerNow)
            setViewTreeSavedStateRegistryOwner(ownerNow)
            setContent {
                OverlayBubble(
                    graph = graph,
                    compact = compact,
                    onOpen = { open(display.displayId) },
                    onHide = {
                        hiddenUntilNext = true
                        detach()
                    },
                    onDrag = { dx, dy -> drag(dx, dy) },
                )
            }
        }
        wm.addView(composeView, lp)
        view = composeView
        owner = ownerNow
        params = lp
        windowManager = wm
    }

    private fun drag(dx: Float, dy: Float) {
        val lp = params ?: return
        val v = view ?: return
        val horizontal = if (lp.gravity and Gravity.END == Gravity.END) -1 else 1
        val vertical = if (lp.gravity and Gravity.BOTTOM == Gravity.BOTTOM) -1 else 1
        lp.x = (lp.x + horizontal * dx).toInt().coerceAtLeast(0)
        lp.y = (lp.y + vertical * dy).toInt().coerceAtLeast(0)
        runCatching { windowManager?.updateViewLayout(v, lp) }
    }

    private fun open(displayId: Int) {
        val intent = Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
        runCatching { app.startActivity(intent, options.toBundle()) }.onFailure { runCatching { app.startActivity(intent) } }
    }

    fun detach() {
        val v = view
        view = null
        if (v != null) runCatching { windowManager?.removeView(v) }
        owner?.destroy()
        owner = null
        params = null
        windowManager = null
    }

    companion object {
        fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

        /** A tela do Android onde a pessoa permite a sobreposição para a L.I.L.Y. */
        fun permissionIntent(context: Context): Intent =
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

@Composable
private fun OverlayBubble(graph: AiGraph, compact: Boolean, onOpen: () -> Unit, onHide: () -> Unit, onDrag: (Float, Float) -> Unit) {
    val presence by LilyPresence.state.collectAsStateWithLifecycle()
    val settings by graph.settings.value.collectAsStateWithLifecycle()
    val look = settings.avatar
    val side = if (compact) minOf(look.size.dp, 76).dp else look.size.dp.dp
    val presentation = if (compact && look.presentation == AvatarPresentation.BUST) AvatarPresentation.FACE else look.presentation
    Column(
        Modifier
            .padding(4.dp)
            .pointerInput(Unit) { detectDragGestures { change, drag -> change.consume(); onDrag(drag.x, drag.y) } }
            .pointerInput(Unit) { detectTapGestures(onTap = { onOpen() }, onLongPress = { onHide() }) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(side)
                .clip(if (presentation == AvatarPresentation.ORB) CircleShape else RoundedCornerShape(28.dp))
                .background(Color(0xCC12101C))
                .border(1.dp, Color.White.copy(alpha = 0.18f), if (presentation == AvatarPresentation.ORB) CircleShape else RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center,
        ) {
            LilyAvatar(presence, look, Modifier.size(side), presentation = presentation)
        }
        val label = presence.label?.takeIf { presence.mode != PresenceMode.IDLE }
        if (label != null && !compact) {
            val pct = presence.progress?.let { " · ${(it * 100).toInt()}%" }.orEmpty()
            Text(
                label + pct,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xCC12101C))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

/** O ciclo de vida que o Compose precisa fora de uma atividade. */
private class OverlayOwner : SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

    fun resume() {
        controller.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
