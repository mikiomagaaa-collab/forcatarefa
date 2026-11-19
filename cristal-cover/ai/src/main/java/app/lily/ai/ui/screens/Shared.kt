package app.lily.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lily.ai.core.DeviceSnapshot
import app.lily.ai.data.ConversationMeta
import app.lily.ai.data.Project
import app.lily.ai.models.Catalog
import app.lily.ai.models.Compat
import app.lily.ai.models.Compatibility
import app.lily.ai.models.ModelSpec
import app.lily.ai.ui.theme.IconButton
import app.lily.ai.ui.theme.Lily
import app.lily.ai.ui.theme.Type
import app.lily.ai.ui.theme.glass
import app.lily.ai.ui.theme.press
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PT = Locale.forLanguageTag("pt-BR")

/** Uma conversa na lista: título, prévia, projeto e quando. */
@Composable
fun ConversationRow(meta: ConversationMeta, project: Project?, onClick: () -> Unit, onMore: (() -> Unit)? = null) {
    val c = Lily.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).glass(c, RoundedCornerShape(20.dp)).press(onClick).padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (meta.pinned) {
                    Icon(Icons.Rounded.PushPin, contentDescription = "Fixada", tint = c.accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(meta.title, style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            }
            if (meta.preview.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(meta.preview.replace('\n', ' '), style = Type.footnote, color = c.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (project != null) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(project.color.toInt())))
                    Spacer(Modifier.width(5.dp))
                    Text(project.name, style = Type.caption, color = c.secondary)
                    Text(" · ", style = Type.caption, color = c.tertiary)
                }
                Text(whenText(meta.updatedAt), style = Type.caption, color = c.tertiary)
            }
        }
        if (onMore != null) IconButton(Icons.Rounded.MoreHoriz, "Opções da conversa", onMore, size = 36.dp, filled = false, tint = c.secondary)
    }
}

/** "14:32", "Ontem", "seg., 3 nov." */
fun whenText(millis: Long): String {
    val zone = ZoneId.systemDefault()
    val at = Instant.ofEpochMilli(millis).atZone(zone)
    val today = LocalDate.now(zone)
    return when (at.toLocalDate()) {
        today -> DateTimeFormatter.ofPattern("HH:mm", PT).format(at)
        today.minusDays(1) -> "Ontem"
        else -> DateTimeFormatter.ofPattern(if (at.year == today.year) "EEE, d MMM" else "d MMM yyyy", PT).format(at)
    }
}

/** Seções do histórico. */
fun dateGroup(millis: Long): String {
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when {
        day == today -> "Hoje"
        day == today.minusDays(1) -> "Ontem"
        day.isAfter(today.minusDays(7)) -> "Últimos 7 dias"
        day.isAfter(today.minusDays(30)) -> "Últimos 30 dias"
        else -> "Mais antigas"
    }
}

@Composable
fun SearchField(query: String, onQuery: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val c = Lily.colors
    Row(
        modifier.fillMaxWidth().glass(c, CircleShape).padding(start = 14.dp, end = 4.dp).height(46.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = c.secondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text(placeholder, style = Type.callout, color = c.tertiary)
            BasicTextField(query, onQuery, singleLine = true, textStyle = Type.callout.copy(color = c.content), cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth())
        }
        if (query.isNotEmpty()) IconButton(Icons.Rounded.Close, "Limpar a busca", { onQuery("") }, size = 36.dp, filled = false, tint = c.secondary)
    }
}

/**
 * O primeiro modelo a instalar: o Qwen3 1.7B (bom português, menos de 1 GB) quando o aparelho o roda
 * sem limitações; senão, o menor. Avaliado com a memória e o espaço reais do aparelho.
 */
fun recommendedChat(device: DeviceSnapshot): ModelSpec {
    val order = listOf(Catalog.qwen3, Catalog.qwen3Small)
    fun fits(m: ModelSpec) = Compatibility.missingStorage(m, device) == 0L
    return order.firstOrNull { Compatibility.evaluate(it, device).level == Compat.COMPATIBLE && fits(it) }
        ?: order.firstOrNull { Compatibility.evaluate(it, device).usable && fits(it) }
        ?: Catalog.qwen3Small
}

/** Cores dos projetos (escolhidas em sequência). */
val ProjectColors = listOf(0xFFB79CFF, 0xFF64D2FF, 0xFF34D399, 0xFFFFB347, 0xFFFF6B7A, 0xFFFF9BD8, 0xFF9DE36B)
