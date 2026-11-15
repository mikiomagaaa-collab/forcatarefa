package app.cristal.cover.data.island

import androidx.compose.runtime.Immutable
import app.cristal.cover.data.media.MediaState
import app.cristal.cover.data.notifications.NotificationItem
import app.cristal.cover.data.prefs.Settings

/** O que a Liquid Island pode mostrar. */
@Immutable
sealed interface IslandActivity {
    /** Identidade estável (a animação de troca só acontece quando muda). */
    val id: String

    data class Media(val media: MediaState) : IslandActivity {
        override val id: String get() = "media"
    }

    data class Call(val item: NotificationItem) : IslandActivity {
        override val id: String get() = "call:${item.key}"
    }

    data class Timer(val item: NotificationItem) : IslandActivity {
        override val id: String get() = "timer:${item.key}"
    }

    data class Charging(val percent: Int) : IslandActivity {
        override val id: String get() = "charging"
    }

    data class Ringer(val muted: Boolean) : IslandActivity {
        override val id: String get() = "ringer:$muted"
    }

    data class Alert(val item: NotificationItem) : IslandActivity {
        override val id: String get() = "alert:${item.key}:${item.postTime}"
    }
}

/**
 * Estado da ilha: a atividade principal (no centro) e, se houver outra contínua ao mesmo tempo,
 * uma bolinha separada à direita (a apresentação "mínima" do iPhone).
 */
@Immutable
data class IslandState(val primary: IslandActivity? = null, val secondary: IslandActivity? = null) {
    val isEmpty: Boolean get() = primary == null
}

object IslandLogic {
    /**
     * Decide o que mostrar. Avisos passageiros (notificação, carregando, modo silencioso) passam
     * na frente; depois chamada, timer e música, nessa ordem.
     */
    fun compute(
        settings: Settings,
        notifications: List<NotificationItem>,
        media: MediaState?,
        transient: IslandActivity?,
    ): IslandState {
        val ongoing = ArrayList<IslandActivity>(3)
        if (settings.islandCalls) {
            notifications.firstOrNull { it.isCall }?.let { ongoing += IslandActivity.Call(it) }
            notifications.firstOrNull { !it.isCall && it.ongoing && it.chronometerBase != null }?.let { ongoing += IslandActivity.Timer(it) }
        }
        if (settings.islandMedia && media != null && media.isPlaying) ongoing += IslandActivity.Media(media)
        val allowedTransient = when (transient) {
            is IslandActivity.Alert -> transient.takeIf { settings.islandAlerts }
            is IslandActivity.Charging -> transient.takeIf { settings.islandCharging }
            is IslandActivity.Ringer -> transient.takeIf { settings.islandRinger }
            else -> transient
        }
        return if (allowedTransient != null) {
            IslandState(allowedTransient, ongoing.firstOrNull())
        } else {
            IslandState(ongoing.getOrNull(0), ongoing.getOrNull(1))
        }
    }

    /** Notificações que merecem aparecer na ilha quando chegam (não contínuas, com texto, não de mídia). */
    fun isAlertWorthy(item: NotificationItem, nowMillis: Long): Boolean =
        !item.ongoing && (item.title.isNotBlank() || item.text.isNotBlank()) && nowMillis - item.postTime < 15_000L
}
