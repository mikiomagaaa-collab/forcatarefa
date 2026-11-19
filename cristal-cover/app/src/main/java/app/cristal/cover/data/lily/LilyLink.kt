package app.cristal.cover.data.lily

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.Display
import app.cristal.cover.core.Logx

/**
 * A ponte do Cristal com a L.I.L.Y AI (outro app do aparelho, com a mesma assinatura). Os nomes
 * precisam bater com `app.lily.shared.bridge.AiBridge` (um teste confere). O Cristal só abre a
 * L.I.L.Y por um toque da pessoa (segurar a ilha, o widget, a busca, a Central de Controle); a IA
 * roda no app dela, no aparelho.
 */
object LilyLink {
    const val PACKAGE = "app.lily.ai"
    const val PERMISSION = "app.lily.ai.permission.USE_AI"
    const val ACTION_VOICE = "app.lily.ai.action.VOICE"
    const val ACTION_ASK = "app.lily.ai.action.ASK"
    private const val MAIN = "app.lily.ai.ui.MainActivity"

    fun installed(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    }.getOrDefault(false)

    /** Instalada e com a ponte liberada (mesma assinatura). */
    fun available(context: Context): Boolean =
        installed(context) && context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** A L.I.L.Y por voz (o painel em lente) na tela [displayId]. */
    fun voice(context: Context, displayId: Int = Display.DEFAULT_DISPLAY): Boolean =
        start(context, Intent(ACTION_VOICE).setPackage(PACKAGE), displayId)

    /** Pergunta [text] numa conversa nova (a L.I.L.Y abre já respondendo). */
    fun ask(context: Context, text: String, displayId: Int = Display.DEFAULT_DISPLAY): Boolean =
        text.isNotBlank() && start(context, Intent(ACTION_ASK).setPackage(PACKAGE).putExtra(Intent.EXTRA_TEXT, text.trim().take(2_000)), displayId)

    /** Abre a L.I.L.Y AI numa conversa nova. */
    fun openChat(context: Context, displayId: Int = Display.DEFAULT_DISPLAY): Boolean =
        start(context, Intent().setClassName(PACKAGE, MAIN).putExtra("route", "chat"), displayId)

    private fun start(context: Context, intent: Intent, displayId: Int): Boolean {
        if (!available(context)) return false
        return try {
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options.toBundle())
            true
        } catch (t: Throwable) {
            Logx.w("Não foi possível abrir a L.I.L.Y", t)
            false
        }
    }
}
