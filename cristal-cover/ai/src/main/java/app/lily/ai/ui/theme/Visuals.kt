package app.lily.ai.ui.theme

import android.graphics.RuntimeShader
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * O fundo da L.I.L.Y: três manchas de luz que derivam devagar sobre a cor do tema (um ciclo de 40 s,
 * desenhado num só Canvas: leve para a bateria).
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, intensity: Float = 1f, content: @Composable BoxScope.() -> Unit) {
    val colors = Lily.colors
    val t by rememberInfiniteTransition(label = "aurora").animateFloat(
        0f, 6.2832f, infiniteRepeatable(tween(40_000, easing = LinearEasing)), label = "fase",
    )
    Box(
        modifier
            .background(colors.background)
            .drawBehind {
                val w = size.width
                val h = size.height
                val alpha = (if (colors.dark) 0.55f else 0.65f) * intensity
                val spots = listOf(
                    Offset(w * (0.2f + 0.12f * cos(t)), h * (0.12f + 0.06f * sin(t * 1.3f))),
                    Offset(w * (0.85f + 0.1f * sin(t * 0.8f)), h * (0.35f + 0.08f * cos(t))),
                    Offset(w * (0.4f + 0.15f * sin(t * 0.6f)), h * (0.85f + 0.05f * cos(t * 1.1f))),
                )
                spots.forEachIndexed { i, c ->
                    val r = maxOf(w, h) * (0.55f - 0.08f * i)
                    drawCircle(Brush.radialGradient(listOf(colors.aurora[i].copy(alpha = alpha), Color.Transparent), center = c, radius = r), radius = r, center = c)
                }
            },
        content = content,
    )
}

/** Uma superfície de vidro: preenchimento translúcido, borda fina e um brilho suave no topo. */
fun Modifier.glass(colors: LilyColors, shape: Shape = RoundedCornerShape(24.dp), strong: Boolean = false): Modifier = this
    .clip(shape)
    .background(if (strong) colors.glassStrong else colors.glass)
    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (colors.dark) 0.06f else 0.35f), Color.Transparent)))
    .border(1.dp, colors.border, shape)

/** Desligado no modo seguro: o orbe usa o gradiente simples, sem shader de GPU. */
@Volatile
var OrbShaderEnabled = true

private const val ORB_SHADER = """
uniform float2 uSize;
uniform float uTime;
uniform float uEnergy;
layout(color) uniform half4 c0;
layout(color) uniform half4 c1;
layout(color) uniform half4 c2;
layout(color) uniform half4 c3;

half4 main(float2 p) {
    float2 uv = (p - uSize * 0.5) / (min(uSize.x, uSize.y) * 0.5);
    float r = length(uv);
    if (r > 1.0) return half4(0.0);
    float a = atan(uv.y, uv.x);
    float swirl = sin(a * 3.0 + uTime * 0.9 + r * 4.0) * 0.5 + 0.5;
    float swirl2 = sin(a * 2.0 - uTime * 0.7 - r * 3.0) * 0.5 + 0.5;
    half3 col = mix(c0.rgb, c1.rgb, swirl);
    col = mix(col, c2.rgb, swirl2 * 0.55);
    col = mix(col, c3.rgb, smoothstep(0.55, 1.0, r) * (0.35 + 0.25 * uEnergy));
    float core = smoothstep(0.65, 0.0, length(uv - float2(-0.22, -0.28)));
    col += half3(core * 0.55);
    float rim = smoothstep(0.82, 1.0, r);
    col = mix(col, half3(1.0), rim * 0.35);
    float alpha = smoothstep(1.0, 0.96, r);
    return half4(col * alpha, alpha);
}
"""

/**
 * O orbe da L.I.L.Y: em repouso respira devagar; pensando, gira; ouvindo, pulsa com o nível da voz.
 * [energy] de 0 (calma) a 1 (ativa).
 */
@Composable
fun LilyOrb(size: Dp, modifier: Modifier = Modifier, energy: Float = 0f, level: Float = 0f) {
    val colors = Lily.colors
    val shader = remember { if (OrbShaderEnabled) runCatching { RuntimeShader(ORB_SHADER) }.getOrNull() else null }
    val brush = remember(shader) { shader?.let { ShaderBrush(it) } }
    val transition = rememberInfiniteTransition(label = "orbe")
    val time by transition.animateFloat(0f, 600f, infiniteRepeatable(tween(600_000, easing = LinearEasing)), label = "tempo")
    val breathe by transition.animateFloat(0.96f, 1.02f, infiniteRepeatable(tween(2_600), RepeatMode.Reverse), label = "respira")
    val scale = breathe + 0.12f * level
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            // Halo de luz em volta.
            drawCircle(
                Brush.radialGradient(listOf(colors.orb[0].copy(alpha = 0.45f + 0.25f * energy), Color.Transparent), radius = this.size.minDimension * 0.75f),
                radius = this.size.minDimension * 0.75f,
            )
            val r = this.size.minDimension * 0.38f * scale
            if (shader != null && brush != null) {
                shader.setFloatUniform("uSize", r * 2, r * 2)
                shader.setFloatUniform("uTime", time * (1f + 2f * energy))
                shader.setFloatUniform("uEnergy", energy)
                listOf("c0", "c1", "c2", "c3").forEachIndexed { i, n ->
                    val c = colors.orb[i]
                    shader.setColorUniform(n, android.graphics.Color.valueOf(c.red, c.green, c.blue, c.alpha))
                }
                val tl = Offset(center.x - r, center.y - r)
                drawContext.canvas.save()
                drawContext.canvas.translate(tl.x, tl.y)
                drawCircle(brush, radius = r, center = Offset(r, r))
                drawContext.canvas.restore()
            } else {
                drawCircle(Brush.radialGradient(colors.orb, center = Offset(center.x - r * 0.3f, center.y - r * 0.3f), radius = r * 1.6f), radius = r)
            }
        }
    }
}
