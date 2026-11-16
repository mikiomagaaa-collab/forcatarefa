package app.cristal.cover.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.cristal.cover.ui.glass.rememberHaptic

/** Cores prontas mostradas acima do espectro (o branco é o "padrão" de relógios e ícones). */
val LiquidQuickColors: List<Color> = listOf(
    Color(0xFFFFFFFF),
    Color(0xFFF2E6D0), // champanhe
    Color(0xFFFFD60A),
    Color(0xFFFF9F0A),
    Color(0xFFFF6B8B),
    Color(0xFFE067C9),
    Color(0xFFB9A2FF),
    Color(0xFF6544E8),
    Color(0xFF0A84FF),
    Color(0xFF40C8E0),
    Color(0xFF30D158),
    Color(0xFF8E8A9C),
    Color(0xFF1C1B22),
)

/**
 * Seletor de cor em espectro: um quadrado de saturação × brilho, uma faixa com todas as cores e
 * as cores prontas. [initial] só define o ponto de partida; cada mudança vai para [onColorChange].
 */
@Composable
fun LiquidColorPicker(initial: Color, onColorChange: (Color) -> Unit, modifier: Modifier = Modifier) {
    val start = remember(initial) { FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) } }
    var hue by remember(initial) { mutableFloatStateOf(start[0]) }
    var saturation by remember(initial) { mutableFloatStateOf(start[1]) }
    var brightness by remember(initial) { mutableFloatStateOf(start[2]) }
    val change by rememberUpdatedState(onColorChange)
    val haptic = rememberHaptic()
    fun emit() = change(Color.hsv(hue.coerceIn(0f, 359.9f), saturation.coerceIn(0f, 1f), brightness.coerceIn(0f, 1f)))

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LiquidQuickColors.forEach { quick ->
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(quick)
                        .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                        .selectable(false, interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                            haptic()
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(quick.toArgb(), hsv)
                            hue = hsv[0]
                            saturation = hsv[1]
                            brightness = hsv[2]
                            emit()
                        },
                )
            }
        }
        // Saturação (horizontal) × brilho (vertical).
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.7f)
                .clip(RoundedCornerShape(LiquidRadius.cell))
                .drawBehind {
                    drawRect(Color.hsv(hue.coerceIn(0f, 359.9f), 1f, 1f))
                    drawRect(Brush.horizontalGradient(listOf(Color.White, Color.White.copy(alpha = 0f))))
                    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0f), Color.Black)))
                    val p = Offset(saturation * size.width, (1f - brightness) * size.height)
                    drawCircle(Color.Black.copy(alpha = 0.25f), 13.dp.toPx(), p)
                    drawCircle(Color.White, 12.dp.toPx(), p, style = Stroke(3.dp.toPx()))
                    drawCircle(Color.hsv(hue.coerceIn(0f, 359.9f), saturation, brightness), 9.dp.toPx(), p)
                }
                .pointerInput(Unit) {
                    fun pick(at: Offset) {
                        saturation = (at.x / size.width).coerceIn(0f, 1f)
                        brightness = (1f - at.y / size.height).coerceIn(0f, 1f)
                        emit()
                    }
                    detectTapGestures { pick(it) }
                }
                .pointerInput(Unit) {
                    detectDragGestures(onDragStart = { /* o primeiro movimento já reposiciona */ }) { input, _ ->
                        input.consume()
                        saturation = (input.position.x / size.width).coerceIn(0f, 1f)
                        brightness = (1f - input.position.y / size.height).coerceIn(0f, 1f)
                        emit()
                    }
                },
        )
        // Faixa de matiz: todas as cores.
        Box(
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .clip(CircleShape)
                .drawBehind {
                    drawRect(Brush.horizontalGradient((0..6).map { Color.hsv((it * 60f).coerceAtMost(359.9f), 1f, 1f) }))
                    val x = (hue / 360f) * size.width
                    val center = Offset(x.coerceIn(size.height / 2, size.width - size.height / 2), size.height / 2)
                    drawCircle(Color.White, size.height / 2 - 2.dp.toPx(), center, style = Stroke(3.dp.toPx()))
                }
                .pointerInput(Unit) {
                    detectTapGestures {
                        hue = (it.x / size.width * 360f).coerceIn(0f, 359.9f)
                        emit()
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { input, _ ->
                        input.consume()
                        hue = (input.position.x / size.width * 360f).coerceIn(0f, 359.9f)
                        emit()
                    }
                },
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val current = Color.hsv(hue.coerceIn(0f, 359.9f), saturation, brightness)
            Box(Modifier.size(28.dp).clip(CircleShape).background(current).border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape))
            Text(
                "#" + Integer.toHexString(current.toArgb()).takeLast(6).uppercase(),
                style = LiquidType.footnote,
                color = Liquid.colors.contentSecondary,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
    }
}
