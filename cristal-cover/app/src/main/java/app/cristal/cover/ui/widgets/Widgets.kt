package app.cristal.cover.ui.widgets

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.cristal.cover.data.battery.BatteryState
import app.cristal.cover.data.calendar.AgendaEvent
import app.cristal.cover.data.calendar.AgendaState
import app.cristal.cover.data.home.WidgetKind
import app.cristal.cover.data.media.MediaState
import app.cristal.cover.data.prefs.TempUnit
import app.cristal.cover.data.weather.WeatherData
import app.cristal.cover.data.weather.WeatherState
import app.cristal.cover.data.weather.WeatherStatus
import app.cristal.cover.ui.glass.GlassSurface
import app.cristal.cover.ui.glass.LocalGlassSpec
import app.cristal.cover.ui.glass.rememberHaptic
import app.cristal.cover.ui.theme.ClockTextStyle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** Tudo o que os widgets precisam para se desenhar. */
@Immutable
data class WidgetData(
    val battery: BatteryState = BatteryState(),
    val weather: WeatherState = WeatherState(),
    val media: MediaState? = null,
    val agenda: AgendaState = AgendaState(),
    val tempUnit: TempUnit = TempUnit.CELSIUS,
    val notificationAccess: Boolean = false,
)

/** Ações disparadas pelos widgets; a tela que os hospeda decide o que fazer. */
@Stable
class WidgetActions(
    val onClockClick: () -> Unit = {},
    val onWeatherClick: () -> Unit = {},
    val onBatteryClick: () -> Unit = {},
    val onAgendaClick: () -> Unit = {},
    val onRequestCalendar: () -> Unit = {},
    val onRequestNotificationAccess: () -> Unit = {},
    val onMediaOpen: () -> Unit = {},
    val onMediaPlayPause: () -> Unit = {},
    val onMediaNext: () -> Unit = {},
    val onMediaPrevious: () -> Unit = {},
)

/** Locale atual, lido de forma que a tela se atualize se o idioma do sistema mudar. */
@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/** Hora atual em milissegundos, atualizada na virada de cada minuto enquanto a tela está visível. */
@Composable
fun rememberMinuteTicker(): State<Long> {
    val state = remember { mutableLongStateOf(System.currentTimeMillis()) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val now = System.currentTimeMillis()
                state.longValue = now
                delay(60_000L - now % 60_000L + 25L)
            }
        }
    }
    return state
}

fun formatTime(millis: Long, is24h: Boolean): String {
    val time = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm", Locale.getDefault()).format(time)
}

fun formatDate(millis: Long): String {
    val locale = Locale.getDefault()
    val pattern = DateFormat.getBestDateTimePattern(locale, "EEEdMMMM")
    val time = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return DateTimeFormatter.ofPattern(pattern, locale).format(time)
}

/** Desenha o widget do tipo pedido dentro de um card de vidro que ocupa todo o espaço dado. */
@Composable
fun WidgetCard(
    kind: WidgetKind,
    data: WidgetData,
    actions: WidgetActions,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 26.dp,
    onLongClick: (() -> Unit)? = null,
    gestureModifier: Modifier = Modifier,
) {
    val onClick: () -> Unit = when (kind) {
        WidgetKind.CLOCK -> actions.onClockClick
        WidgetKind.WEATHER -> actions.onWeatherClick
        WidgetKind.MUSIC -> actions.onMediaOpen
        WidgetKind.BATTERY -> actions.onBatteryClick
        WidgetKind.AGENDA -> if (data.agenda.hasPermission) actions.onAgendaClick else actions.onRequestCalendar
    }
    GlassSurface(
        modifier = modifier,
        cornerRadius = cornerRadius,
        onClick = onClick,
        onLongClick = onLongClick,
        innerModifier = gestureModifier,
        clipContent = true,
    ) {
        when (kind) {
            WidgetKind.CLOCK -> ClockContent(data)
            WidgetKind.WEATHER -> WeatherContent(data)
            WidgetKind.MUSIC -> MusicContent(data, actions)
            WidgetKind.BATTERY -> BatteryContent(data.battery)
            WidgetKind.AGENDA -> AgendaContent(data.agenda)
        }
    }
}

