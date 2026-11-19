package app.lily.ai

import kotlinx.coroutines.launch
import android.app.Application
import android.content.Context
import androidx.work.Configuration
import app.lily.ai.diagnostics.CrashReport
import app.lily.ai.diagnostics.DiagLog
import app.lily.ai.engine.MlKit

class LilyAiApp : Application(), Configuration.Provider {
    private var core: AiGraph? = null

    /**
     * O WorkManager inicia sob demanda (não na partida): só se uma biblioteca pedir. Registra no
     * diagnóstico quando isso acontece.
     */
    override val workManagerConfiguration: Configuration
        get() {
            runCatching { DiagLog.line("WorkManager iniciado sob demanda") }
            return Configuration.Builder().build()
        }

    /** O núcleo (as telas conferem [graphOrNull] antes; os serviços só rodam depois dele). */
    val graph: AiGraph get() = core ?: error("O núcleo da L.I.L.Y não iniciou: ${startupError?.message}")

    /** O núcleo, ou null se ele não iniciou (as telas mostram o diagnóstico em vez de fechar). */
    val graphOrNull: AiGraph? get() = core

    /** Só para os testes: troca o núcleo (por um com módulos falhando, ou nenhum). */
    @androidx.annotation.VisibleForTesting
    internal fun replaceGraph(graph: AiGraph?) {
        core = graph
    }

    /** O erro que impediu o núcleo de iniciar, se houve. */
    var startupError: Throwable? = null
        private set

    /** O processo da tela de diagnóstico roda sozinho, sem o resto do app. */
    private val diagnosticsProcess: Boolean by lazy { getProcessName().endsWith(CrashReport.PROCESS_SUFFIX) }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        if (diagnosticsProcess) return
        // O mais cedo possível: antes dos providers das bibliotecas e de qualquer parte do app.
        runCatching { DiagLog.begin(this) }
        runCatching { CrashReport.install(this) }
    }

    override fun onCreate() {
        super.onCreate()
        if (diagnosticsProcess) return
        DiagLog.line("Application.onCreate")
        MlKit.attach(this)
        try {
            core = DiagLog.step("Núcleo da L.I.L.Y (AiGraph)") { AiGraph(this, safeMode = DiagLog.safeMode) }
        } catch (t: Throwable) {
            // Um módulo obrigatório falhou: o app não fecha; as telas mostram o diagnóstico.
            startupError = t
            runCatching { CrashReport.save(this, Thread.currentThread().name, t, fatal = false) }
        }
        // Avatar flutuante (opcional): só observa; nada aparece sem a pessoa ligar e permitir.
        runCatching { app.lily.ai.presence.AppVisibility.install(this) }
        core?.let { graph -> runCatching { DiagLog.step("Avatar flutuante") { graph.overlay.start() } } }
        // Com o serviço online pronto, atualiza a lista de modelos em segundo plano (o grátis entra no roteador).
        core?.let { graph ->
            graph.scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                if (runCatching { graph.onlineReady() }.getOrDefault(false)) {
                    runCatching {
                        graph.gateway.status()
                        graph.gateway.codeModels(refresh = true)
                    }
                }
            }
        }
        DiagLog.appReady()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        core?.onTrimMemory(level)
    }
}
