package app.lily.ai

import android.app.Application
import app.lily.ai.diagnostics.CrashReport

class LilyAiApp : Application() {
    lateinit var graph: AiGraph
        private set

    override fun onCreate() {
        super.onCreate()
        // A tela do relatório de erro roda num processo próprio, sem o resto do app.
        if (getProcessName().endsWith(CrashReport.PROCESS_SUFFIX)) return
        CrashReport.install(this)
        graph = AiGraph(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::graph.isInitialized) graph.onTrimMemory(level)
    }
}
