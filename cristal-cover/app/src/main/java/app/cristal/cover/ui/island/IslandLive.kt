package app.cristal.cover.ui.island

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.NotificationAdd
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import app.cristal.cover.data.island.BriefSuggestion
import app.cristal.cover.data.brief.BriefLabels
import androidx.compose.material.icons.rounded.Check
import app.cristal.cover.data.island.LiveActivityData
import app.cristal.cover.data.island.LiveCategory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

internal val BriefBlue = Color(0xFF0A84FF)

/** Cor da atividade: a da notificação do app, ou uma por categoria. */
internal fun liveColor(live: LiveActivityData): Color {
    val c = live.item.color
    if (c != 0 && android.graphics.Color.alpha(c) > 0) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c, hsv)
        hsv[1] = hsv[1].coerceAtLeast(0.45f)
        hsv[2] = hsv[2].coerceAtLeast(0.9f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }
    return when (live.category) {
        LiveCategory.DELIVERY -> Color(0xFFFF7A45)
        LiveCategory.RIDE -> Color(0xFFE8E8EE)
        LiveCategory.NAVIGATION -> Color(0xFF5AB0FF)
        LiveCategory.PROGRESS, LiveCategory.GENERIC -> Color(0xFFB9A2FF)
    }
}

private fun liveIcon(live: LiveActivityData): ImageVector = when (live.category) {
    LiveCategory.DELIVERY -> Icons.Rounded.LocalShipping
    LiveCategory.RIDE -> Icons.Rounded.LocalTaxi
    LiveCategory.NAVIGATION -> Icons.Rounded.Navigation
    LiveCategory.PROGRESS, LiveCategory.GENERIC -> Icons.Rounded.Sync
}

/** Ícone do app (ou o da categoria, se o app não tiver ícone carregado). */
@Composable
internal fun LiveAppIcon(live: LiveActivityData, size: androidx.compose.ui.unit.Dp) {
    val icon = live.item.appIcon
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(Color(0xFF2C2C2E)), contentAlignment = Alignment.Center) {
        if (icon != null) {
            Image(icon, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(liveIcon(live), contentDescription = null, tint = liveColor(live), modifier = Modifier.size(size * 0.62f))
        }
    }
}

/** Compacto: ícone do app à esquerda, o tempo ou a etapa que o app escreveu à direita. */
@Composable
internal fun LiveCompact(live: LiveActivityData) {
    val color = liveColor(live)
    Row(Modifier.fillMaxSize().padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        LiveAppIcon(live, 22.dp)
        Spacer(Modifier.weight(1f))
        val trailing = live.eta ?: live.stage?.let { live.stages.getOrNull(it) } ?: live.fraction?.let { "${(it * 100).toInt()}%" }
        if (trailing != null) {
            Text(trailing, style = islandText(14.sp, FontWeight.SemiBold), color = color, maxLines = 1)
        } else {
            Icon(liveIcon(live), contentDescription = null, tint = color, modifier = Modifier.size(17.dp))
        }
    }
}

/** Mínima: só o ícone do app. */
@Composable
internal fun LiveMinimal(live: LiveActivityData) = LiveAppIcon(live, 20.dp)

/**
 * Medidas de dentro da lente, iguais em todas as atividades: o cabeçalho centrado no topo preto, a
 * faixa de luz logo abaixo e o corpo com folgas que acompanham a curva da lente (a última linha
 * entra um pouco mais, porque a lente estreita embaixo).
 */
internal object LensLayout {
    val HEADER_TOP = 17.dp
    val HEADER_HEIGHT = 22.dp
    val HEADER_MAX = 228.dp
    val BAND = 46.dp

    /** Do fim do cabeçalho até o corpo (passa pela faixa de luz). */
    val BODY_GAP = 15.dp
    val SIDE = 34.dp
    val LAST_ROW = 10.dp
    val BOTTOM = 18.dp

