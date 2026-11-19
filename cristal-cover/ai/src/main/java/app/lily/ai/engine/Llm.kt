package app.lily.ai.engine

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Immutable
import app.lily.ai.core.Accel
import app.lily.ai.core.EngineChoice
import app.lily.ai.models.Modality
import app.lily.ai.models.ModelSpec
import app.lily.ai.models.ModelStore
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** O que o motor de linguagem está fazendo agora (mostrado no app). */
@Immutable
sealed interface EngineStatus {
    data object Idle : EngineStatus
    data class Loading(val model: ModelSpec, val accel: Accel) : EngineStatus
    data class Ready(val model: ModelSpec, val accel: Accel, val loadMillis: Long) : EngineStatus
    data class Busy(val model: ModelSpec, val accel: Accel) : EngineStatus
}

/** Uma vez da conversa enviada como histórico ao modelo. */
data class Turn(val fromUser: Boolean, val text: String)

/** O que vai junto da mensagem: imagem ou áudio (arquivos já preparados no cache do app). */
data class Attachments(val imageFile: File? = null, val audioFile: File? = null)

class EngineRefused(message: String) : Exception(message)

/**
 * O motor de linguagem que a conversa, as ferramentas e a ponte usam. O de verdade é o [EngineHost]
 * (LiteRT-LM); os testes trocam por um motor de teste para exercitar o app inteiro.
 */
interface LlmEngine {
    val status: StateFlow<EngineStatus>

    /** Quanto tempo um modelo fica carregado sem uso. */
    var idleMillis: Long

    /** Avisado a cada carregamento (o tempo medido vai para a ficha do modelo). */
    var onLoaded: ((ModelSpec, Accel, Long) -> Unit)?

    /** A resposta em pedaços (texto acumulado); cancelar a coleta interrompe a geração. */
    fun generate(
        choice: EngineChoice.Llm,
        system: String?,
        history: List<Turn>,
        prompt: String,
        attachments: Attachments = Attachments(),
        temperature: Double = 0.7,
    ): Flow<String>

    /** Descarrega o modelo (libera a memória). */
    fun unload()

    /** Para quem precisa da memória já: espera a inferência atual terminar e descarrega. */
    suspend fun unloadNow()
}

/** Um modelo de linguagem carregado no LiteRT-LM. */
class LoadedLlm(val spec: ModelSpec, val accel: Accel, internal val engine: Engine, val loadMillis: Long)

/**
 * O host dos modelos de linguagem: no máximo um carregado (os arquivos têm de centenas de MB a 4 GB),
 * descarregado depois de um tempo sem uso, quando o Android pede memória ou quando outra tarefa
 * pesada (geração de imagem) precisa do espaço. Uma inferência por vez.
 */
class EngineHost(private val context: Context, private val store: ModelStore) : LlmEngine {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loaded: LoadedLlm? = null
    private var idleJob: Job? = null
    private val _status = MutableStateFlow<EngineStatus>(EngineStatus.Idle)
    override val status: StateFlow<EngineStatus> = _status.asStateFlow()

    /** Quanto tempo um modelo fica carregado sem uso (o perfil Eco usa menos). */
    @Volatile override var idleMillis: Long = 120_000L

    @Volatile override var onLoaded: ((ModelSpec, Accel, Long) -> Unit)? = null

    /**
     * Gera a resposta em pedaços. [system] = instruções; [history] = conversa até aqui; [prompt] =
     * a nova mensagem. Cancelar a coleta interrompe a geração no motor.
     */
    override fun generate(
        choice: EngineChoice.Llm,
        system: String?,
        history: List<Turn>,
        prompt: String,
        attachments: Attachments,
        temperature: Double,
    ): Flow<String> = flow {
        refuseIfHot()
        mutex.withLock {
            val llm = obtain(choice)
            _status.value = EngineStatus.Busy(llm.spec, llm.accel)
            var conversation: Conversation? = null
            try {
                conversation = llm.engine.createConversation(
                    ConversationConfig(
                        systemInstruction = system?.let { Contents.of(it) },
                        initialMessages = history.map { if (it.fromUser) Message.user(it.text) else Message.model(it.text) },
                        samplerConfig = SamplerConfig(40, 0.95, temperature, 0),
                        thinkingConfig = ThinkingConfig(false),
                    ),
                )
                val parts = ArrayList<Content>()
                attachments.imageFile?.takeIf { llm.spec.has(Modality.IMAGE) }?.let { parts += Content.ImageFile(it.absolutePath) }
                attachments.audioFile?.takeIf { llm.spec.has(Modality.AUDIO) }?.let { parts += Content.AudioFile(it.absolutePath) }
                parts += Content.Text(prompt)
                val stream = StreamText()
                val active = conversation
                active.sendMessageAsync(Contents.of(parts))
                    .onCompletion { cause -> if (cause != null) runCatching { active.cancelProcess() } }
                    .collect { message -> emit(stream.push(message.textParts())) }
            } finally {
                runCatching { conversation?.close() }
                _status.value = EngineStatus.Ready(llm.spec, llm.accel, llm.loadMillis)
                scheduleIdle()
            }
        }
    }.flowOn(Dispatchers.Default)
        .catch { t -> throw if (t is EngineRefused) t else EngineRefused(friendly(t)) }

    override fun unload() {
        scope.launch { mutex.withLock { unloadLocked() } }
    }

    override suspend fun unloadNow() = mutex.withLock { unloadLocked() }

