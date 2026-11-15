package app.cristal.cover.ui.glass

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.cristal.cover.core.Logx
import app.cristal.cover.data.prefs.GlassQuality
import kotlin.math.ceil
import kotlin.math.min

/** Parâmetros globais do vidro, derivados das configurações e do tema. */
@Immutable
data class GlassSpec(
    val quality: GlassQuality = GlassQuality.LIQUID,
    /** 0 = vidro quase limpo, 1 = bem fosco e com refração forte. */
    val intensity: Float = 0.7f,
    val dark: Boolean = true,
) {
    val blurDp: Float get() = 5f + 21f * intensity
    val saturation: Float get() = 1f + 0.6f * intensity
    val specular: Float get() = 0.30f + 0.40f * intensity
    val dispersion: Float get() = 0.10f + 0.50f * intensity
    val refractionScale: Float get() = 0.45f + 0.85f * intensity

    /** Película aplicada sobre o fundo borrado, para o conteúdo ficar legível. */
    val tint: Color get() = if (dark) Color(0f, 0f, 0f, 0.26f) else Color(1f, 1f, 1f, 0.36f)

    /** Preenchimento do modo leve (sem blur), mais opaco para compensar. */
    val lightFill: Color get() = if (dark) Color(0.09f, 0.10f, 0.13f, 0.58f) else Color(1f, 1f, 1f, 0.66f)

    val contentColor: Color get() = if (dark) Color.White else Color(0xFF14161A)
    val secondaryContentColor: Color get() = contentColor.copy(alpha = 0.68f)
}

val LocalGlassSpec = staticCompositionLocalOf { GlassSpec() }

/** Fundo que os elementos de vidro deste trecho da árvore devem amostrar. */
val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/** Direção da luz (vetor unitário, y para baixo). Padrão: vinda do canto superior esquerdo. */
val DefaultGlassLight = Offset(-0.6f, -0.8f)
val LocalGlassLight = staticCompositionLocalOf<State<Offset>> { mutableStateOf(DefaultGlassLight) }

private data class GlassKey(
    val w: Float,
    val h: Float,
    val radius: Float,
    val blurPx: Float,
    val intensity: Float,
    val tint: Color,
    val light: Offset,
    val touch: Offset,
    val touchAmount: Float,
    val liquid: Boolean,
)

private class GlassCache {
    var origin by mutableStateOf(Offset.Zero)
    var key: GlassKey? = null
    var effect: RenderEffect? = null
    var shaderBroken = false
    val path = Path()
}

/**
 * Transforma o elemento em vidro líquido.
 *
 * Conforme a qualidade em [GlassSpec]:
 * - LIQUID: blur + shader AGSL de refração, dispersão e brilho (ver [GlassShaders]);
 * - BLUR: só o blur real do fundo, com película e borda;
 * - LIGHT: película translúcida com borda, sem nenhuma camada extra na GPU.
 *
 * Se não houver fundo disponível ([backdrop] nulo) ou o shader falhar, cai para o nível abaixo.
 *
 * @param touch estado de toque opcional; quando informado, o vidro reage ao dedo.
 */
@Composable
fun Modifier.liquidGlass(
    cornerRadius: Dp,
    tint: Color = Color.Unspecified,
    touch: GlassTouchState? = null,
    backdrop: GlassBackdrop? = LocalGlassBackdrop.current,
    spec: GlassSpec = LocalGlassSpec.current,
): Modifier {
    val density = LocalDensity.current
    val light = LocalGlassLight.current
    val layer = rememberGraphicsLayer()
    val cache = remember { GlassCache() }

    val wantsLiquid = spec.quality == GlassQuality.LIQUID && backdrop != null
    val shader: RuntimeShader? = remember(wantsLiquid) { if (wantsLiquid) GlassShaders.newLiquid() else null }
    val quality = when {
        backdrop == null -> GlassQuality.LIGHT
        spec.quality == GlassQuality.LIQUID && shader == null -> GlassQuality.BLUR
        else -> spec.quality
    }

    val cornerPx = with(density) { cornerRadius.toPx() }
    val blurPx = with(density) { spec.blurDp.dp.toPx() }
    val bezelMaxPx = with(density) { 18.dp.toPx() }
    val strokePx = with(density) { 1.dp.toPx() }
    val tintColor = if (tint.isSpecified) tint else spec.tint

    return this
        .onGloballyPositioned { coordinates ->
            if (backdrop != null) {
                val position = backdrop.positionOf(coordinates)
                if (position != cache.origin) cache.origin = position
            }
        }
        .drawWithContent {
            val w = size.width
            val h = size.height
            if (w < 1f || h < 1f) {
                drawContent()
                return@drawWithContent
            }
            val radius = min(cornerPx, min(w, h) / 2f)
            val source = backdrop?.layer
            if (quality == GlassQuality.LIGHT || source == null) {
                drawLightGlass(radius, strokePx, if (tint.isSpecified) tint else spec.lightFill, spec.dark)
                drawContent()
                return@drawWithContent
            }

            val liquid = quality == GlassQuality.LIQUID && shader != null && !cache.shaderBroken
            val origin = cache.origin
            val key = GlassKey(
                w = w,
                h = h,
                radius = radius,
                blurPx = blurPx,
                intensity = spec.intensity,
                tint = tintColor,
                light = if (liquid) light.value else Offset.Zero,
                touch = if (liquid && touch != null) touch.position else Offset.Zero,
                touchAmount = if (liquid && touch != null) touch.amount.value else 0f,
                liquid = liquid,
            )
            if (key != cache.key) {
                cache.key = key
                cache.effect = if (liquid) {
                    try {
                        buildLiquidEffect(shader, key, spec, min(min(w, h) * 0.5f, bezelMaxPx))
                    } catch (t: Throwable) {
                        Logx.e("AGSL: falha ao configurar o shader; usando blur simples", t)
                        cache.shaderBroken = true
                        buildBlurEffect(blurPx)
                    }
                } else {
                    buildBlurEffect(blurPx)
                }
            }

            layer.record(IntSize(ceil(w).toInt(), ceil(h).toInt())) {
                translate(-origin.x, -origin.y) { drawLayer(source) }
            }
            layer.renderEffect = cache.effect

            if (liquid && !cache.shaderBroken) {
                // O próprio shader recorta o retângulo arredondado e desenha película e brilho.
                drawLayer(layer)
            } else {
                val path = cache.path
                path.rewind()
                path.addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(radius, radius)))
                clipPath(path) {
                    drawLayer(layer)
                    drawRect(tintColor)
                }
                drawRim(radius, strokePx, spec.dark)
            }
            drawContent()
        }
}

