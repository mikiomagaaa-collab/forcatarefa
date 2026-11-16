package app.cristal.cover.data.brief

import android.content.Context
import android.view.Display
import app.cristal.cover.core.AppScope
import app.cristal.cover.data.island.BriefSuggestion
import app.cristal.cover.data.island.IslandActivity
import app.cristal.cover.data.island.IslandRepository
import app.cristal.cover.data.prefs.SettingsRepository
import app.cristal.cover.ui.brief.BriefConfirmActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * O lado do Cristal no Info Brief. Recebe do teclado o que ele entendeu, mostra na Liquid Island,
 * guarda o histórico e cria o lembrete só depois da confirmação. É o centro dos eventos do Info
 * Brief: teclado → ilha → lembrete → Now Brief.
 */
@Singleton
class BriefRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val islandRepository: IslandRepository,
    private val reminders: ReminderRepository,
    json: Json,
    @AppScope private val scope: CoroutineScope,
) {
    private val store = JsonFileStore(File(context.filesDir, "brief/historico.json"), ListSerializer(BriefRecord.serializer()), json, emptyList())
    private val mutex = Mutex()
    private val loaded = CompletableDeferred<Unit>()
    private val _history = MutableStateFlow<List<BriefRecord>>(emptyList())

    /** Histórico das sugestões (mais recentes primeiro). */
    val history: StateFlow<List<BriefRecord>> = _history.asStateFlow()

    /** Sugestões ainda sem resposta e não vencidas: aparecem no Now Brief. */
    val pending: Flow<List<BriefSuggestion>> = history.map { list ->
        val now = System.currentTimeMillis()
        list.filter { it.status == BriefRecord.PENDING && (it.whenMillis == null || it.whenMillis > now - HOUR) && now - it.received < WEEK }
            .map { it.toSuggestion() }
    }

    private val _flights = MutableSharedFlow<BriefFlight>(extraBufferCapacity = 4)

    /** A esfera a caminho da ilha: a sobreposição da ilha anima e depois chama [show]. */
    val flights: SharedFlow<BriefFlight> = _flights.asSharedFlow()

    /** A Liquid Island está na tela interna e pode receber a esfera (marcado pela sobreposição). */
    val islandReady = MutableStateFlow(false)

    init {
        scope.launch {
            _history.value = store.read().sortedByDescending { it.received }.take(MAX_HISTORY)
            loaded.complete(Unit)
        }
    }

    /** O teclado mandou uma sugestão. Devolve um dos códigos de [BriefContract]. */
    fun receive(s: BriefSuggestion, originX: Float?, originY: Float?, displayId: Int): Int {
        if (!settingsRepository.settings.value.briefEnabled) return BriefContract.RESULT_OFF
        val now = System.currentTimeMillis()
        val duplicate = _history.value.any {
            it.title.equals(s.title, ignoreCase = true) && it.whenMillis == s.whenMillis && now - it.received < DUPLICATE_MS
        }
        if (duplicate) return BriefContract.RESULT_DUPLICATE
        scope.launch { edit { listOf(BriefRecord.of(s, now)) + it } }
        if (!islandReady.value || displayId != Display.DEFAULT_DISPLAY) return BriefContract.RESULT_STORED
        if (originX != null && originY != null) _flights.tryEmit(BriefFlight(s, originX, originY)) else show(s)
        return BriefContract.RESULT_SHOWN
    }

    /** A esfera chegou: a ilha abre a sugestão. */
    fun show(s: BriefSuggestion) = islandRepository.flash(IslandActivity.Brief(s), SHOW_MS)

    /**
     * "Criar lembrete": com data, hora e notificações em ordem, cria na hora e a ilha confirma;
     * senão abre a confirmação para ajustar (nada é agendado sem ela).
     */
    fun accept(s: BriefSuggestion, displayId: Int = Display.DEFAULT_DISPLAY) {
        if (!s.ready || s.kind != "event" || !ReminderNotifier.enabled(context)) {
            islandRepository.dismissTransient()
            BriefConfirmActivity.open(context, s, displayId)
            return
        }
        scope.launch {
            create(s, s.title, s.whenMillis, settingsRepository.settings.value.briefAlarm, s.weekly)
            if (islandReady.value) islandRepository.flash(IslandActivity.Brief(s.copy(created = true)), CONFIRM_MS)
        }
    }

    /** Cria o lembrete (ou a tarefa, sem horário) e marca a sugestão como aceita. */
    suspend fun create(s: BriefSuggestion?, title: String, at: Long?, alarm: Boolean, weekly: Boolean, place: String? = s?.place): Reminder {
        val r = reminders.create(title, at, s?.kind ?: "event", alarm, weekly, place, source = if (s != null) "info_brief" else "manual")
        if (s != null) mark(s.id, BriefRecord.ACCEPTED, r.id)
        return r
    }

    /** "Agora não": some da ilha e fica no histórico como dispensada. */
    fun later(s: BriefSuggestion) {
        islandRepository.dismissTransient()
        scope.launch { mark(s.id, BriefRecord.DECLINED, null) }
    }

    fun clearHistory() {
        scope.launch { edit { emptyList() } }
    }

    private suspend fun mark(id: Long, status: String, reminderId: Long?) {
        edit { list -> list.map { if (it.id == id) it.copy(status = status, reminderId = reminderId ?: it.reminderId) else it } }
    }

    private suspend fun edit(block: (List<BriefRecord>) -> List<BriefRecord>) {
        loaded.await()
        mutex.withLock {
            val next = block(_history.value).take(MAX_HISTORY)
            _history.value = next
            store.write(next)
        }
    }

    private companion object {
        const val SHOW_MS = 14_000L
        const val CONFIRM_MS = 2_800L
        const val DUPLICATE_MS = 10 * 60_000L
        const val HOUR = 3_600_000L
        const val WEEK = 7 * 86_400_000L
        const val MAX_HISTORY = 200
    }
}
