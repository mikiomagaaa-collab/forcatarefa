package app.lily.ai.models

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Immutable
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** O estado de um download no Model Hub. */
@Immutable
sealed interface DlState {
    data object Queued : DlState
    data class Running(val progress: DownloadProgress) : DlState
    data class Paused(val done: Long, val total: Long) : DlState
    data class Failed(val message: String) : DlState
}

/**
 * A fila de downloads, compartilhada entre o Model Hub e o [DownloadService] (que faz o trabalho em
 * primeiro plano, com a notificação que a Liquid Island mostra).
 */
class Downloads(private val context: Context, private val store: ModelStore) {
    private val _states = MutableStateFlow<Map<String, DlState>>(emptyMap())
    val states: StateFlow<Map<String, DlState>> = _states.asStateFlow()
    private val pauseFlags = ConcurrentHashMap<String, Boolean>()
    private val cancelFlags = ConcurrentHashMap<String, Boolean>()

    fun start(spec: ModelSpec) {
        pauseFlags.remove(spec.id)
        cancelFlags.remove(spec.id)
        set(spec.id, DlState.Queued)
        context.startForegroundService(Intent(context, DownloadService::class.java).setAction(DownloadService.ACTION_RUN))
    }

    fun pause(id: String) {
        pauseFlags[id] = true
    }

    /** Pausa tudo (o Android pediu para encerrar o serviço): as partes ficam no disco para retomar. */
    fun pauseAll() {
        _states.value.forEach { (id, state) ->
            pauseFlags[id] = true
            if (state is DlState.Queued) set(id, DlState.Paused(0, Catalog.byId(id)?.totalBytes ?: 0))
        }
    }

    fun resume(spec: ModelSpec) = start(spec)

    fun cancel(spec: ModelSpec) {
        cancelFlags[spec.id] = true
        pauseFlags[spec.id] = true
        _states.update { it - spec.id }
        store.discardPartial(spec)
    }

    internal fun isPaused(id: String): Boolean = pauseFlags[id] == true
    internal fun isCancelled(id: String): Boolean = cancelFlags[id] == true

    internal fun next(): ModelSpec? = _states.value.entries.firstOrNull { it.value is DlState.Queued }?.key?.let(Catalog::byId)

    internal fun set(id: String, state: DlState?) {
        _states.update { if (state == null) it - id else it + (id to state) }
    }

    /** Partes no disco de um download que não terminou (para o Model Hub oferecer "Retomar"). */
    fun pausedOnDisk(spec: ModelSpec): Long = if (store.isInstalled(spec)) 0 else store.bytesOnDisk(spec)
}
