package app.lily.keyboard.brief

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.util.Log
import androidx.compose.ui.geometry.Offset
import java.time.ZoneId

/**
 * Cópia do contrato do Cristal (os dois apps são independentes; os nomes precisam bater com
 * `app.cristal.cover.data.brief.BriefContract`).
 */
internal object CristalBrief {
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

/**
 * A ponte com o Cristal, no próprio aparelho: um broadcast explícito para o pacote do Cristal,
 * protegido por permissão de assinatura (só apps com a mesma chave). Vai só o compromisso
 * entendido, nunca o texto digitado. Sem o Cristal, a sugestão abre a agenda do aparelho.
 */
open class BriefBridge(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())

    open fun installed(): Boolean = runCatching {
        context.packageManager.getPackageInfo(CristalBrief.PACKAGE, 0)
        true
    }.getOrDefault(false)

    /** O Cristal está instalado e o teclado tem a permissão da ponte (mesma assinatura). */
    open fun available(): Boolean =
        installed() && context.checkSelfPermission(CristalBrief.PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** Manda a sugestão; [onResult] recebe um dos códigos de [CristalBrief], na thread principal. */
    open fun suggest(c: BriefCandidate, id: Long, origin: Offset?, displayId: Int, onResult: (Int) -> Unit) {
        if (!available()) {
            onResult(CristalBrief.RESULT_NONE)
            return
        }
        val intent = fill(Intent(CristalBrief.ACTION_SUGGEST), c, id).putExtra(CristalBrief.EXTRA_DISPLAY, displayId)
        if (origin != null) intent.putExtra(CristalBrief.EXTRA_ORIGIN_X, origin.x).putExtra(CristalBrief.EXTRA_ORIGIN_Y, origin.y)
        var answered = false
        val timeout = Runnable {
            if (!answered) {
                answered = true
                onResult(CristalBrief.RESULT_NONE)
            }
        }
        main.postDelayed(timeout, TIMEOUT_MS)
        try {
            context.sendOrderedBroadcast(
                intent,
                null,
                object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context, result: Intent) {
                        if (answered) return
                        answered = true
                        main.removeCallbacks(timeout)
                        onResult(resultCode)
                    }
                },
                main,
                CristalBrief.RESULT_NONE,
                null,
                null,
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Não foi possível falar com o Cristal", t)
            if (!answered) {
                answered = true
                main.removeCallbacks(timeout)
                onResult(CristalBrief.RESULT_NONE)
            }
        }
    }

    /** "Criar lembrete" tocado no teclado: o Cristal abre a confirmação na tela em uso. */
    open fun accept(c: BriefCandidate, id: Long, displayId: Int) {
        runCatching {
            context.sendBroadcast(fill(Intent(CristalBrief.ACTION_ACCEPT), c, id).putExtra(CristalBrief.EXTRA_DISPLAY, displayId))
        }.onFailure { Log.w(TAG, "Não foi possível falar com o Cristal", it) }
    }

    /** Sem o Cristal: a tela de novo evento da agenda, já preenchida (a pessoa confirma lá). */
    open fun calendarIntent(c: BriefCandidate): Intent {
        val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, c.title)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        c.at?.let {
            val begin = it.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 3_600_000L)
        }
        c.place?.let { intent.putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
        if (c.weekly) intent.putExtra(CalendarContract.Events.RRULE, "FREQ=WEEKLY")
        return intent
    }

    private fun fill(intent: Intent, c: BriefCandidate, id: Long): Intent = intent
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

    private companion object {
        const val TAG = "LilyInfoBrief"
        const val TIMEOUT_MS = 1_500L
    }
}
