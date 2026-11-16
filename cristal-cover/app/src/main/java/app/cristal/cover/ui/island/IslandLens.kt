package app.cristal.cover.ui.island

import android.graphics.RuntimeShader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.cristal.cover.core.Logx
import app.cristal.cover.data.prefs.IslandStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Forma da ilha. Com [lens] = 0 é a cápsula de sempre (ou um círculo, quando largura e altura são
 * iguais: é assim que a gota nasce); com 1 vira a lente oval da apresentação expandida, um pouco
 * mais cheia na metade de baixo, como uma gota pendurada, sem "pescoço". Entre os dois, cada ponto
 * do contorno é interpolado.
 */
@Immutable
class IslandLensShape(private val lens: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(IslandLensGeometry.path(size, lens.coerceIn(0f, 1f)))
}

internal object IslandLensGeometry {
    private const val STEPS = 144

    /** Expoentes da superelipse: mais oval em cima, mais cheia embaixo (o peso da gota). */
    const val TOP_EXPONENT = 2.6
    const val BOTTOM_EXPONENT = 2.9

    fun path(size: Size, lens: Float): Path {
        val path = Path()
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return path
        val cx = w / 2f
        val cy = h / 2f
        for (i in 0..STEPS) {
            val t = 2.0 * PI * i / STEPS
            val radius = radius(cos(t), sin(t), w, h, lens)
            val px = (cx + cos(t) * radius).toFloat()
            val py = (cy + sin(t) * radius).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        return path
    }

    /** Distância do centro até a borda na direção (c, s), com y positivo para baixo. */
    fun radius(c: Double, s: Double, w: Float, h: Float, lens: Float): Double {
        // Cápsula (estádio): segmento central de meio-comprimento L com raio R.
        val r = minOf(w, h) / 2.0
        val l = (w / 2.0 - r).coerceAtLeast(0.0)
        val stadium = stadiumRadius(c, s, l, r)
        if (lens <= 0f) return stadium
        val pebble = superellipseRadius(c, s, w / 2.0, h / 2.0, exponentAt(s))
        return stadium + (pebble - stadium) * lens
    }

    /** O expoente muda suavemente do topo (s = -1) para baixo (s = 1). */
    fun exponentAt(s: Double): Double {
        val t = ((s + 1.0) / 2.0).coerceIn(0.0, 1.0)
        val k = t * t * (3 - 2 * t)
        return TOP_EXPONENT + (BOTTOM_EXPONENT - TOP_EXPONENT) * k
    }

    /** O ponto (x, y), medido do canto de cima à esquerda, está dentro da forma? */
    fun contains(size: Size, lens: Float, x: Float, y: Float): Boolean {
        val dx = (x - size.width / 2f).toDouble()
        val dy = (y - size.height / 2f).toDouble()
        val d = sqrt(dx * dx + dy * dy)
        if (d < 1e-6) return true
        return d <= radius(dx / d, dy / d, size.width, size.height, lens)
    }

    /** Distância do centro até a borda do estádio na direção (c, s). */
    fun stadiumRadius(c: Double, s: Double, l: Double, r: Double): Double {
        val ac = abs(c)
        val as_ = abs(s)
        if (as_ > 1e-6) {
            val t1 = r / as_
            if (t1 * ac <= l) return t1
        }
        val disc = (r * r - l * l * s * s).coerceAtLeast(0.0)
        return l * ac + sqrt(disc)
    }

    /** Distância do centro até a borda da superelipse |x/a|^n + |y/b|^n = 1 na direção (c, s). */
    fun superellipseRadius(c: Double, s: Double, a: Double, b: Double, n: Double): Double {
        val v = (abs(c) / a).pow(n) + (abs(s) / b).pow(n)
        return if (v <= 0.0) maxOf(a, b) else v.pow(-1.0 / n)
    }
}

/**
 * Shader do interior da lente (AGSL): topo preto (onde fica o cabeçalho), uma faixa de luz
 * iridescente que corre na horizontal e separa o topo do corpo, o corpo em vidro escuro e uma borda
 * colorida embaixo. É a identidade da lente da Siri do iOS 27, sem copiá-la.
 * Tudo é procedural: a janela da ilha fica por cima de outros apps e não pode ler o que está atrás.
 */
internal object IslandShader {
    const val SOURCE = """
uniform float2 uSize;
uniform float uTime;
uniform float uProgress;
uniform float uBandY;
uniform float uGlass;
uniform float4 uTint;

float3 spectrum(float t) {
    float3 a = float3(1.00, 0.45, 0.18);
    float3 b = float3(0.98, 0.25, 0.55);
    float3 c = float3(0.62, 0.32, 1.00);
    float3 d = float3(0.20, 0.55, 1.00);
    float3 e = float3(0.25, 0.92, 0.95);
    float x = fract(t) * 4.0;
    if (x < 1.0) return mix(a, b, x);
    if (x < 2.0) return mix(b, c, x - 1.0);
    if (x < 3.0) return mix(c, d, x - 2.0);
    return mix(d, e, x - 3.0);
}

half4 main(float2 p) {
    float2 uv = p / uSize;
    float3 base = mix(float3(0.0), float3(0.07, 0.065, 0.095), smoothstep(uBandY, 1.0, uv.y));
    float wave = 0.030 * sin(uv.x * 8.2 + uTime * 1.7) + 0.018 * sin(uv.x * 16.9 - uTime * 2.3);
    float bandY = uBandY + wave;
    float d = (uv.y - bandY) / 0.042;
    float side = smoothstep(0.06, 0.3, uv.x) * smoothstep(0.94, 0.7, uv.x);
    float band = exp(-d * d) * side;
    float3 hue = spectrum(uv.x * 0.85 - uTime * 0.06);
    float3 col = uTint.a > 0.5 ? mix(uTint.rgb, hue, 0.38) : hue;
    float core = exp(-pow((uv.y - bandY) / 0.010, 2.0)) * side;
    float3 glow = (col * band * 1.05 + float3(1.0) * core * 0.22) * uProgress;
    float2 q = (uv - 0.5) * 2.0;
    float edge = smoothstep(0.84, 1.0, length(q * float2(1.0, 1.04)));
    float3 rim = spectrum(atan(q.y, q.x) / 6.2832 + uTime * 0.05) * edge * smoothstep(0.35, 1.0, uv.y) * 0.42 * uProgress;
    float3 spec = float3(1.0) * edge * smoothstep(0.2, -0.8, q.x + q.y) * smoothstep(0.1, 0.5, uv.y) * 0.12 * uProgress;
    float3 rgb = base + glow + rim + spec;
    float alpha = mix(1.0, mix(1.0, 0.84, smoothstep(uBandY, 1.0, uv.y)), uGlass);
    return half4(half3(rgb * alpha), half(alpha));
}
"""

