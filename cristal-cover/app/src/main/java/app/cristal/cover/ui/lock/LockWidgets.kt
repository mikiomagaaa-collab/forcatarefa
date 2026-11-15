package app.cristal.cover.ui.lock

import android.app.AlarmManager
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cristal.cover.core.safely
import app.cristal.cover.data.prefs.LockWidget
import app.cristal.cover.ui.components.WallpaperTextShadow
import app.cristal.cover.ui.glass.GlassSurface
import app.cristal.cover.ui.glass.LocalGlassSpec
import app.cristal.cover.ui.widgets.formatTemp
import app.cristal.cover.ui.widgets.formatTime
import app.cristal.cover.ui.widgets.rememberMinuteTicker
import app.cristal.cover.ui.widgets.weatherIcon
import app.cristal.cover.ui.widgets.weatherLabel

/** Altura da fileira de widgets do bloqueio (com o espaço até o próximo bloco). */
val LockWidgetRowHeight: Dp = 60.dp

/** Fileira de widgets pequenos de vidro (bateria, clima, agenda, alarme), como no bloqueio do iPhone. */
@Composable
fun LockWidgetsRow(widgets: List<LockWidget>, state: LockUiState, modifier: Modifier = Modifier) {
    if (widgets.isEmpty()) return
    val context = LocalContext.current
    val now = rememberMinuteTicker().value
    val is24h = DateFormat.is24HourFormat(context)
    Row(modifier.fillMaxWidth().height(LockWidgetRowHeight), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (w in widgets.take(4)) {
            val (icon, value, label) = when (w) {
                LockWidget.BATTERY -> Triple(
                    if (state.battery.charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
                    "${state.battery.percent}%",
                    if (state.battery.charging) "Carregando" else "Bateria",
                )
                LockWidget.WEATHER -> {
                    val data = state.weather.data
                    if (data == null) {
                        Triple(weatherIcon(0, true), "--", "Clima")
                    } else {
                        Triple(weatherIcon(data.code, data.isDay), formatTemp(data.tempC, state.settings.tempUnit), weatherLabel(data.code))
                    }
                }
                LockWidget.AGENDA -> {
                    val next = state.agenda.events.firstOrNull { it.end > now }
                    when {
                        !state.agenda.hasPermission -> Triple(Icons.Rounded.Event, "Agenda", "Permita no Cristal")
                        next == null -> Triple(Icons.Rounded.Event, "Livre", "Sem compromissos")
                        else -> Triple(Icons.Rounded.Event, if (next.allDay) "Dia todo" else formatTime(next.begin, is24h), next.title)
                    }
                }
                LockWidget.ALARM -> {
                    val next = remember(now) {
                        safely("ler próximo alarme") { context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime }
                    }
                    Triple(Icons.Rounded.Alarm, next?.let { formatTime(it, is24h) } ?: "--", if (next == null) "Sem alarme" else "Alarme")
                }
            }
            LockWidgetTile(icon, value, label, Modifier.weight(1f))
        }
    }
}

@Composable
private fun LockWidgetTile(icon: ImageVector, value: String, label: String, modifier: Modifier) {
    val spec = LocalGlassSpec.current
    GlassSurface(modifier = modifier.fillMaxSize(), cornerRadius = 22.dp, elevation = 8.dp, reactToTouch = false) {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(label, fontSize = 10.sp, color = spec.secondaryContentColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Moldura de edição do relógio na pré-visualização: contorno e uma alça que, arrastada para baixo
 * ou para cima, aumenta ou diminui o relógio (como no iOS 26).
 *
 * @param baseHeight altura do relógio com tamanho 100%, para converter o arrasto em escala.
 */
@Composable
fun BoxScope.ClockResizeHandle(baseHeight: Dp, onDrag: (Float) -> Unit, onEnd: () -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val basePx = with(density) { baseHeight.toPx() }.coerceAtLeast(1f)
    Box(
        Modifier
            .matchParentSize()
            .border(1.5.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(26.dp)),
    )
    Column(
        Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(width = 64.dp, height = 30.dp)
                .pointerInput(basePx) {
                    detectVerticalDragGestures(onDragEnd = onEnd, onDragCancel = onEnd) { change, amount ->
                        change.consume()
                        onDrag(amount / basePx)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(width = 44.dp, height = 6.dp).clip(CircleShape).background(Color.White))
        }
        Text(
            "Arraste para mudar o tamanho",
            color = Color.White,
            style = TextStyle(fontSize = 11.sp, shadow = WallpaperTextShadow),
        )
    }
}
