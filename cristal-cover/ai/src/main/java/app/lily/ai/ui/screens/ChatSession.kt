package app.lily.ai.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.lily.ai.AiGraph
import app.lily.ai.core.EngineChoice
import app.lily.ai.core.RoutePlan
import app.lily.ai.core.TaskKind
import app.lily.ai.data.Attachment
import app.lily.ai.data.ChatMessage
import app.lily.ai.data.MediaSaver
import app.lily.ai.data.MsgStatus
import app.lily.ai.data.Role
import app.lily.ai.engine.Attachments
import app.lily.ai.engine.AudioFiles
import app.lily.ai.engine.Documents
import app.lily.ai.engine.EngineStatus
import app.lily.ai.engine.Label
import app.lily.ai.engine.Prompts
import app.lily.ai.engine.Turn
import app.lily.ai.engine.Vision
import app.lily.ai.models.Catalog
import app.lily.ai.models.Category
import app.lily.ai.models.Modality
import app.lily.ai.models.ModelSpec
import app.lily.ai.models.Runtime
import app.lily.ai.presence.LilyPresence
import app.lily.ai.ui.Route
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Uma imagem anexada (cópia reduzida dentro do app), ainda não enviada. */
@Immutable
data class PendingImage(val file: File, val sourceUri: String?, val thumb: Bitmap)

/** Um áudio anexado (WAV 16 kHz dentro do app), ainda não enviado. */
@Immutable
data class PendingAudio(val file: File, val label: String, val seconds: Int)

/** Um documento anexado: o texto extraído no aparelho (PDF ou texto), guardado dentro do app. */
@Immutable
data class PendingDoc(val file: File, val name: String, val chars: Int, val pages: Int?, val ocrPages: Int)

/** A resposta sendo escrita agora. O [id] é o mesmo da mensagem final (a lista não pisca). */
@Immutable
data class Streaming(val id: String, val text: String, val model: String?, val image: Boolean, val audio: Boolean)

/**
 * O estado de uma conversa: mensagens, anexos, a resposta em andamento e o modelo escolhido.
 * Vive enquanto a conversa estiver na pilha de telas (abrir o Model Hub e voltar não interrompe a
 * resposta). Histórico não é memória: só as mensagens desta conversa vão para o modelo.
 */
@Stable
class ChatSession(private val graph: AiGraph, private val app: Context, start: Route.Chat) {
    var conversationId by mutableStateOf(start.id)
        private set
    val messages = mutableStateListOf<ChatMessage>()
    var loading by mutableStateOf(start.id != null)
        private set
    var draft by mutableStateOf(start.draft.orEmpty())
    var image by mutableStateOf<PendingImage?>(null)
        private set
    var audio by mutableStateOf<PendingAudio?>(null)
        private set
    var document by mutableStateOf<PendingDoc?>(null)
        private set
    var preparing by mutableStateOf(false)
        private set
    var recordingLevel by mutableStateOf<Float?>(null)
        private set
    var streaming by mutableStateOf<Streaming?>(null)
        private set
    var manualModel by mutableStateOf(start.model)
        private set
    var lastPlan by mutableStateOf<RoutePlan?>(null)
        private set
    var notice by mutableStateOf<String?>(null)
    var speakingId by mutableStateOf<String?>(null)
        private set

    /**
     * Sobe a cada resposta de voz pronta para a próxima pergunta (depois da leitura em voz alta, se
     * ela estiver ligada). As telas de voz voltam a ouvir quando este número muda.
     */
    var followUps by mutableStateOf(0)
        private set

    private var job: Job? = null
    @Volatile private var stopRecording = false

    val busy: Boolean get() = streaming != null

    private val _working = MutableStateFlow(false)

    /** true enquanto uma resposta é gerada; para quem observa sem interface (o aviso da ilha). */
    val working: StateFlow<Boolean> = _working.asStateFlow()

    init {
        graph.scope.launch {
            start.id?.let { id ->
                messages.addAll(graph.conversations.messages(id))
                if (manualModel == null) manualModel = graph.conversations.meta(id)?.modelId
            }
            loading = false
            start.image?.let { attachImage(it).join() }
            start.audio?.let { attachAudio(it).join() }
            if (start.send) send(viaVoice = start.voice)
        }
    }

    // --- Anexos -------------------------------------------------------------------------------

