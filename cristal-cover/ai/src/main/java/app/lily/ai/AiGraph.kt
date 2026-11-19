package app.lily.ai

import app.lily.ai.diagnostics.CrashReport
import android.app.Application
import android.content.ComponentCallbacks2
import androidx.compose.runtime.Immutable
import app.lily.ai.assist.AssistController
import app.lily.ai.core.DeviceMonitor
import app.lily.ai.core.Profile
import app.lily.ai.core.RouterInput
import app.lily.ai.data.ConversationStore
import app.lily.ai.data.MemoryStore
import app.lily.ai.data.PerfStore
import app.lily.ai.data.SettingsStore
import app.lily.ai.diagnostics.DiagLog
import app.lily.ai.engine.EngineHost
import app.lily.ai.engine.LlmEngine
import app.lily.ai.engine.UnavailableEngine
import app.lily.ai.engine.Voice
import app.lily.ai.imagegen.ImageGenJobs
import app.lily.ai.models.Downloads
import app.lily.ai.models.ModelStore
import app.lily.ai.tools.AiTools
import app.lily.ai.avatar.AvatarOverlay
import app.lily.ai.core.OnlineMode
import app.lily.ai.imagegen.GenState
import app.lily.ai.presence.LilyPresence
import app.lily.ai.remote.GatewayClient
import app.lily.ai.remote.GatewayStore
import app.lily.ai.ui.screens.Network
import app.lily.ai.video.VideoJobs
import app.lily.ai.codex.Projects
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Um módulo que não iniciou nesta partida (o app segue sem ele). */
@Immutable
data class ModuleFailure(val module: String, val reason: String)

/**
 * As peças da L.I.L.Y AI, criadas uma vez por processo. Cada uma inicia como uma etapa registrada
 * no diagnóstico; as opcionais têm uma reserva e, se falharem, o app abre sem elas e avisa. Nada
 * aqui carrega modelos: o motor só carrega um modelo quando uma conversa pede.
 *
 * [safeMode]: aberta depois de falhas seguidas (ou escolhida no diagnóstico); a interface desliga
 * os efeitos mais pesados.
 */
class AiGraph(val app: Application, val safeMode: Boolean = false, private val failing: Set<String> = emptySet()) {
    private val _failures = ArrayList<ModuleFailure>()

    /** Os módulos opcionais que não iniciaram. */
    val failures: List<ModuleFailure> get() = _failures

    private val status = ArrayList<String>()

    /** Inicia um módulo como etapa; com [fallback], uma falha vira "indisponível" em vez de fechar o app. */
    private fun <T> module(name: String, fallback: (() -> T)?, create: () -> T): T = try {
        DiagLog.step(name) {
            // Só os testes simulam falhas (para conferir que o app segue sem o módulo).
            if (name in failing) error("falha simulada no teste")
            create()
        }.also { status += "ok · $name" }
    } catch (t: Throwable) {
        if (fallback == null) throw t
        DiagLog.moduleFailed(name, t)
        _failures += ModuleFailure(name, "${t.javaClass.simpleName}: ${t.message.orEmpty()}".take(200))
        status += "INDISPONÍVEL · $name · ${t.javaClass.simpleName}: ${t.message.orEmpty().take(160)}"
        fallback()
    }

