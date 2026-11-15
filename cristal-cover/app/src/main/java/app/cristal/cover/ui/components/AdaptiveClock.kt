package app.cristal.cover.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import app.cristal.cover.data.prefs.ClockFont
import app.cristal.cover.data.prefs.ClockLayout
import app.cristal.cover.ui.theme.clockStyle

/** Tamanho calculado do relógio: [scale] multiplica a fonte de referência; [stretch] estica na vertical. */
@Immutable
data class ClockFit(val stacked: Boolean, val scale: Float, val stretch: Float)

object ClockFitting {
    /** Fonte de referência usada para medir (em sp). */
    const val REFERENCE_SP = 100f

    /** Empilha (horas sobre minutos) quando o espaço livre é alto o bastante. */
    fun stacked(layout: ClockLayout, boxWidth: Float, boxHeight: Float): Boolean = when (layout) {
        ClockLayout.LINE -> false
        ClockLayout.STACKED -> true
        ClockLayout.AUTO -> boxHeight > boxWidth * 0.62f
    }

    /**
     * Faz o relógio ocupar a caixa: cresce até bater na largura ou na altura e, se ainda sobrar
     * altura, estica as letras na vertical (até [maxStretch]), como o relógio adaptativo do iOS 26.
     *
     * @param textWidth e [textHeight] medidas do texto na fonte de referência.
     */
    fun fit(textWidth: Float, textHeight: Float, boxWidth: Float, boxHeight: Float, maxStretch: Float, stacked: Boolean): ClockFit {
        if (textWidth <= 0f || textHeight <= 0f || boxWidth <= 0f || boxHeight <= 0f) return ClockFit(stacked, 1f, 1f)
        val scale = minOf(boxWidth * 0.96f / textWidth, boxHeight / textHeight).coerceIn(0.2f, 5f)
        val leftover = boxHeight / (textHeight * scale)
        val stretch = if (leftover > 1.06f) leftover.coerceAtMost(maxStretch) else 1f
        return ClockFit(stacked, scale, stretch)
    }
}

/**
 * Relógio de formato adaptativo.
 *
 * Com [adaptive], ocupa a caixa recebida em [modifier] (que precisa ter largura e altura):
 * escolhe uma linha ou horas empilhadas sobre os minutos, cresce até caber e estica na vertical
 * quando sobra altura. Sem [adaptive], usa [fixedSize] e uma linha.
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
    /** Onde o relógio fica na caixa quando sobra altura (no bloqueio, em cima; no Cover Clock, no meio). */
    verticalAlignment: Alignment.Vertical = Alignment.Top,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val base = remember(font) { font.clockStyle() }
    val parts = remember(time) {
        val i = time.indexOf(':')
        if (i <= 0) listOf(time) else listOf(time.substring(0, i), time.substring(i + 1))
    }
    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth.toFloat()
        val h = if (constraints.hasBoundedHeight) constraints.maxHeight.toFloat() else w
        val stacked = adaptive && parts.size == 2 && ClockFitting.stacked(layout, w, h)
        // Empilhado, a hora ganha o zero da frente ("07" sobre "31"), para os dois blocos terem a mesma largura.
        val lines = if (stacked) listOf(parts[0].padStart(2, '0'), parts[1]) else listOf(time)
        val reference = base.copy(fontSize = ClockFitting.REFERENCE_SP.sp)
        // Altura natural de cada linha na fonte de referência (as fontes altas precisam dela inteira).
        val measured = remember(lines, reference) {
            lines.map { measurer.measure(it, reference, softWrap = false, maxLines = 1).size }
        }
        val fit = remember(measured, w, h, adaptive, font, stacked) {
            val textW = measured.maxOf { it.width }.toFloat()
            val textH = measured.sumOf { it.height }.toFloat()
            if (adaptive) {
                ClockFitting.fit(textW, textH, w, h, font.maxStretch, stacked)
            } else {
                ClockFit(false, fixedSize.value / ClockFitting.REFERENCE_SP, 1f)
            }
        }
        val fontSize = (ClockFitting.REFERENCE_SP * fit.scale).sp
        val sized = base.copy(fontSize = fontSize, shadow = shadow)
        val style = if (brush != null) sized.copy(brush = brush) else sized.copy(color = color)
        Column(
            modifier = Modifier.align(
                when (verticalAlignment) {
                    Alignment.Bottom -> when (horizontalAlignment) {
                        Alignment.Start -> Alignment.BottomStart
                        Alignment.End -> Alignment.BottomEnd
                        else -> Alignment.BottomCenter
                    }
                    Alignment.CenterVertically -> when (horizontalAlignment) {
                        Alignment.Start -> Alignment.CenterStart
                        Alignment.End -> Alignment.CenterEnd
                        else -> Alignment.Center
                    }
                    else -> when (horizontalAlignment) {
                        Alignment.Start -> Alignment.TopStart
                        Alignment.End -> Alignment.TopEnd
                        else -> Alignment.TopCenter
                    }
                },
            ),
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = Arrangement.Top,
        ) {
            for ((i, line) in lines.withIndex()) {
                val lineHeight = with(density) { (measured[i].height * fit.scale * fit.stretch).toDp() }
                Text(
                    line,
                    style = style,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                    modifier = Modifier
                        .height(lineHeight)
                        .wrapContentWidth(unbounded = true)
                        .graphicsLayer {
                            scaleY = fit.stretch
                            transformOrigin = TransformOrigin(0.5f, 0f)
                        },
                )
            }
        }
    }
}
