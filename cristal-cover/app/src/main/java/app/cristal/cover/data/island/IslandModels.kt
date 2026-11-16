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

    /** Atividade de outro app: entrega, corrida, navegação ou algo com progresso. */
    data class Live(val live: LiveActivityData) : IslandActivity {
        override val id: String get() = "live:${live.item.key}"
    }

    /** Sugestão do Info Brief (compromisso reconhecido no teclado), à espera de confirmação. */
    data class Brief(val suggestion: BriefSuggestion) : IslandActivity {
        override val id: String get() = "brief:${suggestion.id}"
    }
}

/** Compromisso reconhecido pelo Info Brief, para a ilha oferecer o lembrete. */
@Immutable
data class BriefSuggestion(
    val id: Long,
    val title: String,
    /** Momento sugerido (ms do relógio); a data pode precisar de confirmação. */
    val whenMillis: Long?,
    val dateConfirmed: Boolean,
    /** event, task, place, recurring ou trip. */
    val kind: String,
    /** Só a expressão reconhecida ("às 7 horas da noite"), nunca a conversa. */
    val sourceText: String,
    /** O horário foi dito com clareza (sem ele, a confirmação pergunta). */
    val timeConfirmed: Boolean = whenMillis != null,
    /** Repete toda semana ("toda segunda"). */
    val weekly: Boolean = false,
    /** Lugar citado ("shopping"), para abrir no mapa. */
    val place: String? = null,
    /** O lembrete já foi criado: a ilha mostra a confirmação. */
    val created: Boolean = false,
) {
    /** Dá para criar direto da ilha, sem perguntar nada. */
    val ready: Boolean get() = whenMillis != null && dateConfirmed && timeConfirmed
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
        val ongoing = ArrayList<IslandActivity>(4)
        val lives = if (settings.islandLive) {
            notifications.mapNotNull(LiveIntegration::toLive).sortedBy(LiveIntegration::rank)
        } else {
            emptyList()
        }
        val liveKeys = lives.map { it.item.key }.toSet()
        if (settings.islandCalls) notifications.firstOrNull { it.isCall }?.let { ongoing += IslandActivity.Call(it) }
        // Navegação e entregas/corridas chegando vêm antes do timer e da música.
        lives.filter { LiveIntegration.rank(it) <= 1 }.forEach { ongoing += IslandActivity.Live(it) }
        if (settings.islandCalls) {
            notifications.firstOrNull { !it.isCall && it.ongoing && it.chronometerBase != null && it.key !in liveKeys }
                ?.let { ongoing += IslandActivity.Timer(it) }
        }
        if (settings.islandMedia && media != null && media.isPlaying) ongoing += IslandActivity.Media(media)
        lives.filter { LiveIntegration.rank(it) > 1 }.forEach { ongoing += IslandActivity.Live(it) }
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
