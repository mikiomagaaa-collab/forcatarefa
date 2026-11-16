package app.cristal.cover.data.brief

import android.content.Intent
import app.cristal.cover.data.island.BriefSuggestion

/**
 * Contrato da ponte entre o L.I.L.Y Keyboard e o Cristal. Os dois apps são assinados com a mesma
 * chave e a permissão é de assinatura: só eles conversam por aqui. O teclado manda apenas o que
 * entendeu (título, horário e a expressão reconhecida), nunca a conversa.
 *
 * O teclado tem uma cópia destas constantes (os apps são independentes).
 */
object BriefContract {
    const val PERMISSION = "app.cristal.cover.permission.INFO_BRIEF"
    const val ACTION_SUGGEST = "app.cristal.cover.action.BRIEF_SUGGEST"

    /** "Criar lembrete" tocado no próprio teclado (quando a ilha não pôde mostrar). */
    const val ACTION_ACCEPT = "app.cristal.cover.action.BRIEF_ACCEPT"

    const val EXTRA_ID = "id"
    const val EXTRA_TITLE = "title"
    const val EXTRA_KIND = "kind"
    const val EXTRA_WHEN = "when"
    const val EXTRA_DATE_CONFIRMED = "date_confirmed"
    const val EXTRA_TIME_CONFIRMED = "time_confirmed"
    const val EXTRA_WEEKLY = "weekly"
    const val EXTRA_PLACE = "place"
    const val EXTRA_EXPRESSION = "expression"
    const val EXTRA_ORIGIN_X = "origin_x"
    const val EXTRA_ORIGIN_Y = "origin_y"
    const val EXTRA_DISPLAY = "display"

    /** Respostas do broadcast ordenado. */
    const val RESULT_SHOWN = 1
    const val RESULT_STORED = 2
    const val RESULT_OFF = 3
    const val RESULT_DUPLICATE = 4

    val KINDS = setOf("event", "task", "place", "recurring", "trip")

    private const val MAX_TITLE = 60
    private const val MAX_EXPRESSION = 80
    private const val DAY = 86_400_000L

    /** Escreve a sugestão num Intent (o inverso de [parse]). */
    fun fill(intent: Intent, s: BriefSuggestion): Intent = intent
        .putExtra(EXTRA_ID, s.id)
        .putExtra(EXTRA_TITLE, s.title)
        .putExtra(EXTRA_KIND, s.kind)
        .putExtra(EXTRA_WHEN, s.whenMillis ?: -1L)
        .putExtra(EXTRA_DATE_CONFIRMED, s.dateConfirmed)
        .putExtra(EXTRA_TIME_CONFIRMED, s.timeConfirmed)
        .putExtra(EXTRA_WEEKLY, s.weekly)
        .putExtra(EXTRA_PLACE, s.place)
        .putExtra(EXTRA_EXPRESSION, s.sourceText)

    /** Lê e valida a sugestão (mesmo vinda de um app de confiança, nada passa sem conferir). */
    fun parse(intent: Intent, now: Long): BriefSuggestion? {
        val title = intent.getStringExtra(EXTRA_TITLE)?.trim()?.take(MAX_TITLE)?.takeIf { it.isNotBlank() } ?: return null
        val kind = intent.getStringExtra(EXTRA_KIND)?.takeIf { it in KINDS } ?: return null
        val whenMillis = intent.getLongExtra(EXTRA_WHEN, -1L).takeIf { it > 0 && it in (now - DAY)..(now + 400 * DAY) }
        val id = intent.getLongExtra(EXTRA_ID, 0L).takeIf { it != 0L } ?: now
        return BriefSuggestion(
            id = id,
            title = title,
            whenMillis = whenMillis,
            dateConfirmed = whenMillis != null && intent.getBooleanExtra(EXTRA_DATE_CONFIRMED, false),
            kind = kind,
            sourceText = intent.getStringExtra(EXTRA_EXPRESSION)?.take(MAX_EXPRESSION).orEmpty(),
            timeConfirmed = whenMillis != null && intent.getBooleanExtra(EXTRA_TIME_CONFIRMED, false),
            weekly = intent.getBooleanExtra(EXTRA_WEEKLY, false),
            place = intent.getStringExtra(EXTRA_PLACE)?.trim()?.take(MAX_TITLE)?.takeIf { it.isNotBlank() },
        )
    }
}
