package app.cristal.cover.ui.fold

import android.graphics.BlendMode
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlin.math.pow

/**
 * Onde está a dobra nesta tela: linha horizontal (o Razr em pé) ou vertical (deitado), na fração
 * [position] da altura ou da largura. Na tela externa, a dobradiça fica na borda de baixo (1).
 */
@Immutable
data class FoldGeometry(val horizontal: Boolean = true, val position: Float = 0.5f) {
    companion object {
        val InnerPortrait = FoldGeometry(true, 0.5f)
        val Cover = FoldGeometry(true, 1f)
    }
}

/** Uma camada da cena: cada uma assenta num ritmo (o papel de parede antes, os componentes depois). */
enum class FoldLayer(val maxBlur: Float, val pace: Float, val zoom: Float, val luminance: Float, val line: Float) {
    /** O papel de parede: desfoque menor, assenta primeiro, leve zoom de chegada (continuidade). */
    WALLPAPER(0.45f, 0.65f, 0.035f, 0.8f, 0f),

    /** Ícones e widgets: desfoque maior, assentam por último, com a linha de luz na dobra. */
    CONTENT(1f, 1.3f, 0f, 1f, 1f),

    /** Uma captura de outro app (sobreposição de fechamento). */
    SNAPSHOT(1f, 1f, 0f, 1f, 1f),
}

/**
 * O efeito da transição de dobra, inspirado na abertura do iPhone Duo e com a identidade do Liquid
 * Flip. Não é um desfoque por cima de uma captura inteira: cada ponto da tela tem um quanto de
 * transformação que depende da distância até a dobra e do progresso. A faixa da dobra muda por
 * último e as regiões longe dela ficam nítidas antes ("gradiente de nitidez").
 *
 * Como é feito (GPU, API 33+): duas camadas de desfoque gaussiano de verdade (leve e forte) e a
 * imagem nítida são misturadas por máscaras AGSL; um passo final escurece perto da dobra, estica o
 * conteúdo de leve em direção a ela e acende a linha de luz em espectro, que some ao assentar.
 * Com o progresso em 1, não há efeito nenhum (custo zero).
 */
object FoldFocus {

    /** O quanto um ponto está em transformação (0 nítido, 1 no máximo): a mesma conta nos shaders e no Kotlin. */
    fun amount(progress: Float, distance: Float): Float {
        val q = 1f - progress.coerceIn(0f, 1f)
        val band = 1.15f * q
        val regional = if (band > 1e-4f) 1f - smooth(band * 0.25f, band, distance) else 0f
        val global = q * q * 0.55f
        return maxOf(regional * q.pow(0.6f), global).coerceIn(0f, 1f)
    }

    /** O progresso desta camada a partir do progresso geral (cada camada no seu ritmo). */
    fun paced(progress: Float, layer: FoldLayer): Float = progress.coerceIn(0f, 1f).pow(layer.pace)

    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private const val COMMON = """
uniform float2 size;
uniform float progress;
uniform float horizontal;
uniform float position;

float axisOf(float2 p) { return horizontal > 0.5 ? p.y / size.y : p.x / size.x; }

// 0 na dobra, 1 na borda mais distante.
float distanceToFold(float2 p) {
    float span = max(position, 1.0 - position);
    return clamp(abs(axisOf(p) - position) / max(span, 0.001), 0.0, 1.0);
}

float regionalOf(float d) {
    float q = 1.0 - progress;
    float band = 1.15 * q;
    return band > 0.0001 ? 1.0 - smoothstep(band * 0.25, band, d) : 0.0;
}

float amountOf(float2 p) {
    float q = 1.0 - progress;
    float d = distanceToFold(p);
    float global = q * q * 0.55;
    return clamp(max(regionalOf(d) * pow(q, 0.6), global), 0.0, 1.0);
}
"""

    /** Devolve a camada com o peso dela: 1 - smoothstep(lo, hi, quanto). */
    private const val MASK = """
uniform shader content;
uniform float lo;
uniform float hi;
$COMMON
half4 main(float2 p) {
    float w = 1.0 - smoothstep(lo, hi, amountOf(p));
    return content.eval(p) * w;
}
"""

