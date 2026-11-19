package app.lily.ai.avatar

import android.animation.ValueAnimator
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.lily.ai.presence.Presence
import app.lily.ai.presence.PresenceMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** As cores da avatar, derivadas de uma matiz (a do tema ou a escolhida). */
@Immutable
data class AvatarPalette(
    val hair: Color,
    val hairHi: Color,
    val skin: Color,
    val skinShade: Color,
    val iris: Color,
    val irisDeep: Color,
    val outfit: Color,
    val outfitHi: Color,
    val glow: Color,
    val ink: Color,
) {
    companion object {
        private fun hsv(h: Float, s: Float, v: Float, a: Float = 1f): Color =
            Color(android.graphics.Color.HSVToColor((a * 255).toInt(), floatArrayOf(((h % 360) + 360) % 360, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))))

        fun hueOf(color: Color): Float {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(color.toArgb(), hsv)
            return hsv[0]
        }

        fun of(hue: Float): AvatarPalette = AvatarPalette(
            hair = hsv(hue, 0.6f, 0.5f),
            hairHi = hsv(hue + 10, 0.42f, 0.98f),
            skin = hsv(hue + 20, 0.06f, 0.99f),
            skinShade = hsv(hue + 10, 0.18f, 0.86f),
            // Olhos e luzes na cor vizinha (violeta pede ciano, como na identidade Liquid Flip).
            iris = hsv(hue - 75, 0.6f, 1f),
            irisDeep = hsv(hue - 60, 0.85f, 0.5f),
            outfit = hsv(hue - 25, 0.5f, 0.22f),
            outfitHi = hsv(hue - 60, 0.4f, 0.9f),
            glow = hsv(hue - 75, 0.5f, 1f),
            ink = hsv(hue + 10, 0.6f, 0.15f),
        )
    }
}

private val SUCCESS = Color(0xFF5BE49B)
private val ERROR = Color(0xFFFF6B7A)
private val LISTEN = Color(0xFF63D8FF)

/**
 * A L.I.L.Y em pessoa: uma assistente holográfica original, desenhada por código (escala sem
 * perder qualidade, segue a cor do tema e anima cada estado). Não usa câmera, microfone nem rede:
 * só mostra o [presence] que os módulos informam.
 *
 * [timeMs] fixa o instante (testes e prévias); sem ele, anima no ritmo da tela.
 */