private fun buildBlurEffect(blurPx: Float): RenderEffect? {
    if (blurPx < 1f) return null
    return AndroidRenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP).asComposeRenderEffect()
}

private fun buildLiquidEffect(shader: RuntimeShader, key: GlassKey, spec: GlassSpec, bezelPx: Float): RenderEffect {
    shader.setFloatUniform("uSize", key.w, key.h)
    shader.setFloatUniform("uRadius", key.radius)
    shader.setFloatUniform("uBezel", bezelPx)
    shader.setFloatUniform("uRefraction", bezelPx * spec.refractionScale)
    shader.setFloatUniform("uDispersion", spec.dispersion)
    shader.setFloatUniform("uTint", key.tint.red, key.tint.green, key.tint.blue, key.tint.alpha)
    shader.setFloatUniform("uLight", key.light.x, key.light.y)
    shader.setFloatUniform("uSpecular", spec.specular)
    shader.setFloatUniform("uSaturation", spec.saturation)
    shader.setFloatUniform("uTouch", key.touch.x, key.touch.y)
    shader.setFloatUniform("uTouchAmt", key.touchAmount)
    val refraction = AndroidRenderEffect.createRuntimeShaderEffect(shader, "content")
    val chained = if (key.blurPx >= 1f) {
        val blur = AndroidRenderEffect.createBlurEffect(key.blurPx, key.blurPx, Shader.TileMode.CLAMP)
        AndroidRenderEffect.createChainEffect(refraction, blur)
    } else {
        refraction
    }
    return chained.asComposeRenderEffect()
}

private fun DrawScope.drawLightGlass(radius: Float, strokePx: Float, fill: Color, dark: Boolean) {
    drawRoundRect(color = fill, cornerRadius = CornerRadius(radius, radius))
    drawRim(radius, strokePx, dark)
}

/** Borda luminosa: mais clara no canto de onde vem a luz, quase invisível no meio. */
private fun DrawScope.drawRim(radius: Float, strokePx: Float, dark: Boolean) {
    val strong = if (dark) 0.42f else 0.85f
    val brush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = strong),
            Color.White.copy(alpha = strong * 0.15f),
            Color.White.copy(alpha = strong * 0.5f),
        ),
        start = Offset.Zero,
        end = Offset(size.width, size.height),
    )
    val inset = strokePx / 2f
    drawRoundRect(
        brush = brush,
        topLeft = Offset(inset, inset),
        size = androidx.compose.ui.geometry.Size(size.width - strokePx, size.height - strokePx),
        cornerRadius = CornerRadius((radius - inset).coerceAtLeast(0f)),
        style = Stroke(width = strokePx),
    )
}

/**
 * Sombra suave sob o vidro. Não é aplicada no modo leve nem sem fundo, porque a sombra apareceria
 * através da película translúcida.
 */
@Composable
fun Modifier.glassShadow(cornerRadius: Dp, elevation: Dp = 12.dp): Modifier {
    val spec = LocalGlassSpec.current
    val backdrop = LocalGlassBackdrop.current
    if (elevation <= 0.dp || spec.quality == GlassQuality.LIGHT || backdrop == null) return this
    return this.shadow(
        elevation = elevation,
        shape = RoundedCornerShape(cornerRadius),
        clip = false,
        ambientColor = Color.Black.copy(alpha = 0.35f),
        spotColor = Color.Black.copy(alpha = 0.5f),
    )
}
