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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.rounded.Photo
import app.cristal.cover.data.home.PhotoWidgetStore
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
import app.cristal.cover.ui.theme.clockStyle
import app.cristal.cover.data.prefs.ClockFont
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.rounded.Mic

/** Tudo o que os widgets precisam para se desenhar. */
@Immutable
data class WidgetData(
    val battery: BatteryState = BatteryState(),
    val weather: WeatherState = WeatherState(),
    val media: MediaState? = null,
    val agenda: AgendaState = AgendaState(),
    val tempUnit: TempUnit = TempUnit.CELSIUS,
    val notificationAccess: Boolean = false,
    val torchOn: Boolean = false,
    /** Fonte escolhida para o relógio (a mesma do bloqueio interno). */
    val clockFont: ClockFont = ClockFont.SYSTEM,
    /** A L.I.L.Y AI está instalada e com a ponte liberada. */
    val lily: Boolean = false,
    /** O resumo do Info Brief (lembretes, sugestões e o que está acontecendo). */
    val brief: BriefSummary = BriefSummary(),
)

/** O que o widget do Info Brief mostra: os próximos itens e quantos há de cada coisa. */
@Immutable
data class BriefSummary(
    val items: List<BriefItem> = emptyList(),
    val suggestions: Int = 0,
    val timers: Int = 0,
    val lives: Int = 0,
)

/** Um item do Info Brief: lembrete ou compromisso, com a hora (null = sem hora). */
@Immutable
data class BriefItem(val title: String, val at: Long?, val reminder: Boolean)

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
    val onTorch: () -> Unit = {},
    val onCamera: () -> Unit = {},
    val onSearch: () -> Unit = {},
    val onSettings: () -> Unit = {},
    /** Widget da L.I.L.Y: abrir a conversa (toque) e falar (microfone). */
    val onLilyAsk: () -> Unit = {},
    val onLilyVoice: () -> Unit = {},
    /** Uma sugestão tocada no widget da L.I.L.Y (abre a conversa já perguntando). */
    val onLilyPrompt: (String) -> Unit = {},
    /** Abrir o Info Brief completo. */
    val onBriefOpen: () -> Unit = {},
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

/**
 * Desenha o widget do tipo pedido na superfície da família (vidro com a aparência escolhida nas
 * configurações). O conteúdo se reorganiza para o tamanho real que recebe.
 */
@Composable
fun WidgetCard(
    kind: WidgetKind,
    data: WidgetData,
    actions: WidgetActions,
    modifier: Modifier = Modifier,
    cornerRadius: Dp? = null,
    onLongClick: (() -> Unit)? = null,
    gestureModifier: Modifier = Modifier,
    /** Dados próprios do widget (no de foto, os arquivos das fotos). */
    extra: String? = null,
) {
    val onClick: () -> Unit = when (kind) {
        WidgetKind.CLOCK -> actions.onClockClick
        WidgetKind.WEATHER -> actions.onWeatherClick
        WidgetKind.MUSIC -> if (data.media == null && !data.notificationAccess) actions.onRequestNotificationAccess else actions.onMediaOpen
        WidgetKind.BATTERY -> actions.onBatteryClick
        WidgetKind.AGENDA -> if (data.agenda.hasPermission) actions.onAgendaClick else actions.onRequestCalendar
        WidgetKind.ANALOG_CLOCK -> actions.onClockClick
        WidgetKind.DATE -> actions.onAgendaClick
        WidgetKind.SEARCH -> actions.onSearch
        WidgetKind.LILY -> actions.onLilyAsk
        WidgetKind.BRIEF -> actions.onBriefOpen
        WidgetKind.SHORTCUTS, WidgetKind.APP_WIDGET, WidgetKind.PHOTO -> ({})
    }
    WidgetSurface(
        kind = kind,
        modifier = modifier,
        cornerRadius = cornerRadius,
        onClick = onClick,
        onLongClick = onLongClick,
        gestureModifier = gestureModifier,
        themeAccent = app.cristal.cover.ui.design.Liquid.colors.accent,
    ) {
        when (kind) {
            WidgetKind.CLOCK -> ClockDesign(data)
            WidgetKind.WEATHER -> WeatherDesign(data)
            WidgetKind.MUSIC -> MusicDesign(data, actions)
            WidgetKind.BATTERY -> BatteryDesign(data.battery)
            WidgetKind.AGENDA -> AgendaDesign(data)
            WidgetKind.ANALOG_CLOCK -> AnalogDesign()
            WidgetKind.DATE -> DateDesign(data)
            WidgetKind.SHORTCUTS -> ShortcutsDesign(data, actions)
            WidgetKind.SEARCH -> SearchDesign(data, actions)
            WidgetKind.PHOTO -> PhotoDesign(extra)
            WidgetKind.LILY -> LilyDesign(data, actions)
            WidgetKind.BRIEF -> BriefDesign(data)
            WidgetKind.APP_WIDGET -> Unit
        }
    }
}

// --- Textos do clima e da agenda (também usados no bloqueio e nas configurações) -------------

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