    /** Luminosidade, esticamento em direção à dobra e a linha de luz em espectro. */
    private const val FINISH = """
uniform shader content;
uniform float luminance;
uniform float line;
$COMMON
half3 spectrum(float t) {
    half3 a = half3(1.0, 0.478, 0.18);
    half3 b = half3(0.98, 0.25, 0.55);
    half3 c = half3(0.62, 0.32, 1.0);
    half3 d = half3(0.2, 0.55, 1.0);
    half3 e = half3(0.25, 0.92, 0.95);
    t = clamp(t, 0.0, 1.0) * 4.0;
    if (t < 1.0) return mix(a, b, t);
    if (t < 2.0) return mix(b, c, t - 1.0);
    if (t < 3.0) return mix(c, d, t - 2.0);
    return mix(d, e, t - 3.0);
}
half4 main(float2 p) {
    float q = 1.0 - progress;
    float regional = regionalOf(distanceToFold(p));
    float2 c = p;
    float s = 0.05 * q * regional;
    if (horizontal > 0.5) {
        float f = position * size.y;
        c.y = f + (p.y - f) * (1.0 - s);
    } else {
        float f = position * size.x;
        c.x = f + (p.x - f) * (1.0 - s);
    }
    half4 col = content.eval(c);
    col.rgb *= 1.0 - q * luminance * (0.16 + 0.34 * regional);
    float foldPx = horizontal > 0.5 ? position * size.y : position * size.x;
    float along = horizontal > 0.5 ? p.x / size.x : p.y / size.y;
    float dist = abs((horizontal > 0.5 ? p.y : p.x) - foldPx);
    float glow = exp(-dist / (4.0 + 36.0 * q)) * q * line * 0.55;
    col.rgb += spectrum(along) * glow * col.a;
    return col;
}
"""

    /** Os shaders compilados de uma camada (reaproveitados quadro a quadro). */
    class Shaders internal constructor() {
        internal val sharp = RuntimeShader(MASK)
        internal val soft = RuntimeShader(MASK)
        internal val finish = RuntimeShader(FINISH)
    }

    fun shaders(): Shaders? = runCatching { Shaders() }.getOrNull()

    /**
     * O efeito para este quadro, ou null quando a camada já assentou (sem custo). [maxBlurPx] é o
     * desfoque no auge da transformação.
     */
    fun effect(
        shaders: Shaders,
        width: Float,
        height: Float,
        progress: Float,
        geometry: FoldGeometry,
        layer: FoldLayer,
        maxBlurPx: Float,
    ): RenderEffect? {
        val p = paced(progress, layer)
        if (p >= 0.999f || width < 2f || height < 2f) return null
        val q = 1f - p
        fun RuntimeShader.common() {
            setFloatUniform("size", width, height)
            setFloatUniform("progress", p)
            setFloatUniform("horizontal", if (geometry.horizontal) 1f else 0f)
            setFloatUniform("position", geometry.position)
        }
        shaders.sharp.common()
        shaders.sharp.setFloatUniform("lo", 0f)
        shaders.sharp.setFloatUniform("hi", 0.5f)
        shaders.soft.common()
        shaders.soft.setFloatUniform("lo", 0.5f)
        shaders.soft.setFloatUniform("hi", 1f)
        shaders.finish.common()
        shaders.finish.setFloatUniform("luminance", layer.luminance)
        shaders.finish.setFloatUniform("line", layer.line)

        val radius = (maxBlurPx * layer.maxBlur * (0.35f + 0.65f * q)).coerceAtLeast(0.5f)
        val strong = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
        val light = RenderEffect.createBlurEffect(radius / 3f, radius / 3f, Shader.TileMode.CLAMP)
        val lightMasked = RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(shaders.soft, "content"), light)
        val sharpMasked = RenderEffect.createRuntimeShaderEffect(shaders.sharp, "content")
        val blurred = RenderEffect.createBlendModeEffect(strong, lightMasked, BlendMode.SRC_OVER)
        val composite = RenderEffect.createBlendModeEffect(blurred, sharpMasked, BlendMode.SRC_OVER)
        return RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(shaders.finish, "content"), composite)
    }
}

/**
 * Aplica a transição de dobra a uma camada. [progress] é lido só no desenho (não recompõe a tela a
 * cada quadro). Sem suporte a shaders (ou com o progresso em 1), não faz nada.
 */
@Composable
fun Modifier.foldFocus(progress: State<Float>, geometry: FoldGeometry, layer: FoldLayer, maxBlur: Dp): Modifier {
    val shaders = remember { FoldFocus.shaders() }
    val maxBlurPx = with(LocalDensity.current) { maxBlur.toPx() }
    if (shaders == null) return this
    return this.graphicsLayer {
        val p = progress.value
        val paced = FoldFocus.paced(p, layer)
        if (layer.zoom > 0f) {
            val s = 1f + layer.zoom * (1f - paced)
            scaleX = s
            scaleY = s
        }
        val effect = FoldFocus.effect(shaders, size.width, size.height, p, geometry, layer, maxBlurPx)
        renderEffect = effect?.asComposeRenderEffect()
        // O desfoque não vaza para fora da camada.
        clip = effect != null
    }
}
