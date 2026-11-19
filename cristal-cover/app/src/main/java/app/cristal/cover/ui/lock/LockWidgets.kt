package app.cristal.cover.ui.lock

import android.app.AlarmManager
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import app.cristal.cover.ui.widgets.formatTemp
import app.cristal.cover.ui.widgets.formatTime
import app.cristal.cover.ui.widgets.rememberMinuteTicker
import app.cristal.cover.ui.widgets.weatherIcon
import java.time.Instant
import java.time.ZoneId

/** Altura da fileira de widgets do bloqueio. */
val LockWidgetRowHeight: Dp = 64.dp

/** Fileira de widgets redondos de vidro (bateria, clima, agenda, alarme), como no bloqueio do iPhone. */
@Composable
fun LockWidgetsRow(widgets: List<LockWidget>, state: LockUiState, modifier: Modifier = Modifier) {
    if (widgets.isEmpty()) return
    Row(
        modifier.fillMaxWidth().height(LockWidgetRowHeight),
        horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (w in widgets.take(4)) LockWidgetCircle(w, state, Modifier.size(LockWidgetRowHeight))
    }
}

@Composable
private fun LockWidgetCircle(widget: LockWidget, state: LockUiState, modifier: Modifier) {
    val context = LocalContext.current
    val now = rememberMinuteTicker().value
    val is24h = DateFormat.is24HourFormat(context)
    val ink = Color.White
    GlassSurface(modifier = modifier, cornerRadius = LockWidgetRowHeight / 2, elevation = 6.dp, reactToTouch = false, contentAlignment = Alignment.Center) {
        when (widget) {
            LockWidget.BATTERY -> {
                val fraction = state.battery.percent / 100f
                Gauge(fraction, if (state.battery.charging) Color(0xFF34C759) else ink)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (state.battery.charging) Icon(Icons.Rounded.Bolt, contentDescription = null, tint = ink, modifier = Modifier.size(12.dp))
                    Text("${state.battery.percent}", color = ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
            LockWidget.WEATHER -> {
                val data = state.weather.data
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(weatherIcon(data?.code ?: 0, data?.isDay ?: true), contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
                    Text(data?.let { formatTemp(it.tempC, state.settings.tempUnit) } ?: "--", color = ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            LockWidget.AGENDA -> {
                val next = state.agenda.events.firstOrNull { it.end > now }
                val day = remember(now / 3_600_000L) { Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).dayOfMonth }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Event, contentDescription = null, tint = ink, modifier = Modifier.size(14.dp))
                    Text(
                        if (next == null || next.allDay) "$day" else formatTime(next.begin, is24h),
                        color = ink,
                        fontSize = if (next == null || next.allDay) 18.sp else 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
            LockWidget.ALARM -> {
                val next = remember(now) {
                    safely("ler próximo alarme") { context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Alarm, contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
                    Text(next?.let { formatTime(it, is24h) } ?: "--", color = ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Clip)
                }
            }
        }
    }
}

/** Anel de progresso dos widgets redondos (trilho apagado + arco aceso). */
@Composable
private fun Gauge(fraction: Float, color: Color) {
    Canvas(Modifier.fillMaxSize().padding(5.dp)) {
        val stroke = 4.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(Color.White.copy(alpha = 0.22f), 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(color, 135f, 270f * fraction.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

/**
 * Moldura de edição do relógio na pré-visualização (como no iOS 26): contorno fino e uma alça no
 * canto de baixo. Arrastar a alça para baixo estica o relógio; para cima, encolhe.
 *
 * @param baseHeight altura do relógio com tamanho 100%, para converter o arrasto em escala.
 */
@Composable
fun BoxScope.ClockResizeHandle(baseHeight: Dp, onDrag: (Float) -> Unit, onEnd: () -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val basePx = with(density) { baseHeight.toPx() }.coerceAtLeast(1f)
    Box(modifier.matchParentSize()) {
        Box(
            Modifier
                .matchParentSize()
                .border(1.2.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(22.dp)),
        )
        // Alça: um arco no canto inferior direito, com uma área de toque generosa.
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 10.dp, y = 10.dp)
                .size(48.dp)
                .semantics { contentDescription = "Esticar o relógio" }
                .pointerInput(basePx) {
                    detectDragGestures(onDragEnd = onEnd, onDragCancel = onEnd) { change, amount ->
                        change.consume()
                        onDrag(amount.y / basePx)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(22.dp)) {
                val stroke = 4.dp.toPx()
                drawArc(
                    Color.White,
                    startAngle = 0f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(-size.width + stroke, -size.height + stroke),
                    size = Size(size.width * 2 - stroke * 2, size.height * 2 - stroke * 2),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            "Arraste o canto para esticar",
            color = Color.White,
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, shadow = WallpaperTextShadow),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 22.dp)
                .background(Color.Black.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}