    fun attachImage(uri: Uri): Job = graph.scope.launch {
        preparing = true
        try {
            val prepared = withContext(Dispatchers.IO) {
                val bitmap = MediaSaver.load(app, uri, 1024)
                val file = File(graph.conversations.attachments, "img-${UUID.randomUUID()}.jpg")
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                PendingImage(file, uri.toString(), thumbnail(bitmap))
            }
            discard(image?.file)
            image = prepared
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            notice = "Não consegui abrir esta imagem."
        } finally {
            preparing = false
        }
    }

    fun attachAudio(uri: Uri): Job = graph.scope.launch {
        preparing = true
        try {
            val prepared = withContext(Dispatchers.IO) {
                val file = File(graph.conversations.attachments, "aud-${UUID.randomUUID()}.wav")
                AudioFiles.toWav(app, uri, file)
                PendingAudio(file, displayName(uri) ?: "Áudio", wavSeconds(file))
            }
            discard(audio?.file)
            audio = prepared
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            notice = "Não consegui ler este áudio."
        } finally {
            preparing = false
        }
    }

    /** Lê o documento no aparelho (texto embutido do PDF ou OCR das páginas escaneadas). */
    fun attachDocument(uri: Uri): Job = graph.scope.launch {
        preparing = true
        try {
            val extracted = withContext(Dispatchers.IO) { Documents.extract(app, uri) }
            if (extracted.text.isBlank()) {
                notice = "Não encontrei texto neste documento."
                return@launch
            }
            val file = File(graph.conversations.attachments, "doc-${UUID.randomUUID()}.txt")
            withContext(Dispatchers.IO) { file.writeText(extracted.text) }
            discard(document?.file)
            document = PendingDoc(file, extracted.name, extracted.text.length, extracted.pages, extracted.ocrPages)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            notice = "Não consegui ler este documento."
        } finally {
            preparing = false
        }
    }

    /** Grava até 30 s. A tela pede a permissão do microfone antes (sempre num toque). */
    fun startRecording() {
        if (recordingLevel != null || busy) return
        stopRecording = false
        recordingLevel = 0f
        graph.scope.launch {
            try {
                val file = File(graph.conversations.attachments, "rec-${UUID.randomUUID()}.wav")
                withContext(Dispatchers.IO) { AudioFiles.record(file, { stopRecording }) { recordingLevel = it } }
                discard(audio?.file)
                audio = PendingAudio(file, "Gravação", wavSeconds(file))
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                notice = "A gravação falhou: ${t.message ?: "microfone indisponível"}."
            } finally {
                recordingLevel = null
            }
        }
    }

    fun stopRecording() {
        stopRecording = true
    }

    fun removeAttachments() {
        discard(image?.file)
        discard(audio?.file)
        discard(document?.file)
        image = null
        audio = null
        document = null
    }

    // --- Conversa ------------------------------------------------------------------------------

    /**
     * Manda uma pergunta. Se ainda houver uma resposta saindo, ela é interrompida (fica guardada como
     * "parada") e a L.I.L.Y responde a pergunta NOVA: nada de continuar a anterior nem perder a fala.
     */
    fun send(text: String = draft, viaVoice: Boolean = false) {
        if (preparing || recordingLevel != null) return
        val clean = text.trim()
        // Uma pergunta nova cala a leitura da resposta anterior (senão ela "continua respondendo" a outra).
        stopSpeaking()
        val img = image
        val aud = audio
        val doc = document
        if (clean.isEmpty() && img == null && aud == null && doc == null) return
        draft = ""
        image = null
        audio = null
        document = null
        val attachments = buildList {
            img?.let { add(Attachment("image", it.file.path, it.sourceUri)) }
            aud?.let { add(Attachment("audio", it.file.path)) }
            doc?.let { add(Attachment("document", it.file.path, name = it.name)) }
        }
        val user = ChatMessage(role = Role.USER, text = clean, attachments = attachments)
        val previous = job?.takeIf { it.isActive }
        if (previous == null) {
            messages += user
            respond(user, viaVoice)
            return
        }
        // Interrompe a resposta em andamento; a pergunta nova entra depois que ela foi guardada.
        previous.cancel()
        _working.value = true
        job = graph.scope.launch {
            previous.join()
            messages += user
            runAnswer(user, viaVoice)
        }
    }

    fun stop() {
        job?.cancel()
    }

    /** Gera de novo a última resposta (com o modelo escolhido agora). */
    fun regenerate() {
        if (busy) return
        val lastUser = messages.lastOrNull { it.role == Role.USER } ?: return
        val index = messages.indexOf(lastUser)
        while (messages.size > index + 1) messages.removeAt(messages.lastIndex)
        respond(lastUser)
    }

