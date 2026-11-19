package app.lily.ai.presence

import android.os.SystemClock
import androidx.compose.runtime.Immutable
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** O que a L.I.L.Y está fazendo, do jeito que a avatar mostra. */
enum class PresenceMode(val label: String) {
    IDLE("Pronta"),
    LISTENING("Ouvindo"),
    THINKING("Pensando"),
    SPEAKING("Falando"),
    WORKING("Trabalhando"),
    SUCCESS("Concluído"),
    ERROR("Algo deu errado"),
}

/**
 * O estado visível da L.I.L.Y. [progress] = 0..1 quando a tarefa informa (null = sem medida);
 * [level] = intensidade do áudio (ouvindo ou falando); [label] = o que está fazendo, em poucas palavras;
 * [source] = o módulo (conversa, voz, codex, vídeo, imagem, downloads).
 */
@Immutable
data class Presence(
    val mode: PresenceMode = PresenceMode.IDLE,
    val label: String? = null,
    val progress: Float? = null,
    val level: Float = 0f,
    val source: String? = null,
    /** Quantas tarefas estão em andamento ao mesmo tempo. */
    val active: Int = 0,
)

/**
 * Um único estado de presença para o app inteiro: cada módulo informa o que está fazendo e a avatar
 * (dentro do app, na sobreposição e na tela externa) mostra o mais importante. Sucesso e erro aparecem
 * por alguns segundos e voltam ao estado anterior. Nada aqui liga câmera ou microfone: só reflete o que
 * os módulos já estão fazendo por pedido da pessoa.
 */
object LilyPresence {
    private data class Entry(val presence: Presence, val since: Long, val until: Long? = null)

    private val entries = ConcurrentHashMap<String, Entry>()
    private val _state = MutableStateFlow(Presence())
    val state: StateFlow<Presence> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var expiry: Job? = null

    /** Relógio (os testes trocam). */
    @Volatile var clock: () -> Long = { SystemClock.elapsedRealtime() }

    const val TRANSIENT_MS = 2_600L

    /** Prioridade de cada modo quando há vários ao mesmo tempo (maior aparece). */
    private fun rank(mode: PresenceMode): Int = when (mode) {
        PresenceMode.LISTENING -> 7
        PresenceMode.SPEAKING -> 6
        PresenceMode.ERROR -> 5
        PresenceMode.SUCCESS -> 4
        PresenceMode.THINKING -> 3
        PresenceMode.WORKING -> 2
        PresenceMode.IDLE -> 0
    }

    fun listening(source: String, level: Float = 0f) = put(source, Presence(PresenceMode.LISTENING, "Ouvindo", null, level.coerceIn(0f, 1f), source))

    fun speaking(source: String, level: Float) = put(source, Presence(PresenceMode.SPEAKING, "Falando", null, level.coerceIn(0f, 1f), source))

    fun thinking(source: String, label: String = "Pensando") = put(source, Presence(PresenceMode.THINKING, label, null, 0f, source))

    fun working(source: String, label: String, progress: Float? = null) =
        put(source, Presence(PresenceMode.WORKING, label, progress?.coerceIn(0f, 1f), 0f, source))

    /** A tarefa terminou: mostra sucesso ou erro por alguns segundos. */
    fun finished(source: String, success: Boolean, label: String? = null) {
        val now = clock()
        val p = Presence(if (success) PresenceMode.SUCCESS else PresenceMode.ERROR, label ?: if (success) "Concluído" else "Não deu certo", null, 0f, source)
        entries[source] = Entry(p, now, now + TRANSIENT_MS)
        recompute()
        scheduleExpiry()
    }

    /** O módulo parou sem resultado para mostrar (cancelado, painel fechado). */
    fun clear(source: String) {
        if (entries.remove(source) != null) recompute()
    }

    /** Só para os testes. */
    fun reset() {
        entries.clear()
        expiry?.cancel()
        _state.value = Presence()
    }

    private fun put(source: String, presence: Presence) {
        val previous = entries[source]
        val since = if (previous?.presence?.mode == presence.mode) previous.since else clock()
        entries[source] = Entry(presence, since)
        recompute()
    }

    /** Recalcula o estado (também tira os transitórios vencidos). Público para os testes. */
    fun recompute() {
        val now = clock()
        entries.entries.removeIf { (_, e) -> e.until != null && e.until <= now }
        val live = entries.values
        val top = live.maxWithOrNull(compareBy<Entry> { rank(it.presence.mode) }.thenBy { it.since })
        val active = live.count { it.presence.mode == PresenceMode.WORKING || it.presence.mode == PresenceMode.THINKING }
        _state.value = top?.presence?.copy(active = active) ?: Presence()
    }

    private fun scheduleExpiry() {
        expiry?.cancel()
        expiry = scope.launch {
            while (true) {
                val now = clock()
                val next = entries.values.mapNotNull { it.until }.minOrNull() ?: break
                delay((next - now).coerceAtLeast(16))
                recompute()
            }
        }
    }
}
