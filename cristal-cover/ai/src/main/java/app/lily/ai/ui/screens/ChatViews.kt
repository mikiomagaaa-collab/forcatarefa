package app.lily.ai.ui.screens

import android.media.MediaPlayer
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.lily.ai.data.ChatMessage
import app.lily.ai.data.MsgStatus
import app.lily.ai.engine.EngineStatus
import app.lily.ai.ui.theme.ButtonKind
import app.lily.ai.ui.theme.IconButton
import app.lily.ai.ui.theme.Lily
import app.lily.ai.ui.theme.LilyOrb
import app.lily.ai.ui.theme.PillButton
import app.lily.ai.ui.theme.Type
import app.lily.ai.ui.theme.glass
import app.lily.ai.ui.theme.press
import app.lily.shared.brief.BriefCandidate
import app.lily.shared.brief.BriefText
import java.io.File
import java.util.Locale

private val BriefBlue = Color(0xFF0A84FF)

/** A mensagem de quem usa: anexos, o texto num balão e, se houver, o compromisso reconhecido. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UserMessage(msg: ChatMessage, brief: BriefCandidate?, onLongPress: () -> Unit, onBrief: () -> Unit) {
    val c = Lily.colors
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        msg.attachments.forEach { a ->
            when (a.kind) {
                "image" -> AttachedImage(a.path, Modifier.padding(bottom = 6.dp))
                "audio" -> AudioChip(File(a.path), "Áudio", ChatSession.wavSeconds(File(a.path)), Modifier.padding(bottom = 6.dp))
                "document" -> DocumentChip(a.name ?: "Documento", File(a.path).length(), Modifier.padding(bottom = 6.dp))
            }
        }
        if (msg.text.isNotBlank()) {
            Box(
                Modifier
                    .widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 6.dp))
                    .background(Brush.linearGradient(listOf(c.accent.copy(alpha = 0.34f), c.accent2.copy(alpha = 0.20f))))
                    .border(1.dp, c.border, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 6.dp))
                    .combinedClickable(onClick = {}, onLongClick = onLongPress, onLongClickLabel = "Opções da mensagem")
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            ) {
                Text(msg.text, style = Type.body, color = c.content)
            }
        }
        if (brief != null) {
            Spacer(Modifier.height(6.dp))
            BriefChip(brief, onBrief)
        }
    }
}

/** "Compromisso identificado: Show · Hoje 19:00 · Criar lembrete" (só cria depois da confirmação no Cristal). */
@Composable
fun BriefChip(c: BriefCandidate, onClick: () -> Unit) {
    val colors = Lily.colors
    Row(
        Modifier
            .clip(CircleShape)
            .background(BriefBlue.copy(alpha = 0.14f))
            .border(1.dp, BriefBlue.copy(alpha = 0.35f), CircleShape)
            .press(onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Event, contentDescription = null, tint = BriefBlue, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("${BriefText.found(c.kind)}: ${BriefText.summary(c)}", style = Type.caption, color = colors.content, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Text(" · ${BriefText.action(c.kind)}", style = Type.caption.copy(fontWeight = FontWeight.SemiBold), color = BriefBlue, maxLines = 1)
    }
}

/** Cabeçalho das respostas: o orbe, o nome e o mecanismo que respondeu. */
@Composable
private fun AnswerHeader(model: String?, energy: Float, tool: String? = null) {
    val c = Lily.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        LilyOrb(22.dp, energy = energy)
        Spacer(Modifier.width(6.dp))
        Text("L.I.L.Y", style = Type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.content)
        val detail = listOfNotNull(tool, model).joinToString(" · ")
        if (detail.isNotEmpty()) Text(" · $detail", style = Type.caption, color = c.tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun AssistantMessage(
    msg: ChatMessage,
    last: Boolean,
    speaking: Boolean,
    onSpeak: () -> Unit,
    onRegenerate: () -> Unit,
    onMore: () -> Unit,
    onHub: (String) -> Unit,
) {
    val c = Lily.colors
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(end = 12.dp)) {
        AnswerHeader(msg.model, 0f, msg.tool)
        Spacer(Modifier.height(8.dp))
        if (msg.status == MsgStatus.ERROR) {
            Column(Modifier.fillMaxWidth().glass(c, RoundedCornerShape(20.dp)).padding(14.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = c.danger, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(msg.text, style = Type.callout, color = c.content)
                }
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    msg.install?.let { PillButton("Abrir o Model Hub", { onHub(it) }, kind = ButtonKind.TINTED, height = 38.dp) }
                    if (last) PillButton("Tentar de novo", onRegenerate, kind = ButtonKind.GLASS, height = 38.dp)
                }
            }
        } else {
            SelectionContainer { RichText(msg.text) }
            if (msg.status == MsgStatus.STOPPED) Text("Resposta interrompida", style = Type.caption, color = c.tertiary, modifier = Modifier.padding(top = 6.dp))
            msg.install?.let {
                Spacer(Modifier.height(10.dp))
                PillButton("Abrir o Model Hub", { onHub(it) }, kind = ButtonKind.TINTED, height = 38.dp)
            }
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(Icons.Rounded.ContentCopy, "Copiar", { copyToClipboard(context, msg.text) }, size = 36.dp, filled = false, tint = c.secondary)
                IconButton(if (speaking) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.VolumeUp, if (speaking) "Parar a leitura" else "Ler em voz alta", onSpeak, size = 36.dp, filled = false, tint = if (speaking) c.accent else c.secondary)
                IconButton(Icons.Rounded.Share, "Compartilhar", { shareText(context, msg.text) }, size = 36.dp, filled = false, tint = c.secondary)
                if (last) IconButton(Icons.Rounded.Refresh, "Gerar de novo", onRegenerate, size = 36.dp, filled = false, tint = c.secondary)
                IconButton(Icons.Rounded.MoreHoriz, "Mais", onMore, size = 36.dp, filled = false, tint = c.secondary)
                Spacer(Modifier.weight(1f))
                msg.millis?.let { Text(seconds(it), style = Type.caption, color = c.tertiary) }
            }
        }
    }
}

/** A resposta chegando: antes do primeiro pedaço, o que está acontecendo de verdade (carregando, olhando a imagem…). */
@Composable
fun StreamingMessage(s: Streaming, status: EngineStatus) {
    val c = Lily.colors
    Column(Modifier.fillMaxWidth().padding(end = 12.dp)) {
        AnswerHeader(s.model, 1f)
        Spacer(Modifier.height(8.dp))
        if (s.text.isEmpty()) {
            val phase = when {
                status is EngineStatus.Loading -> "Carregando ${status.model.name} (${status.accel.label}). A primeira vez leva alguns segundos."
                s.model == "ML Kit" -> "Analisando a imagem com o ML Kit…"
                s.audio -> "Ouvindo o áudio…"
                s.image -> "Olhando a imagem…"
                else -> "Pensando…"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypingDots()
                Spacer(Modifier.width(10.dp))
                Text(phase, style = Type.footnote, color = c.secondary)
            }
        } else {
            RichText(s.text)
            Spacer(Modifier.height(6.dp))
            TypingDots()
        }
    }
}

@Composable
fun TypingDots(color: Color = Lily.colors.accent) {
    val t = rememberInfiniteTransition(label = "pontos")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { i ->
            val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(520, delayMillis = i * 140), RepeatMode.Reverse), label = "p$i")
            Box(Modifier.size(7.dp).graphicsLayer { alpha = a; scaleX = 0.8f + 0.2f * a; scaleY = 0.8f + 0.2f * a }.clip(CircleShape).background(color))
        }
    }
}