    /**
     * Onde as tarefas de fundo da L.I.L.Y rodam. Um erro inesperado numa delas vai para o relatório do
     * Diagnóstico (não fatal) em vez de fechar o app.
     */
    val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + kotlinx.coroutines.CoroutineExceptionHandler { _, t ->
            runCatching { DiagLog.line("✗ Erro numa tarefa de fundo: ${t.javaClass.name}: ${t.message}", android.util.Log.ERROR) }
            runCatching { CrashReport.save(app, "tarefa de fundo", t, fatal = false) }
        },
    )
    val settings = module(SETTINGS, null) { SettingsStore(app) }
    val device = module(DEVICE, null) { DeviceMonitor(app) }
    val store = module(MODELS, null) { ModelStore(File(app.filesDir, "models")) }
    val downloads = module(DOWNLOADS, null) { Downloads(app, store) }
    val conversations = module(CONVERSATIONS, { ConversationStore(File(app.cacheDir, "conversas-reserva")) }) {
        ConversationStore(File(app.filesDir, "conversations"))
    }
    val memory = module(MEMORY, { MemoryStore(File(app.cacheDir, "memoria-reserva.json")) }) { MemoryStore(File(app.filesDir, "memory.json")) }

    /** O motor de linguagem (LiteRT-LM). Só os testes trocam. */
    var engines: LlmEngine = module(ENGINE, { UnavailableEngine(failures.lastOrNull()?.reason ?: "não iniciou") }) { EngineHost(app, store) }
        @androidx.annotation.VisibleForTesting internal set
    val perf = module(PERF, { PerfStore(File(app.cacheDir, "perf-reserva.json")) }) { PerfStore(File(app.filesDir, "perf.json")) }
    val imageGen = module(IMAGE_GEN, { ImageGenJobs(app) }) { ImageGenJobs(app) }
    val tools = module(TOOLS, null) { AiTools(this) }

    /** O serviço online (L.I.L.Y Gateway): só a chave de acesso fica aqui, cifrada pelo Keystore. */
    var gatewayStore: GatewayStore = module(GATEWAY, { GatewayStore(app, NoKeystoreBox) }) { GatewayStore(app) }
        @androidx.annotation.VisibleForTesting internal set
    var gateway: GatewayClient = GatewayClient(gatewayStore) { Network.online(app) }
        @androidx.annotation.VisibleForTesting internal set

    val overlay: AvatarOverlay by lazy { AvatarOverlay(this, app) }

    /** Codex: os projetos que a pessoa autorizou (pasta do aparelho ou repositório pelo gateway). */
    val codexProjects = module(CODEX, { Projects(app) }) { Projects(app) }

    /** Video AI: o histórico e o acompanhamento dos vídeos gerados online. */
    val videos = module(VIDEO, { VideoJobs(app) { gateway } }) { VideoJobs(app) { gateway } }
    val assist: AssistController by lazy { AssistController(this, app) }
    val voice: Voice by lazy { Voice(app) }

    init {
        engines.onLoaded = { spec, accel, ms -> perf.recordLoad(spec.id, accel.label, ms) }
        scope.launch {
            settings.value.collect { s ->
                // Eco descarrega mais cedo (devolve a memória ao sistema mais rápido).
                val minutes = if (s.profile == Profile.ECO) minOf(1, s.unloadMinutes) else s.unloadMinutes
                engines.idleMillis = minutes.coerceAtLeast(1) * 60_000L
                voiceRateIfReady(s.voiceRate)
            }
        }
        if (safeMode) status += "Modo seguro: interface sem efeitos pesados"
        DiagLog.modules(status)
        // A avatar acompanha a geração de imagem (as outras tarefas avisam a presença diretamente).
        scope.launch {
            var running = false
            imageGen.state.collect { g ->
                when (g) {
                    is GenState.Running -> {
                        running = true
                        LilyPresence.working(PRESENCE_IMAGE, "Gerando imagem", g.fraction)
                    }
                    is GenState.Done -> if (running) LilyPresence.finished(PRESENCE_IMAGE, true, "Imagem pronta").also { running = false }
                    is GenState.Failed -> if (running) LilyPresence.finished(PRESENCE_IMAGE, false, "A imagem não saiu").also { running = false }
                    else -> if (running) LilyPresence.clear(PRESENCE_IMAGE).also { running = false }
                }
            }
        }
    }

    /** Modelos online podem ser usados agora (gateway configurado, aviso aceito e internet). */
    fun onlineReady(): Boolean {
        val s = settings.value.value
        return s.onlineConsent && s.online != OnlineMode.OFF && gateway.configured && gateway.online()
    }

    /** O que o roteador precisa: perfil, instalados, padrões e a escolha manual. */
    fun routerInput(manual: String? = null): RouterInput {
        val s = settings.value.value
        val ready = onlineReady()
        return RouterInput(
            profile = s.profile,
            installed = store.installedIds(),
            defaults = store.defaults(),
            manual = manual,
            remote = if (ready) gateway.cachedModels() else emptyList(),
            online = if (s.onlineConsent) s.online else OnlineMode.OFF,
            connected = ready,
        )
    }

    /** Memórias que a IA pode usar agora (vazio com a memória desligada). */
    fun activeMemories(): List<String> = if (settings.value.value.memoryEnabled) memory.active() else emptyList()

    /** O Android pediu memória: o modelo de conversa sai primeiro. */
    fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) engines.unload()
    }

    private var voiceCreated = false

    private fun voiceRateIfReady(rate: Float) {
        if (voiceCreated) voice.rate = rate
    }

    fun speak(text: String, done: (() -> Unit)? = null) {
        voiceCreated = true
        voice.rate = settings.value.value.voiceRate
        voice.speak(text, done)
    }

    companion object {
        const val SETTINGS = "Configurações"
        const val DEVICE = "Leitura do aparelho"
        const val MODELS = "Model Hub (modelos instalados)"
        const val DOWNLOADS = "Downloads de modelos"
        const val CONVERSATIONS = "Histórico de conversas"
        const val MEMORY = "Memória"
        const val ENGINE = "Motor de linguagem (LiteRT-LM)"
        const val PERF = "Medições de desempenho"
        const val IMAGE_GEN = "Geração de imagem"
        const val TOOLS = "Ferramentas"
        const val GATEWAY = "Serviço online (L.I.L.Y Gateway)"
        const val VIDEO = "Video AI"
        const val CODEX = "L.I.L.Y Codex"
        const val PRESENCE_IMAGE = "imagem"
    }
}

/** Reserva sem Keystore: não guarda a chave (o app segue só com os modelos do aparelho). */
private object NoKeystoreBox : app.lily.ai.remote.SecretBox {
    override fun seal(plain: String): String = throw IllegalStateException("Keystore indisponível neste aparelho")
    override fun open(sealed: String): String? = null
}
