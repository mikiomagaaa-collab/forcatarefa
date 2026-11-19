package app.cristal.cover.ui.brief

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.cristal.cover.data.brief.BriefLabels
import app.cristal.cover.data.island.BriefSuggestion
import app.cristal.cover.ui.design.Liquid
import app.cristal.cover.ui.design.LiquidButton
import app.cristal.cover.ui.design.LiquidButtonStyle
import app.cristal.cover.ui.design.LiquidChip
import app.cristal.cover.ui.design.LiquidListGroup
import app.cristal.cover.ui.design.LiquidListRow
import app.cristal.cover.ui.design.LiquidMotion
import app.cristal.cover.ui.design.LiquidRadius
import app.cristal.cover.ui.design.LiquidToggle
import app.cristal.cover.ui.design.LiquidType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** O que a pessoa confirmou. [at] null = tarefa sem data (fica no Now Brief, sem aviso). */
data class BriefDraft(
    val suggestion: BriefSuggestion,
    val title: String,
    val at: Long?,
    val alarm: Boolean,
    val weekly: Boolean,
)

@Stable
class BriefSheetActions(
    val onCreate: (BriefDraft) -> Unit,
    val onLater: () -> Unit,
    val onPickDate: (LocalDate?, (LocalDate) -> Unit) -> Unit,
    val onPickTime: (LocalTime?, (LocalTime) -> Unit) -> Unit,
    val onAllowNotifications: () -> Unit,
    val onAllowExact: () -> Unit,
    val onOpenMap: (String) -> Unit,
)

/** Regras da folha, separadas da interface para os testes. */
object BriefSheetLogic {
    /** Sem horário, o lembrete de um dia avisa às 9:00. */
    val MORNING: LocalTime = LocalTime.of(9, 0)

    fun at(date: LocalDate?, time: LocalTime?, zone: ZoneId = ZoneId.systemDefault()): Long? =
        date?.atTime(time ?: MORNING)?.atZone(zone)?.toInstant()?.toEpochMilli()

    /** Pode ficar sem data: tarefas, lugares e viagens. */
    fun allowsNoDate(kind: String): Boolean = kind == "task" || kind == "place" || kind == "trip"

    fun initialDate(s: BriefSuggestion, zone: ZoneId = ZoneId.systemDefault()): LocalDate? =
        s.whenMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }

    fun initialTime(s: BriefSuggestion, zone: ZoneId = ZoneId.systemDefault()): LocalTime? =
        if (s.timeConfirmed) s.whenMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() } else null
}

private val pt = Locale.forLanguageTag("pt-BR")
private val dayFormat = DateTimeFormatter.ofPattern("EEE, d 'de' MMM", pt)
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", pt)

private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Hoje"
    today.plusDays(1) -> "Amanhã"
    else -> dayFormat.format(date).replaceFirstChar { it.uppercase() }
}

/**
 * A folha de confirmação: sobe sobre o app em uso com o que o Info Brief entendeu, para conferir e
 * ajustar. Nada é agendado sem o toque em "Criar".
 */
