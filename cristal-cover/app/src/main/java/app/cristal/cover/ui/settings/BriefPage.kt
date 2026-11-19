package app.cristal.cover.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.cristal.cover.data.brief.BriefLabels
import app.cristal.cover.data.brief.BriefRecord
import app.cristal.cover.data.brief.Reminder
import app.cristal.cover.data.brief.ReminderNotifier
import app.cristal.cover.data.brief.ReminderScheduler
import app.cristal.cover.data.prefs.PrefKeys
import app.cristal.cover.data.prefs.Settings
import app.cristal.cover.ui.glass.LocalGlassSpec
import app.cristal.cover.ui.launcher.GlassButton
import app.cristal.cover.ui.launcher.SwitchRow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private const val KEYBOARD_PACKAGE = "app.lily.keyboard"

/** "Seg, 12 de jan · 19:00", "Hoje · 19:00" ou "Sem data". */
internal fun reminderWhen(at: Long?, now: Long = System.currentTimeMillis()): String {
    if (at == null) return "Sem data"
    val pt = Locale.forLanguageTag("pt-BR")
    val then = Calendar.getInstance().apply { timeInMillis = at }
    val today = Calendar.getInstance().apply { timeInMillis = now }
    val sameYear = then.get(Calendar.YEAR) == today.get(Calendar.YEAR)
    val days = then.get(Calendar.DAY_OF_YEAR) - today.get(Calendar.DAY_OF_YEAR)
    val day = when {
        sameYear && days == 0 -> "Hoje"
        sameYear && days == 1 -> "Amanhã"
        else -> SimpleDateFormat("EEE, d 'de' MMM", pt).format(at).replaceFirstChar { it.uppercase() }
    }
    return day + " · " + SimpleDateFormat("HH:mm", pt).format(at)
}

