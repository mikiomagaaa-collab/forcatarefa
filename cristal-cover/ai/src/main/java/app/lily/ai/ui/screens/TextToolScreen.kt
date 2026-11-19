package app.lily.ai.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lily.ai.AiGraph
import app.lily.ai.core.EngineChoice
import app.lily.ai.core.TaskKind
import app.lily.ai.engine.Attachments
import app.lily.ai.engine.AudioFiles
import app.lily.ai.engine.Prompts
import app.lily.ai.engine.SpeechInput
import app.lily.ai.engine.Tone
import app.lily.ai.engine.Translator
import app.lily.ai.engine.Vision
import app.lily.ai.engine.WritingStyle
import app.lily.ai.models.Category
import app.lily.ai.ui.LocalGraph
import app.lily.ai.ui.LocalPermissions
import app.lily.ai.ui.MainActivity
import app.lily.ai.ui.Nav
import app.lily.ai.ui.Route
import app.lily.ai.ui.theme.ButtonKind
import app.lily.ai.ui.theme.Chip
import app.lily.ai.ui.theme.GlassCard
import app.lily.ai.ui.theme.IconButton
import app.lily.ai.ui.theme.Lily
import app.lily.ai.ui.theme.LilyOrb
import app.lily.ai.ui.theme.PillButton
import app.lily.ai.ui.theme.SectionHeader
import app.lily.ai.ui.theme.TopBar
import app.lily.ai.ui.theme.Type
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** O estado de uma ferramenta de texto (guardado enquanto a tela estiver na pilha). */
@Stable
class TextToolState(private val graph: AiGraph, private val app: Context, start: Route.TextTool) {
    val task = start.task
    var input by mutableStateOf(start.text)
    var instruction by mutableStateOf("")
    var style by mutableStateOf(WritingStyle.NATURAL)
    var tone by mutableStateOf(Tone.NEUTRAL)
    var language by mutableStateOf("en")
    var output by mutableStateOf("")
    var engine by mutableStateOf<String?>(null)
    var running by mutableStateOf(false)
        private set
    var millis by mutableStateOf<Long?>(null)
        private set
    var error by mutableStateOf<String?>(null)
    var install by mutableStateOf<String?>(null)
        private set
    var audio by mutableStateOf<PendingAudio?>(null)
        private set
    var recordingLevel by mutableStateOf<Float?>(null)
        private set
    var speaking by mutableStateOf(false)
        private set
    private var job: Job? = null
    @Volatile private var stopRecording = false

    val languageName: String get() = Translator.name(language)

    fun run() {
        job?.cancel()
        error = null
        install = null
        millis = null
        when (task) {
            TaskKind.SPEAK -> speak()
            TaskKind.LANGUAGE_ID -> languageId()
            TaskKind.TRANSCRIBE_AUDIO -> transcribe()
            else -> textTask()
        }
    }

    fun stop() {
        job?.cancel()
        if (speaking) {
            graph.voice.stop()
            speaking = false
        }
    }

    private fun textTask() {
        val text = input.trim()
        if (text.isEmpty()) return
        val (plan, flow) = graph.tools.textTool(
            task,
            text,
            style,
            tone,
            languageName.lowercase(Locale.forLanguageTag("pt-BR")),
            language,
            instruction.trim().ifBlank { null },
        )
        val e = plan.engine
        if (e is EngineChoice.Unavailable) {
            error = e.reason
            install = (e.install ?: Category.CHAT).name
            return
        }
        engine = engineLabel(plan).first
        output = ""
        running = true
        val started = SystemClock.elapsedRealtime()
        job = graph.scope.launch {
            try {
                flow.collect { output = it }
                if (output.isBlank()) error = "O modelo não devolveu texto. Tente de novo."
            } catch (c: CancellationException) {
                // Parado por quem usa: fica o que já chegou.
            } catch (t: Throwable) {
                error = t.message ?: "A ferramenta falhou."
            } finally {
                running = false
                millis = SystemClock.elapsedRealtime() - started
            }
        }
    }

    private fun speak() {
        val text = input.trim()
        if (text.isEmpty()) return
        engine = "Voz do Android"
        speaking = true
        graph.speak(text) { speaking = false }
    }

