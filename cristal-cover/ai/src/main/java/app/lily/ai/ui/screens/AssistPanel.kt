package app.lily.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lily.ai.data.MsgStatus
import app.lily.ai.data.Role
import app.lily.ai.engine.EngineStatus
import app.lily.ai.ui.LocalGraph
import app.lily.ai.ui.LocalPermissions
import app.lily.ai.ui.MainActivity
import app.lily.ai.ui.theme.ButtonKind
import app.lily.ai.ui.theme.IconButton
import app.lily.ai.ui.theme.Lily
import app.lily.ai.ui.theme.LilyOrb
import app.lily.ai.ui.theme.PillButton
import app.lily.ai.ui.theme.Type
import kotlinx.coroutines.delay

/** A lente da L.I.L.Y: corpo escuro de vidro com a faixa de luz no topo, como a da Liquid Island. */
private val LensBody = Color(0xF20E0D14)
private val LensShape = RoundedCornerShape(32.dp)

/**
 * O painel de voz: ouve ao abrir, mostra a pergunta, a resposta chegando e o modelo que respondeu.
 * Dá para falar de novo (acompanhamento na mesma conversa), digitar, ouvir a resposta e continuar
 * no app.
 */
@Composable
fun AssistPanel(
    session: ChatSession,
    listenTrigger: Int,
    onClose: () -> Unit,
    onOpenApp: (String?) -> Unit,
    onHub: () -> Unit,
) {
    val graph = LocalGraph.current
    val c = Lily.colors
    val permissions = LocalPermissions.current
    val engine by graph.engines.status.collectAsStateWithLifecycle()
    var listening by remember { mutableStateOf(false) }
    var followUp by remember { mutableStateOf(false) }
    var typing by remember { mutableStateOf(false) }
    val voice = rememberVoiceInput()

    // Conversa contínua: depois da resposta (e da leitura), ouve de novo para a próxima pergunta.
    FollowUpEffect(session) {
        if (!listening && !typing && !session.busy && permissions.has(MainActivity.MICROPHONE)) {
            followUp = true
            listening = true
        }
    }

    // Chamar de novo (ilha, widget, assistente) sempre abre o microfone, até com uma resposta saindo:
    // a pergunta nova interrompe a anterior.
    LaunchedEffect(listenTrigger) {
        if (listenTrigger > 0) {
            permissions.request(MainActivity.MICROPHONE) { ok ->
                if (ok) listening = true else session.notice = "Permita o microfone para falar com a L.I.L.Y (ou digite)."
            }
        }
    }
    // Ouvir cala a leitura da resposta anterior (a L.I.L.Y não se ouve nem continua a outra resposta).
    LaunchedEffect(listening) { if (listening) session.stopSpeaking() }
    ListenEffect(
        voice,
        listening,
        onFinal = { text -> if (text.isNotBlank()) session.send(text, viaVoice = true) },
        onError = { if (!(followUp && it == app.lily.ai.engine.SpeechInput.NOTHING_HEARD)) session.notice = it },
        onEnd = {
            listening = false
            followUp = false
        },
    )
    LaunchedEffect(session.notice) {
        if (session.notice != null) {
            delay(4_000)
            session.notice = null
        }
    }

    val messages = session.messages
    val lastUserIndex = messages.indexOfLast { it.role == Role.USER }
    val lastUser = messages.getOrNull(lastUserIndex)
    val lastAnswer = messages.lastOrNull { it.role == Role.ASSISTANT }?.takeIf { messages.indexOf(it) > lastUserIndex }
    val streaming = session.streaming
    val status = when {
        listening && followUp -> "Pode continuar: estou ouvindo…"
        listening -> "Ouvindo…"
        engine is EngineStatus.Loading -> "Carregando ${(engine as EngineStatus.Loading).model.name}…"
        session.busy -> "Pensando · ${streaming?.model ?: "no aparelho"}"
        lastAnswer?.model != null -> "${lastAnswer.model} · no aparelho"
        else -> "No aparelho"
    }

    Box(Modifier.fillMaxSize().imePadding().pointerInput(Unit) { detectTapGestures { onClose() } }) {
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 6.dp)
                .fillMaxWidth(0.95f)
                .widthIn(max = 400.dp)
                .shadow(28.dp, LensShape)
                .clip(LensShape)
                .background(LensBody)
                .border(1.dp, Color(0x26FFFFFF), LensShape)
                .pointerInput(Unit) { detectTapGestures { } }
                .semantics { contentDescription = "L.I.L.Y" },
        ) {
            // A faixa de luz da lente (as cores do orbe).
            Box(Modifier.fillMaxWidth().height(3.dp).background(Brush.horizontalGradient(c.orb)))
            Column(Modifier.padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    app.lily.ai.avatar.PresenceAvatar(48.dp, presentation = app.lily.ai.avatar.AvatarPresentation.FACE, listeningLevel = voice.level)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("L.I.L.Y", style = Type.headline, color = Color.White)
                        Text(status, style = Type.caption, color = Color.White.copy(alpha = 0.62f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(Icons.Rounded.Close, "Fechar", onClose, size = 36.dp, filled = false, tint = Color.White.copy(alpha = 0.8f))
                }
                val question = if (listening) voice.partial.ifBlank { null } else lastUser?.text
                if (question != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(question, style = Type.callout, color = Color.White.copy(alpha = 0.72f), maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = 6.dp))
                }
                Box(Modifier.fillMaxWidth().heightIn(max = 320.dp).padding(top = 8.dp, end = 6.dp).verticalScroll(rememberScrollState())) {
                    when {
                        streaming != null && streaming.text.isEmpty() -> Row(verticalAlignment = Alignment.CenterVertically) {
                            TypingDots()
                            Spacer(Modifier.width(8.dp))
                            Text(if (engine is EngineStatus.Loading) "Carregando o modelo…" else "Pensando…", style = Type.footnote, color = Color.White.copy(alpha = 0.6f))
                        }
                        streaming != null -> RichText(streaming.text, color = Color.White, style = Type.callout)
                        lastAnswer != null && lastAnswer.status == MsgStatus.ERROR -> Column {
                            Text(lastAnswer.text, style = Type.callout, color = c.warning)
                            if (lastAnswer.install != null) {
                                Spacer(Modifier.height(8.dp))
                                PillButton("Abrir o Model Hub", onHub, kind = ButtonKind.TINTED, height = 36.dp)
                            }
                        }
                        lastAnswer != null -> RichText(lastAnswer.text, color = Color.White, style = Type.callout)
                        !listening && lastUser == null -> Text("Toque no microfone e fale, ou digite.", style = Type.footnote, color = Color.White.copy(alpha = 0.6f))
                    }
                }
                session.notice?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = Type.caption, color = c.warning)
                }
                if (typing) {
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.padding(end = 6.dp)) {
                        Composer(
                            text = session.draft,
                            onText = { session.draft = it },
                            onSend = {
                                session.send()
                                typing = false
                            },
                            placeholder = "Pergunte qualquer coisa",
                            busy = session.busy,
                            onStop = session::stop,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(Icons.Rounded.Keyboard, if (typing) "Fechar o teclado" else "Digitar", { typing = !typing }, size = 40.dp, tint = Color.White)
                    // O microfone fica sempre disponível: falar durante uma resposta faz outra pergunta.
                    val micAction = {
                        if (listening) {
                            listening = false
                        } else {
                            permissions.request(MainActivity.MICROPHONE) { ok -> if (ok) listening = true else session.notice = "Permita o microfone para falar com a L.I.L.Y." }
                        }
                    }
                    Box(Modifier.size(48.dp).clip(CircleShape).background(Brush.linearGradient(c.orb.take(2)))) {
                        IconButton(Icons.Rounded.Mic, if (listening) "Parar de ouvir" else if (session.busy) "Fazer outra pergunta" else "Falar", micAction, size = 48.dp, tint = Color.White, filled = false)
                    }
                    if (session.busy) IconButton(Icons.Rounded.Stop, "Parar a resposta", { session.stop() }, size = 40.dp, tint = Color.White)
                    val answer = lastAnswer?.takeIf { it.status == MsgStatus.DONE }
                    IconButton(
                        Icons.AutoMirrored.Rounded.VolumeUp,
                        if (answer != null && session.speakingId == answer.id) "Parar a leitura" else "Ler a resposta",
                        { answer?.let(session::speak) },
                        size = 40.dp,
                        tint = Color.White,
                        enabled = answer != null,
                    )
                    Spacer(Modifier.weight(1f))
                    if (session.conversationId != null) {
                        PillButton("Abrir na L.I.L.Y", { onOpenApp(session.conversationId) }, kind = ButtonKind.GLASS, height = 38.dp)
                    }
                }
            }
        }
    }
}
