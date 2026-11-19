package app.cristal.cover.ui.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cristal.cover.data.brief.BriefLabels
import app.cristal.cover.data.brief.Reminder
import app.cristal.cover.data.calendar.AgendaEvent
import app.cristal.cover.data.home.WidgetKind
import app.cristal.cover.data.island.BriefSuggestion
import app.cristal.cover.data.island.LiveActivityData
import app.cristal.cover.data.island.LiveCategory
import app.cristal.cover.data.notifications.NotificationItem
import app.cristal.cover.ui.components.WallpaperTextShadow
import app.cristal.cover.ui.glass.GlassSurface
import app.cristal.cover.ui.glass.LocalGlassSpec
import app.cristal.cover.ui.island.LiveAppIcon
import app.cristal.cover.ui.island.liveColor
import app.cristal.cover.ui.widgets.WidgetActions
import app.cristal.cover.ui.widgets.WidgetCard
import app.cristal.cover.ui.widgets.WidgetData
import app.cristal.cover.ui.widgets.currentLocale
import app.cristal.cover.ui.widgets.formatDate
import app.cristal.cover.ui.widgets.rememberMinuteTicker
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** O que o Now Brief junta, além dos widgets: lembretes, sugestões e atividades em andamento. */
@Immutable
data class NowBriefData(
    val reminders: List<Reminder> = emptyList(),
    val suggestions: List<BriefSuggestion> = emptyList(),
    val lives: List<LiveActivityData> = emptyList(),
    val timers: List<NotificationItem> = emptyList(),
)

/** O resumo que o widget do Info Brief mostra: os lembretes por hora e quantos há de cada coisa. */
fun NowBriefData.summary(): app.cristal.cover.ui.widgets.BriefSummary = app.cristal.cover.ui.widgets.BriefSummary(
    items = reminders.sortedWith(compareBy(nullsLast()) { it.at }).map { app.cristal.cover.ui.widgets.BriefItem(it.title, it.at, reminder = true) },
    suggestions = suggestions.size,
    timers = timers.size,
    lives = lives.size,
)

@Stable
class NowBriefActions(
    val onCompleteReminder: (Reminder) -> Unit = {},
    val onAcceptSuggestion: (BriefSuggestion) -> Unit = {},
    val onDismissSuggestion: (BriefSuggestion) -> Unit = {},
    val onOpenNotification: (NotificationItem) -> Unit = {},
    val onOpenSettings: () -> Unit = {},
)

/** Uma linha da linha do tempo: compromisso da agenda ou lembrete do Cristal. */
@Immutable
data class TimelineEntry(
    val key: String,
    val title: String,
    val at: Long,
    val allDay: Boolean,
    val subtitle: String,
    val color: Int,
    val reminder: Reminder? = null,
)

/** As regras do Now Brief, separadas da interface (testáveis). */
object NowBriefLogic {
    private const val HOUR = 3_600_000L

    /** Agenda e lembretes das próximas 48 h, em ordem (o que acabou de passar fica 1 h). */
    fun timeline(events: List<AgendaEvent>, reminders: List<Reminder>, now: Long, limit: Int = 6): List<TimelineEntry> {
        val from = now - HOUR
        val to = now + 48 * HOUR
        val fromAgenda = events.filter { (if (it.allDay) it.end else it.begin) in from..to || (it.begin <= now && it.end >= now) }.map {
            TimelineEntry("e${it.id}:${it.begin}", it.title, it.begin, it.allDay, it.location, it.color)
        }
        val fromReminders = reminders.filter { !it.done && it.at != null && it.at in from..to }.map {
            TimelineEntry("r${it.id}", it.title, it.at!!, false, it.place ?: if (it.weekly) "Toda semana" else "Lembrete", 0, it)
        }
        return (fromAgenda + fromReminders).sortedWith(compareBy({ !it.allDay }, { it.at })).take(limit)
    }

    /** Tarefas sem data, das mais antigas para as mais novas. */
    fun tasks(reminders: List<Reminder>): List<Reminder> = reminders.filter { !it.done && it.at == null }.sortedBy { it.created }

