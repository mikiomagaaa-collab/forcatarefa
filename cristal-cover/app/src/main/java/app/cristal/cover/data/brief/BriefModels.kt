package app.cristal.cover.data.brief

import androidx.compose.runtime.Immutable
import app.cristal.cover.data.island.BriefSuggestion
import kotlinx.serialization.Serializable

/**
 * Um lembrete do Cristal. Com [at] é um compromisso que avisa na hora; sem ele, uma tarefa que
 * fica na lista do Now Brief até ser concluída.
 */
@Immutable
@Serializable
data class Reminder(
    val id: Long,
    val title: String,
    val at: Long? = null,
    val created: Long,
    val kind: String = "event",
    /** Toca como alarme (som contínuo até tocar na notificação). */
    val alarm: Boolean = false,
    val weekly: Boolean = false,
    val place: String? = null,
    val done: Boolean = false,
    /** Quando avisou pela última vez (para não avisar duas vezes). */
    val firedAt: Long? = null,
    /** De onde veio: "info_brief" ou "manual". */
    val source: String = "manual",
)

/** Uma sugestão do Info Brief no histórico. */
@Immutable
@Serializable
data class BriefRecord(
    val id: Long,
    val title: String,
    val kind: String,
    val whenMillis: Long? = null,
    val dateConfirmed: Boolean = false,
    val timeConfirmed: Boolean = false,
    val weekly: Boolean = false,
    val place: String? = null,
    /** Só a expressão reconhecida, nunca a mensagem. */
    val expression: String = "",
    val received: Long,
    val status: String = PENDING,
    val reminderId: Long? = null,
) {
    fun toSuggestion(): BriefSuggestion = BriefSuggestion(
        id = id,
        title = title,
        whenMillis = whenMillis,
        dateConfirmed = dateConfirmed,
        kind = kind,
        sourceText = expression,
        timeConfirmed = timeConfirmed,
        weekly = weekly,
        place = place,
    )

    companion object {
        const val PENDING = "pending"
        const val ACCEPTED = "accepted"
        const val DECLINED = "declined"

        fun of(s: BriefSuggestion, now: Long): BriefRecord = BriefRecord(
            id = s.id,
            title = s.title,
            kind = s.kind,
            whenMillis = s.whenMillis,
            dateConfirmed = s.dateConfirmed,
            timeConfirmed = s.timeConfirmed,
            weekly = s.weekly,
            place = s.place,
            expression = s.sourceText,
            received = now,
        )
    }
}

/** A esfera do Info Brief voando do teclado até a ilha (posição de partida na tela, em px). */
data class BriefFlight(val suggestion: BriefSuggestion, val originX: Float, val originY: Float)

/** Rótulos do tipo de sugestão. */
object BriefLabels {
    fun found(kind: String): String = when (kind) {
        "task" -> "Tarefa identificada"
        "place" -> "Lugar identificado"
        "recurring" -> "Rotina identificada"
        "trip" -> "Viagem identificada"
        else -> "Compromisso identificado"
    }

    fun action(kind: String): String = when (kind) {
        "task" -> "Adicionar tarefa"
        "trip" -> "Organizar viagem"
        else -> "Criar lembrete"
    }

    fun status(status: String): String = when (status) {
        BriefRecord.ACCEPTED -> "Criado"
        BriefRecord.DECLINED -> "Dispensado"
        else -> "Aguardando"
    }
}