@Composable
fun LilyAvatar(
    presence: Presence,
    look: AvatarLook,
    modifier: Modifier = Modifier,
    themeAccent: Color = Color(0xFFB79CFF),
    presentation: AvatarPresentation = look.presentation,
    timeMs: Long? = null,
) {
    val systemAnimations = remember { runCatching { ValueAnimator.areAnimatorsEnabled() }.getOrDefault(true) }
    val moving = timeMs == null && look.motion != AvatarMotion.OFF && systemAnimations && !LocalInspectionMode.current
    val speed = if (look.motion == AvatarMotion.REDUCED) 0.55f else 1f
    // Relógio de animação infinita: respeita a política do sistema (e para nos testes).
    val clock by produceState(timeMs ?: 1_000L, moving) {
        if (!moving) return@produceState
        val start = withInfiniteAnimationFrameNanos { it }
        while (true) {
            withInfiniteAnimationFrameNanos { value = ((it - start) / 1_000_000L * speed).toLong() + 1_000L }
        }
    }
    val t = timeMs ?: clock
    val target = AvatarPose.target(presence.mode)
    val anim: AnimationSpec<Float> = if (moving) spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow) else snap()
    val pose = Pose(
        eyeOpen = animateFloatAsState(target.eyeOpen, anim, label = "olhos").value,
        smile = animateFloatAsState(target.smile, anim, label = "sorriso").value,
        browRaise = animateFloatAsState(target.browRaise, anim, label = "sobrancelha").value,
        browTilt = animateFloatAsState(target.browTilt, anim, label = "inclinação da sobrancelha").value,
        gazeX = animateFloatAsState(target.gazeX, anim, label = "olhar x").value,
        gazeY = animateFloatAsState(target.gazeY, anim, label = "olhar y").value,
        tilt = animateFloatAsState(target.tilt, anim, label = "cabeça").value,
        happyEyes = animateFloatAsState(target.happyEyes, anim, label = "olhos felizes").value,
        squint = animateFloatAsState(target.squint, anim, label = "concentrada").value,
    )
    val level by animateFloatAsState(presence.level, if (moving) tween(90) else snap(), label = "nível")
    val progress by animateFloatAsState(presence.progress ?: 0f, if (moving) tween(400) else snap(), label = "progresso")
    val hue = if (look.hue in 0..359) look.hue.toFloat() else AvatarPalette.hueOf(themeAccent)
    val palette = remember(hue) { AvatarPalette.of(hue) }
    val description = "L.I.L.Y: ${presence.label ?: presence.mode.label}"
    Canvas(
        modifier
            .semantics { contentDescription = description }
            .graphicsLayer {
                alpha = look.opacity.coerceIn(0.3f, 1f)
                compositingStrategy = CompositingStrategy.Offscreen
            },
    ) {
        val frame = AvatarFrame(
            t = t,
            mode = presence.mode,
            pose = pose,
            level = level,
            progress = presence.progress?.let { progress },
            palette = palette,
            style = look.style,
            effects = moving && look.motion == AvatarMotion.FULL,
        )
        when (presentation) {
            AvatarPresentation.ORB -> drawOrb(frame)
            AvatarPresentation.FACE -> drawBust(frame, face = true)
            AvatarPresentation.BUST -> drawBust(frame, face = false)
        }
    }
}

private fun <T> snap() = androidx.compose.animation.core.snap<T>()

/** Tudo o que um quadro precisa (calculado fora do desenho). */
@Immutable
internal data class AvatarFrame(
    val t: Long,
    val mode: PresenceMode,
    val pose: Pose,
    val level: Float,
    val progress: Float?,
    val palette: AvatarPalette,
    val style: AvatarStyle,
    val effects: Boolean,
) {
    val stateColor: Color
        get() = when (mode) {
            PresenceMode.SUCCESS -> SUCCESS
            PresenceMode.ERROR -> ERROR
            PresenceMode.LISTENING -> LISTEN
            else -> palette.glow
        }
}

// --- Busto -----------------------------------------------------------------------------------

private fun DrawScope.drawBust(f: AvatarFrame, face: Boolean) {
    // Área de desenho em unidades: o busto ocupa 100 × 100; o rosto, uma janela menor dele.
    val (vx, vy, vw, vh) = if (face) listOf(20f, 2f, 60f, 66f) else listOf(0f, 0f, 100f, 100f)
    val scale = min(size.width / vw, size.height / vh)
    val dx = (size.width - vw * scale) / 2f - vx * scale
    val dy = (size.height - vh * scale) / 2f - vy * scale
    val breath = AvatarPose.breathing(f.t)
    val sway = sin(2 * PI * f.t / 3_700.0).toFloat() * 0.7f
    withTransform({
        translate(dx, dy)
        scale(scale, scale, Offset.Zero)
    }) {
        // Aura atrás da cabeça.
        val auraStrength = when (f.mode) {
            PresenceMode.IDLE -> 0.28f
            PresenceMode.LISTENING -> 0.32f + 0.4f * f.level
            else -> 0.42f
        }
        if (!face) {
            drawCircle(
                Brush.radialGradient(listOf(f.stateColor.copy(alpha = auraStrength), Color.Transparent), center = Offset(50f, 40f), radius = 46f),
                radius = 46f,
                center = Offset(50f, 40f),
            )
        }
        // Ombros sobem e descem com a respiração; a cabeça inclina sobre o pescoço.
        withTransform({ translate(0f, breath * 0.35f) }) {
            drawOutfit(f)
        }
        withTransform({
            translate(0f, breath * 0.5f)
            rotate(f.pose.tilt, Offset(50f, 62f))
        }) {
            drawHairBack(f, sway)
            drawNeck(f)
            drawHead(f)
            drawFace(f)
            drawHairFront(f, sway)
            drawEarpiece(f)
            drawHalo(f)
        }
        drawStateExtras(f)
        if (f.style == AvatarStyle.HOLO) drawScanlines(f)
    }
}

