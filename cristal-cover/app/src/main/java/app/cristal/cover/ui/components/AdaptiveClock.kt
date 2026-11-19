package app.cristal.cover.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cristal.cover.data.prefs.ClockFill
import app.cristal.cover.data.prefs.ClockFont
import app.cristal.cover.data.prefs.ClockLayout
import app.cristal.cover.ui.glass.GlassBackdrop
import app.cristal.cover.ui.theme.clockStyle
import app.cristal.cover.ui.theme.inkBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.ceil

/** Tamanho calculado do relógio: [scale] multiplica a fonte de referência; [stretch] estica na vertical. */
@Immutable
data class ClockFit(val stacked: Boolean, val scale: Float, val stretch: Float)

/**
 * Números de vidro (estilo "Vidro" do relógio do iOS 26): o fundo desfocado aparece dentro dos
 * algarismos, com uma película branca de [opacity] e uma borda brilhante.
 */
@Immutable
data class ClockGlass(
    val backdrop: GlassBackdrop,
    val opacity: Float = 0.32f,
    val blurDp: Float = 16f,
    val tint: Color = Color.White,
    /** Cor de baixo da película (vidro colorido em gradiente); igual a [tint] por padrão. */
    val tint2: Color = tint,
)

/**
 * Relógio colorido: os algarismos cheios de cor (sólido, gradiente ou translúcido), respeitando o
 * desenho da fonte. Com [split], horas e minutos têm cores próprias (a segunda nos minutos), seja
 * empilhado ou numa linha só (o corte fica no meio dos dois-pontos).
 */
@Immutable
data class ClockFillSpec(val mode: ClockFill, val color: Color, val color2: Color, val opacity: Float, val split: Boolean) {
    /** Opacidade efetiva do miolo (o translúcido deixa o fundo aparecer). */
    val fillAlpha: Float get() = if (mode == ClockFill.TRANSLUCENT) (opacity * 0.62f).coerceIn(0.12f, 0.8f) else opacity.coerceIn(0.2f, 1f)

    /** O pincel de uma linha do relógio ([index] de [lines]). */
    fun brushFor(layout: TextLayoutResult, index: Int, lines: Int): Brush {
        val a = fillAlpha
        val c1 = color.copy(alpha = a)
        val c2 = color2.copy(alpha = a)
        if (split) {
            if (lines >= 2) return lineBrush(if (index == 0) c1 else c2)
            val text = layout.layoutInput.text.text
            val colon = text.indexOf(':')
            val width = layout.size.width.toFloat()
            if (colon > 0 && width > 0f) {
                val cut = (layout.getBoundingBox(colon).center.x / width).coerceIn(0.05f, 0.95f)
                return Brush.horizontalGradient(0f to c1, cut to c1, cut to c2, 1f to c2)
            }
        }
        return if (mode == ClockFill.GRADIENT) Brush.verticalGradient(listOf(c1, c2)) else lineBrush(c1)
    }

    /** Cor quase chapada, com um leve escurecer embaixo (profundidade sem perder a cor). */
    private fun lineBrush(c: Color): Brush = Brush.verticalGradient(listOf(c, c.copy(alpha = c.alpha * 0.9f)))
}

object ClockFitting {
    /** Fonte de referência usada para medir (em sp). */
    const val REFERENCE_SP = 100f

    /**
     * Uma linha ou empilhado. No automático, o relógio prefere UMA linha esticada na vertical
     * (como no iOS 26) e só empilha quando, mesmo esticado ao máximo, ainda sobraria muita altura.
     */
    fun stacked(layout: ClockLayout, lineFit: ClockFit, lineHeight: Float, boxWidth: Float, boxHeight: Float): Boolean = when (layout) {
        ClockLayout.LINE -> false
        ClockLayout.STACKED -> true
        ClockLayout.AUTO -> {
            val filled = lineHeight * lineFit.scale * lineFit.stretch
            boxHeight > boxWidth * 0.75f && filled > 0f && boxHeight / filled > 1.3f
        }
    }

    /**
     * Faz o relógio ocupar a caixa: cresce até bater na largura ou na altura e, se ainda sobrar
     * altura, estica as letras na vertical (até [maxStretch]), como o relógio adaptativo do iOS 26.
     */
    fun fit(textWidth: Float, textHeight: Float, boxWidth: Float, boxHeight: Float, maxStretch: Float, stacked: Boolean): ClockFit {
        if (textWidth <= 0f || textHeight <= 0f || boxWidth <= 0f || boxHeight <= 0f) return ClockFit(stacked, 1f, 1f)
        val scale = minOf(boxWidth * 0.96f / textWidth, boxHeight / textHeight).coerceIn(0.2f, 5f)
        val leftover = boxHeight / (textHeight * scale)
        val stretch = if (leftover > 1.04f) leftover.coerceAtMost(maxStretch) else 1f
        return ClockFit(stacked, scale, stretch)
    }
}

