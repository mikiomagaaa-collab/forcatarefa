package app.lily.ai

import android.app.Application

class LilyAiApp : Application() {
    lateinit var graph: AiGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AiGraph(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::graph.isInitialized) graph.onTrimMemory(level)
    }
}
