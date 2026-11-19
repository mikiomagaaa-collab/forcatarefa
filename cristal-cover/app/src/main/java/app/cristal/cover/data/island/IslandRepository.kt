package app.cristal.cover.data.island

import app.cristal.cover.core.AppScope
import app.cristal.cover.data.battery.BatteryRepository
import app.cristal.cover.data.media.MediaRepository
import app.cristal.cover.data.notifications.NotificationRepository
import app.cristal.cover.data.prefs.SettingsRepository
import app.cristal.cover.data.system.QuickSettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Fonte de dados da Liquid Island: junta música, notificações (chamadas, timers e as que acabaram
 * de chegar), carregamento e modo silencioso num único [IslandState].
 */
@Singleton
class IslandRepository @Inject constructor(
    settingsRepository: SettingsRepository,
    notificationRepository: NotificationRepository,
    mediaRepository: MediaRepository,
    batteryRepository: BatteryRepository,
    quickSettingsRepository: QuickSettingsRepository,
    @AppScope private val scope: CoroutineScope,
) {
    private val transient = MutableStateFlow<IslandActivity?>(null)
    private var transientJob: Job? = null
    private var seenKeys: Set<String>? = null

    val state: StateFlow<IslandState> = combine(
        settingsRepository.settings,
        notificationRepository.items,
        mediaRepository.state,
        transient,
    ) { settings, notifications, media, flash -> IslandLogic.compute(settings, notifications, media, flash) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), IslandState())

    init {
        scope.launch {
            notificationRepository.items.collect { list ->
                val keys = list.map { it.key }.toSet()
                val seen = seenKeys
                seenKeys = keys
                // Na primeira leitura tudo já existia: só avisa do que chegar depois.
                if (seen == null) return@collect
                val now = System.currentTimeMillis()
                val fresh = list.filter { it.key !in seen && IslandLogic.isAlertWorthy(it, now) }.maxByOrNull { it.postTime }
                if (fresh != null) flash(IslandActivity.Alert(fresh), ALERT_MS)
            }
        }
        scope.launch {
            batteryRepository.state.map { it.charging }.distinctUntilChanged().drop(1).collect { charging ->
                if (charging) flash(IslandActivity.Charging(batteryRepository.state.value.percent), CHARGING_MS)
            }
        }
        scope.launch {
            quickSettingsRepository.state.map { it.ringerMuted }.distinctUntilChanged().drop(1).collect { muted ->
                flash(IslandActivity.Ringer(muted), RINGER_MS)
            }
        }
    }

    /** Mostra um aviso passageiro por [millis]. */
    fun flash(activity: IslandActivity, millis: Long) {
        transientJob?.cancel()
        transient.value = activity
        transientJob = scope.launch {
            delay(millis)
            if (transient.value == activity) transient.value = null
        }
    }

    /** Some com o aviso atual (o usuário tocou ou deslizou). */
    fun dismissTransient() {
        transientJob?.cancel()
        transient.value = null
    }

    private companion object {
        const val ALERT_MS = 4_800L
        const val CHARGING_MS = 3_200L
        const val RINGER_MS = 2_000L
    }
}
