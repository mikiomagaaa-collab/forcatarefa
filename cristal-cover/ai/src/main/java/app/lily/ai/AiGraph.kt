package app.lily.ai

import android.app.Application
import android.content.ComponentCallbacks2
import app.lily.ai.assist.AssistController
import app.lily.ai.core.DeviceMonitor
import app.lily.ai.core.Profile
import app.lily.ai.core.RouterInput
import app.lily.ai.data.ConversationStore
import app.lily.ai.data.MemoryStore
import app.lily.ai.data.PerfStore
import app.lily.ai.data.SettingsStore
import app.lily.ai.engine.EngineHost
import app.lily.ai.engine.LlmEngine
import app.lily.ai.engine.Voice
import app.lily.ai.imagegen.ImageGenJobs
import app.lily.ai.models.Downloads
import app.lily.ai.models.ModelStore
import app.lily.ai.tools.AiTools
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** As peças da L.I.L.Y AI, criadas uma vez por processo. */
class AiGraph(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val device = DeviceMonitor(app)
    val store = ModelStore(File(app.filesDir, "models"))
    val downloads = Downloads(app, store)
    val settings = SettingsStore(app)
    val conversations = ConversationStore(File(app.filesDir, "conversations"))
    val memory = MemoryStore(File(app.filesDir, "memory.json"))
    /** O motor de linguagem (LiteRT-LM). Só os testes trocam. */
    var engines: LlmEngine = EngineHost(app, store)
        @androidx.annotation.VisibleForTesting internal set
    val perf = PerfStore(File(app.filesDir, "perf.json"))
    val imageGen = ImageGenJobs(app)
    val tools = AiTools(this)
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
    }

    /** O que o roteador precisa: perfil, instalados, padrões e a escolha manual. */
    fun routerInput(manual: String? = null): RouterInput =
        RouterInput(settings.value.value.profile, store.installedIds(), store.defaults(), manual)

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
}
