package app.cristal.cover.ui.widgets

import android.text.format.DateFormat
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.cristal.cover.data.battery.BatteryState
import app.cristal.cover.data.calendar.AgendaEvent
import app.cristal.cover.data.home.PhotoWidgetStore
import app.cristal.cover.data.home.WidgetKind
import app.cristal.cover.data.weather.WeatherStatus
import app.cristal.cover.ui.lily.LilyGlyph
import app.cristal.cover.ui.theme.UiFont
import app.cristal.cover.ui.theme.clockStyle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------------------------
// Peças comuns: a mesma tipografia (Inter, escala do iPhone), espaçamentos e componentes em todos
// os widgets, para eles parecerem da mesma família.
// ---------------------------------------------------------------------------------------------

private val pal: WidgetPalette @Composable get() = LocalWidgetPalette.current

private val density: Float @Composable get() = LocalWidgetStyle.current.density.scale

/** Na tela externa (menor, vista de mais longe) os textos crescem um pouco. */
private val textScale: Float @Composable get() = if (LocalWidgetScreen.current == WidgetScreen.COVER) 1.06f else 1f

private fun type(size: Float, weight: FontWeight, tracking: Float = 0f) =
    TextStyle(fontFamily = UiFont, fontSize = size.sp, fontWeight = weight, letterSpacing = tracking.em, fontFeatureSettings = "tnum")

/** Margem interna de um widget, conforme o tamanho e a densidade escolhida. */
@Composable
private fun pad(size: WidgetSize): Dp = (if (size == WidgetSize.STRIP) 14.dp else 16.dp) * density

