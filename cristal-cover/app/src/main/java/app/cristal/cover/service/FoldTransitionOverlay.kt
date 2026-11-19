package app.cristal.cover.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.cristal.cover.core.Logx
import app.cristal.cover.core.safely
import app.cristal.cover.ui.fold.FoldGeometry
import app.cristal.cover.ui.fold.FoldLayer
import app.cristal.cover.ui.fold.foldFocus
import java.util.concurrent.Executor

/**
 * Transição de fechamento sobre qualquer app. Quando o sensor da dobradiça mostra o aparelho
 * fechando, a tela interna vira uma captura de um instante (só na memória) que entra na dobra com o
 * mesmo efeito do launcher (ui/fold): desfoque por região, luz caindo e a faixa de luz da Liquid
 * Island na linha da dobradiça. Sobre outros apps só dá para usar a captura: o Android não deixa um
 * app transformar a tela ao vivo de outro app (isso seria da System UI). Some se a pessoa parar no meio (modo flex), reabrir ou terminar de fechar.
 * Sem captura, duas cortinas de vidro escuro fecham em direção à dobra.
 */
internal class FoldTransitionOverlay(private val service: AccessibilityService) {
    private val main = Handler(Looper.getMainLooper())
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    private var windowManager: WindowManager? = null
    private var capturing = false
    private var lastAngle: Float? = null
    private var lastClosingAt = 0L

    /** Quanto já fechou, de 0 (aberto) a 1 (quase fechado), pela dobradiça. */
    private val target = mutableFloatStateOf(0f)
    private var shot by mutableStateOf<ImageBitmap?>(null)

    private val stall = Runnable { checkStall() }

    /** Novo ângulo da dobradiça (graus; 180 = aberto). [enabled] = Flip Transition ligada. */
    fun onHinge(angle: Float?, folded: Boolean?, enabled: Boolean) {
        val previous = lastAngle
        lastAngle = angle
        if (!enabled || angle == null) {
            remove()
            return
        }
        if (folded == true || angle < END) {
            // Terminou de fechar: a tela interna apaga; tira a sobreposição para ela não voltar ao abrir.
            remove()
            return
        }
        val closing = previous != null && angle < previous - 0.5f
        if (closing) lastClosingAt = SystemClock.uptimeMillis()
        val fraction = fractionFor(angle)
        if (view != null || capturing) {
            target.floatValue = fraction
            if (fraction <= 0.001f && !closing) main.postDelayed({ if (target.floatValue <= 0.001f) remove() }, 260)
            scheduleStall()
            return
        }
        if (closing && angle < START && angle > FLEX_FLOOR && !locked()) begin(fraction)
    }

    fun hide() = remove()

    private fun begin(fraction: Float) {
        capturing = true
        target.floatValue = fraction
        scheduleStall()
        try {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                Executor { main.post(it) },
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        val buffer = result.hardwareBuffer
                        val bitmap = safely("ler a captura") { Bitmap.wrapHardwareBuffer(buffer, result.colorSpace) }
                        buffer.close()
                        show(bitmap?.asImageBitmap())
                    }

                    override fun onFailure(errorCode: Int) {
                        Logx.d("Captura para a transição indisponível ($errorCode)")
                        show(null)
                    }
                },
            )
        } catch (t: Throwable) {
            Logx.w("Sem captura para a transição", t)
            show(null)
        }
    }

    private fun show(image: ImageBitmap?) {
        if (!capturing) return
        capturing = false
        // A pessoa pode ter reaberto enquanto a captura saía.
        if (target.floatValue <= 0.001f) return
        val display = service.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY) ?: return
        val context = service.createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null)
        val wm = context.getSystemService(WindowManager::class.java) ?: return
        shot = image
        val newOwner = OverlayOwner().also { it.start() }
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(newOwner)
            setViewTreeViewModelStoreOwner(newOwner)
            setViewTreeSavedStateRegistryOwner(newOwner)
            setContent { FoldContent(shot, { target.floatValue }) }
        }
        val params = WindowManager.LayoutParams(
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
            title = "FoldTransition"
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        val added = safely("mostrar a transição de fechamento") {
            wm.addView(composeView, params)
            true
        } ?: false
        if (!added) {
            newOwner.stop()
            shot = null
            return
        }
        view = composeView
        owner = newOwner
        windowManager = wm
    }

    /** Parou de fechar (modo flex, hesitação): desfaz em vez de congelar a tela. */
    private fun scheduleStall() {
        main.removeCallbacks(stall)
        main.postDelayed(stall, STALL_MS)
    }

    private fun checkStall() {
        if (view == null && !capturing) return
        if (SystemClock.uptimeMillis() - lastClosingAt >= STALL_MS) {
            target.floatValue = 0f
            capturing = false
            main.postDelayed({ if (target.floatValue <= 0.001f) remove() }, 260)
        } else {
            scheduleStall()
        }
    }

    private fun remove() {
        main.removeCallbacks(stall)
        capturing = false
        target.floatValue = 0f
        val v = view ?: return
        view = null
        safely("remover a transição") { windowManager?.removeViewImmediate(v) }
        safely("descartar a transição") { v.disposeComposition() }
        owner?.stop()
        owner = null
        windowManager = null
        shot = null
    }

    private fun locked(): Boolean = service.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    companion object {
        /** Começa a contar a partir daqui (180 = totalmente aberto). */
        const val START = 165f

        /** Daqui para baixo, já fechou. */
        const val END = 30f

        /** Abaixo disto o movimento já é de fechar mesmo; acima, pode ser o modo flex. */
        const val FLEX_FLOOR = 40f
        const val STALL_MS = 420L

        fun fractionFor(angle: Float): Float = ((START - angle) / (START - 45f)).coerceIn(0f, 1f)
    }
}

