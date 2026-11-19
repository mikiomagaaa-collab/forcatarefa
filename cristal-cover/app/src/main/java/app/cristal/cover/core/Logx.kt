package app.cristal.cover.core

import android.util.Log

/** Log centralizado: nunca deixa uma exceção de log derrubar o app. */
object Logx {
    private const val TAG = "Cristal"

    fun d(msg: String) {
        runCatching { Log.d(TAG, msg) }
    }

    fun w(msg: String, t: Throwable? = null) {
        runCatching { if (t != null) Log.w(TAG, msg, t) else Log.w(TAG, msg) }
    }

    fun e(msg: String, t: Throwable? = null) {
        runCatching { if (t != null) Log.e(TAG, msg, t) else Log.e(TAG, msg) }
    }
}

/** Executa [block] e devolve `null` (com log) se algo falhar. */
inline fun <T> safely(what: String, block: () -> T): T? = try {
    block()
} catch (t: Throwable) {
    if (t is kotlinx.coroutines.CancellationException) throw t
    Logx.w("Falha em: $what", t)
    null
}