// --- Relógio --------------------------------------------------------------------------------------

@Composable
private fun ClockContent(data: WidgetData) {
    val now = rememberMinuteTicker().value
    val is24h = DateFormat.is24HourFormat(LocalContext.current)
    val spec = LocalGlassSpec.current
    val locale = currentLocale()
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp)) {
        val compact = maxWidth < 220.dp
        val timeSize = (maxHeight.value * if (compact) 0.42f else 0.56f).coerceIn(26f, 92f)
        if (compact) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatTime(now, is24h), style = ClockTextStyle, fontSize = timeSize.sp, maxLines = 1)
                Text(
                    formatDate(now),
                    fontSize = 12.sp,
                    color = spec.secondaryContentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Text(formatTime(now, is24h), style = ClockTextStyle, fontSize = timeSize.sp, maxLines = 1)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        formatDate(now).replaceFirstChar { it.titlecase(locale) },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    val weather = data.weather.data
                    val extra = buildString {
                        append("${data.battery.percent}%")
                        if (data.battery.charging) append(" ⚡")
                        if (weather != null) append("  ·  ${formatTemp(weather.tempC, data.tempUnit)}")
                    }
                    Text(extra, fontSize = 13.sp, color = spec.secondaryContentColor, maxLines = 1)
                }
            }
        }
    }
}

// --- Clima ----------------------------------------------------------------------------------------

fun formatTemp(celsius: Double, unit: TempUnit): String {
    val value = if (unit == TempUnit.FAHRENHEIT) celsius * 9.0 / 5.0 + 32.0 else celsius
    return "${value.roundToInt()}°"
}

/** Descrição em português para os códigos de tempo da OMM usados pelo Open-Meteo. */
fun weatherLabel(code: Int): String = when (code) {
    0 -> "Céu limpo"
    1 -> "Poucas nuvens"
    2 -> "Parcialmente nublado"
    3 -> "Nublado"
    45, 48 -> "Neblina"
    51, 53, 55, 56, 57 -> "Garoa"
    61, 63 -> "Chuva"
    65 -> "Chuva forte"
    66, 67 -> "Chuva congelante"
    71, 73, 75, 77 -> "Neve"
    80, 81 -> "Pancadas de chuva"
    82 -> "Pancadas fortes"
    85, 86 -> "Pancadas de neve"
    95 -> "Trovoadas"
    96, 99 -> "Trovoadas com granizo"
    else -> "Tempo instável"
}

fun weatherIcon(code: Int, isDay: Boolean): ImageVector = when (code) {
    0, 1 -> if (isDay) Icons.Rounded.WbSunny else Icons.Rounded.NightsStay
    2 -> Icons.Rounded.WbCloudy
    3 -> Icons.Rounded.Cloud
    45, 48 -> Icons.Rounded.BlurOn
    51, 53, 55, 56, 57 -> Icons.Rounded.Grain
    61, 63, 65, 66, 67, 80, 81, 82 -> Icons.Rounded.WaterDrop
    71, 73, 75, 77, 85, 86 -> Icons.Rounded.AcUnit
    95, 96, 99 -> Icons.Rounded.Thunderstorm
    else -> Icons.Rounded.Cloud
}