private fun headPath(): Path = Path().apply {
    moveTo(50f, 19f)
    cubicTo(60.5f, 19f, 67f, 26f, 67f, 36f)
    cubicTo(67f, 47.5f, 61.5f, 56f, 55.2f, 59.6f)
    quadraticTo(50f, 62.8f, 44.8f, 59.6f)
    cubicTo(38.5f, 56f, 33f, 47.5f, 33f, 36f)
    cubicTo(33f, 26f, 39.5f, 19f, 50f, 19f)
    close()
}

private fun DrawScope.drawOutfit(f: AvatarFrame) {
    val p = f.palette
    val body = Path().apply {
        moveTo(11f, 101f)
        cubicTo(12.5f, 87f, 22f, 79.5f, 36f, 76.8f)
        quadraticTo(42f, 75.4f, 43.6f, 71f)
        lineTo(56.4f, 71f)
        quadraticTo(58f, 75.4f, 64f, 76.8f)
        cubicTo(78f, 79.5f, 87.5f, 87f, 89f, 101f)
        close()
    }
    val flat = f.style == AvatarStyle.MINIMAL
    drawPath(body, if (flat) Brush.verticalGradient(listOf(p.outfit, p.outfit)) else Brush.verticalGradient(listOf(lerp(p.outfit, p.outfitHi, 0.18f), p.outfit), 72f, 100f))
    drawPath(body, p.outfitHi.copy(alpha = 0.45f), style = Stroke(0.5f))
    // Gola alta com a linha de luz e os detalhes de painel (a assinatura da L.I.L.Y).
    val collar = Path().apply {
        moveTo(41.5f, 64.5f)
        quadraticTo(50f, 69.5f, 58.5f, 64.5f)
        lineTo(60f, 72.5f)
        quadraticTo(50f, 78f, 40f, 72.5f)
        close()
    }
    drawPath(collar, lerp(p.outfit, p.outfitHi, 0.12f))
    drawPath(
        Path().apply { moveTo(40.6f, 71.6f); quadraticTo(50f, 77f, 59.4f, 71.6f) },
        f.stateColor.copy(alpha = if (flat) 0.7f else 0.9f),
        style = Stroke(0.7f, cap = StrokeCap.Round),
    )
    val panel = p.outfitHi.copy(alpha = 0.35f)
    drawLine(panel, Offset(50f, 77.5f), Offset(50f, 92f), 0.45f)
    drawCircle(f.stateColor.copy(alpha = 0.85f), 0.8f, Offset(50f, 84f))
    drawLine(panel, Offset(27f, 86f), Offset(38f, 80.5f), 0.45f)
    drawLine(panel, Offset(73f, 86f), Offset(62f, 80.5f), 0.45f)
    drawCircle(panel, 0.6f, Offset(38f, 80.5f))
    drawCircle(panel, 0.6f, Offset(62f, 80.5f))
}

private fun DrawScope.drawNeck(f: AvatarFrame) {
    val p = f.palette
    val neck = Path().apply {
        moveTo(44.6f, 55f)
        lineTo(55.4f, 55f)
        quadraticTo(55.2f, 63f, 57f, 68f)
        lineTo(43f, 68f)
        quadraticTo(44.8f, 63f, 44.6f, 55f)
        close()
    }
    drawPath(neck, Brush.verticalGradient(listOf(p.skinShade, lerp(p.skinShade, p.ink, 0.12f)), 55f, 68f))
}