    private fun languageId() {
        val text = input.trim()
        if (text.isEmpty()) return
        engine = "ML Kit"
        running = true
        job = graph.scope.launch {
            try {
                val code = Vision.language(text)
                output = if (code == "und") {
                    "Não deu para identificar o idioma com segurança."
                } else {
                    val pt = Locale.forLanguageTag("pt-BR")
                    Locale.forLanguageTag(code).getDisplayName(pt).replaceFirstChar { it.titlecase(pt) } + " ($code)"
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                error = t.message ?: "A identificação falhou."
            } finally {
                running = false
            }
        }
    }

    private fun transcribe() {
        val wav = audio?.file ?: return
        val plan = graph.tools.plan(TaskKind.TRANSCRIBE_AUDIO)
        val e = plan.engine
        if (e is EngineChoice.Unavailable) {
            error = e.reason + " (o reconhecimento de fala do Android só funciona com o microfone, então arquivos precisam de um modelo que ouve, como o Gemma 4 E2B)."
            install = Category.SPEECH.name
            return
        }
        engine = engineLabel(plan).first
        output = ""
        running = true
        val started = SystemClock.elapsedRealtime()
        job = graph.scope.launch {
            try {
                graph.tools.text(plan, Prompts.tool(TaskKind.TRANSCRIBE_AUDIO, ""), attachments = Attachments(audioFile = wav), temperature = 0.2).collect { output = it }
            } catch (c: CancellationException) {
                // Parado.
            } catch (t: Throwable) {
                error = t.message ?: "A transcrição falhou."
            } finally {
                running = false
                millis = SystemClock.elapsedRealtime() - started
            }
        }
    }

    fun attachAudio(uri: Uri) {
        graph.scope.launch {
            try {
                val file = File(app.cacheDir, "tools/aud-${UUID.randomUUID()}.wav")
                withContext(Dispatchers.IO) { AudioFiles.toWav(app, uri, file) }
                audio?.file?.delete()
                audio = PendingAudio(file, "Áudio", ChatSession.wavSeconds(file))
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                error = "Não consegui ler este áudio."
            }
        }
    }

    fun startRecording() {
        if (recordingLevel != null) return
        stopRecording = false
        recordingLevel = 0f
        graph.scope.launch {
            try {
                val file = File(app.cacheDir, "tools/rec-${UUID.randomUUID()}.wav")
                withContext(Dispatchers.IO) { AudioFiles.record(file, { stopRecording }) { recordingLevel = it } }
                audio?.file?.delete()
                audio = PendingAudio(file, "Gravação", ChatSession.wavSeconds(file))
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                error = "A gravação falhou."
            } finally {
                recordingLevel = null
            }
        }
    }

    fun stopRecording() {
        stopRecording = true
    }

    fun dispose() {
        job?.cancel()
        stopRecording = true
        if (speaking) graph.voice.stop()
        audio?.file?.delete()
    }
}

private fun verb(task: TaskKind): String = when (task) {
    TaskKind.CORRECT -> "Corrigir"
    TaskKind.REWRITE -> "Reescrever"
    TaskKind.SUMMARIZE -> "Resumir"
    TaskKind.EXPAND -> "Expandir"
    TaskKind.TRANSLATE -> "Traduzir"
    TaskKind.EXPLAIN -> "Explicar"
    TaskKind.ORGANIZE -> "Organizar"
    TaskKind.GENERATE_TEXT -> "Escrever"
    TaskKind.REPLY -> "Sugerir resposta"
    TaskKind.LANGUAGE_ID -> "Identificar"
    TaskKind.SPEAK -> "Ler em voz alta"
    TaskKind.TRANSCRIBE_AUDIO -> "Transcrever"
    else -> "Executar"
}

private fun placeholder(task: TaskKind): String = when (task) {
    TaskKind.GENERATE_TEXT -> "O que você quer que eu escreva? Ex.: um convite para o churrasco de sábado"
    TaskKind.REPLY -> "Cole a mensagem que você recebeu"
    TaskKind.SPEAK -> "Cole ou escreva o texto para ouvir"
    else -> "Cole ou escreva o texto aqui"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextToolScreen(nav: Nav, key: Int, route: Route.TextTool) {
    val graph = LocalGraph.current
    val c = Lily.colors
    val context = LocalContext.current
    val permissions = LocalPermissions.current
    val state = remember(key) { nav.retain(key, { TextToolState(graph, context.applicationContext, route) }, TextToolState::dispose) }
    val task = state.task
    val store by graph.store.state.collectAsStateWithLifecycle()
    val settings by graph.settings.value.collectAsStateWithLifecycle()
    val plan = remember(store, settings.profile, task) { graph.tools.plan(task) }
    var listening by remember { mutableStateOf(false) }
    val voice = rememberVoiceInput()
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(state::attachAudio) }
    val onDevice = remember { SpeechInput.onDevice(context) }

    ListenEffect(
        voice,
        listening,
        onFinal = { text -> if (text.isNotBlank()) state.output = listOf(state.output, text).filter { it.isNotBlank() }.joinToString("\n") },
        onError = { state.error = it },
        onEnd = { listening = false },
    )

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar(task.label, onBack = { nav.back() }, subtitle = when (task) {
            TaskKind.TRANSCRIBE_MIC -> if (onDevice) "Reconhecimento do Android · no aparelho" else "Reconhecimento do Android · pode usar a rede"
            else -> engineLabel(plan).first + " · no aparelho"
        })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            when (task) {
                TaskKind.TRANSCRIBE_MIC -> {
                    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.heightIn(min = 150.dp), contentAlignment = Alignment.Center) {
                            app.lily.ai.avatar.PresenceAvatar(150.dp, listeningLevel = voice.level)
                        }
                        Text(
                            if (listening) voice.partial.ifBlank { "Ouvindo…" } else "Toque no microfone e fale. Ele para sozinho quando você termina.",
                            style = Type.callout,
                            color = c.secondary,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        PillButton(if (listening) "Parar" else "Ditar", {
                            if (listening) {
                                listening = false
                            } else {
                                permissions.request(MainActivity.MICROPHONE) { ok -> if (ok) listening = true else state.error = "Sem a permissão do microfone não dá para ditar." }
                            }
                        }, icon = Icons.Rounded.Mic)
                    }
                }
                TaskKind.TRANSCRIBE_AUDIO -> {
                    GlassCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("Um áudio de até 30 segundos", style = Type.headline, color = c.content)
                        Text("Arquivos do WhatsApp, gravações e músicas faladas. A transcrição roda no aparelho com um modelo que ouve.", style = Type.footnote, color = c.secondary)
                        Spacer(Modifier.height(12.dp))
                        state.audio?.let { a ->
                            AudioChip(a.file, a.label, a.seconds)
                            Spacer(Modifier.height(12.dp))
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PillButton("Arquivo de áudio", { pickAudio.launch("audio/*") }, kind = ButtonKind.GLASS, icon = Icons.Rounded.AudioFile, height = 40.dp)
                            if (state.recordingLevel != null) {
                                PillButton("Parar a gravação", state::stopRecording, kind = ButtonKind.TINTED, height = 40.dp)
                            } else {
                                PillButton("Gravar", {
                                    permissions.request(MainActivity.MICROPHONE) { ok -> if (ok) state.startRecording() else state.error = "Sem a permissão do microfone não dá para gravar." }
                                }, kind = ButtonKind.GLASS, icon = Icons.Rounded.Mic, height = 40.dp)
                            }
                        }
                    }
                }
                else -> {
                    GlassCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Box(Modifier.fillMaxWidth().heightIn(min = 120.dp)) {
                            if (state.input.isEmpty()) Text(placeholder(task), style = Type.body, color = c.tertiary)
                            BasicTextField(
                                state.input,
                                { state.input = it },
                                textStyle = Type.body.copy(color = c.content),
                                cursorBrush = SolidColor(c.accent),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${state.input.length} caracteres", style = Type.caption, color = c.tertiary, modifier = Modifier.weight(1f))
                            IconButton(Icons.Rounded.ContentPaste, "Colar", {
                                val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                                clip?.getItemAt(0)?.coerceToText(context)?.toString()?.let { state.input = it }
                            }, size = 36.dp, filled = false, tint = c.secondary)
                        }
                    }
                    if (task == TaskKind.REPLY) {
                        Spacer(Modifier.height(10.dp))
                        SearchlessField(state.instruction, { state.instruction = it }, "O que você quer responder? (opcional)")
                    }
                }
            }

            // Opções de cada ferramenta.
            if (task == TaskKind.REWRITE) {
                SectionHeader("Tom")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tone.entries.forEach { t -> Chip(t.label, state.tone == t, { state.tone = t }) }
                }
            }
            if (task in setOf(TaskKind.CORRECT, TaskKind.REWRITE, TaskKind.EXPAND, TaskKind.REPLY, TaskKind.GENERATE_TEXT)) {
                SectionHeader("Jeito de escrever")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Natural", state.style == WritingStyle.NATURAL, { state.style = WritingStyle.NATURAL })
                    Chip("Informal (mantém vc, pq, tbm)", state.style == WritingStyle.INFORMAL, { state.style = WritingStyle.INFORMAL })
                    Chip("Formal", state.style == WritingStyle.FORMAL, { state.style = WritingStyle.FORMAL })
                }
            }
            if (task == TaskKind.TRANSLATE) {
                SectionHeader("Traduzir para")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Translator.languages.forEach { (code, name) -> Chip(name, state.language == code, { state.language = code }) }
                }
            }

            Spacer(Modifier.height(16.dp))
            if (task != TaskKind.TRANSCRIBE_MIC) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.running || state.speaking) {
                        PillButton(if (state.speaking) "Parar a leitura" else "Parar", state::stop, kind = ButtonKind.TINTED)
                    } else {
                        val ready = if (task == TaskKind.TRANSCRIBE_AUDIO) state.audio != null && state.recordingLevel == null else state.input.isNotBlank()
                        PillButton(verb(task), state::run, enabled = ready)
                    }
                }
            }

            state.error?.let { e ->
                Spacer(Modifier.height(14.dp))
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(e, style = Type.callout, color = c.content)
                    state.install?.let { cat ->
                        Spacer(Modifier.height(10.dp))
                        PillButton("Abrir o Model Hub", { nav.go(Route.Hub(cat)) }, kind = ButtonKind.TINTED, height = 38.dp)
                    }
                }
            }

            if (state.output.isNotEmpty() || state.running && task != TaskKind.SPEAK) {
                SectionHeader("Resultado")
                GlassCard(Modifier.fillMaxWidth(), strong = true) {
                    Text(
                        listOfNotNull(state.engine, state.millis?.let { seconds(it) }, "no aparelho").joinToString(" · "),
                        style = Type.caption,
                        color = c.tertiary,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (state.output.isEmpty()) {
                        TypingDots()
                    } else {
                        SelectionContainer { RichText(state.output) }
                        if (state.running) {
                            Spacer(Modifier.height(6.dp))
                            TypingDots()
                        }
                    }
                    if (!state.running && state.output.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(Icons.Rounded.ContentCopy, "Copiar", { copyToClipboard(context, state.output) }, size = 38.dp, filled = false, tint = c.secondary)
                            IconButton(Icons.Rounded.Share, "Compartilhar", { shareText(context, state.output) }, size = 38.dp, filled = false, tint = c.secondary)
                            IconButton(Icons.AutoMirrored.Rounded.VolumeUp, "Ler em voz alta", { graph.speak(Markdown.plain(state.output)) }, size = 38.dp, filled = false, tint = c.secondary)
                            if (task.isText || task == TaskKind.TRANSCRIBE_MIC || task == TaskKind.TRANSCRIBE_AUDIO) {
                                IconButton(Icons.Rounded.SwapVert, "Usar como texto de entrada", { state.input = state.output }, size = 38.dp, filled = false, tint = c.secondary)
                            }
                            IconButton(Icons.Rounded.Forum, "Continuar no chat", { nav.go(Route.Chat(draft = state.output)) }, size = 38.dp, filled = false, tint = c.secondary)
                        }
                        if (task == TaskKind.TRANSCRIBE_MIC || task == TaskKind.TRANSCRIBE_AUDIO) {
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PillButton("Resumir", { nav.go(Route.TextTool(TaskKind.SUMMARIZE, state.output)) }, kind = ButtonKind.GLASS, height = 36.dp)
                                PillButton("Organizar", { nav.go(Route.TextTool(TaskKind.ORGANIZE, state.output)) }, kind = ButtonKind.GLASS, height = 36.dp)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }
    LaunchedEffect(Unit) {
        // Texto vindo de outra tela (por exemplo, "Resumir" de uma transcrição): já executa.
        if (route.text.isNotBlank() && state.output.isEmpty() && !state.running && task.isText) state.run()
    }
}

/** Um campo de uma linha em vidro (instrução da resposta). */
@Composable
private fun SearchlessField(value: String, onValue: (String) -> Unit, placeholder: String) {
    val c = Lily.colors
    GlassCard(Modifier.fillMaxWidth(), padding = 14.dp) {
        Box {
            if (value.isEmpty()) Text(placeholder, style = Type.callout, color = c.tertiary)
            BasicTextField(value, onValue, textStyle = Type.callout.copy(color = c.content), cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth())
        }
    }
}
