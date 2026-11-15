package app.cristal.cover.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cristal.cover.data.prefs.ClockFont
import app.cristal.cover.data.prefs.ClockLayout
import app.cristal.cover.ui.components.AdaptiveClock

/** Galeria de fontes do relógio: cada opção mostra a hora desenhada na própria fonte. */
@Composable
fun ClockFontPicker(selected: ClockFont, layout: ClockLayout, onSelect: (ClockFont) -> Unit) {
    val ink = LocalContentColor.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (font in ClockFont.entries) {
            val on = font == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(font) },
            ) {
                Box(
                    Modifier
                        .size(width = 92.dp, height = 74.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF2A1F49), Color(0xFF0F0C1C))))
                        .border(if (on) 2.dp else 0.75.dp, if (on) Color.White else ink.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                        .padding(6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AdaptiveClock(
                        time = "10:42",
                        font = font,
                        layout = layout,
                        adaptive = true,
                        color = Color.White,
                        shadow = null,
                        modifier = Modifier.fillMaxWidth().size(width = 80.dp, height = 60.dp),
                    )
                }
                Text(font.label, fontSize = 12.sp, color = ink.copy(alpha = if (on) 1f else 0.75f), modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