    /** null = automático (o roteador escolhe). */
    fun chooseModel(id: String?) {
        manualModel = id
        conversationId?.let { cid -> graph.scope.launch { graph.conversations.setModel(cid, id) } }
    }

    /** Prévia do que o roteador escolheria agora para uma mensagem de texto. */
    fun previewPlan(): RoutePlan = graph.tools.plan(TaskKind.CHAT, manualModel, needsImage = image != null, needsAudio = audio != null)

    /** Para a leitura em voz alta desta conversa (ao ouvir de novo ou mandar outra pergunta). */
    fun stopSpeaking() {
        if (speakingId == null) return
        graph.voice.stop()
        speakingId = null
    }

    /** [then] roda só quando a leitura chega ao fim (não quando é interrompida). */
    fun speak(message: ChatMessage, then: (() -> Unit)? = null) {
        if (speakingId == message.id) {
            graph.voice.stop()
            speakingId = null
            return
        }
        speakingId = message.id
        graph.speak(Markdown.plain(message.text)) {
            speakingId = null
            then?.invoke()
        }
    }

    /** Guarda na memória (com a confirmação da tela); false se parece dado sensível. */
    fun remember(text: String): Boolean = graph.memory.add(text) != null

    fun dispose() {
        job?.cancel()
        stopRecording = true
        if (speakingId != null) graph.voice.stop()
        if (!busy) removeAttachments()
    }

    private fun respond(user: ChatMessage, viaVoice: Boolean = false) {
        _working.value = true
        job = graph.scope.launch { runAnswer(user, viaVoice) }
    }

    private suspend fun runAnswer(user: ChatMessage, viaVoice: Boolean) {
        try {
            answer(user, UUID.randomUUID().toString(), viaVoice)
        } finally {
            // Só a resposta mais recente desliga o "trabalhando" (uma interrompida não apaga a nova).
            if (job == null || job == currentCoroutineContext()[Job]) _working.value = false
        }
    }

    private suspend fun answer(user: ChatMessage, answerId: String, viaVoice: Boolean = false) {
        run {
            val id = ensureConversation(user)
            graph.conversations.save(id, messages.toList())
            val ownImage = user.file("image")
            val imageFile = ownImage ?: recentImage(user)
            val audioFile = user.file("audio")
            val plan = withContext(Dispatchers.Default) {
                graph.tools.plan(TaskKind.CHAT, manualModel, needsImage = imageFile != null, needsAudio = audioFile != null)
            }
            lastPlan = plan
            val engine = plan.engine
            if (engine !is EngineChoice.Llm && engine !is EngineChoice.Remote) {
                val unavailable = engine as? EngineChoice.Unavailable
                val answer = if (ownImage != null && audioFile == null) {
                    visionFallback(answerId, ownImage)
                } else {
                    ChatMessage(
                        id = answerId,
                        role = Role.ASSISTANT,
                        text = unavailable?.reason ?: "Não há um modelo para esta mensagem.",
                        status = MsgStatus.ERROR,
                        model = "Roteador",
                        install = (unavailable?.install ?: Category.CHAT).name,
                    )
                }
                finish(id, answer, viaVoice)
                return@run
            }
            val remote = (engine as? EngineChoice.Remote)?.model
            val local = (engine as? EngineChoice.Llm)?.model
            val modelName = remote?.let { "${it.label} · online" } ?: local!!.name
            // Contexto do modelo (o online costuma ser bem maior; limitado para não encarecer).
            val contextTokens = remote?.let { (it.context.takeIf { c -> c > 0 } ?: 32_000).coerceAtMost(64_000) } ?: (local!!.contextTokens ?: 2048)
            streaming = Streaming(answerId, "", modelName, imageFile != null, audioFile != null)
            LilyPresence.thinking(PRESENCE, if (remote != null) "Pensando (online)" else "Pensando")
            val question = when {
                user.text.isNotBlank() -> user.text
                audioFile != null -> Prompts.tool(TaskKind.TRANSCRIBE_AUDIO, "")
                imageFile != null -> Prompts.tool(TaskKind.DESCRIBE_IMAGE, "")
                else -> "Resuma este documento em português, com os pontos principais."
            }
            val prompt = withDocument(question, user, contextTokens)
            // O histórico usa o que sobra do contexto depois da pergunta (e do documento).
            // Modelos pequenos do aparelho se perdem com conversa longa (repetem a resposta anterior):
            // levam só as duas últimas trocas, o bastante para "ele" e "isso".
            val history = history(contextTokens, user, reserve = prompt.length, maxTurns = if (local != null && contextTokens <= 4096) 4 else Int.MAX_VALUE)
            val started = SystemClock.elapsedRealtime()
            var first = 0L
            var text = ""
            var status = MsgStatus.DONE
            try {
                graph.tools.text(plan, prompt, history, Attachments(imageFile, audioFile)).collect { piece ->
                    if (first == 0L && piece.isNotEmpty()) first = SystemClock.elapsedRealtime()
                    text = piece
                    streaming = Streaming(answerId, piece, modelName, imageFile != null, audioFile != null)
                }
                if (text.isBlank()) {
                    status = MsgStatus.ERROR
                    text = "O modelo não devolveu nenhum texto. Tente reformular a pergunta."
                }
            } catch (c: CancellationException) {
                status = MsgStatus.STOPPED
            } catch (t: Throwable) {
                status = MsgStatus.ERROR
                text = t.message ?: "A IA não conseguiu responder."
            } finally {
                val end = SystemClock.elapsedRealtime()
                val accel = local?.let { m -> (graph.engines.status.value as? EngineStatus.Ready)?.takeIf { it.model.id == m.id }?.accel?.label }
                val cost = when {
                    remote == null -> null
                    remote.free -> "grátis"
                    else -> graph.tools.lastRemoteUsage?.costUsd?.let { usd(it) }
                }
                val answer = ChatMessage(
                    id = answerId,
                    role = Role.ASSISTANT,
                    text = text.trim(),
                    model = listOfNotNull(modelName, accel, cost).joinToString(" · "),
                    status = status,
                    millis = end - started,
                )
                when (status) {
                    MsgStatus.DONE -> LilyPresence.finished(PRESENCE, true, "Resposta pronta")
                    MsgStatus.ERROR -> LilyPresence.finished(PRESENCE, false, "Não consegui responder")
                    else -> LilyPresence.clear(PRESENCE)
                }
                withContext(NonCancellable) {
                    if (status == MsgStatus.DONE && first > 0 && local != null) graph.perf.recordAnswer(local.id, text.length, end - first)
                    finish(id, answer, viaVoice)
                }
            }
        }
    }