    fun create(): RuntimeShader? = try {
        RuntimeShader(SOURCE)
    } catch (t: Throwable) {
        Logx.w("Shader da ilha indisponível; usando degradês", t)
        null
    }
}

/** Cores fixas da ilha. */
internal object IslandPalette {
    val Glass = Color(0xE6171520)
    val Spectrum = listOf(Color(0xFFFF7A2E), Color(0xFFFA408C), Color(0xFF9E52FF), Color(0xFF338CFF), Color(0xFF40EBF2))
}

/**
 * Pinta o fundo da ilha: cápsula preta (ou de vidro) e, na lente, o interior com a faixa de luz.
 * [time] em segundos; [tint] a cor da atividade (ou Unspecified para o espectro); [bandY] a altura
 * da faixa em fração da altura (logo abaixo do cabeçalho).
 */
@Composable
internal fun Modifier.islandFill(
    style: IslandStyle,
    shape: IslandLensShape,
    lens: () -> Float,
    time: () -> Float,
    tint: Color,
    bandY: Float,
): Modifier {
    val shader = remember { IslandShader.create() }
    val brush = remember(shader) { shader?.let { ShaderBrush(it) } }
    return this.drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = (outline as? Outline.Generic)?.path ?: return@drawBehind
        val progress = lens().coerceIn(0f, 1f)
        clipPath(path) {
            // Base: preta na nativa, vidro escuro na versão Liquid Glass.
            drawRect(if (style == IslandStyle.GLASS) IslandPalette.Glass else Color.Black)
            if (progress > 0.01f) {
                if (shader != null && brush != null) {
                    shader.setFloatUniform("uSize", size.width, size.height)
                    shader.setFloatUniform("uTime", time())
                    shader.setFloatUniform("uProgress", progress)
                    shader.setFloatUniform("uBandY", bandY)
                    shader.setFloatUniform("uGlass", if (style == IslandStyle.GLASS) 1f else 0f)
                    val t = if (tint == Color.Unspecified) Color.Transparent else tint
                    shader.setFloatUniform("uTint", t.red, t.green, t.blue, if (tint == Color.Unspecified) 0f else 1f)
                    drawRect(brush)
                } else {
                    fallbackBand(progress, bandY, tint)
                }
            }
        }
        // Borda de luz: sutil na nativa, iridescente no vidro (e em toda expandida).
        val rimAlpha = if (style == IslandStyle.GLASS) 0.55f else 0.35f * progress
        if (rimAlpha > 0.01f) {
            drawPath(
                path,
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = rimAlpha), Color.White.copy(alpha = 0.04f), IslandPalette.Spectrum[3].copy(alpha = rimAlpha * 0.8f)),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
                style = Stroke(width = density),
            )
        }
    }
}

/** Faixa de luz sem shader (aparelhos sem AGSL): um degradê do espectro com bordas suaves. */
private fun DrawScope.fallbackBand(progress: Float, bandY: Float, tint: Color) {
    val colors = if (tint == Color.Unspecified) IslandPalette.Spectrum else listOf(tint, IslandPalette.Spectrum[2], tint)
    val center = size.height * bandY
    val thick = size.height * 0.16f
    drawRect(
        Brush.horizontalGradient(colors.map { it.copy(alpha = 0.75f * progress) }),
        topLeft = Offset(size.width * 0.08f, center - thick / 2),
        size = Size(size.width * 0.84f, thick),
        alpha = 0.6f,
    )
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.28f * progress), Color.Transparent), startY = center - thick / 4, endY = center + thick / 4),
        topLeft = Offset(size.width * 0.12f, center - thick / 4),
        size = Size(size.width * 0.76f, thick / 2),
    )
}