    /** Altura da faixa de luz em fração da altura da lente. */
    fun band(height: Dp): Float = if (height.value > 0f) (BAND / height).coerceIn(0.12f, 0.5f) else 0.3f
}

/** Cabeçalho da lente: um ícone, o nome e um rótulo pequeno opcional, centrados no topo preto. */
@Composable
internal fun LensHeader(
    leading: @Composable () -> Unit,
    label: String,
    chip: String?,
    labelColor: Color = Color.White.copy(alpha = 0.8f),
) {
    Box(
        Modifier.fillMaxWidth().padding(top = LensLayout.HEADER_TOP).height(LensLayout.HEADER_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        Row(Modifier.widthIn(max = LensLayout.HEADER_MAX), verticalAlignment = Alignment.CenterVertically) {
            leading()
            Text(
                label,
                style = islandText(12.5.sp, FontWeight.SemiBold),
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 7.dp).weight(1f, fill = false),
            )
            if (chip != null) {
                Box(
                    Modifier.padding(start = 7.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    Text(chip, style = islandText(10.5.sp, FontWeight.SemiBold), color = Color.White.copy(alpha = 0.85f), maxLines = 1)
                }
            }
        }
    }
}

/**
 * Expandida: app e fonte dos dados no topo; título e texto exatamente como o app escreveu, o tempo
 * e a barra (só quando o app publicou progresso, ou as etapas reconhecidas no texto do app).
 */
@Composable
internal fun LiveExpanded(live: LiveActivityData) {
    val color = liveColor(live)
    Column(Modifier.fillMaxSize()) {
        LensHeader(leading = { LiveAppIcon(live, 18.dp) }, label = live.item.appLabel, chip = live.source.label)
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = LensLayout.SIDE, end = LensLayout.SIDE, top = LensLayout.BODY_GAP, bottom = LensLayout.BOTTOM),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(live.item.title.ifBlank { live.item.appLabel }, style = islandText(16.sp, FontWeight.SemiBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (live.item.text.isNotBlank()) {
                        Text(live.item.text, style = islandText(13.sp, FontWeight.Normal), color = Color.White.copy(alpha = 0.64f), maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
                    }
                }
                live.eta?.let { Text(it, style = islandText(24.sp, FontWeight.SemiBold), color = color, modifier = Modifier.padding(start = 10.dp), maxLines = 1) }
            }
            Column(Modifier.padding(horizontal = LensLayout.LAST_ROW)) {
                val fraction = live.fraction
                when {
                    fraction != null -> LiveBar(fraction, live.points, color)
                    live.stages.isNotEmpty() && live.stage != null -> {
                        LiveBar((live.stage + 0.5f) / live.stages.size, live.stages.indices.map { (it + 0.5f) / live.stages.size }, color)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            live.stages.forEachIndexed { i, name ->
                                Text(
                                    name,
                                    style = islandText(10.5.sp, if (i == live.stage) FontWeight.SemiBold else FontWeight.Normal),
                                    color = Color.White.copy(alpha = if (i == live.stage) 1f else 0.5f),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    live.item.progressIndeterminate -> LiveBar(0.5f, emptyList(), color, indeterminate = true)
                    else -> Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}

@Composable
private fun LiveBar(fraction: Float, points: List<Float>, color: Color, indeterminate: Boolean = false) {
    Canvas(Modifier.fillMaxWidth().height(10.dp).semantics { contentDescription = "Progresso" }) {
        val h = 5.dp.toPx()
        val y = (size.height - h) / 2
        val r = CornerRadius(h / 2, h / 2)
        drawRoundRect(Color.White.copy(alpha = 0.16f), topLeft = Offset(0f, y), size = Size(size.width, h), cornerRadius = r)
        if (!indeterminate) drawRoundRect(color, topLeft = Offset(0f, y), size = Size(size.width * fraction, h), cornerRadius = r)
        for (p in points) {
            val cx = size.width * p
            drawCircle(Color.Black, 6.dp.toPx(), Offset(cx, size.height / 2))
            drawCircle(if (p <= fraction + 0.001f) color else Color(0xFF3A3744), 4.5.dp.toPx(), Offset(cx, size.height / 2))
        }
    }
}

// --- Info Brief --------------------------------------------------------------------------------

private fun briefTime(suggestion: BriefSuggestion): String? {
    val at = suggestion.whenMillis ?: return null
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(at)
}

private fun briefDay(suggestion: BriefSuggestion): String {
    val at = suggestion.whenMillis ?: return "Sem data"
    val then = Calendar.getInstance().apply { timeInMillis = at }
    val now = Calendar.getInstance()
    val sameYear = then.get(Calendar.YEAR) == now.get(Calendar.YEAR)
    val days = then.get(Calendar.DAY_OF_YEAR) - now.get(Calendar.DAY_OF_YEAR)
    val label = when {
        sameYear && days == 0 -> "Hoje"
        sameYear && days == 1 -> "Amanhã"
        else -> SimpleDateFormat("EEE, d 'de' MMM", Locale.forLanguageTag("pt-BR")).format(at).replaceFirstChar { it.uppercase() }
    }
    return if (suggestion.dateConfirmed) label else "$label?"
}

@Composable
internal fun BriefCompact(suggestion: BriefSuggestion) {
    Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Event, contentDescription = null, tint = BriefBlue, modifier = Modifier.size(18.dp))
        Spacer(Modifier.weight(1f))
        Text(
            listOfNotNull(briefTime(suggestion), suggestion.title).joinToString(" · "),
            style = islandText(13.sp, FontWeight.SemiBold),
            color = Color(0xFF9DCBFF),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Sugestão do Info Brief: o compromisso entendido e as duas ações. Nada é criado sem o toque. */
@Composable
internal fun BriefExpanded(
    suggestion: BriefSuggestion,
    onAccept: () -> Unit,
    onLater: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        LensHeader(
            leading = {
                Box(Modifier.size(18.dp).clip(CircleShape).background(if (suggestion.created) Color(0xFF30D158) else BriefBlue), contentAlignment = Alignment.Center) {
                    Icon(if (suggestion.created) Icons.Rounded.Check else Icons.Rounded.Event, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
            },
            label = if (suggestion.created) "Lembrete criado" else BriefLabels.found(suggestion.kind),
            chip = null,
            labelColor = if (suggestion.created) Color(0xFF8BE7A5) else Color(0xFF9DCBFF),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = LensLayout.SIDE, end = LensLayout.SIDE, top = LensLayout.BODY_GAP, bottom = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(suggestion.title, style = islandText(19.sp, FontWeight.SemiBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(briefDay(suggestion), style = islandText(13.sp, FontWeight.Normal), color = Color.White.copy(alpha = 0.64f), maxLines = 1)
                }
                briefTime(suggestion)?.let { Text(it, style = islandText(28.sp, FontWeight.SemiBold), color = Color.White) }
            }
            if (suggestion.created) {
                Text(
                    "Avisa na hora, aqui na ilha. Está também no Now Brief.",
                    style = islandText(12.5.sp, FontWeight.Normal),
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = LensLayout.LAST_ROW),
                    maxLines = 2,
                )
            } else {
                Row(Modifier.padding(horizontal = LensLayout.LAST_ROW), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BriefButton(BriefLabels.action(suggestion.kind), BriefBlue, Icons.Rounded.NotificationAdd, Modifier.weight(1.3f), onAccept)
                    BriefButton("Agora não", Color.White.copy(alpha = 0.14f), null, Modifier.weight(1f), onLater)
                }
            }
        }
    }
}

@Composable
private fun BriefButton(label: String, background: Color, icon: ImageVector?, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(38.dp)
            .clip(CircleShape)
            .background(background)
            .pointerInput(Unit) { detectTapGestures { onClick() } },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = islandText(14.sp, FontWeight.SemiBold), color = Color.White, maxLines = 1)
    }
}
