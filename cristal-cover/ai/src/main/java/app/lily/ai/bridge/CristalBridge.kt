package app.lily.ai.bridge

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import app.lily.ai.engine.Prompts
import app.lily.shared.bridge.CristalBrief
import app.lily.shared.brief.BriefCandidate
import app.lily.shared.brief.BriefDetector
import app.lily.shared.brief.BriefIntents
import app.lily.shared.brief.BriefKind
import java.time.LocalDateTime

/**
 * Lembretes pela IA usam o mesmo caminho do Info Brief: a L.I.L.Y reconhece o compromisso no texto
 * e o Cristal abre a confirmação (dia, horário, repetir, alarme). Nada é criado sem esse toque.
 */
object CristalBridge {
    fun available(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(CristalBrief.PACKAGE, 0)
        context.checkSelfPermission(CristalBrief.PERMISSION) == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /** O compromisso do texto (ou, se não houver data, uma tarefa com as primeiras palavras). */
    fun candidate(text: String, now: LocalDateTime = LocalDateTime.now()): BriefCandidate =
        BriefDetector.detect(text, now) ?: BriefCandidate(
            kind = BriefKind.TASK,
            title = Prompts.titleFrom(text).removeSuffix("…"),
            at = null,
            dateConfirmed = false,
            timeConfirmed = false,
            weekly = false,
            place = null,
            expression = "",
            start = 0,
            end = 0,
        )

    /** Pede ao Cristal para abrir a confirmação do lembrete na tela [displayId]. */
    fun proposeReminder(context: Context, text: String, displayId: Int): Boolean {
        if (!available(context)) return false
        val intent = BriefIntents.fill(Intent(CristalBrief.ACTION_ACCEPT), candidate(text), System.currentTimeMillis())
            .putExtra(CristalBrief.EXTRA_DISPLAY, displayId)
        return runCatching { context.sendBroadcast(intent) }.isSuccess
    }
}