    /** A frase do topo: o dia em poucas palavras. */
    fun summary(data: NowBriefData, events: List<AgendaEvent>, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        fun isToday(at: Long) = Instant.ofEpochMilli(at).atZone(zone).toLocalDate() == today
        val meetings = events.count { !it.allDay && isToday(it.begin) && it.end >= now } +
            data.reminders.count { !it.done && it.at != null && isToday(it.at) && it.at >= now }
        val parts = ArrayList<String>()
        if (meetings > 0) parts += if (meetings == 1) "1 compromisso hoje" else "$meetings compromissos hoje"
        val tasks = tasks(data.reminders).size
        if (tasks > 0) parts += if (tasks == 1) "1 tarefa" else "$tasks tarefas"
        data.lives.firstOrNull()?.let { live ->
            parts += when (live.category) {
                LiveCategory.DELIVERY -> "entrega em andamento"
                LiveCategory.RIDE -> "corrida em andamento"
                LiveCategory.NAVIGATION -> "navegação ativa"
                else -> "${live.item.appLabel} em andamento"
            }
        }
        if (data.suggestions.isNotEmpty()) parts += if (data.suggestions.size == 1) "1 sugestão" else "${data.suggestions.size} sugestões"
        return if (parts.isEmpty()) "Nada marcado por enquanto." else parts.joinToString(" · ").replaceFirstChar { it.uppercase() }
    }
}

private val headerStyle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, shadow = WallpaperTextShadow)
private val sectionStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp, shadow = WallpaperTextShadow)

/**
 * Now Brief: o painel à esquerda da primeira página (substitui o antigo "Hoje"). Cartões com o que
 * importa agora: atividades em andamento, a linha do tempo da agenda e dos lembretes, tarefas,
 * sugestões do Info Brief, clima e bateria. Sem chat: a organização acontece nos bastidores.
 */
@Composable
fun NowBriefPanel(widgetData: WidgetData, widgetActions: WidgetActions, data: NowBriefData, actions: NowBriefActions) {
    val now = rememberMinuteTicker().value
    val locale = currentLocale()
    val hour = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).hour
    val greeting = when (hour) {
        in 5..11 -> "Bom dia"
        in 12..17 -> "Boa tarde"
        else -> "Boa noite"
    }
    val events = widgetData.agenda.events
    val timeline = NowBriefLogic.timeline(events, data.reminders, now)
    val tasks = NowBriefLogic.tasks(data.reminders)
    val playing = widgetData.media?.isPlaying == true
    LazyColumn(
        modifier = Modifier.fillMaxSize().semantics { contentDescription = "Now Brief" },
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") {
            Column(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 2.dp)) {
                Text(greeting, color = Color.White, style = headerStyle)
                Text(formatDate(now).replaceFirstChar { it.titlecase(locale) }, color = Color.White.copy(alpha = 0.85f), style = TextStyle(fontSize = 13.sp, shadow = WallpaperTextShadow))
                Text(
                    NowBriefLogic.summary(data, events, now),
                    color = Color.White,
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, shadow = WallpaperTextShadow),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        if (data.lives.isNotEmpty() || data.timers.isNotEmpty() || playing) {
            item(key = "agora") { Section("Agora") }
            data.lives.forEach { live -> item(key = "live:${live.item.key}") { LiveCard(live) { actions.onOpenNotification(live.item) } } }
            data.timers.forEach { t -> item(key = "timer:${t.key}") { TimerCard(t) { actions.onOpenNotification(t) } } }
            if (playing) {
                item(key = "music") { WidgetCard(WidgetKind.MUSIC, widgetData, widgetActions, Modifier.fillMaxWidth().height(108.dp)) }
            }
        }
        item(key = "proximos") {
            TimelineCard(timeline, widgetData.agenda.hasPermission, widgetActions, actions)
        }
        if (tasks.isNotEmpty()) {
            item(key = "tarefas") { TasksCard(tasks, actions) }
        }
        if (data.suggestions.isNotEmpty()) {
            item(key = "sugestoes") { SuggestionsCard(data.suggestions, now, actions) }
        }
        item(key = "weather") {
            WidgetCard(WidgetKind.WEATHER, widgetData, widgetActions, Modifier.fillMaxWidth().height(132.dp))
        }
        item(key = "battery") {
            WidgetCard(WidgetKind.BATTERY, widgetData, widgetActions, Modifier.fillMaxWidth().height(92.dp))
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(text.uppercase(Locale.forLanguageTag("pt-BR")), color = Color.White.copy(alpha = 0.9f), style = sectionStyle, modifier = Modifier.padding(start = 6.dp, top = 4.dp))
}

@Composable
private fun CardTitle(text: String, trailing: String? = null) {
    val spec = LocalGlassSpec.current
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = spec.contentColor, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, fontSize = 12.sp, color = spec.secondaryContentColor)
    }
}

