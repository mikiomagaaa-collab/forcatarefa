package app.cristal.cover.data.brief

import android.content.Context
import app.cristal.cover.core.AppScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Lembretes e tarefas do Cristal, guardados num arquivo pequeno (fora do banco da tela inicial) e
 * avisados pelo AlarmManager. Nada é criado sem a confirmação de quem usa.
 */
@Singleton
class ReminderRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    json: Json,
    @AppScope private val scope: CoroutineScope,
) {
    private val store = JsonFileStore(File(context.filesDir, "brief/lembretes.json"), ListSerializer(Reminder.serializer()), json, emptyList())
    private val mutex = Mutex()
    private val loaded = CompletableDeferred<Unit>()
    private val _reminders = MutableStateFlow<List<Reminder>>(emptyList())

    /** Todos os lembretes e tarefas (os concluídos somem depois de um mês). */
    val reminders: StateFlow<List<Reminder>> = _reminders.asStateFlow()

    init {
        scope.launch {
            val now = System.currentTimeMillis()
            _reminders.value = store.read().filter { !it.done || now - (it.at ?: it.created) < MONTH }
            loaded.complete(Unit)
        }
    }

    suspend fun create(
        title: String,
        at: Long?,
        kind: String = "event",
        alarm: Boolean = false,
        weekly: Boolean = false,
        place: String? = null,
        source: String = "manual",
    ): Reminder {
        val now = System.currentTimeMillis()
        val reminder = edit { list ->
            val id = maxOf(now, (list.maxOfOrNull { it.id } ?: 0L) + 1)
            val r = Reminder(id, title.trim().take(80), at, now, kind, alarm, weekly && at != null, place, source = source)
            (list + r) to r
        }
        ReminderScheduler.schedule(context, reminder)
        return reminder
    }

    /** Concluir: a tarefa sai da lista; o que repete toda semana continua (só esta vez acaba). */
    suspend fun complete(id: Long) {
        val r = edit { list ->
            val r = list.firstOrNull { it.id == id } ?: return@edit list to null
            if (r.weekly) list to r else list.map { if (it.id == id) it.copy(done = true) else it } to r
        } ?: return
        ReminderNotifier.cancel(context, id)
        if (!r.weekly) ReminderScheduler.cancel(context, id)
    }

    suspend fun snooze(id: Long, minutes: Int) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        val r = edit { list ->
            val r = list.firstOrNull { it.id == id } ?: return@edit list to null
            val snoozed = r.copy(at = at, firedAt = null, done = false, weekly = false)
            list.map { if (it.id == id) snoozed else it } to snoozed
        } ?: return
        ReminderNotifier.cancel(context, id)
        ReminderScheduler.schedule(context, r)
    }

    suspend fun delete(id: Long) {
        edit { list -> list.filterNot { it.id == id } to Unit }
        ReminderScheduler.cancel(context, id)
        ReminderNotifier.cancel(context, id)
    }

    /**
     * Chegou a hora: marca o aviso e, se repete, agenda a próxima semana. Devolve o lembrete a
     * avisar (null se não há nada a avisar, por exemplo um aviso antigo de algo já adiado).
     */
    suspend fun fire(id: Long): Reminder? {
        val now = System.currentTimeMillis()
        var next: Reminder? = null
        val fired = edit { list ->
            val r = list.firstOrNull { it.id == id && !it.done } ?: return@edit list to null
            val at = r.at ?: return@edit list to null
            if (at > now + 60_000L) return@edit list to null
            if (r.firedAt != null && r.firedAt >= at) return@edit list to null
            val updated = if (r.weekly) r.copy(at = nextWeek(at, now), firedAt = null) else r.copy(firedAt = now)
            if (r.weekly) next = updated
            list.map { if (it.id == id) updated else it } to r
        }
        next?.let { ReminderScheduler.schedule(context, it) }
        return fired
    }

    /** Depois de ligar o aparelho ou liberar o alarme exato: agenda tudo de novo e avisa o que passou. */
    suspend fun rescheduleAll(): List<Reminder> {
        loaded.await()
        val now = System.currentTimeMillis()
        val missed = ArrayList<Reminder>()
        for (r in _reminders.value) {
            val at = r.at ?: continue
            if (r.done) continue
            if (at > now) {
                ReminderScheduler.schedule(context, r)
            } else if (r.firedAt == null || r.firedAt < at) {
                fire(r.id)?.let(missed::add)
            }
        }
        return missed
    }

    private suspend fun <R> edit(block: (List<Reminder>) -> Pair<List<Reminder>, R>): R {
        loaded.await()
        return mutex.withLock {
            val (next, result) = block(_reminders.value)
            if (next !== _reminders.value) {
                _reminders.value = next
                store.write(next)
            }
            result
        }
    }

    companion object {
        private const val MONTH = 30L * 86_400_000L

        /** A mesma hora, uma ou mais semanas depois, até passar de agora. */
        fun nextWeek(at: Long, now: Long): Long {
            val c = Calendar.getInstance().apply { timeInMillis = at }
            do c.add(Calendar.WEEK_OF_YEAR, 1) while (c.timeInMillis <= now)
            return c.timeInMillis
        }
    }
}
