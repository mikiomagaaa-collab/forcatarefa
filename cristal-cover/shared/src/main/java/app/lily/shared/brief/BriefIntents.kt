package app.lily.shared.brief

import android.content.Intent
import app.lily.shared.bridge.CristalBrief
import java.time.ZoneId

/** Escreve um compromisso reconhecido no formato que o Cristal lê (Info Brief). */
object BriefIntents {
    fun fill(intent: Intent, c: BriefCandidate, id: Long): Intent = intent
        .setPackage(CristalBrief.PACKAGE)
        .putExtra(CristalBrief.EXTRA_ID, id)
        .putExtra(CristalBrief.EXTRA_TITLE, c.title)
        .putExtra(CristalBrief.EXTRA_KIND, c.kind.wire)
        .putExtra(CristalBrief.EXTRA_WHEN, c.at?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: -1L)
        .putExtra(CristalBrief.EXTRA_DATE_CONFIRMED, c.dateConfirmed)
        .putExtra(CristalBrief.EXTRA_TIME_CONFIRMED, c.timeConfirmed)
        .putExtra(CristalBrief.EXTRA_WEEKLY, c.weekly)
        .putExtra(CristalBrief.EXTRA_PLACE, c.place)
        .putExtra(CristalBrief.EXTRA_EXPRESSION, c.expression)
}
