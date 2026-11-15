package app.cristal.cover.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import app.cristal.cover.core.AppScope
import app.cristal.cover.core.IoDispatcher
import app.cristal.cover.core.Logx
import app.cristal.cover.core.safely
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class AgendaEvent(
    val id: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val color: Int,
    val location: String,
)

@Immutable
data class AgendaState(
    val hasPermission: Boolean = false,
    val events: List<AgendaEvent> = emptyList(),
)

/** Próximos compromissos da agenda do aparelho (somente leitura). */
@Singleton
class CalendarRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope private val scope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val _state = MutableStateFlow(AgendaState())
    val state: StateFlow<AgendaState> = _state.asStateFlow()

    private var observing = false
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Recarrega os eventos. Barato o bastante para chamar a cada onResume. */
    fun refresh() {
        scope.launch {
            val granted = hasPermission()
            if (granted && !observing) {
                observing = safely("observar agenda") {
                    context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
                    true
                } ?: false
            }
            val events = if (granted) withContext(io) { query() } else emptyList()
            _state.value = AgendaState(hasPermission = granted, events = events)
        }
    }

    private fun query(): List<AgendaEvent> {
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().let {
            ContentUris.appendId(it, now - HOUR_MS)
            ContentUris.appendId(it, now + WINDOW_MS)
            it.build()
        }
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.EVENT_LOCATION,
        )
        val out = ArrayList<AgendaEvent>()
        try {
            context.contentResolver.query(
                uri,
                projection,
                "${CalendarContract.Instances.VISIBLE} = 1",
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { c ->
                while (c.moveToNext() && out.size < MAX_EVENTS) {
                    val end = c.getLong(3)
                    if (end < now) continue
                    out += AgendaEvent(
                        id = c.getLong(0),
                        title = c.getString(1).orEmpty().ifBlank { "(Sem título)" },
                        begin = c.getLong(2),
                        end = end,
                        allDay = c.getInt(4) != 0,
                        color = c.getInt(5),
                        location = c.getString(6).orEmpty(),
                    )
                }
            }
        } catch (t: Throwable) {
            // SecurityException se a permissão foi revogada, ou provedor de agenda indisponível.
            Logx.w("Falha ao consultar a agenda", t)
        }
        return out
    }

    private companion object {
        const val HOUR_MS = 60 * 60 * 1000L
        const val WINDOW_MS = 7 * 24 * HOUR_MS
        const val MAX_EVENTS = 8
    }
}