@Composable
private fun WeatherContent(data: WidgetData) {
    val spec = LocalGlassSpec.current
    val weather = data.weather.data
    Box(Modifier.fillMaxSize().padding(14.dp)) {
        if (weather == null) {
            val message = when (data.weather.status) {
                WeatherStatus.LOADING -> "Carregando clima…"
                WeatherStatus.ERROR -> "Sem conexão. Toque para tentar de novo."
                else -> "Toque para escolher a cidade"
            }
            EmptyWidget(Icons.Rounded.WbCloudy, message)
            return@Box
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > 250.dp
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            weatherIcon(weather.code, weather.isDay),
                            contentDescription = null,
                            modifier = Modifier.size(30.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            formatTemp(weather.tempC, data.tempUnit),
                            style = ClockTextStyle,
                            fontSize = 40.sp,
                            maxLines = 1,
                        )
                    }
                    Text(
                        weatherLabel(weather.code),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val today = weather.days.firstOrNull()
                    val range = if (today != null) {
                        "↑${formatTemp(today.maxC, data.tempUnit)} ↓${formatTemp(today.minC, data.tempUnit)}"
                    } else {
                        "Sensação ${formatTemp(weather.feelsLikeC, data.tempUnit)}"
                    }
                    Text(range, fontSize = 12.sp, color = spec.secondaryContentColor, maxLines = 1)
                    Text(
                        weather.place.ifBlank { "Local atual" },
                        fontSize = 11.sp,
                        color = spec.secondaryContentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (day in weather.days.drop(1).take(3)) ForecastDay(day.date, day.code, day.maxC, day.minC, data.tempUnit)
                    }
                }
            }
        }
    }
}

@Composable
private fun ForecastDay(date: String, code: Int, maxC: Double, minC: Double, unit: TempUnit) {
    val spec = LocalGlassSpec.current
    val locale = currentLocale()
    val label = remember(date, locale) {
        runCatching { LocalDate.parse(date).dayOfWeek.getDisplayName(JavaTextStyle.SHORT, locale).take(3) }.getOrDefault("")
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = spec.secondaryContentColor, maxLines = 1)
        Icon(weatherIcon(code, true), contentDescription = null, modifier = Modifier.padding(vertical = 4.dp).size(20.dp))
        Text(formatTemp(maxC, unit), fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Text(formatTemp(minC, unit), fontSize = 11.sp, color = spec.secondaryContentColor, maxLines = 1)
    }
}

// --- Música ---------------------------------------------------------------------------------------

@Composable
private fun MusicContent(data: WidgetData, actions: WidgetActions) {
    val spec = LocalGlassSpec.current
    val media = data.media
    Box(Modifier.fillMaxSize().padding(12.dp)) {
        if (media == null) {
            if (data.notificationAccess) {
                EmptyWidget(Icons.Rounded.MusicNote, "Nada tocando agora")
            } else {
                EmptyWidget(
                    Icons.Rounded.MusicNote,
                    "Ative o acesso às notificações para controlar a música",
                    onClick = actions.onRequestNotificationAccess,
                )
            }
            return@Box
        }
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                    .clip(RoundedCornerShape(18.dp))
                    .background(spec.contentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                val art = media.art
                if (art != null) {
                    Image(bitmap = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    media.title.ifBlank { "Reproduzindo" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (media.artist.isNotBlank()) {
                    Text(
                        media.artist,
                        fontSize = 12.sp,
                        color = spec.secondaryContentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MediaButton(Icons.Rounded.SkipPrevious, "Anterior", media.canSkipPrevious, 36.dp, actions.onMediaPrevious)
                    MediaButton(
                        if (media.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (media.isPlaying) "Pausar" else "Tocar",
                        true,
                        44.dp,
                        actions.onMediaPlayPause,
                    )
                    MediaButton(Icons.Rounded.SkipNext, "Próxima", media.canSkipNext, 36.dp, actions.onMediaNext)
                }
            }
        }
    }
}

@Composable
fun MediaButton(icon: ImageVector, description: String, enabled: Boolean, size: Dp, onClick: () -> Unit) {
    val haptic = rememberHaptic()
    val color = LocalContentColor.current
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = if (enabled) 0.14f else 0.05f))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptic()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = color.copy(alpha = if (enabled) 1f else 0.35f),
            modifier = Modifier.size(size * 0.56f),
        )
    }
}

// --- Bateria --------------------------------------------------------------------------------------