@Composable
private fun LiveCard(live: LiveActivityData, onClick: () -> Unit) {
    val spec = LocalGlassSpec.current
    val color = liveColor(live)
    GlassSurface(Modifier.fillMaxWidth(), cornerRadius = 22.dp, onClick = onClick) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveAppIcon(live, 34.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(live.item.title.ifBlank { live.item.appLabel }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = spec.contentColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (live.item.text.isNotBlank()) {
                        Text(live.item.text, fontSize = 13.sp, color = spec.secondaryContentColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                live.eta?.let { Text(it, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = color, modifier = Modifier.padding(start = 10.dp)) }
            }
            val fraction = live.fraction ?: live.stage?.let { (it + 0.5f) / live.stages.size.coerceAtLeast(1) }
            if (fraction != null) {
                Canvas(Modifier.fillMaxWidth().padding(top = 10.dp).height(6.dp)) {
                    val r = CornerRadius(size.height / 2, size.height / 2)
                    drawRoundRect(spec.contentColor.copy(alpha = 0.15f), cornerRadius = r)
                    drawRoundRect(color, size = Size(size.width * fraction, size.height), cornerRadius = r)
                }
                live.stage?.let { stage ->
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        live.stages.forEachIndexed { i, name ->
                            Text(name, fontSize = 10.5.sp, fontWeight = if (i == stage) FontWeight.SemiBold else FontWeight.Normal, color = if (i == stage) spec.contentColor else spec.secondaryContentColor)
                        }
                    }
                }
            }
            Text(live.source.label, fontSize = 10.5.sp, color = spec.secondaryContentColor, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun TimerCard(item: NotificationItem, onClick: () -> Unit) {
    val spec = LocalGlassSpec.current
    GlassSurface(Modifier.fillMaxWidth(), cornerRadius = 22.dp, onClick = onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Timer, contentDescription = null, tint = Color(0xFFFF9F0A), modifier = Modifier.size(26.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(item.title.ifBlank { item.appLabel }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = spec.contentColor, maxLines = 1)
                Text(item.appLabel, fontSize = 12.sp, color = spec.secondaryContentColor, maxLines = 1)
            }
        }
    }
}

private val hhmm = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("pt-BR"))

private fun dayOf(at: Long, now: Long): String {
    val zone = ZoneId.systemDefault()
    val d = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return when (d) {
        today -> "Hoje"
        today.plusDays(1) -> "Amanhã"
        else -> DateTimeFormatter.ofPattern("EEE", Locale.forLanguageTag("pt-BR")).format(d).replaceFirstChar { it.uppercase() }
    }
}

@Composable
private fun TimelineCard(entries: List<TimelineEntry>, calendarAllowed: Boolean, widgetActions: WidgetActions, actions: NowBriefActions) {
    val spec = LocalGlassSpec.current
    val now = System.currentTimeMillis()
    GlassSurface(Modifier.fillMaxWidth(), cornerRadius = 22.dp, onClick = if (calendarAllowed) widgetActions.onAgendaClick else widgetActions.onRequestCalendar) {
        Column(Modifier.padding(14.dp)) {
            CardTitle("Próximos", if (entries.isEmpty()) null else "48 horas")
            if (entries.isEmpty()) {
                Text(
                    if (calendarAllowed) "Nada nas próximas 48 horas." else "Nada marcado. Toque para mostrar também a agenda do aparelho.",
                    fontSize = 13.sp,
                    color = spec.secondaryContentColor,
                )
            }
            entries.forEachIndexed { i, e ->
                if (i > 0) Box(Modifier.fillMaxWidth().padding(start = 64.dp).height(1.dp).background(spec.contentColor.copy(alpha = 0.08f)))
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(52.dp)) {
                        Text(if (e.allDay) "dia" else hhmm.format(Instant.ofEpochMilli(e.at).atZone(ZoneId.systemDefault())), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = spec.contentColor)
                        Text(if (e.allDay) "todo" else dayOf(e.at, now), fontSize = 11.sp, color = spec.secondaryContentColor)
                    }
                    Box(
                        Modifier
                            .padding(horizontal = 8.dp)
                            .width(4.dp)
                            .height(30.dp)
                            .clip(CircleShape)
                            .background(if (e.reminder != null) Color(0xFF0A84FF) else if (e.color != 0) Color(e.color).copy(alpha = 1f) else spec.contentColor.copy(alpha = 0.5f)),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(e.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = spec.contentColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (e.subtitle.isNotBlank()) Text(e.subtitle, fontSize = 12.sp, color = spec.secondaryContentColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    e.reminder?.let { r ->
                        if (r.weekly) Icon(Icons.Rounded.Repeat, contentDescription = "Toda semana", tint = spec.secondaryContentColor, modifier = Modifier.size(16.dp).padding(end = 2.dp))
                        RoundCheck("Concluir ${r.title}") { actions.onCompleteReminder(r) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TasksCard(tasks: List<Reminder>, actions: NowBriefActions) {
    val spec = LocalGlassSpec.current
    GlassSurface(Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
        Column(Modifier.padding(14.dp)) {
            CardTitle("Tarefas", "${tasks.size}")
            tasks.take(6).forEach { t ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundCheck("Concluir ${t.title}") { actions.onCompleteReminder(t) }
                    Text(t.title, fontSize = 14.sp, color = spec.contentColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp).weight(1f))
                    t.place?.let { Text(it, fontSize = 12.sp, color = spec.secondaryContentColor, maxLines = 1) }
                }
            }
        }
    }
}

@Composable
private fun SuggestionsCard(suggestions: List<BriefSuggestion>, now: Long, actions: NowBriefActions) {
    val spec = LocalGlassSpec.current
    GlassSurface(Modifier.fillMaxWidth(), cornerRadius = 22.dp, onClick = actions.onOpenSettings) {
        Column(Modifier.padding(14.dp)) {
            CardTitle("Sugestões do Info Brief")
            suggestions.forEach { s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF0A84FF)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Event, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(s.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = spec.contentColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val whenText = s.whenMillis?.let { dayOf(it, now) + (if (s.timeConfirmed) " " + hhmm.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) else "") + if (s.dateConfirmed) "" else "?" }
                        Text(listOfNotNull(BriefLabels.found(s.kind), whenText).joinToString(" · "), fontSize = 12.sp, color = spec.secondaryContentColor, maxLines = 1)
                    }
                    Text(
                        BriefLabels.action(s.kind).substringBefore(' '),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFF0A84FF))
                            .clickable { actions.onAcceptSuggestion(s) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                    Box(
                        Modifier
                            .padding(start = 6.dp)
                            .size(30.dp)
                            .clip(CircleShape)
                            .clickable { actions.onDismissSuggestion(s) }
                            .semantics { contentDescription = "Dispensar ${s.title}" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = null, tint = spec.secondaryContentColor, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundCheck(description: String, onClick: () -> Unit) {
    val spec = LocalGlassSpec.current
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .border(1.5.dp, spec.contentColor.copy(alpha = 0.45f), CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Check, contentDescription = null, tint = spec.contentColor.copy(alpha = 0.25f), modifier = Modifier.size(16.dp))
    }
}