private fun DrawScope.drawHead(f: AvatarFrame) {
    val p = f.palette
    val head = headPath()
    val skinAlpha = if (f.style == AvatarStyle.GLASS) 0.86f else 1f
    if (f.style == AvatarStyle.HOLO) {
        // Bordas em espectro (como uma projeção).
        withTransform({ translate(0.55f, 0f) }) { drawPath(head, LISTEN.copy(alpha = 0.35f), style = Stroke(0.7f)) }
        withTransform({ translate(-0.55f, 0f) }) { drawPath(head, Color(0xFFFF6FD8).copy(alpha = 0.28f), style = Stroke(0.7f)) }
    }
    val fill = if (f.style == AvatarStyle.MINIMAL) Brush.linearGradient(listOf(p.skin, p.skin)) else Brush.linearGradient(listOf(p.skin, p.skinShade), Offset(40f, 22f), Offset(60f, 62f))
    drawPath(head, fill, alpha = skinAlpha)
    drawPath(head, p.glow.copy(alpha = 0.45f), style = Stroke(0.45f))
    if (f.style == AvatarStyle.GLASS) {
        drawPath(Path().apply { moveTo(38f, 27f); quadraticTo(42f, 21.5f, 49f, 21f) }, Color.White.copy(alpha = 0.6f), style = Stroke(0.9f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawFace(f: AvatarFrame) {
    val p = f.palette
    val pose = f.pose
    // Brilho nas bochechas e o traço de circuito do lado direito.
    if (f.style != AvatarStyle.MINIMAL) {
        for (cx in listOf(40.6f, 59.4f)) {
            drawCircle(Brush.radialGradient(listOf(p.glow.copy(alpha = 0.2f), Color.Transparent), Offset(cx, 49f), 4.6f), 4.6f, Offset(cx, 49f))
        }
    }
    val mark = f.stateColor.copy(alpha = 0.85f)
    drawLine(mark, Offset(60.5f, 50.4f), Offset(63.2f, 48.6f), 0.4f, StrokeCap.Round)
    drawLine(mark, Offset(61.2f, 51.8f), Offset(63.9f, 50f), 0.4f, StrokeCap.Round)
    drawCircle(mark, 0.42f, Offset(64.6f, 48.2f))
    // Olhos.
    val blink = AvatarPose.blink(f.t)
    val (idleX, idleY) = if (f.mode == PresenceMode.IDLE) AvatarPose.idleGaze(f.t) else 0f to 0f
    for (cx in listOf(42.8f, 57.2f)) drawEye(f, cx, 42.2f, blink, pose.gazeX + idleX, pose.gazeY + idleY)
    // Sobrancelhas.
    for (left in listOf(true, false)) {
        val cx = if (left) 42.8f else 57.2f
        val inner = if (left) cx + 3.4f else cx - 3.4f
        val outer = if (left) cx - 3.9f else cx + 3.9f
        val base = 36.6f - pose.browRaise * 1.25f
        val innerY = base - pose.browTilt * 1.3f + (1f - pose.browTilt) * 0.2f
        val outerY = base + 0.35f
        val brow = Path().apply {
            moveTo(outer, outerY)
            quadraticTo(cx, base - 1.3f, inner, innerY)
        }
        drawPath(brow, p.hair.copy(alpha = 0.95f), style = Stroke(0.85f, cap = StrokeCap.Round))
    }
    // Nariz discreto.
    drawPath(Path().apply { moveTo(50.7f, 46.4f); quadraticTo(51.3f, 48.7f, 49.6f, 49.3f) }, p.skinShade, style = Stroke(0.45f, cap = StrokeCap.Round))
    // Boca.
    val open = AvatarPose.mouthOpen(f.mode, f.level, f.t)
    val smile = pose.smile
    val y = 53.8f
    val half = 3.1f * (1f + 0.08f * smile)
    val cornerY = y - smile * 0.9f
    val midY = y + smile * 1.5f
    val upper = Path().apply {
        moveTo(50f - half, cornerY)
        quadraticTo(50f, midY - 0.2f, 50f + half, cornerY)
    }
    val lip = lerp(p.skinShade, Color(0xFFB0567A), 0.55f)
    if (open > 0.04f) {
        val mouth = Path().apply {
            moveTo(50f - half, cornerY)
            quadraticTo(50f, midY - 0.6f, 50f + half, cornerY)
            quadraticTo(50f, midY + 0.6f + open * 3.4f, 50f - half, cornerY)
            close()
        }
        drawPath(mouth, p.ink.copy(alpha = 0.92f))
        drawPath(mouth, lip, style = Stroke(0.45f))
    } else {
        drawPath(upper, lip, style = Stroke(0.65f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawEye(f: AvatarFrame, cx: Float, cy: Float, blink: Float, gx: Float, gy: Float) {
    val p = f.palette
    val pose = f.pose
    val w = 8.2f
    if (pose.happyEyes > 0.5f) {
        val arc = Path().apply {
            moveTo(cx - w / 2f, cy + 0.7f)
            quadraticTo(cx, cy - 2.6f, cx + w / 2f, cy + 0.7f)
        }
        drawPath(arc, p.hair, style = Stroke(1.05f, cap = StrokeCap.Round))
        return
    }
    val open = (pose.eyeOpen * (1f - blink) * (1f - 0.42f * pose.squint)).coerceIn(0.06f, 1.25f)
    val h = 4.9f
    val almond = Path().apply {
        moveTo(cx - w / 2f, cy)
        quadraticTo(cx, cy - h * open, cx + w / 2f, cy)
        quadraticTo(cx, cy + h * 0.6f * open, cx - w / 2f, cy)
        close()
    }
    drawPath(almond, lerp(Color.White, p.skin, 0.25f))
    clipPath(almond) {
        val ix = cx + gx.coerceIn(-1f, 1f) * 1.4f
        val iy = cy - 0.3f + gy.coerceIn(-1f, 1f) * 0.8f
        drawCircle(Brush.radialGradient(listOf(p.iris, p.irisDeep), Offset(ix, iy), 2.7f), 2.6f, Offset(ix, iy))
        drawCircle(p.ink, 1.05f, Offset(ix, iy))
        drawCircle(Color.White.copy(alpha = 0.92f), 0.52f, Offset(ix - 0.65f, iy - 0.75f))
        drawCircle(Color.White.copy(alpha = 0.5f), 0.26f, Offset(ix + 0.7f, iy + 0.5f))
    }
    // Pálpebra de cima com o cílio no canto de fora.
    val lid = Path().apply {
        moveTo(cx - w / 2f - 0.2f, cy + 0.1f)
        quadraticTo(cx, cy - h * open - 0.25f, cx + w / 2f + 0.2f, cy + 0.1f)
    }
    drawPath(lid, p.hair, style = Stroke(0.85f, cap = StrokeCap.Round))
    val outer = if (cx < 50f) cx - w / 2f else cx + w / 2f
    val dir = if (cx < 50f) -1f else 1f
    drawLine(p.hair, Offset(outer, cy), Offset(outer + dir * 1.1f, cy - 1f), 0.7f, StrokeCap.Round)
}

private fun DrawScope.drawHairBack(f: AvatarFrame, sway: Float) {
    val p = f.palette
    val back = Path().apply {
        moveTo(50f, 12.5f)
        cubicTo(36f, 12.5f, 27.5f, 22f, 28f, 35f)
        cubicTo(28.3f, 45f, 27f, 53f, 29.5f + sway, 60.5f)
        quadraticTo(33f, 63f, 36.5f, 59.6f)
        lineTo(63.5f, 59.6f)
        quadraticTo(67f, 63f, 70.5f + sway, 60.5f)
        cubicTo(73f, 53f, 71.7f, 45f, 72f, 35f)
        cubicTo(72.5f, 22f, 64f, 12.5f, 50f, 12.5f)
        close()
    }
    val brush = if (f.style == AvatarStyle.MINIMAL) Brush.linearGradient(listOf(p.hair, p.hair)) else Brush.verticalGradient(listOf(lerp(p.hair, p.hairHi, 0.35f), p.hair, lerp(p.hair, p.ink, 0.25f)), 12f, 62f)
    drawPath(back, brush, alpha = if (f.style == AvatarStyle.GLASS) 0.9f else 1f)
}

private fun DrawScope.drawHairFront(f: AvatarFrame, sway: Float) {
    val p = f.palette
    val fringe = Path().apply {
        moveTo(66.6f, 31f)
        cubicTo(66.2f, 20.5f, 58.5f, 15.6f, 49.5f, 16f)
        cubicTo(40.5f, 16.4f, 33.6f, 22f, 32.6f, 31.5f)
        cubicTo(32.2f, 39f, 32.6f, 46.5f, 31.4f + sway * 0.8f, 53.5f)
        quadraticTo(34.8f, 47f, 35.7f, 38.5f)
        cubicTo(36.8f, 31.6f, 41f, 28.6f, 45.6f, 29.4f)
        quadraticTo(50.4f, 27.2f, 52.8f, 24.4f)
        quadraticTo(56.4f, 28.6f, 61.2f, 29.6f)
        quadraticTo(64.6f, 30.6f, 66.6f, 37f)
        close()
    }
    val brush = if (f.style == AvatarStyle.MINIMAL) Brush.linearGradient(listOf(p.hair, p.hair)) else Brush.linearGradient(listOf(p.hairHi, p.hair), Offset(52f, 15f), Offset(38f, 48f))
    drawPath(fringe, brush, alpha = if (f.style == AvatarStyle.GLASS) 0.92f else 1f)
    if (f.style != AvatarStyle.MINIMAL) {
        val strand = p.hairHi.copy(alpha = 0.55f)
        drawPath(Path().apply { moveTo(52.6f, 18f); quadraticTo(44f, 20f, 37.5f, 30f) }, strand, style = Stroke(0.35f, cap = StrokeCap.Round))
        drawPath(Path().apply { moveTo(58f, 19.5f); quadraticTo(53f, 22f, 51.5f, 25.5f) }, strand, style = Stroke(0.3f, cap = StrokeCap.Round))
        drawPath(Path().apply { moveTo(35.2f, 33f); quadraticTo(33.8f, 42f, 33f, 49f) }, strand, style = Stroke(0.3f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawEarpiece(f: AvatarFrame) {
    val p = f.palette
    drawRoundRect(p.outfit, Offset(65.6f, 38.6f), Size(2.6f, 6.2f), androidx.compose.ui.geometry.CornerRadius(1.3f))
    val pulse = if (f.mode == PresenceMode.LISTENING) 0.5f + 0.5f * f.level else 0.55f + 0.25f * sin(2 * PI * f.t / 2_200.0).toFloat()
    drawCircle(f.stateColor.copy(alpha = pulse.coerceIn(0.2f, 1f)), 0.8f, Offset(66.9f, 41.7f))
}

private fun DrawScope.drawHalo(f: AvatarFrame) {
    val c = Offset(50f, 9.6f)
    val rx = 19f
    val ry = 3.7f
    val tl = Offset(c.x - rx, c.y - ry)
    val sz = Size(rx * 2, ry * 2)
    val color = f.stateColor
    val base = when (f.mode) {
        PresenceMode.LISTENING -> 0.35f + 0.55f * f.level
        PresenceMode.SUCCESS, PresenceMode.ERROR -> 0.9f
        else -> 0.4f
    }
    drawOval(color.copy(alpha = base * 0.6f), tl, sz, style = Stroke(0.55f + if (f.mode == PresenceMode.LISTENING) f.level * 1.2f else 0f))
    val spin = (f.t / 18f) % 360f
    when (f.mode) {
        PresenceMode.WORKING -> {
            val prog = f.progress
            if (prog != null) {
                drawArc(color, -90f, 360f * prog.coerceIn(0f, 1f), false, tl, sz, style = Stroke(1.3f, cap = StrokeCap.Round))
            } else {
                drawArc(color, spin * 2.2f, 80f, false, tl, sz, style = Stroke(1.3f, cap = StrokeCap.Round))
            }
        }
        PresenceMode.THINKING -> {
            for (i in 0 until 3) {
                val a = Math.toRadians((spin * 1.6f + i * 120f).toDouble())
                drawCircle(color.copy(alpha = 0.95f), 0.95f, Offset(c.x + rx * cos(a).toFloat(), c.y + ry * sin(a).toFloat()))
            }
        }
        else -> {
            for (i in 0 until 3) drawArc(color.copy(alpha = base), spin + i * 120f, 34f, false, tl, sz, style = Stroke(0.9f, cap = StrokeCap.Round))
        }
    }
}

private fun DrawScope.drawStateExtras(f: AvatarFrame) {
    when (f.mode) {
        PresenceMode.LISTENING -> {
            // Ondas de som saindo do fone.
            for (i in 1..3) {
                val r = 2.4f * i + (f.t % 900L) / 900f * 2.4f
                val a = (0.55f * f.level + 0.2f) * (1f - i / 4f)
                drawArc(LISTEN.copy(alpha = a.coerceIn(0f, 1f)), -40f, 80f, false, Offset(67f - r, 41.7f - r), Size(r * 2, r * 2), style = Stroke(0.5f, cap = StrokeCap.Round))
            }
        }
        PresenceMode.SUCCESS -> {
            val n = if (f.effects) 6 else 3
            for (i in 0 until n) {
                val a = 2 * PI * i / n + f.t / 1_400.0
                val r = 30f + 3f * sin(f.t / 300.0 + i).toFloat()
                drawSparkle(Offset(50f + r * cos(a).toFloat(), 40f + r * 0.75f * sin(a).toFloat()), 1.6f, SUCCESS)
            }
        }
        PresenceMode.ERROR -> {
            // Um "!" no halo: o erro nunca parece sucesso.
            val at = Offset(72f, 9f)
            drawCircle(ERROR, 3.1f, at)
            drawLine(Color.White, Offset(at.x, at.y - 1.6f), Offset(at.x, at.y + 0.4f), 0.75f, StrokeCap.Round)
            drawCircle(Color.White, 0.45f, Offset(at.x, at.y + 1.6f))
        }
        else -> Unit
    }
}

private fun DrawScope.drawSparkle(at: Offset, r: Float, color: Color) {
    val path = Path().apply {
        moveTo(at.x, at.y - r)
        quadraticTo(at.x, at.y, at.x + r, at.y)
        quadraticTo(at.x, at.y, at.x, at.y + r)
        quadraticTo(at.x, at.y, at.x - r, at.y)
        quadraticTo(at.x, at.y, at.x, at.y - r)
        close()
    }
    drawPath(path, color)
}

/** Linhas de varredura só onde há desenho (estilo holográfico). */
private fun DrawScope.drawScanlines(f: AvatarFrame) {
    val flicker = if (f.effects) 0.05f + 0.02f * sin(f.t / 70.0).toFloat() else 0.06f
    var y = 0f
    val offset = if (f.effects) (f.t / 60f) % 1.4f else 0f
    while (y < 100f) {
        drawLine(Color.White.copy(alpha = flicker), Offset(0f, y + offset), Offset(100f, y + offset), 0.35f, blendMode = BlendMode.SrcAtop)
        y += 1.4f
    }
}

// --- Orbe ------------------------------------------------------------------------------------

private fun DrawScope.drawOrb(f: AvatarFrame) {
    val p = f.palette
    val scale = min(size.width, size.height) / 100f
    val dx = (size.width - 100f * scale) / 2f
    val dy = (size.height - 100f * scale) / 2f
    val breath = AvatarPose.breathing(f.t)
    withTransform({
        translate(dx, dy)
        scale(scale, scale, Offset.Zero)
    }) {
        val c = Offset(50f, 50f)
        val r = 36f + breath * 0.8f
        drawCircle(Brush.radialGradient(listOf(f.stateColor.copy(alpha = 0.35f), Color.Transparent), c, 50f), 50f, c)
        drawCircle(Brush.radialGradient(listOf(lerp(p.glow, Color.White, 0.35f), p.hairHi, p.hair, p.ink), Offset(42f, 38f), r * 1.5f), r, c)
        drawCircle(Color.White.copy(alpha = 0.22f), r, c, style = Stroke(1.2f))
        drawPath(Path().apply { moveTo(30f, 34f); quadraticTo(36f, 22f, 50f, 20f) }, Color.White.copy(alpha = 0.55f), style = Stroke(2.2f, cap = StrokeCap.Round))
        val blink = AvatarPose.blink(f.t)
        val pose = f.pose
        for (ex in listOf(40f, 60f)) {
            val gx = pose.gazeX * 2.2f
            val gy = pose.gazeY * 2f
            if (pose.happyEyes > 0.5f) {
                drawPath(Path().apply { moveTo(ex - 4f + gx, 50f); quadraticTo(ex + gx, 44f, ex + 4f + gx, 50f) }, Color.White, style = Stroke(2.2f, cap = StrokeCap.Round))
            } else {
                val h = (11f * pose.eyeOpen * (1f - blink) * (1f - 0.4f * pose.squint)).coerceAtLeast(1.4f)
                drawRoundRect(Color.White, Offset(ex - 3f + gx, 49f - h / 2f + gy), Size(6f, h), androidx.compose.ui.geometry.CornerRadius(3f))
            }
        }
        val open = AvatarPose.mouthOpen(f.mode, f.level, f.t)
        if (open > 0.04f) {
            drawOval(Color.White.copy(alpha = 0.9f), Offset(46f, 60f), Size(8f, 2f + open * 5f))
        } else if (pose.smile > 0.2f || pose.smile < -0.2f) {
            drawPath(Path().apply { moveTo(45f, 61f - pose.smile * 0.8f); quadraticTo(50f, 61f + pose.smile * 3f, 55f, 61f - pose.smile * 0.8f) }, Color.White.copy(alpha = 0.85f), style = Stroke(1.6f, cap = StrokeCap.Round))
        }
        val ringTl = Offset(c.x - r - 6f, c.y - r - 6f)
        val ringSz = Size((r + 6f) * 2, (r + 6f) * 2)
        when (f.mode) {
            PresenceMode.WORKING -> {
                val prog = f.progress
                if (prog != null) drawArc(f.stateColor, -90f, 360f * prog, false, ringTl, ringSz, style = Stroke(2.4f, cap = StrokeCap.Round))
                else drawArc(f.stateColor, (f.t / 6f) % 360f, 90f, false, ringTl, ringSz, style = Stroke(2.4f, cap = StrokeCap.Round))
            }
            PresenceMode.LISTENING -> drawCircle(LISTEN.copy(alpha = 0.3f + 0.6f * f.level), r + 6f, c, style = Stroke(1.5f + 3f * f.level))
            PresenceMode.ERROR -> drawCircle(ERROR, r + 6f, c, style = Stroke(2.4f))
            PresenceMode.SUCCESS -> drawCircle(SUCCESS, r + 6f, c, style = Stroke(2.4f))
            PresenceMode.THINKING -> for (i in 0 until 3) {
                val a = Math.toRadians(((f.t / 5f) % 360f + i * 120f).toDouble())
                drawCircle(f.stateColor, 2.2f, Offset(c.x + (r + 6f) * cos(a).toFloat(), c.y + (r + 6f) * sin(a).toFloat()))
            }
            else -> Unit
        }
    }
}