@Composable
internal fun BriefPage(vm: SettingsViewModel, settings: Settings, wallpaper: ImageBitmap?, onBack: () -> Unit) {
    val context = LocalContext.current
    val history by vm.briefHistory.collectAsStateWithLifecycle()
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    // As permissões mudam fora do app: relê ao voltar para a tela.
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) { owner.repeatOnLifecycle(Lifecycle.State.RESUMED) { tick++ } }
    val notificationsOk = remember(tick) {
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED && ReminderNotifier.enabled(context)
    }
    val exactOk = remember(tick) { ReminderScheduler.canExact(context) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val keyboardInstalled = remember(tick) { context.packageManager.getLaunchIntentForPackage(KEYBOARD_PACKAGE) != null }

    SettingsScaffold("Info Brief", settings, wallpaper, onBack) {
        item {
            SettingsCard {
                InfoText(
                    "O L.I.L.Y Keyboard reconhece compromissos, datas, tarefas e lugares no que você digita, no próprio " +
                        "aparelho, e oferece um lembrete na Liquid Island. Nada é criado sem a sua confirmação. Campos de " +
                        "senha, códigos e conversas anônimas nunca são analisados, e o texto não sai do teclado: o Cristal " +
                        "recebe só o compromisso entendido.",
                )
                SwitchRow(
                    title = "Receber sugestões do teclado",
                    checked = settings.briefEnabled,
                    onChange = { vm.set(PrefKeys.BRIEF_ENABLED, it) },
                    subtitle = "Desligado, o Cristal ignora o que o teclado mandar.",
                )
                GlassButton("Ver demonstração", Modifier.fillMaxWidth().padding(top = 6.dp)) { vm.briefDemo() }
            }
        }
        item {
            SettingsCard("Lembretes") {
                SwitchRow(
                    title = "Tocar como alarme",
                    checked = settings.briefAlarm,
                    onChange = { vm.set(PrefKeys.BRIEF_ALARM, it) },
                    subtitle = "Som de alarme que continua até você responder. Dá para mudar em cada lembrete.",
                )
                PermissionLine(
                    "Notificações",
                    if (notificationsOk) "Permitidas: o lembrete avisa na hora." else "Sem elas, o lembrete só aparece no Now Brief.",
                    notificationsOk,
                    "Permitir",
                ) { askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
                PermissionLine(
                    "Alarmes e lembretes",
                    if (exactOk) "Liberado: avisa no minuto exato." else "Sem liberar, o aviso pode atrasar alguns minutos.",
                    exactOk,
                    "Abrir",
                ) { openExactAlarmSettings(context) }
            }
        }
        item {
            SettingsCard("Próximos") {
                val upcoming = reminders.filter { !it.done }.sortedWith(compareBy(nullsLast()) { it.at })
                if (upcoming.isEmpty()) {
                    InfoText("Nenhum lembrete. Os que você criar pelo Info Brief ou pelo Now Brief aparecem aqui.")
                }
                upcoming.forEach { ReminderLine(it) { vm.deleteReminder(it.id) } }
            }
        }
        item {
            SettingsCard("Histórico de sugestões") {
                if (history.isEmpty()) InfoText("Ainda não houve sugestões.")
                history.take(30).forEach { HistoryLine(it) }
                if (history.isNotEmpty()) {
                    GlassButton("Limpar histórico", Modifier.fillMaxWidth().padding(top = 6.dp)) { vm.clearBriefHistory() }
                }
            }
        }
        item {
            SettingsCard("No teclado") {
                InfoText(
                    "Para ligar ou desligar o Info Brief, escolher o que ele reconhece e o destaque no texto, abra o " +
                        "L.I.L.Y Keyboard > Info Brief. A sugestão aparece 1,5 s depois de você parar de digitar e também " +
                        "ao enviar a mensagem.",
                )
                if (keyboardInstalled) {
                    NavRow(Icons.Rounded.Keyboard, "Abrir o L.I.L.Y Keyboard", "Info Brief, tipos reconhecidos e privacidade") {
                        context.packageManager.getLaunchIntentForPackage(KEYBOARD_PACKAGE)?.let { context.startActivity(it.putExtra("page", "brief")) }
                    }
                } else {
                    InfoText("O L.I.L.Y Keyboard não está instalado.")
                }
            }
        }
    }
}

private fun openExactAlarmSettings(context: Context) {
    runCatching {
        context.startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
    }
}

@Composable
private fun PermissionLine(title: String, subtitle: String, ok: Boolean, action: String, onClick: () -> Unit) {
    val ink = LocalGlassSpec.current.contentColor
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (ok) Color(0xFF30D158) else Color(0xFFFF9F0A)))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, color = ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = ink.copy(alpha = 0.65f), fontSize = 13.sp)
        }
        if (!ok) {
            Text(
                action,
                color = Color(0xFF0A84FF),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ReminderLine(r: Reminder, onDelete: () -> Unit) {
    val ink = LocalGlassSpec.current.contentColor
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(r.title, color = ink, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(reminderWhen(r.at) + if (r.alarm) " · alarme" else "", color = ink.copy(alpha = 0.65f), fontSize = 13.sp)
                if (r.weekly) Icon(Icons.Rounded.Repeat, contentDescription = "Toda semana", tint = ink.copy(alpha = 0.65f), modifier = Modifier.size(14.dp))
            }
        }
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(ink.copy(alpha = 0.1f))
                .clickable(onClick = onDelete)
                .semantics { contentDescription = "Apagar ${r.title}" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, contentDescription = null, tint = ink.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun HistoryLine(r: BriefRecord) {
    val ink = LocalGlassSpec.current.contentColor
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(r.title, color = ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(BriefLabels.found(r.kind), r.expression.takeIf { it.isNotBlank() }?.let { "“$it”" }).joinToString(" · "),
                color = ink.copy(alpha = 0.6f),
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val accepted = r.status == BriefRecord.ACCEPTED
        Text(
            BriefLabels.status(r.status),
            color = if (accepted) Color(0xFF30D158) else ink.copy(alpha = 0.6f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background((if (accepted) Color(0xFF30D158) else ink).copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
