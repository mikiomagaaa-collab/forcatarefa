package app.lily.ai.engine

import android.content.Context
import app.lily.ai.diagnostics.DiagLog
import com.google.mlkit.common.sdkinternal.MlKitContext

/**
 * Inicia o ML Kit na primeira vez que uma ferramenta precisa dele, e não na partida do app (o
 * provider dele foi tirado do manifesto). Se ele não iniciar neste aparelho, só as ferramentas de
 * texto da foto, rótulos, idioma, tradução e recorte ficam indisponíveis, com o motivo; o resto do
 * app segue.
 */
object MlKit {
    @Volatile
    private var app: Context? = null

    @Volatile
    private var ready = false

    fun attach(context: Context) {
        app = context.applicationContext ?: context
    }

    @Synchronized
    fun ensure() {
        if (ready) return
        val context = app ?: throw EngineRefused("O ML Kit ainda não está pronto.")
        try {
            DiagLog.step("ML Kit (primeiro uso)") { MlKitContext.initializeIfNeeded(context) }
            ready = true
        } catch (t: Throwable) {
            DiagLog.moduleFailed("ML Kit (texto da foto, rótulos, idioma, tradução, recorte)", t)
            throw EngineRefused("O ML Kit não iniciou neste aparelho: ${t.message ?: t.javaClass.simpleName}")
        }
    }
}
