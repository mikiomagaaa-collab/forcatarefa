package app.cristal.cover.ui.lock

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import app.cristal.cover.data.prefs.ClockFill
import app.cristal.cover.ui.design.Liquid
import app.cristal.cover.ui.design.LiquidChip
import app.cristal.cover.ui.design.LiquidColorPicker
import app.cristal.cover.ui.design.LiquidListGroup
import app.cristal.cover.ui.design.LiquidListRow
import app.cristal.cover.ui.design.LiquidSlider
import app.cristal.cover.ui.design.LiquidToggle
import app.cristal.cover.ui.design.LiquidType

/**
 * O relógio colorido no editor do bloqueio: o preenchimento (Original continua igual), a
 * intensidade, a segunda cor, horas e minutos separados e a harmonia com a data. A prévia acima
 * mostra cada mudança na hora.
 */
@Composable
internal fun ClockFillEditor(current: ClockDraft, update: (ClockDraft) -> Unit) {
    val colors = Liquid.colors
    var picker by remember { mutableStateOf(false) }
    val colored = current.fill != ClockFill.ORIGINAL
    LiquidListGroup {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClockFill.entries.forEach { fill ->
                    LiquidChip(fill.label, current.fill == fill, {
                        // Ao sair do original sem cor escolhida, começa no violeta do Liquid Flip.
                        val color = if (fill != ClockFill.ORIGINAL && current.color == 0) ClockColors.Default.toArgb() else current.color
                        update(current.copy(fill = fill, color = color))
                    })
                }
            }
            Text(current.fill.detail, style = LiquidType.footnote, color = colors.contentSecondary, modifier = Modifier.padding(top = 8.dp))
            AnimatedVisibility(colored) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    LiquidSlider(
                        value = current.fillOpacity,
                        onValueChange = { update(current.copy(fillOpacity = it)) },
                        valueRange = 0.2f..1f,
                        label = if (current.fill == ClockFill.TRANSLUCENT) "Transparência do miolo" else "Intensidade da cor",
                    )
                    val needsSecond = current.fill == ClockFill.GRADIENT || current.split
                    AnimatedVisibility(needsSecond) {
                        Column(Modifier.padding(top = 12.dp)) {
                            Text(
                                if (current.color2 == 0) "Segunda cor: automática (combina com a primeira)" else "Segunda cor",
                                style = LiquidType.subhead,
                                color = colors.content,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            ColorRow(
                                selected = current.color2,
                                onSelect = { update(current.copy(color2 = it)) },
                                onSpectrum = { picker = !picker },
                                spectrumOpen = picker,
                            )
                            AnimatedVisibility(picker) {
                                LiquidColorPicker(
                                    initial = if (current.color2 == 0) ClockColors.derived(ClockColors.first(current.color)) else Color(current.color2),
                                    onColorChange = { update(current.copy(color2 = it.toArgb())) },
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        if (colored) {
            LiquidListRow("Horas e minutos com cores próprias") {
                LiquidToggle(current.split, { update(current.copy(split = it)) })
            }
            LiquidListRow("Data na cor do relógio", divider = false) {
                LiquidToggle(current.harmony, { update(current.copy(harmony = it)) })
            }
        }
    }
}