private val Spectrum = listOf(Color(0xFFFF7A2E), Color(0xFFFA408C), Color(0xFF9E52FF), Color(0xFF338CFF), Color(0xFF40EBF2))

/**
 * A tela entrando na dobra: a captura ganha o mesmo efeito da transição do launcher (desfoque por
 * região a partir da dobra, luz caindo e um leve esticamento em direção à dobradiça, com a linha de
 * luz em espectro), com uma escala suave em vez de encolher de uma vez. Sem captura, duas cortinas
 * de vidro escuro fecham em direção à dobra.
 */
@Composable
internal fun FoldContent(shot: ImageBitmap?, target: () -> Float) {
    val p by animateFloatAsState(target(), spring(dampingRatio = 1f, stiffness = 700f), label = "fechar")
    val focus = remember { derivedStateOf { 1f - p } }
    val config = LocalConfiguration.current
    val geometry = if (config.screenHeightDp >= config.screenWidthDp) FoldGeometry.InnerPortrait else FoldGeometry(horizontal = false, position = 0.5f)
    Box(Modifier.fillMaxSize()) {
        if (shot != null) {
            Box(Modifier.fillMaxSize().background(Color.Black))
            Image(
                shot,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val s = 1f - 0.06f * p
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                        shape = RoundedCornerShape((30f * p).dp)
                        clip = true
                    }
                    .foldFocus(focus, geometry, FoldLayer.SNAPSHOT, 30.dp),
            )
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val mid = size.height / 2f
                // Sem captura: cortinas escuras fecham em direção à dobra.
                val h = mid * p
                val r = CornerRadius(36.dp.toPx() * p)
                drawRoundRect(Color(0xF00B0A10), topLeft = Offset(0f, -r.y), size = Size(size.width, h + r.y), cornerRadius = r)
                drawRoundRect(Color(0xF00B0A10), topLeft = Offset(0f, size.height - h), size = Size(size.width, h + r.y), cornerRadius = r)
                // A faixa de luz na dobradiça: cresce e fica mais intensa conforme fecha.
                val glow = 70.dp.toPx() * (0.4f + 0.6f * p)
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Spectrum[3].copy(alpha = 0.32f * p), Color.Transparent),
                        startY = mid - glow,
                        endY = mid + glow,
                    ),
                    topLeft = Offset(0f, mid - glow),
                    size = Size(size.width, glow * 2),
                )
                val line = 2.dp.toPx() * (0.5f + p)
                val inset = size.width * (0.22f - 0.16f * p)
                drawRect(
                    Brush.horizontalGradient(Spectrum.map { it.copy(alpha = 0.95f * p) }, startX = inset, endX = size.width - inset),
                    topLeft = Offset(inset, mid - line / 2),
                    size = Size(size.width - inset * 2, line),
                )
            }
        }
    }
}