@Composable
private fun BatteryContent(battery: BatteryState) {
    val spec = LocalGlassSpec.current
    val ringColor = when {
        battery.charging || battery.full -> Color(0xFF34C759)
        battery.percent <= 15 -> Color(0xFFFF453A)
        battery.powerSave -> Color(0xFFFFD60A)
        else -> spec.contentColor
    }
    val track = spec.contentColor.copy(alpha = 0.16f)
    val status = when {
        battery.full -> "Carregada"
        battery.charging -> "Carregando"
        battery.powerSave -> "Economia"
        else -> "Bateria"
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(12.dp)) {
        if (maxWidth < 240.dp) {
            // Célula estreita (2×2): anel com a porcentagem no centro e o estado embaixo.
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                    BatteryRing(battery.percent, ringColor, track)
                    Text("${battery.percent}%", style = ClockTextStyle, fontSize = 22.sp, maxLines = 1)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    if (battery.charging) {
                        Icon(Icons.Rounded.Bolt, contentDescription = null, tint = ringColor, modifier = Modifier.size(14.dp))
                    }
                    Text(status, fontSize = 12.sp, color = spec.secondaryContentColor, maxLines = 1)
                }
            }
        } else {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true), contentAlignment = Alignment.Center) {
                    BatteryRing(battery.percent, ringColor, track)
                    if (battery.charging) {
                        Icon(Icons.Rounded.Bolt, contentDescription = null, tint = ringColor, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("${battery.percent}%", style = ClockTextStyle, fontSize = 28.sp, maxLines = 1)
                    Text(status, fontSize = 12.sp, color = spec.secondaryContentColor, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun BatteryRing(percent: Int, color: Color, track: Color) {
    Canvas(Modifier.fillMaxSize().padding(3.dp)) {
        val stroke = size.minDimension * 0.11f
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(track, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        drawArc(color, -90f, 360f * percent / 100f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

// --- Agenda ---------------------------------------------------------------------------------------

@Composable
private fun AgendaContent(agenda: AgendaState) {
    val spec = LocalGlassSpec.current
    Box(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
        when {
            !agenda.hasPermission -> EmptyWidget(Icons.Rounded.Event, "Toque para permitir o acesso à agenda")
            agenda.events.isEmpty() -> EmptyWidget(Icons.Rounded.Event, "Nenhum compromisso nos próximos 7 dias")
            else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                val rows = ((maxHeight.value - 4f) / 34f).toInt().coerceIn(1, 4)
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    for (event in agenda.events.take(rows)) {
                        Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .width(4.dp)
                                    .height(22.dp)
                                    .clip(CircleShape)
                                    .background(if (event.color != 0) Color(event.color).copy(alpha = 1f) else spec.contentColor),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    event.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    eventWhen(event),
                                    fontSize = 11.sp,
                                    color = spec.secondaryContentColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "Hoje 14:00", "Amanhã, dia todo", "seg. 09:30". */
fun eventWhen(event: AgendaEvent, nowMillis: Long = System.currentTimeMillis()): String {
    val zone = ZoneId.systemDefault()
    // Eventos de dia inteiro são gravados em UTC; lê-los no fuso local mudaria o dia.
    val start: ZonedDateTime = Instant.ofEpochMilli(event.begin).atZone(if (event.allDay) ZoneId.of("UTC") else zone)
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val day = when (start.toLocalDate()) {
        today -> "Hoje"
        today.plusDays(1) -> "Amanhã"
        else -> start.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.getDefault())
    }
    val time = if (event.allDay) "dia todo" else DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()).format(start)
    val place = if (event.location.isBlank()) "" else " · ${event.location}"
    return if (event.allDay) "$day, $time$place" else "$day $time$place"
}

// --- Comum ----------------------------------------------------------------------------------------

@Composable
private fun EmptyWidget(icon: ImageVector, message: String, onClick: (() -> Unit)? = null) {
    val spec = LocalGlassSpec.current
    Row(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = spec.secondaryContentColor, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Text(message, fontSize = 13.sp, color = spec.secondaryContentColor, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}
