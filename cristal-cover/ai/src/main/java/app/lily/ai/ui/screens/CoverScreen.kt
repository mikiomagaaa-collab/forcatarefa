package app.lily.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lily.ai.data.MsgStatus
import app.lily.ai.data.Role
import app.lily.ai.engine.EngineStatus
import app.lily.ai.ui.LocalGraph
import app.lily.ai.ui.LocalPermissions
import app.lily.ai.ui.MainActivity
import app.lily.ai.ui.Nav
import app.lily.ai.ui.Route
import app.lily.ai.ui.theme.IconButton
import app.lily.ai.ui.theme.Lily
import app.lily.ai.ui.theme.LilyOrb
import app.lily.ai.ui.theme.Type
import app.lily.ai.ui.theme.press
import kotlinx.coroutines.delay

/**
 * A L.I.L.Y na tela externa do Razr: pensada para a voz. Toque no orbe, fale e ouça a resposta.
 * A conversa vai para o histórico e continua na tela interna quando o aparelho é aberto.
 */
@Composable
fun CoverScreen(nav: Nav) {
    val graph = LocalGraph.current
    val c = Lily.colors
    val context = LocalContext.current
    val permissions = LocalPermissions.current
    var generation by remember { mutableIntStateOf(0) }
    val session = remember(generation) {
        nav.retain(Nav.COVER_KEY, { ChatSession(graph, context.applicationContext, Route.Chat()) }, ChatSession::dispose)
    }
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
            delay(3_000)
            session.notice = null
        }
    }

    fun listen() {
        if (listening) {
            listening = false
            return
        }
        permissions.request(MainActivity.MICROPHONE) { ok -> if (ok) listening = true else session.notice = "Permita o microfone para falar com a L.I.L.Y." }
    }

    val lastUserIndex = session.messages.indexOfLast { it.role == Role.USER }
    val lastUser = session.messages.getOrNull(lastUserIndex)
    // Só a resposta desta pergunta (a anterior não aparece embaixo da pergunta nova).
    val lastAnswer = session.messages.lastOrNull { it.role == Role.ASSISTANT }?.takeIf { session.messages.indexOf(it) > lastUserIndex }
    val streaming = session.streaming

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 14.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val model = when (val e = engine) {
                is EngineStatus.Loading -> "Carregando ${e.model.name}"
                is EngineStatus.Busy -> e.model.name
                is EngineStatus.Ready -> e.model.name
                EngineStatus.Idle -> "No aparelho"
            }
            Text("L.I.L.Y · $model", style = Type.caption, color = c.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (session.messages.isNotEmpty() && !session.busy) {
                IconButton(Icons.Rounded.Add, "Nova pergunta", {
                    nav.release<ChatSession>(Nav.COVER_KEY)?.dispose()
                    generation++
                }, size = 34.dp)
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                listening || (lastUser == null && streaming == null) -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.press({ listen() })) { app.lily.ai.avatar.PresenceAvatar(118.dp, presentation = app.lily.ai.avatar.AvatarPresentation.FACE, listeningLevel = voice.level) }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when {
                            listening -> voice.partial.ifBlank { if (followUp) "Pode continuar: estou ouvindo…" else "Ouvindo…" }
                            session.notice != null -> session.notice.orEmpty()
                            else -> "Toque no orbe e fale"
                        },
                        style = Type.callout,
                        color = c.content,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!listening && session.notice == null) {
                        Text("Abra o aparelho para a conversa completa.", style = Type.caption, color = c.tertiary, textAlign = TextAlign.Center)
                    }
                }
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    lastUser?.let { Text(it.text, style = Type.footnote, color = c.secondary, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                    Spacer(Modifier.height(8.dp))
                    when {
                        streaming != null && streaming.text.isEmpty() -> Row(verticalAlignment = Alignment.CenterVertically) {
                            TypingDots()
                            Spacer(Modifier.size(8.dp))
                            Text(if (engine is EngineStatus.Loading) "Carregando o modelo…" else "Pensando…", style = Type.footnote, color = c.secondary)
                        }
                        streaming != null -> RichText(streaming.text, style = Type.callout)
                        lastAnswer != null && lastAnswer.status == MsgStatus.ERROR -> Text(lastAnswer.text + " Abra o aparelho para resolver.", style = Type.callout, color = c.warning)
                        lastAnswer != null -> RichText(lastAnswer.text, style = Type.callout)
                    }
                    session.notice?.let { Text(it, style = Type.caption, color = c.warning, modifier = Modifier.padding(top = 6.dp)) }
                }
            }
        }

        if (typing) {
            Composer(
                text = session.draft,
                onText = { session.draft = it },
                onSend = {
                    session.send()
                    typing = false
                },
                placeholder = "Digite",
                busy = session.busy,
                onStop = session::stop,
            )
            Spacer(Modifier.height(4.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(Icons.Rounded.Keyboard, if (typing) "Fechar o teclado" else "Digitar", { typing = !typing }, size = 42.dp)
            // O microfone fica sempre disponível: falar durante uma resposta faz outra pergunta.
            IconButton(Icons.Rounded.Mic, if (listening) "Parar de ouvir" else if (session.busy) "Fazer outra pergunta" else "Falar", { listen() }, size = 54.dp, tint = c.onAccent, filled = false, modifier = Modifier.background(c.accent, CircleShape))
            if (session.busy) IconButton(Icons.Rounded.Stop, "Parar a resposta", session::stop, size = 42.dp)
            val answer = lastAnswer?.takeIf { it.status == MsgStatus.DONE }
            IconButton(
                Icons.AutoMirrored.Rounded.VolumeUp,
                if (answer != null && session.speakingId == answer.id) "Parar a leitura" else "Ler a resposta",
                { answer?.let(session::speak) },
                size = 42.dp,
                enabled = answer != null,
            )
        }
    }
}