/** Uma imagem anexada; toque para ver inteira. */
@Composable
fun AttachedImage(path: String, modifier: Modifier = Modifier) {
    val bitmap = rememberFileBitmap(path, 720)
    var open by remember { mutableStateOf(false) }
    Box(modifier.widthIn(max = 240.dp).heightIn(max = 260.dp).clip(RoundedCornerShape(18.dp)).background(Lily.colors.glass).clickable { open = true }) {
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), contentDescription = "Imagem enviada", contentScale = ContentScale.Fit)
        } else {
            Box(Modifier.size(160.dp))
        }
    }
    if (open && bitmap != null) {
        Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { open = false }, contentAlignment = Alignment.Center) {
                val full = rememberFileBitmap(path, 2048) ?: bitmap
                Image(full.asImageBitmap(), contentDescription = "Imagem enviada", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** Um áudio com reprodução (só toca com um toque). */
@Composable
fun AudioChip(file: File, label: String, seconds: Int, modifier: Modifier = Modifier) {
    val c = Lily.colors
    var player by remember(file) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(file) { mutableStateOf(false) }
    DisposableEffect(file) {
        onDispose {
            player?.release()
            player = null
        }
    }
    Row(
        modifier.clip(CircleShape).background(c.accent.copy(alpha = 0.16f)).padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            if (playing) "Pausar" else "Ouvir",
            {
                val p = player ?: runCatching {
                    MediaPlayer().apply {
                        setDataSource(file.path)
                        prepare()
                        setOnCompletionListener {
                            playing = false
                            it.seekTo(0)
                        }
                    }
                }.getOrNull()?.also { player = it }
                if (p != null) {
                    if (playing) p.pause() else p.start()
                    playing = !playing
                }
            },
            size = 34.dp,
            tint = c.accent,
            filled = false,
        )
        Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("$label · ${seconds}s", style = Type.footnote, color = c.content)
    }
}

/** Um documento anexado (o texto foi extraído no aparelho). */
@Composable
fun DocumentChip(name: String, bytes: Long, modifier: Modifier = Modifier) {
    val c = Lily.colors
    Row(modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(16.dp)).background(c.accent2.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Description, contentDescription = null, tint = c.accent2, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(name, style = Type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Texto lido no aparelho · ${if (bytes >= 1024) "${bytes / 1024} KB" else "$bytes B"}", style = Type.caption, color = c.secondary)
        }
    }
}

/** "2,4 s" ou "1 min 12 s". */
fun seconds(millis: Long): String {
    val s = millis / 1000.0
    return if (s < 60) String.format(Locale.forLanguageTag("pt-BR"), "%.1f s", s) else "${(s / 60).toInt()} min ${(s % 60).toInt()} s"
}