    private fun obtain(choice: EngineChoice.Llm): LoadedLlm {
        val current = loaded
        if (current != null && current.spec.id == choice.model.id && current.accel == choice.accel) return current
        unloadLocked()
        val spec = choice.model
        val file = store.file(spec, spec.files.first())
        if (!file.isFile) throw EngineRefused("${spec.name} não está instalado.")
        return try {
            load(spec, file, choice.accel, choice.threads)
        } catch (t: Throwable) {
            if (choice.accel != Accel.GPU) throw EngineRefused(friendly(t))
            // A GPU não aceitou este modelo: tenta na CPU antes de desistir.
            Log.w(TAG, "GPU recusou ${spec.id}; tentando a CPU", t)
            load(spec, file, Accel.CPU, choice.threads)
        }.also { loaded = it }
    }

    private fun load(spec: ModelSpec, file: File, accel: Accel, threads: Int): LoadedLlm {
        _status.value = EngineStatus.Loading(spec, accel)
        val started = SystemClock.elapsedRealtime()
        val backend: Backend = if (accel == Accel.GPU) Backend.GPU() else Backend.CPU(threads)
        val config = EngineConfig(
            modelPath = file.absolutePath,
            backend = backend,
            visionBackend = if (spec.has(Modality.IMAGE)) backend else null,
            audioBackend = if (spec.has(Modality.AUDIO)) Backend.CPU(threads) else null,
            maxNumTokens = spec.contextTokens ?: 2048,
            maxNumImages = if (spec.has(Modality.IMAGE)) 1 else null,
            cacheDir = File(context.cacheDir, "litertlm").apply { mkdirs() }.absolutePath,
        )
        app.lily.ai.diagnostics.DiagLog.line("▶ Carregando ${spec.id} na ${accel.label} (${file.length() / 1_048_576} MB)")
        val engine = try {
            Engine(config)
        } catch (t: Throwable) {
            _status.value = EngineStatus.Idle
            app.lily.ai.diagnostics.DiagLog.line("✗ Modelo ${spec.id} na ${accel.label}: ${t.javaClass.name}: ${t.message}", Log.ERROR)
            throw t
        }
        try {
            engine.initialize()
        } catch (t: Throwable) {
            runCatching { engine.close() }
            _status.value = EngineStatus.Idle
            app.lily.ai.diagnostics.DiagLog.line("✗ Modelo ${spec.id} na ${accel.label}: ${t.javaClass.name}: ${t.message}", Log.ERROR)
            throw t
        }
        val ms = SystemClock.elapsedRealtime() - started
        app.lily.ai.diagnostics.DiagLog.line("✓ Modelo ${spec.id} na ${accel.label} em $ms ms")
        _status.value = EngineStatus.Ready(spec, accel, ms)
        runCatching { onLoaded?.invoke(spec, accel, ms) }
        return LoadedLlm(spec, accel, engine, ms)
    }

    private fun unloadLocked() {
        idleJob?.cancel()
        loaded?.let { runCatching { it.engine.close() } }
        loaded = null
        _status.value = EngineStatus.Idle
    }

    private fun scheduleIdle() {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(idleMillis)
            mutex.withLock { unloadLocked() }
        }
    }

    private fun refuseIfHot() {
        val status = context.getSystemService(PowerManager::class.java)?.currentThermalStatus ?: 0
        if (status >= PowerManager.THERMAL_STATUS_CRITICAL) {
            throw EngineRefused("O aparelho está muito quente. Espere esfriar um pouco para usar a IA.")
        }
    }

    private fun friendly(t: Throwable): String {
        val msg = t.message.orEmpty()
        return when {
            t is EngineRefused -> msg
            t is OutOfMemoryError || msg.contains("memory", ignoreCase = true) ->
                "Faltou memória para o modelo. Feche outros apps ou use um modelo menor (perfil Eco)."
            else -> "O modelo não conseguiu responder: ${msg.ifBlank { t.javaClass.simpleName }}"
        }
    }

    private companion object {
        const val TAG = "LilyEngine"
    }
}

/** O texto de uma resposta (as partes de texto da mensagem). */
fun Message.textParts(): String = contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }

/**
 * Junta os pedaços da resposta. Aceita tanto pedaços novos quanto o texto acumulado (o que o motor
 * mandar) e esconde o bloco de raciocínio `<think>…</think>` dos modelos que o produzem.
 */
class StreamText {
    private val raw = StringBuilder()

    fun push(piece: String): String {
        if (piece.isNotEmpty()) {
            val current = raw.toString()
            if (current.isNotEmpty() && piece.length >= current.length && piece.startsWith(current)) {
                raw.setLength(0)
                raw.append(piece)
            } else {
                raw.append(piece)
            }
        }
        return visible(raw.toString())
    }

    companion object {
        fun visible(text: String): String {
            var out = text.replace(Regex("(?s)<think>.*?</think>"), "")
            val open = out.indexOf("<think>")
            if (open >= 0) out = out.substring(0, open)
            return out.trimStart()
        }
    }
}

/**
 * O motor quando o LiteRT-LM não iniciou nesta partida: o app abre do mesmo jeito e as conversas
 * explicam o motivo em vez de fechar o app.
 */
class UnavailableEngine(private val reason: String) : LlmEngine {
    override val status: StateFlow<EngineStatus> = kotlinx.coroutines.flow.MutableStateFlow(EngineStatus.Idle)
    override var idleMillis: Long = 120_000L
    override var onLoaded: ((ModelSpec, Accel, Long) -> Unit)? = null

    override fun generate(
        choice: EngineChoice.Llm,
        system: String?,
        history: List<Turn>,
        prompt: String,
        attachments: Attachments,
        temperature: Double,
    ): Flow<String> = kotlinx.coroutines.flow.flow { throw EngineRefused("O motor de linguagem não iniciou nesta abertura ($reason). Veja o L.I.L.Y Diagnóstico.") }

    override fun unload() = Unit

    override suspend fun unloadNow() = Unit
}