/** Rótulo de topo (como "CLIMA" no iOS): pequeno, forte e na cor de destaque ou secundária. */
@Composable
private fun Header(text: String, color: Color = pal.secondary, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = type(12.5f * textScale, FontWeight.SemiBold, 0.01f), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Hero(text: String, size: TextUnit, weight: FontWeight = FontWeight.Light, color: Color = pal.primary, modifier: Modifier = Modifier) {
    Text(text, style = type(size.value, weight, -0.03f).copy(lineHeight = size * 1.02f), color = color, maxLines = 1, softWrap = false, modifier = modifier)
}

@Composable
private fun Line(
    text: String,
    color: Color = pal.primary,
    size: Float = 14f,
    weight: FontWeight = FontWeight.Medium,
    lines: Int = 1,
    modifier: Modifier = Modifier,
) {
    val k = textScale
    Text(text, style = type(size * k, weight).copy(lineHeight = (size * k * 1.28f).sp), color = color, maxLines = lines, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

@Composable
private fun Ring(progress: Float, size: Dp, stroke: Dp, color: Color, track: Color, content: @Composable () -> Unit = {}) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val inset = w / 2
            val arc = Size(this.size.width - w, this.size.height - w)
            drawArc(track, -90f, 360f, false, Offset(inset, inset), arc, style = Stroke(w))
            drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

@Composable
private fun Bar(progress: Float, color: Color, height: Dp = 6.dp, modifier: Modifier = Modifier) {
    val track = pal.track
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = this.size.height / 2
        drawRoundRect(track, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
        val w = this.size.width * progress.coerceIn(0f, 1f)
        if (w > 0f) drawRoundRect(color, size = Size(w.coerceAtLeast(this.size.height), this.size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
    }
}

/** Botão redondo de ação (controles de música, atalhos). */
@Composable
private fun RoundButton(icon: ImageVector, description: String, size: Dp, filled: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    val p = pal
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) p.accent else p.track)
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = (if (filled) p.onAccent else p.primary).copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(size * 0.5f))
    }
}

/** Sugestão em cápsula (L.I.L.Y). */
@Composable
private fun Chip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = pal
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(p.track)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(text, style = type(13f, FontWeight.Medium), color = p.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Divider(vertical: Boolean = false) {
    val c = pal.divider
    if (vertical) Box(Modifier.fillMaxHeight().width(1.dp).background(c)) else Box(Modifier.fillMaxWidth().height(1.dp).background(c))
}

/** Estado vazio ou pedido de permissão, com o mesmo desenho em todos os widgets. */
@Composable
private fun Empty(icon: ImageVector, title: String, detail: String?, size: WidgetSize) {
    val p = pal
    if (size == WidgetSize.STRIP) {
        Row(Modifier.fillMaxSize().padding(horizontal = pad(size)), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = p.accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Line(title, size = 14f, weight = FontWeight.SemiBold)
                if (detail != null) Line(detail, p.secondary, 12f, FontWeight.Normal)
            }
        }
    } else {
        Column(Modifier.fillMaxSize().padding(pad(size)), verticalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = p.accent, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(8.dp))
            Line(title, size = 15f, weight = FontWeight.SemiBold, lines = 2)
            if (detail != null) Line(detail, p.secondary, 12.5f, FontWeight.Normal, lines = 2, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** Mede o espaço, decide o tamanho e entrega ao conteúdo. */
@Composable
private fun Sized(content: @Composable BoxWithConstraintsScope.(WidgetSize) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) { content(WidgetSize.of(maxWidth, maxHeight)) }
}

private fun weekday(millis: Long, locale: Locale, full: Boolean = true): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).dayOfWeek
        .getDisplayName(if (full) JavaTextStyle.FULL else JavaTextStyle.SHORT, locale)
        .replace("-feira", "").replaceFirstChar { it.titlecase(locale) }

private fun dayMonth(millis: Long, locale: Locale): String =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMMM"), locale)
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

private fun shortWeekday(date: String, locale: Locale): String = runCatching {
    LocalDate.parse(date).dayOfWeek.getDisplayName(JavaTextStyle.SHORT, locale).trimEnd('.').replaceFirstChar { it.titlecase(locale) }
}.getOrDefault(date)

private fun hourMinute(millis: Long, is24h: Boolean): String = formatTime(millis, is24h)

// ---------------------------------------------------------------------------------------------
// Relógio digital: editorial e minimalista.
// ---------------------------------------------------------------------------------------------

@Composable
internal fun ClockDesign(data: WidgetData) {
    val now = rememberMinuteTicker().value
    val is24h = DateFormat.is24HourFormat(LocalContext.current)
    val locale = currentLocale()
    val p = pal
    val time = formatTime(now, is24h)
    val clock = data.clockFont.clockStyle()
    Sized { size ->
        val maxW = maxWidth
        val maxH = maxHeight
        val padding = pad(size)
        when (size) {
            WidgetSize.STRIP -> Row(Modifier.fillMaxSize().padding(horizontal = padding), verticalAlignment = Alignment.CenterVertically) {
                Text(time, style = clock.copy(fontSize = (maxH.value * 0.5f).coerceIn(26f, 44f).sp), color = p.primary, maxLines = 1, softWrap = false)
                Spacer(Modifier.width(14.dp))
                Divider(vertical = true)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Line(weekday(now, locale), p.accent, 13f, FontWeight.SemiBold)
                    Line(dayMonth(now, locale), p.secondary, 13f)
                }
            }
            WidgetSize.SMALL -> Column(Modifier.fillMaxSize().padding(padding)) {
                Header(weekday(now, locale).uppercase(locale), p.accent)
                Spacer(Modifier.weight(1f))
                Text(time, style = clock.copy(fontSize = (maxW.value * 0.3f).coerceIn(34f, 60f).sp), color = p.primary, maxLines = 1, softWrap = false)
                Line(dayMonth(now, locale), p.secondary, 13f)
                Spacer(Modifier.weight(1f))
                ClockFooter(data)
            }
            else -> Row(Modifier.fillMaxSize().padding(padding)) {
                Column(Modifier.weight(1.25f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    Header(weekday(now, locale).uppercase(locale), p.accent)
                    Text(
                        time,
                        style = clock.copy(fontSize = (maxH.value * if (size == WidgetSize.MEDIUM) 0.42f else 0.24f).coerceIn(40f, 96f).sp),
                        color = p.primary,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Line(dayMonth(now, locale), p.secondary, 14f)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
                    val next = data.agenda.events.firstOrNull { it.end >= now }
                    if (next != null) {
                        EventRow(next, now, is24h, compact = true)
                        Divider()
                    }
                    ClockFooter(data)
                    if (size != WidgetSize.MEDIUM) {
                        data.agenda.events.filter { it.end >= now }.drop(1).take(3).forEach { EventRow(it, now, is24h, compact = true) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClockFooter(data: WidgetData) {
    val p = pal
    Row(verticalAlignment = Alignment.CenterVertically) {
        val weather = data.weather.data
        if (weather != null) {
            Icon(weatherIcon(weather.code, weather.isDay), null, tint = p.secondary, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(3.dp))
            Line(formatTemp(weather.tempC, data.tempUnit), p.secondary, 13f)
            Spacer(Modifier.width(10.dp))
        }
        Icon(if (data.battery.charging) Icons.Rounded.Bolt else Icons.Rounded.Smartphone, null, tint = p.secondary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(2.dp))
        Line("${data.battery.percent}%", p.secondary, 13f)
    }
}

// ---------------------------------------------------------------------------------------------
// Relógio de ponteiros: mostrador limpo, marcas de hora e ponteiro de destaque.
// ---------------------------------------------------------------------------------------------

@Composable
internal fun AnalogDesign() {
    val now = rememberMinuteTicker().value
    val p = pal
    Box(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxHeight().aspectRatio(1f)) {
            val r = size.minDimension / 2
            val c = center
            drawCircle(p.track.copy(alpha = p.track.alpha * 0.5f), r)
            for (i in 0 until 60) {
                val major = i % 5 == 0
                if (!major && r < 70.dp.toPx()) continue
                val a = i / 60f * 2 * PI
                val outer = r * 0.92f
                val inner = r * if (major) (if (i % 15 == 0) 0.74f else 0.8f) else 0.87f
                drawLine(
                    if (major) p.primary.copy(alpha = 0.85f) else p.tertiary,
                    Offset(c.x + (sin(a) * inner).toFloat(), c.y - (cos(a) * inner).toFloat()),
                    Offset(c.x + (sin(a) * outer).toFloat(), c.y - (cos(a) * outer).toFloat()),
                    strokeWidth = if (major) r * 0.035f else r * 0.015f,
                    cap = StrokeCap.Round,
                )
            }
            val time = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
            val minutes = time.minute
            val hours = time.hour % 12 + minutes / 60f
            rotate(hours * 30f) { drawLine(p.primary, c, Offset(c.x, c.y - r * 0.5f), r * 0.07f, StrokeCap.Round) }
            rotate(minutes * 6f) { drawLine(p.primary, c, Offset(c.x, c.y - r * 0.76f), r * 0.045f, StrokeCap.Round) }
            drawCircle(p.accent, r * 0.07f)
            drawCircle(p.primary, r * 0.025f)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Data e agenda.
// ---------------------------------------------------------------------------------------------

@Composable
internal fun DateDesign(data: WidgetData) {
    val now = rememberMinuteTicker().value
    val locale = currentLocale()
    val is24h = DateFormat.is24HourFormat(LocalContext.current)
    val p = pal
    val zoned = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
    Sized { size ->
        val maxW = maxWidth
        val maxH = maxHeight
        if (size == WidgetSize.SMALL || size == WidgetSize.STRIP) {
            Column(Modifier.fillMaxSize().padding(pad(size))) {
                Header(weekday(now, locale).uppercase(locale), p.status(Color(0xFFFF453A)))
                Hero(zoned.dayOfMonth.toString(), (maxH.value * 0.42f).coerceIn(34f, 72f).sp, FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                val next = data.agenda.events.firstOrNull { it.end >= now }
                if (next != null) {
                    Line(next.title, p.primary, 12.5f, FontWeight.SemiBold)
                    Line(eventWhen(next, now), p.secondary, 12f)
                } else {
                    Line(zoned.month.getDisplayName(JavaTextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }, p.secondary, 13f)
                }
            }
        } else {
            AgendaDesign(data, size, now, locale, is24h)
        }
    }
}

@Composable
internal fun AgendaDesign(data: WidgetData) {
    val now = rememberMinuteTicker().value
    val locale = currentLocale()
    val is24h = DateFormat.is24HourFormat(LocalContext.current)
    Sized { size -> AgendaDesign(data, size, now, locale, is24h) }
}

@Composable
private fun BoxWithConstraintsScope.AgendaDesign(data: WidgetData, size: WidgetSize, now: Long, locale: Locale, is24h: Boolean) {
    val p = pal
    val agenda = data.agenda
    if (!agenda.hasPermission) return Empty(Icons.Rounded.Event, "Mostrar a agenda", "Toque para permitir a leitura do calendário", size)
    val upcoming = agenda.events.filter { it.end >= now }
    val zoned = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
    val red = p.status(Color(0xFFFF453A))
    when (size) {
        WidgetSize.STRIP -> Row(Modifier.fillMaxSize().padding(horizontal = pad(size)), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Line(weekday(now, locale, full = false).uppercase(locale), red, 11f, FontWeight.Bold)
                Line(zoned.dayOfMonth.toString(), p.primary, 22f, FontWeight.SemiBold)
            }
            Spacer(Modifier.width(14.dp))
            val next = upcoming.firstOrNull()
            if (next == null) Line("Nada na agenda hoje", p.secondary, 14f) else EventRow(next, now, is24h, compact = true, modifier = Modifier.weight(1f))
        }
        WidgetSize.SMALL -> Column(Modifier.fillMaxSize().padding(pad(size))) {
            Header(weekday(now, locale).uppercase(locale), red)
            Line(zoned.dayOfMonth.toString(), p.primary, 30f, FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            val next = upcoming.firstOrNull()
            if (next == null) {
                Line("Nada na agenda", p.secondary, 13f, lines = 2)
            } else {
                EventRow(next, now, is24h, compact = false)
            }
        }
        else -> if (maxWidth < 300.dp) {
            // Alto e estreito (2×4): a data numa linha no alto e a lista embaixo, com mais espaço por item.
            Column(Modifier.fillMaxSize().padding(pad(size))) {
                Header(weekday(now, locale).uppercase(locale), red)
                Row(verticalAlignment = Alignment.Bottom) {
                    Hero(zoned.dayOfMonth.toString(), 40.sp, FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    Line(zoned.month.getDisplayName(JavaTextStyle.SHORT, locale).trimEnd('.').replaceFirstChar { it.titlecase(locale) }, p.secondary, 14f, modifier = Modifier.padding(bottom = 8.dp))
                }
                Spacer(Modifier.height(8.dp))
                Divider()
                Spacer(Modifier.height(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (upcoming.isEmpty()) {
                        Line("Nada na agenda", p.primary, 15f, FontWeight.SemiBold)
                        Line("Aproveite o dia livre", p.secondary, 13f, lines = 2)
                    } else {
                        upcoming.take(if (size == WidgetSize.EXTRA_LARGE) 6 else 4).forEach { EventRow(it, now, is24h, compact = false) }
                    }
                }
            }
        } else Row(Modifier.fillMaxSize().padding(pad(size))) {
            Column(Modifier.width(76.dp)) {
                Header(weekday(now, locale).uppercase(locale), red)
                Hero(zoned.dayOfMonth.toString(), 44.sp, FontWeight.SemiBold)
                Line(zoned.month.getDisplayName(JavaTextStyle.SHORT, locale).trimEnd('.').replaceFirstChar { it.titlecase(locale) }, p.secondary, 13f)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(9.dp, if (upcoming.isEmpty()) Alignment.CenterVertically else Alignment.Top)) {
                if (upcoming.isEmpty()) {
                    Line("Nada na agenda", p.primary, 15f, FontWeight.SemiBold)
                    Line("Aproveite o dia livre", p.secondary, 13f)
                } else {
                    val max = when (size) {
                        WidgetSize.MEDIUM -> 3
                        WidgetSize.LARGE -> 6
                        else -> 9
                    }
                    upcoming.take(max).forEach { EventRow(it, now, is24h, compact = size == WidgetSize.MEDIUM) }
                }
            }
        }
    }
}

/** Um compromisso: barra na cor do calendário, título e quando. */
@Composable
private fun EventRow(event: AgendaEvent, now: Long, is24h: Boolean, compact: Boolean, modifier: Modifier = Modifier) {
    val p = pal
    val color = if (p.accent == p.primary) p.primary else Color(event.color or 0xFF000000.toInt())
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(if (compact) 30.dp else 36.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Line(event.title.ifBlank { "Compromisso" }, p.primary, 13.5f, FontWeight.SemiBold, lines = if (compact) 1 else 2)
            Line(if (event.allDay) "Dia todo" else eventWhen(event, now), p.secondary, 12f)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Clima: temperatura em destaque, condição e previsão bem distribuída.
// ---------------------------------------------------------------------------------------------

@Composable
internal fun WeatherDesign(data: WidgetData) {
    val p = pal
    val locale = currentLocale()
    val state = data.weather
    val w = state.data
    Sized { size ->
        val maxW = maxWidth
        val maxH = maxHeight
        if (w == null) {
            when (state.status) {
                WeatherStatus.NO_PLACE -> Empty(Icons.Rounded.LocationOn, "Escolha uma cidade", "Toque para ver o clima", size)
                WeatherStatus.LOADING -> Empty(weatherIcon(1, true), "Atualizando o clima…", null, size)
                else -> Empty(weatherIcon(3, true), "Clima indisponível", "Sem conexão agora; tenta de novo sozinho", size)
            }
            return@Sized
        }
        val today = w.days.firstOrNull()
        val hiLo = today?.let { "Máx ${formatTemp(it.maxC, data.tempUnit)}  Mín ${formatTemp(it.minC, data.tempUnit)}" }
        val icon = weatherIcon(w.code, w.isDay)
        val iconTint = if (p.accent == p.primary) p.primary else weatherTint(w.code, w.isDay)
        when (size) {
            WidgetSize.STRIP -> Row(Modifier.fillMaxSize().padding(horizontal = pad(size)), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Hero(formatTemp(w.tempC, data.tempUnit), 30.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Line(weatherLabel(w.code), p.primary, 14f, FontWeight.SemiBold)
                    Line(w.place, p.secondary, 12.5f)
                }
            }
            WidgetSize.SMALL -> Column(Modifier.fillMaxSize().padding(pad(size))) {
                Row(verticalAlignment = Alignment.Top) {
                    Header(w.place, p.primary, modifier = Modifier.weight(1f))
                    Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
                }
                Hero(formatTemp(w.tempC, data.tempUnit), (maxW.value * 0.3f).coerceIn(36f, 58f).sp)
                Spacer(Modifier.weight(1f))
                Line(weatherLabel(w.code), p.primary, 13.5f, FontWeight.SemiBold)
                if (hiLo != null) Line(hiLo, p.secondary, 12f)
            }
            else -> Column(Modifier.fillMaxSize().padding(pad(size))) {
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Header(w.place, p.primary)
                        Hero(formatTemp(w.tempC, data.tempUnit), 52.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Icon(icon, null, tint = iconTint, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(6.dp))
                        Line(weatherLabel(w.code), p.primary, 13.5f, FontWeight.SemiBold)
                        if (hiLo != null) Line(hiLo, p.secondary, 12f)
                        if (size == WidgetSize.MEDIUM) Line("Sensação ${formatTemp(w.feelsLikeC, data.tempUnit)} · ${w.humidity}%", p.tertiary, 11.5f)
                    }
                }
                if (size != WidgetSize.MEDIUM) {
                    // Grande: os detalhes de agora em três blocos (dados reais da previsão).
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WeatherDetail("Sensação", formatTemp(w.feelsLikeC, data.tempUnit), Modifier.weight(1f))
                        WeatherDetail("Umidade", "${w.humidity}%", Modifier.weight(1f))
                        WeatherDetail("Vento", "${w.windKmh.roundToInt()} km/h", Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.weight(1f))
                if (size == WidgetSize.MEDIUM) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        w.days.drop(1).take(5).forEach { d ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Line(shortWeekday(d.date, locale), p.secondary, 12f, FontWeight.SemiBold)
                                Icon(weatherIcon(d.code, true), null, tint = if (p.accent == p.primary) p.primary else weatherTint(d.code, true), modifier = Modifier.padding(vertical = 3.dp).size(18.dp))
                                Line(formatTemp(d.maxC, data.tempUnit), p.primary, 12.5f, FontWeight.SemiBold)
                            }
                        }
                    }
                } else {
                    Divider()
                    Spacer(Modifier.height(6.dp))
                    val days = w.days.drop(1).take(if (size == WidgetSize.LARGE) 5 else 7)
                    val lo = days.minOfOrNull { it.minC } ?: 0.0
                    val hi = days.maxOfOrNull { it.maxC } ?: 1.0
                    days.forEach { d ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Line(shortWeekday(d.date, locale), p.primary, 13.5f, FontWeight.SemiBold, modifier = Modifier.width(44.dp))
                            Icon(weatherIcon(d.code, true), null, tint = if (p.accent == p.primary) p.primary else weatherTint(d.code, true), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Line(formatTemp(d.minC, data.tempUnit), p.secondary, 12.5f, modifier = Modifier.width(36.dp))
                            TempRange(d.minC, d.maxC, lo, hi, Modifier.weight(1f))
                            Line(formatTemp(d.maxC, data.tempUnit), p.primary, 12.5f, FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp).width(36.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Um detalhe do clima de agora (sensação, umidade, vento) num bloco discreto. */
@Composable
private fun WeatherDetail(label: String, value: String, modifier: Modifier) {
    val p = pal
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(p.track.copy(alpha = p.track.alpha * 0.7f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Line(label, p.secondary, 11.5f, FontWeight.Medium)
        Line(value, p.primary, 16f, FontWeight.SemiBold)
    }
}

/** A faixa de temperatura do dia dentro da faixa da semana (como no iOS), do azul ao laranja. */
@Composable
private fun TempRange(min: Double, max: Double, lo: Double, hi: Double, modifier: Modifier) {
    val p = pal
    val span = (hi - lo).takeIf { it > 0.1 } ?: 1.0
    Canvas(modifier.height(5.dp)) {
        val r = size.height / 2
        drawRoundRect(p.track, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
        val start = ((min - lo) / span).toFloat() * size.width
        val end = ((max - lo) / span).toFloat() * size.width
        val brush = if (p.accent == p.primary) Brush.horizontalGradient(listOf(p.secondary, p.primary)) else Brush.horizontalGradient(listOf(Color(0xFF5AC8FA), Color(0xFFFFCC00), Color(0xFFFF9500)))
        drawRoundRect(brush, topLeft = Offset(start, 0f), size = Size((end - start).coerceAtLeast(size.height), size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
    }
}

private fun weatherTint(code: Int, isDay: Boolean): Color = when (code) {
    0, 1 -> if (isDay) Color(0xFFFFCC00) else Color(0xFFD7D9F5)
    2, 3, 45, 48 -> Color(0xFFD9DEE8)
    in 51..67, in 80..82 -> Color(0xFF64B5FF)
    in 71..77, 85, 86 -> Color(0xFFE6F3FF)
    in 95..99 -> Color(0xFFFFD60A)
    else -> Color(0xFFE6E8EF)
}

// ---------------------------------------------------------------------------------------------
// Bateria: anel refinado, porcentagem e estado de carga.
// ---------------------------------------------------------------------------------------------

@Composable
internal fun BatteryDesign(battery: BatteryState) {
    val p = pal
    val level = battery.percent.coerceIn(0, 100)
    val color = when {
        battery.charging || battery.full -> p.status(Color(0xFF30D158))
        battery.powerSave -> p.status(Color(0xFFFFD60A))
        level <= 15 -> p.status(Color(0xFFFF453A))
        else -> p.status(Color(0xFF30D158))
    }
    val status = when {
        battery.full -> "Carregada"
        battery.charging -> "Carregando"
        battery.powerSave -> "Economia de energia"
        level <= 15 -> "Bateria baixa"
        else -> "Bateria"
    }
    Sized { size ->
        val maxW = maxWidth
        val maxH = maxHeight
        when (size) {
            WidgetSize.STRIP -> Row(Modifier.fillMaxSize().padding(horizontal = pad(size)), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (battery.charging) Icons.Rounded.Bolt else Icons.Rounded.Smartphone, null, tint = color, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Line(status, p.secondary, 12.5f)
                    Bar(level / 100f, color, 7.dp, Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.width(12.dp))
                Hero("$level%", 26.sp, FontWeight.Medium)
            }
            WidgetSize.SMALL -> Column(Modifier.fillMaxSize().padding(pad(size)), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.weight(1f))
                Ring(level / 100f, (maxH.value * 0.5f).coerceIn(56f, 96f).dp, 7.dp, color, p.track) {
                    Icon(if (battery.charging) Icons.Rounded.Bolt else Icons.Rounded.Smartphone, null, tint = p.primary, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.height(8.dp))
                Hero("$level%", 22.sp, FontWeight.SemiBold)
                Line(status, p.secondary, 12f)
                Spacer(Modifier.weight(1f))
            }
            else -> Row(Modifier.fillMaxSize().padding(pad(size)), verticalAlignment = Alignment.CenterVertically) {
                Ring(level / 100f, (maxH.value * 0.62f).coerceIn(64f, 120f).dp, 8.dp, color, p.track) {
                    Icon(if (battery.charging) Icons.Rounded.Bolt else Icons.Rounded.Smartphone, null, tint = p.primary, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Header("BATERIA")
                    Hero("$level%", 44.sp, FontWeight.Light)
                    Line(status, color, 13.5f, FontWeight.SemiBold)
                    battery.temperatureC?.let { Line("Temperatura ${it.roundToInt()} °C", p.tertiary, 12f) }
                    Spacer(Modifier.height(8.dp))
                    Bar(level / 100f, color, 6.dp)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Música: capa, título, artista e controles integrados.
// ---------------------------------------------------------------------------------------------

@Composable
internal fun MusicDesign(data: WidgetData, actions: WidgetActions) {
    val p = pal
    val media = data.media
    Sized { size ->
        val maxW = maxWidth
        val maxH = maxHeight
        if (media == null) {
            if (!data.notificationAccess) {
                Box(Modifier.fillMaxSize().clickable(onClick = actions.onRequestNotificationAccess)) {
                    Empty(Icons.Rounded.MusicNote, "Mostrar a música", "Permita o acesso às notificações", size)
                }
            } else {
                Empty(Icons.Rounded.MusicNote, "Nada tocando", "Toque para abrir o player", size)
            }
            return@Sized
        }
        val tint = media.tint?.let { Color(it or 0xFF000000.toInt()) }
        val controlColor = if (pal.accent == pal.primary) pal.primary else (tint?.let { androidx.compose.ui.graphics.lerp(it, Color.White, 0.35f) } ?: p.accent)
        val progress = if (media.duration > 0) (media.position.toFloat() / media.duration).coerceIn(0f, 1f) else null
        val art: @Composable (Modifier) -> Unit = { m -> Artwork(media.art, m) }
        when (size) {
            WidgetSize.STRIP -> Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                art(Modifier.fillMaxHeight(0.78f).aspectRatio(1f))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Line(media.title, p.primary, 14f, FontWeight.SemiBold)
                    Line(media.artist, p.secondary, 12.5f)
                }
                RoundButton(if (media.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (media.isPlaying) "Pausar" else "Tocar", 40.dp, true, actions.onMediaPlayPause)
            }
            WidgetSize.SMALL -> Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    art(Modifier.weight(1f).aspectRatio(1f))
                    Spacer(Modifier.width(8.dp))
                    RoundButton(if (media.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (media.isPlaying) "Pausar" else "Tocar", 34.dp, true, actions.onMediaPlayPause)
                }
                Spacer(Modifier.weight(1f))
                Line(media.title, p.primary, 13.5f, FontWeight.SemiBold)
                Line(media.artist, p.secondary, 12f)
            }
            WidgetSize.MEDIUM -> Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                art(Modifier.fillMaxHeight().aspectRatio(1f))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Line(media.title, p.primary, 15f, FontWeight.SemiBold, lines = 2)
                        Line(media.artist, p.secondary, 13f)
                    }
                    if (progress != null) Bar(progress, controlColor, 4.dp)
                    MusicControls(media.isPlaying, media.canSkipPrevious, media.canSkipNext, actions, controlColor)
                }
            }
            else -> Column(Modifier.fillMaxSize().padding(14.dp)) {
                art(Modifier.weight(1f).fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Line(media.title, p.primary, 16f, FontWeight.SemiBold)
                Line(media.artist, p.secondary, 13.5f)
                if (progress != null) Bar(progress, controlColor, 4.dp, Modifier.padding(vertical = 10.dp))
                MusicControls(media.isPlaying, media.canSkipPrevious, media.canSkipNext, actions, controlColor)
            }
        }
    }
}

@Composable
private fun MusicControls(playing: Boolean, canPrevious: Boolean, canNext: Boolean, actions: WidgetActions, color: Color) {
    val p = pal
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.SkipPrevious,
            "Anterior",
            tint = p.primary.copy(alpha = if (canPrevious) 1f else 0.35f),
            modifier = Modifier.size(30.dp).clickable(enabled = canPrevious, onClick = actions.onMediaPrevious),
        )
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(color).clickable(onClick = actions.onMediaPlayPause),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pausar" else "Tocar", tint = Color(0xFF111216), modifier = Modifier.size(26.dp))
        }
        Icon(
            Icons.Rounded.SkipNext,
            "Próxima",
            tint = p.primary.copy(alpha = if (canNext) 1f else 0.35f),
            modifier = Modifier.size(30.dp).clickable(enabled = canNext, onClick = actions.onMediaNext),
        )
    }
}

@Composable
private fun Artwork(art: ImageBitmap?, modifier: Modifier) {
    val p = pal
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(p.track), contentAlignment = Alignment.Center) {
        if (art != null) {
            Image(art, "Capa", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Rounded.MusicNote, null, tint = p.secondary, modifier = Modifier.size(28.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Fotos: imagem de ponta a ponta, troca suave e pontos discretos.
// ---------------------------------------------------------------------------------------------

@Composable
internal fun PhotoDesign(extra: String?) {
    val context = LocalContext.current
    val files = remember(extra) { PhotoWidgetStore.decode(extra) }
    var index by remember(extra) { mutableIntStateOf(0) }
    LaunchedEffect(files) {
        if (files.size > 1) {
            while (true) {
                delay(20_000)
                index = (index + 1) % files.size
            }
        }
    }
    val name = files.getOrNull(index)
    val photo by produceState<ImageBitmap?>(null, name) {
        value = name?.let { withContext(Dispatchers.IO) { PhotoWidgetStore.load(context, it, 900) } }
    }
    Box(
        Modifier
            .fillMaxSize()
            .then(if (files.size > 1) Modifier.pointerInput(files) { detectTapGestures { index = (index + 1) % files.size } } else Modifier),
    ) {
        Crossfade(photo, label = "foto") { image ->
            if (image != null) {
                Image(image, contentDescription = "Foto", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(pal.track), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Photo, contentDescription = null, tint = pal.secondary, modifier = Modifier.size(34.dp))
                }
            }
        }
        if (files.size > 1) {
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 9.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                files.indices.take(8).forEach { i ->
                    Box(Modifier.size(if (i == index) 6.dp else 5.dp).clip(CircleShape).background(Color.White.copy(alpha = if (i == index) 0.95f else 0.45f)))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// L.I.L.Y: sugestões úteis do momento, sem parecer um botão gigante de chatbot.
// ---------------------------------------------------------------------------------------------

/** Sugestões a partir do que está acontecendo (agenda, clima, bateria, música, hora do dia). */
fun lilySuggestions(data: WidgetData, now: Long): List<String> {
    val hour = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).hour
    return buildList {
        data.agenda.events.firstOrNull { it.begin in now..(now + 3 * 3_600_000L) }?.let { add("Preparar para ${it.title.take(28)}") }
        val code = data.weather.data?.code
        if (code != null && (code in 51..67 || code in 80..82 || code in 95..99)) add("Vai chover: o que levar?")
        if (data.battery.percent <= 20 && !data.battery.charging) add("Como economizar bateria?")
        data.media?.let { add("Sobre esta música") }
        when (hour) {
            in 5..10 -> add("Planejar o meu dia")
            in 11..14 -> add("Ideias para o almoço")
            in 18..23 -> add("Resumir o meu dia")
        }
        add("Escrever uma mensagem")
        add("Traduzir um texto")
        add("Explicar um conceito")
    }.distinct()
}

private fun greeting(now: Long): String = when (Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).hour) {
    in 5..11 -> "Bom dia"
    in 12..17 -> "Boa tarde"
    else -> "Boa noite"
}

@Composable
internal fun LilyDesign(data: WidgetData, actions: WidgetActions) {
    val p = pal
    val now = rememberMinuteTicker().value
    Sized { size ->
        val maxW = maxWidth
        val maxH = maxHeight
        if (!data.lily) {
            Row(Modifier.fillMaxSize().padding(horizontal = pad(size)), verticalAlignment = Alignment.CenterVertically) {
                LilyGlyph(if (size == WidgetSize.STRIP) 30.dp else 38.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Line("Instale a L.I.L.Y AI", p.primary, 14.5f, FontWeight.SemiBold)
                    Line("IA no aparelho, sem internet", p.secondary, 12.5f)
                }
            }
            return@Sized
        }
        val suggestions = lilySuggestions(data, now)
        when (size) {
            WidgetSize.STRIP -> Row(Modifier.fillMaxSize().padding(start = 12.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                LilyGlyph(30.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Header("L.I.L.Y", p.secondary)
                    Line(suggestions.first(), p.primary, 14.5f, FontWeight.Medium, modifier = Modifier.clickable { actions.onLilyPrompt(suggestions.first()) })
                }
                RoundButton(Icons.Rounded.Mic, "Falar com a L.I.L.Y", 40.dp, true, actions.onLilyVoice)
            }
            WidgetSize.SMALL -> Column(Modifier.fillMaxSize().padding(pad(size))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LilyGlyph(26.dp)
                    Spacer(Modifier.weight(1f))
                    RoundButton(Icons.Rounded.Mic, "Falar com a L.I.L.Y", 34.dp, true, actions.onLilyVoice)
                }
                Spacer(Modifier.weight(1f))
                Line(greeting(now), p.secondary, 13f)
                Line(suggestions.first(), p.primary, 15f, FontWeight.SemiBold, lines = 2, modifier = Modifier.clickable { actions.onLilyPrompt(suggestions.first()) })
            }
            else -> Column(Modifier.fillMaxSize().padding(pad(size))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LilyGlyph(30.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Line(greeting(now), p.primary, 15f, FontWeight.SemiBold)
                        Line("Sugestões para agora", p.secondary, 12.5f)
                    }
                    RoundButton(Icons.Rounded.Mic, "Falar com a L.I.L.Y", 38.dp, true, actions.onLilyVoice)
                }
                Spacer(Modifier.weight(1f))
                val rows = if (size == WidgetSize.MEDIUM) 2 else 4
                suggestions.take(rows * 2).chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pair.forEach { s -> Chip(s, { actions.onLilyPrompt(s) }, Modifier.weight(1f, fill = false)) }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Info Brief: o próximo item e o que está pendente, em resumo.
// ---------------------------------------------------------------------------------------------

@Composable
internal fun BriefDesign(data: WidgetData) {
    val p = pal
    val brief = data.brief
    val now = rememberMinuteTicker().value
    val is24h = DateFormat.is24HourFormat(LocalContext.current)
    val amber = p.status(Color(0xFFFF9F0A))
    Sized { size ->
        val maxW = maxWidth
        val maxH = maxHeight
        val next = brief.items.firstOrNull()
        val counters = buildList {
            if (brief.suggestions > 0) add(Icons.Rounded.AutoAwesome to "${brief.suggestions} sugest${if (brief.suggestions == 1) "ão" else "ões"}")
            if (brief.timers > 0) add(Icons.Rounded.Timer to "${brief.timers} timer${if (brief.timers == 1) "" else "s"}")
            if (brief.lives > 0) add(Icons.Rounded.Notifications to "${brief.lives} ao vivo")
        }
        if (next == null && counters.isEmpty()) {
            Empty(Icons.Rounded.CalendarToday, "Tudo em dia", "Nada pendente agora", size)
            return@Sized
        }
        when (size) {
            WidgetSize.STRIP -> Row(Modifier.fillMaxSize().padding(horizontal = pad(size)), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (next?.reminder == true) Icons.Rounded.Alarm else Icons.Rounded.Event, null, tint = amber, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Line(next?.title ?: counters.first().second, p.primary, 14f, FontWeight.SemiBold)
                    Line(next?.at?.let { briefWhen(it, now, is24h) } ?: "Info Brief", p.secondary, 12.5f)
                }
            }
            else -> Column(Modifier.fillMaxSize().padding(pad(size))) {
                Header("INFO BRIEF", amber)
                Spacer(Modifier.height(6.dp))
                val max = when (size) {
                    WidgetSize.SMALL -> 1
                    WidgetSize.MEDIUM -> 2
                    else -> 5
                }
                brief.items.take(max).forEach { item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (item.reminder) Icons.Rounded.Alarm else Icons.Rounded.Event, null, tint = p.secondary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Line(item.title, p.primary, 14f, FontWeight.SemiBold, lines = if (size == WidgetSize.SMALL) 2 else 1)
                            item.at?.let { Line(briefWhen(it, now, is24h), p.secondary, 12f) }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (counters.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        counters.take(if (size == WidgetSize.SMALL) 1 else 3).forEach { (icon, text) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(icon, null, tint = p.secondary, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(3.dp))
                                Line(text, p.secondary, 12f)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun briefWhen(at: Long, now: Long, is24h: Boolean): String {
    val day = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
    val time = hourMinute(at, is24h)
    return when (day) {
        today -> if (at < now) "Agora · $time" else "Hoje, $time"
        today.plusDays(1) -> "Amanhã, $time"
        else -> "${day.dayOfMonth}/${day.monthValue}, $time"
    }
}

// ---------------------------------------------------------------------------------------------
// Sistema: atalhos com ícones coerentes e boa distribuição.
// ---------------------------------------------------------------------------------------------

@Composable
internal fun ShortcutsDesign(data: WidgetData, actions: WidgetActions) {
    val p = pal
    val items = listOf(
        Shortcut(if (data.torchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff, "Lanterna", data.torchOn, actions.onTorch),
        Shortcut(Icons.Rounded.PhotoCamera, "Câmera", false, actions.onCamera),
        Shortcut(Icons.Rounded.Search, "Buscar", false, actions.onSearch),
        Shortcut(Icons.Rounded.Settings, "Ajustes", false, actions.onSettings),
    )
    Sized { size ->
        val maxW = maxWidth
        val maxH = maxHeight
        when (size) {
            WidgetSize.STRIP -> Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                items.forEach { RoundButton(it.icon, it.label, (maxH.value * 0.56f).coerceIn(38f, 52f).dp, it.active, it.action) }
            }
            WidgetSize.SMALL -> Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                items.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { ShortcutCell(it, (maxW.value * 0.27f).coerceIn(38f, 54f).dp) }
                    }
                }
            }
            else -> Column(Modifier.fillMaxSize().padding(pad(size))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Header("ATALHOS", modifier = Modifier.weight(1f))
                    Icon(if (data.battery.charging) Icons.Rounded.Bolt else Icons.Rounded.Smartphone, null, tint = p.secondary, modifier = Modifier.size(13.dp))
                    Line(" ${data.battery.percent}%", p.secondary, 12.5f)
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    items.forEach { ShortcutCell(it, 50.dp) }
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

private class Shortcut(val icon: ImageVector, val label: String, val active: Boolean, val action: () -> Unit)

@Composable
private fun ShortcutCell(item: Shortcut, size: Dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RoundButton(item.icon, item.label, size, item.active, item.action)
        Line(item.label, pal.secondary, 11.5f, modifier = Modifier.padding(top = 4.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// Busca: uma cápsula limpa.
// ---------------------------------------------------------------------------------------------

@Composable
internal fun SearchDesign(data: WidgetData, actions: WidgetActions) {
    val p = pal
    Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Search, null, tint = p.secondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Line("Buscar apps", p.secondary, 16f, FontWeight.Normal, modifier = Modifier.weight(1f))
        if (data.lily) {
            Box(Modifier.clip(CircleShape).clickable(onClick = actions.onLilyVoice).padding(4.dp)) { LilyGlyph(26.dp) }
        }
    }
}