/**
 * Relógio de formato adaptativo.
 *
 * Com [adaptive], ocupa a caixa recebida em [modifier] (que precisa ter largura e altura): cresce
 * até caber, estica na vertical quando sobra altura (relógio "escorrido") e, se for muito alto,
 * empilha as horas sobre os minutos. Sem [adaptive], usa [fixedSize] e uma linha.
 *
 * Desenha os algarismos à mão (TextMeasurer), o que permite o estilo de vidro com [glass].
 */
@Composable
fun AdaptiveClock(
    time: String,
    font: ClockFont,
    layout: ClockLayout,
    adaptive: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    brush: Brush? = null,
    shadow: Shadow? = WallpaperTextShadow,
    fixedSize: TextUnit = 96.sp,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    /** Onde o relógio fica na caixa quando sobra altura. */
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    glass: ClockGlass? = null,
    /** Relógio colorido (quando não é de vidro). */
    fill: ClockFillSpec? = null,
) {
    val measurer = rememberTextMeasurer()
    val context = LocalContext.current
    val density = LocalDensity.current
    val base = remember(font) { font.clockStyle() }
    val parts = remember(time) {
        val i = time.indexOf(':')
        if (i <= 0) listOf(time) else listOf(time.substring(0, i), time.substring(i + 1))
    }
    BoxWithConstraints(modifier.semantics { contentDescription = time }) {
        val w = constraints.maxWidth.toFloat()
        val h = if (constraints.hasBoundedHeight) constraints.maxHeight.toFloat() else w
        val reference = base.copy(fontSize = ClockFitting.REFERENCE_SP.sp)

        val ink = remember(font) { font.inkBounds(context) }
        val refPx = with(density) { ClockFitting.REFERENCE_SP.sp.toPx() }
        // Altura dos algarismos (sem o espaço vazio da linha) na fonte de referência.
        val inkRef = (ink[1] - ink[0]) * refPx
        val gapRatio = 0.10f

        val plan = remember(time, reference, w, h, adaptive, font, layout, refPx) {
            val lineRef = measurer.measure(time, reference, softWrap = false, maxLines = 1).size
            val lineFit = ClockFitting.fit(lineRef.width.toFloat(), inkRef, w, h, font.maxStretch, false)
            val stacked = adaptive && parts.size == 2 && ClockFitting.stacked(layout, lineFit, inkRef, w, h)
            // Empilhado, a hora ganha o zero da frente ("07" sobre "34"), para os dois blocos terem a mesma largura.
            val lines = if (stacked) listOf(parts[0].padStart(2, '0'), parts[1]) else listOf(time)
            val widths = lines.map { measurer.measure(it, reference, softWrap = false, maxLines = 1).size.width }
            val fit = when {
                !adaptive -> ClockFit(false, fixedSize.value / ClockFitting.REFERENCE_SP, 1f)
                stacked -> ClockFitting.fit(
                    widths.max().toFloat(), inkRef * lines.size + inkRef * gapRatio * (lines.size - 1), w, h, font.maxStretch, true,
                )
                else -> lineFit
            }
            val style = reference.copy(fontSize = (ClockFitting.REFERENCE_SP * fit.scale).sp)
            val px = refPx * fit.scale
            ClockPlan(
                fit = fit,
                lines = lines.map { measurer.measure(it, style, softWrap = false, maxLines = 1) },
                inkTop = ink[0] * px,
                inkHeight = (ink[1] - ink[0]) * px,
                gap = (ink[1] - ink[0]) * px * gapRatio,
            )
        }

        val paintBrush = brush
        val glassLayer = rememberGraphicsLayer()
        val maskLayer = rememberGraphicsLayer()
        var origin by remember { mutableStateOf(Offset.Zero) }
        val backdrop = glass?.backdrop

        // Sombra suave por trás (legibilidade sobre fotos claras), numa camada separada.
        Box(
            Modifier.fillMaxSize().drawBehind {
                eachLine(plan, horizontalAlignment, verticalAlignment) { layoutResult ->
                    drawText(
                        layoutResult,
                        color = Color.Black.copy(alpha = if (glass != null) 0.06f else 0.001f),
                        shadow = if (glass != null) Shadow(Color.Black.copy(alpha = 0.28f), Offset(0f, 2.dp.toPx()), 14.dp.toPx()) else shadow,
                    )
                }
            },
        )
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { if (backdrop != null) origin = backdrop.positionOf(it) }
                // A camada separada só é necessária para a máscara do vidro (DstIn); sem vidro, desenho direto.
                .graphicsLayer { compositingStrategy = if (glass != null) CompositingStrategy.Offscreen else CompositingStrategy.Auto }
                .drawBehind {
                    if (glass != null && backdrop != null) {
                        val blur = glass.blurDp.dp.toPx()
                        // 1. O fundo desfocado, sem esticar, só na área do relógio.
                        glassLayer.record(IntSize(ceil(size.width).toInt(), ceil(size.height).toInt())) {
                            translate(-origin.x, -origin.y) { drawLayer(backdrop.layer) }
                        }
                        glassLayer.renderEffect = if (blur >= 1f) BlurEffect(blur, blur, TileMode.Clamp) else null
                        drawLayer(glassLayer)
                        // 2. Máscara: fica só o que está dentro dos algarismos.
                        maskLayer.record(IntSize(ceil(size.width).toInt(), ceil(size.height).toInt())) {
                            eachLine(plan, horizontalAlignment, verticalAlignment) { drawText(it, color = Color.Black) }
                        }
                        maskLayer.blendMode = BlendMode.DstIn
                        drawLayer(maskLayer)
                        // 3. Película leitosa e borda de luz, mais forte no alto (luz vinda de cima).
                        eachLine(plan, horizontalAlignment, verticalAlignment) { layoutResult ->
                            drawText(
                                layoutResult,
                                brush = Brush.verticalGradient(
                                    listOf(glass.tint.copy(alpha = glass.opacity + 0.12f), glass.tint2.copy(alpha = glass.opacity * 0.55f)),
                                ),
                                // Sempre explícito: o TextPaint é o mesmo da sombra (que desenha quase transparente).
                                alpha = 1f,
                            )
                            drawText(
                                layoutResult,
                                brush = Brush.verticalGradient(listOf(glass.tint.copy(alpha = 0.95f), glass.tint.copy(alpha = 0.30f))),
                                alpha = 1f,
                                drawStyle = Stroke(width = 1.3.dp.toPx()),
                            )
                        }
                    } else if (fill != null) {
                        var index = 0
                        val count = plan.lines.size
                        eachLine(plan, horizontalAlignment, verticalAlignment) { layoutResult ->
                            drawText(layoutResult, brush = fill.brushFor(layoutResult, index, count), alpha = 1f)
                            if (fill.mode == ClockFill.TRANSLUCENT) {
                                // Contorno de luz: o número continua legível com o fundo aparecendo.
                                drawText(
                                    layoutResult,
                                    brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.9f), fill.color.copy(alpha = 0.55f))),
                                    alpha = 1f,
                                    drawStyle = Stroke(width = 1.4.dp.toPx()),
                                )
                            }
                            index++
                        }
                    } else {
                        eachLine(plan, horizontalAlignment, verticalAlignment) { layoutResult ->
                            if (paintBrush != null) {
                                drawText(layoutResult, brush = paintBrush, alpha = 1f)
                            } else {
                                drawText(layoutResult, color = color)
                            }
                        }
                    }
                },
        )
    }
}