    private suspend fun finish(conversation: String, answer: ChatMessage, viaVoice: Boolean = false) {
        messages += answer
        streaming = null
        graph.conversations.save(conversation, messages.toList())
        val settings = graph.settings.value.value
        if (!viaVoice || answer.status != MsgStatus.DONE) return
        val next: (() -> Unit)? = if (settings.voiceFollowUp) ({ graph.scope.launch(Dispatchers.Main) { followUps++ } }) else null
        if (settings.speakVoiceAnswers) speak(answer, then = next) else next?.invoke()
    }

    private suspend fun ensureConversation(first: ChatMessage): String {
        conversationId?.let { return it }
        val title = when {
            first.text.isNotBlank() -> Prompts.titleFrom(first.text)
            first.attachments.any { it.kind == "image" } -> "Imagem"
            first.attachments.any { it.kind == "document" } -> first.attachments.first { it.kind == "document" }.name ?: "Documento"
            else -> "Áudio"
        }
        val meta = graph.conversations.create(title)
        manualModel?.let { graph.conversations.setModel(meta.id, it) }
        conversationId = meta.id
        return meta.id
    }

    /**
     * O histórico que cabe no contexto do modelo (as mensagens mais recentes), alternando pessoa e
     * IA como os modelos esperam. Erros e a mensagem atual ficam de fora.
     */
    private fun history(contextTokens: Int, current: ChatMessage, reserve: Int = 0, maxTurns: Int = Int.MAX_VALUE): List<Turn> {
        val budget = (max(512, contextTokens - 1024) * 3 - reserve).coerceAtLeast(0)
        val picked = ArrayList<Turn>()
        var used = 0
        for (m in messages.asReversed()) {
            if (picked.size >= maxTurns) break
            if (m.id == current.id || m.status == MsgStatus.ERROR || m.status == MsgStatus.STOPPED || m.text.isBlank()) continue
            val text = m.text.take(2_000)
            if (used + text.length > budget) break
            used += text.length
            picked += Turn(m.role == Role.USER, text)
        }
        picked.reverse()
        val merged = ArrayList<Turn>()
        for (t in picked) {
            val last = merged.lastOrNull()
            if (last != null && last.fromUser == t.fromUser) merged[merged.lastIndex] = Turn(t.fromUser, last.text + "\n\n" + t.text) else merged += t
        }
        while (merged.firstOrNull()?.fromUser == false) merged.removeAt(0)
        if (merged.lastOrNull()?.fromUser == true) merged.removeAt(merged.lastIndex)
        return merged
    }