@Composable
fun BriefConfirmSheet(
    suggestion: BriefSuggestion,
    alarmDefault: Boolean,
    notificationsOk: Boolean,
    exactOk: Boolean,
    created: Boolean,
    actions: BriefSheetActions,
    now: () -> Long = System::currentTimeMillis,
) {
    val colors = Liquid.colors
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(now()).atZone(zone).toLocalDate()
    var title by rememberSaveable { mutableStateOf(suggestion.title) }
    var date by remember { mutableStateOf(BriefSheetLogic.initialDate(suggestion, zone) ?: if (BriefSheetLogic.allowsNoDate(suggestion.kind)) null else today) }
    var time by remember { mutableStateOf(BriefSheetLogic.initialTime(suggestion, zone)) }
    var weekly by rememberSaveable { mutableStateOf(suggestion.weekly || suggestion.kind == "recurring") }
    var alarm by rememberSaveable { mutableStateOf(alarmDefault) }
    val suggestedTime = remember { BriefSheetLogic.initialTime(suggestion, zone) }
    val at = BriefSheetLogic.at(date, time, zone)
    val past = at != null && at < now() - 60_000L

    // A folha sobe com a mola do sistema.
    val rise = remember { Animatable(1f) }
    LaunchedEffect(Unit) { rise.animateTo(0f, LiquidMotion.Smooth.spec()) }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.scrim)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = actions.onLater),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .graphicsLayer { translationY = rise.value * size.height }
                .clip(RoundedCornerShape(topStart = LiquidRadius.sheet, topEnd = LiquidRadius.sheet))
                .background(colors.surface)
                .border(1.dp, colors.separator, RoundedCornerShape(topStart = LiquidRadius.sheet, topEnd = LiquidRadius.sheet))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(width = 38.dp, height = 5.dp).clip(CircleShape).background(colors.contentTertiary.copy(alpha = 0.5f)))
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(BriefBlue), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Event, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Text(BriefLabels.found(suggestion.kind), style = LiquidType.subhead.copy(fontWeight = FontWeight.SemiBold), color = BriefBlue, modifier = Modifier.padding(start = 10.dp).weight(1f))
                Text("Info Brief", style = LiquidType.caption, color = colors.contentSecondary)
            }
            Spacer(Modifier.height(12.dp))
            BasicTextField(
                value = title,
                onValueChange = { title = it.take(80) },
                singleLine = true,
                textStyle = LiquidType.title2.copy(color = colors.content),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Título do lembrete" },
            )
            if (suggestion.sourceText.isNotBlank()) {
                Text("Você escreveu: “${suggestion.sourceText}”", style = LiquidType.footnote, color = colors.contentSecondary, modifier = Modifier.padding(top = 4.dp))
            }

            SheetLabel("Dia", Icons.Rounded.Event)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiquidChip("Hoje", date == today, { date = today })
                LiquidChip("Amanhã", date == today.plusDays(1), { date = today.plusDays(1) })
                val d = date
                if (d != null && d != today && d != today.plusDays(1)) LiquidChip(dayLabel(d, today), true, {})
                LiquidChip("Outro dia…", false, { actions.onPickDate(date) { date = it } })
                if (BriefSheetLogic.allowsNoDate(suggestion.kind)) LiquidChip("Sem data", date == null, { date = null })
            }
            if (!suggestion.dateConfirmed && date != null) {
                Text("Confira o dia: a mensagem não dizia com certeza.", style = LiquidType.footnote, color = colors.warning, modifier = Modifier.padding(top = 6.dp))
            }

            if (date != null) {
                SheetLabel("Horário", Icons.Rounded.Schedule)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (suggestedTime != null) LiquidChip(timeFormat.format(suggestedTime), time == suggestedTime, { time = suggestedTime })
                    val t = time
                    if (t != null && t != suggestedTime) LiquidChip(timeFormat.format(t), true, {})
                    LiquidChip("Outro horário…", false, { actions.onPickTime(time) { time = it } })
                    LiquidChip("Sem horário", time == null, { time = null })
                }
                if (time == null) {
                    Text("Sem horário, o lembrete avisa às 9:00 do dia.", style = LiquidType.footnote, color = colors.contentSecondary, modifier = Modifier.padding(top = 6.dp))
                }
                if (past) {
                    Text("Esse horário já passou. Escolha outro.", style = LiquidType.footnote, color = colors.danger, modifier = Modifier.padding(top = 6.dp))
                }
            }

            Spacer(Modifier.height(14.dp))
            LiquidListGroup {
                if (date != null) {
                    LiquidListRow(
                        "Repetir toda semana",
                        icon = Icons.Rounded.Repeat,
                        iconBackground = Color(0xFF5E5CE6),
                        divider = false,
                        trailing = { LiquidToggle(weekly, { weekly = it }) },
                    )
                    LiquidListRow(
                        "Tocar como alarme",
                        icon = Icons.Rounded.Notifications,
                        iconBackground = Color(0xFFFF9F0A),
                        trailing = { LiquidToggle(alarm, { alarm = it }) },
                    )
                }
                suggestion.place?.let { place ->
                    LiquidListRow(
                        "Abrir “$place” no mapa",
                        icon = Icons.Rounded.Map,
                        iconBackground = Color(0xFF30D158),
                        divider = date != null,
                        chevron = true,
                        onClick = { actions.onOpenMap(place) },
                    )
                }
            }

            if (date != null && !notificationsOk) {
                PermissionHint("Permita as notificações para o lembrete avisar na hora.", "Permitir", actions.onAllowNotifications)
            }
            if (date != null && !exactOk) {
                PermissionHint("Para avisar no minuto exato, libere “Alarmes e lembretes”. Sem isso, o aviso pode atrasar alguns minutos.", "Abrir", actions.onAllowExact)
            }

            Spacer(Modifier.height(16.dp))
            AnimatedContent(created, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "criado") { done ->
                if (done) {
                    Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.success, modifier = Modifier.size(24.dp))
                        Text(if (date == null) "Tarefa adicionada" else "Lembrete criado", style = LiquidType.headline, color = colors.content, modifier = Modifier.padding(start = 8.dp))
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LiquidButton("Agora não", actions.onLater, Modifier.weight(1f), style = LiquidButtonStyle.TINTED)
                        LiquidButton(
                            if (date == null) "Adicionar tarefa" else BriefLabels.action(suggestion.kind),
                            { actions.onCreate(BriefDraft(suggestion, title.trim().ifBlank { suggestion.title }, at, alarm && date != null, weekly && date != null)) },
                            Modifier.weight(1.4f),
                            enabled = !past,
                        )
                    }
                }
            }
            Text(
                "Reconhecido no teclado, no próprio aparelho. Só o compromisso chegou ao Cristal.",
                style = LiquidType.caption,
                color = colors.contentTertiary,
                modifier = Modifier.padding(top = 12.dp).align(Alignment.CenterHorizontally),
            )
        }
    }
}

internal val BriefBlue = Color(0xFF0A84FF)

@Composable
private fun SheetLabel(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(Modifier.padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Liquid.colors.contentSecondary, modifier = Modifier.size(16.dp))
        Text(text.uppercase(), style = LiquidType.caption.copy(fontWeight = FontWeight.SemiBold), color = Liquid.colors.contentSecondary, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun PermissionHint(text: String, action: String, onClick: () -> Unit) {
    val colors = Liquid.colors
    Row(
        Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(LiquidRadius.control))
            .background(colors.warning.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = LiquidType.footnote, color = colors.content, modifier = Modifier.weight(1f))
        Text(
            action,
            style = LiquidType.subhead.copy(fontWeight = FontWeight.SemiBold),
            color = colors.accent,
            modifier = Modifier
                .padding(start = 10.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
