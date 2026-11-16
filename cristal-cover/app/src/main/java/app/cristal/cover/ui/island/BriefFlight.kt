package app.cristal.cover.ui.island

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sign

/** Cores da esfera do Info Brief. */
internal object BriefSphere {
    val Core = Color(0xFF64D2FF)
    val Blue = Color(0xFF0A84FF)
    val Glow = Color(0x660A84FF)
}

/** Ponto de uma curva de Bézier quadrática (o arco do voo). */
internal fun quad(a: Offset, c: Offset, b: Offset, t: Float): Offset {
    val u = 1f - t
    return Offset(u * u * a.x + 2 * u * t * c.x + t * t * b.x, u * u * a.y + 2 * u * t * c.y + t * t * b.y)
}

/** O ponto de controle do arco: sobe primeiro e entra na câmera de lado, como algo lançado. */
internal fun arcControl(origin: Offset, target: Offset): Offset {
    val dx = target.x - origin.x
    val side = if (abs(dx) < 1f) 1f else sign(dx)
    return Offset(origin.x + dx * 0.15f - side * abs(target.y - origin.y) * 0.12f, target.y + (origin.y - target.y) * 0.42f)
}

/**
 * A esfera azul do Info Brief voando do teclado até a Liquid Island: um arco suave com um rastro
 * curto que acelera e encolhe ao chegar, como uma gota sendo absorvida pela ilha. Chama [onArrive]
 * no pouso (a ilha abre a sugestão) e [onDone] quando terminou de sumir.
 */
@Composable
fun BriefFlightLayer(origin: Offset, target: Offset, onArrive: () -> Unit, onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val arrive by rememberUpdatedState(onArrive)
    val done by rememberUpdatedState(onDone)
    val control = remember(origin, target) { arcControl(origin, target) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(FLIGHT_MS, easing = CubicBezierEasing(0.45f, 0f, 0.2f, 1f)))
        arrive()
        progress.animateTo(1.25f, tween(220))
        done()
    }
    Canvas(Modifier.fillMaxSize()) {
        val p = progress.value
        val t = p.coerceAtMost(1f)
        // Rastro: posições um pouco atrás na curva, cada vez mais fracas.
        for (i in 4 downTo 1) {
            val back = (t - i * 0.035f).coerceAtLeast(0f)
            if (back <= 0f) continue
            sphere(quad(origin, control, target, back), radius(back) * (1f - i * 0.14f), alpha = 0.16f * (5 - i) * fade(p))
        }
        sphere(quad(origin, control, target, t), radius(t), alpha = fade(p))
    }
}

private const val FLIGHT_MS = 620

/** Começa do tamanho da esfera no teclado e encolhe até caber na câmera. */
private fun DrawScope.radius(t: Float): Float = (11.dp.toPx() * (1f - 0.45f * t * t)).coerceAtLeast(3.dp.toPx())

/** Depois do pouso, a esfera é absorvida (some em ~220 ms). */
private fun fade(p: Float): Float = if (p <= 1f) 1f else (1f - (p - 1f) / 0.25f).coerceIn(0f, 1f)

private fun DrawScope.sphere(center: Offset, radius: Float, alpha: Float) {
    if (alpha <= 0.01f) return
    drawCircle(
        Brush.radialGradient(listOf(BriefSphere.Glow.copy(alpha = 0.4f * alpha), Color.Transparent), center = center, radius = radius * 2.6f),
        radius = radius * 2.6f,
        center = center,
    )
    drawCircle(
        Brush.radialGradient(
            listOf(Color.White.copy(alpha = alpha), BriefSphere.Core.copy(alpha = alpha), BriefSphere.Blue.copy(alpha = alpha)),
            center = center + Offset(-radius * 0.3f, -radius * 0.35f),
            radius = radius * 1.3f,
        ),
        radius = radius,
        center = center,
    )
}