    /**
     * O documento da mensagem (ou o enviado há pouco, para perguntas de acompanhamento) vai junto da
     * pergunta, cortado para caber no contexto do modelo (e o modelo é avisado quando cortou).
     */
    private suspend fun withDocument(question: String, current: ChatMessage, contextTokens: Int): String {
        val index = messages.indexOfFirst { it.id == current.id }
        val doc = current.attachments.firstOrNull { it.kind == "document" }
            ?: messages.subList(max(0, index - 4), max(0, index)).asReversed().firstNotNullOfOrNull { m -> m.attachments.firstOrNull { it.kind == "document" } }
            ?: return question
        val text = withContext(Dispatchers.IO) { runCatching { File(doc.path).readText() }.getOrDefault("") }
        if (text.isBlank()) return question
        val limit = max(1_500, (contextTokens - 1_200) * 3 * 2 / 3)
        val cut = text.length > limit
        return buildString {
            append("Documento “").append(doc.name ?: "documento").append("”")
            if (cut) append(" (só o começo: o documento inteiro não cabe no modelo)")
            append(":\n\n").append(text.take(limit)).append("\n\n---\n\n").append(question)
        }
    }

    /** Uma pergunta de acompanhamento sobre a foto enviada há pouco leva a foto junto (se houver modelo de visão). */
    private fun recentImage(current: ChatMessage): File? {
        val visionInstalled = Catalog.all.any { it.runtime == Runtime.LITERT_LM && it.has(Modality.IMAGE) && it.id in graph.store.installedIds() }
        if (!visionInstalled) return null
        val index = messages.indexOfFirst { it.id == current.id }
        if (index <= 0) return null
        return messages.subList(max(0, index - 4), index).asReversed().firstNotNullOfOrNull { it.file("image") }
    }

    /** Sem modelo de visão: o que o ML Kit encontra na foto, dito com honestidade. */
    private suspend fun visionFallback(answerId: String, imageFile: File): ChatMessage {
        streaming = Streaming(answerId, "", "ML Kit", image = true, audio = false)
        val started = SystemClock.elapsedRealtime()
        return try {
            val bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(imageFile.path) } ?: throw IOException("imagem ilegível")
            val labels = Vision.labels(bitmap)
            val text = Vision.ocr(bitmap)
            ChatMessage(
                id = answerId,
                role = Role.ASSISTANT,
                text = fallbackText(labels, text),
                model = "ML Kit · no aparelho",
                tool = "Análise básica",
                millis = SystemClock.elapsedRealtime() - started,
                install = Category.VISION.name,
            )
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            ChatMessage(id = answerId, role = Role.ASSISTANT, text = "Não consegui analisar a imagem: ${t.message}", status = MsgStatus.ERROR, install = Category.VISION.name)
        }
    }

    private fun ChatMessage.file(kind: String): File? = attachments.firstOrNull { it.kind == kind }?.let { File(it.path) }?.takeIf { it.isFile }

    private fun discard(file: File?) {
        file?.let { f -> graph.scope.launch(Dispatchers.IO) { f.delete() } }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    companion object {
        const val PRESENCE = "conversa"

        fun wavSeconds(file: File): Int = ((file.length() - 44).coerceAtLeast(0) / (AudioFiles.RATE * 2)).toInt()

        fun thumbnail(bitmap: Bitmap, side: Int = 256): Bitmap {
            val longest = max(bitmap.width, bitmap.height)
            if (longest <= side) return bitmap
            val s = side.toFloat() / longest
            return Bitmap.createScaledBitmap(bitmap, (bitmap.width * s).roundToInt().coerceAtLeast(1), (bitmap.height * s).roundToInt().coerceAtLeast(1), true)
        }

        fun fallbackText(labels: List<Label>, text: String): String = buildString {
            append("Ainda não há um modelo de visão instalado, então não consigo descrever a foto em detalhes nem responder perguntas sobre ela. ")
            append("Com o reconhecimento do ML Kit, no aparelho, encontrei:\n\n")
            if (labels.isNotEmpty()) {
                append("**Objetos e cenas:** ")
                append(labels.take(8).joinToString(", ") { "${it.text} (${(it.confidence * 100).roundToInt()}%)" })
                append("\n\n")
            }
            if (text.isNotBlank()) append("**Texto na imagem:**\n").append(text.take(1_500)).append("\n\n")
            if (labels.isEmpty() && text.isBlank()) append("Nada que eu consiga reconhecer com segurança.\n\n")
            append("Para descrições completas, instale um modelo de visão no Model Hub.")
        }
    }
}
