package app.lily.shared.bridge

/**
 * Cópia do contrato do Cristal (os dois apps são independentes; os nomes precisam bater com
 * `app.cristal.cover.data.brief.BriefContract`).
 */
object CristalBrief {
    const val PACKAGE = "app.cristal.cover"
    const val PERMISSION = "app.cristal.cover.permission.INFO_BRIEF"
    const val ACTION_SUGGEST = "app.cristal.cover.action.BRIEF_SUGGEST"
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

    /** Sem resposta: o Cristal não está instalado, não tem a ponte ou não respondeu a tempo. */
    const val RESULT_NONE = 0
    const val RESULT_SHOWN = 1
    const val RESULT_STORED = 2
    const val RESULT_OFF = 3
    const val RESULT_DUPLICATE = 4
}