/**
 * Linhas medidas e posição dos algarismos: [inkTop] é o topo dos números em relação à linha de base
 * (negativo) e [inkHeight] a altura deles, já no tamanho final.
 */
@Immutable
private class ClockPlan(
    val fit: ClockFit,
    val lines: List<TextLayoutResult>,
    val inkTop: Float,
    val inkHeight: Float,
    val gap: Float,
) {
    val height: Float get() = (inkHeight * lines.size + gap * (lines.size - 1)) * fit.stretch
}

/** Percorre as linhas já posicionadas e esticadas, chamando [draw] no sistema de coordenadas de cada uma. */
private inline fun DrawScope.eachLine(
    plan: ClockPlan,
    horizontal: Alignment.Horizontal,
    vertical: Alignment.Vertical,
    draw: DrawScope.(TextLayoutResult) -> Unit,
) {
    val total = plan.height
    var y = when (vertical) {
        Alignment.Bottom -> size.height - total
        Alignment.CenterVertically -> (size.height - total) / 2f
        else -> 0f
    }
    for (line in plan.lines) {
        val lw = line.size.width.toFloat()
        val x = when (horizontal) {
            Alignment.Start -> 0f
            Alignment.End -> size.width - lw
            else -> (size.width - lw) / 2f
        }
        // O topo dos algarismos encosta em y; a esticada parte daí para baixo.
        val baseline = line.firstBaseline
        translate(x, y) {
            scale(1f, plan.fit.stretch, pivot = Offset.Zero) {
                translate(0f, -(baseline + plan.inkTop)) { draw(line) }
            }
        }
        y += (plan.inkHeight + plan.gap) * plan.fit.stretch
    }
}
