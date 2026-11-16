package app.lily.ai.diagnostics

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.Process
import app.lily.ai.BuildConfig
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.system.exitProcess

/**
 * O último erro que fechou a L.I.L.Y AI, guardado no aparelho para a pessoa ver e copiar sem
 * computador. Com o app na tela, o relatório abre na hora em [CrashActivity], que roda num processo
 * separado e não depende do resto do app; ele também entra no relatório do "Teste do aparelho".
 * Nada é enviado para fora do aparelho.
 */
object CrashReport {
    private const val FILE = "ultimo-erro.txt"
    private const val MAX_CHARS = 24_000

    /** Processo da tela do relatório (ver o manifesto). */
    const val PROCESS_SUFFIX = ":relatorio"

    @Volatile
    private var started = 0

    /** Liga a captura: guarda o erro e, com o app na tela, mostra o relatório. Depois o Android fecha o app como sempre. */
    fun install(app: Application) {
        app.registerActivityLifecycleCallbacks(Counter)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { save(app, thread.name, error) }
            // Na tela (ou abrindo pelo ícone, ainda na thread principal): mostra o relatório já.
            if (started > 0 || thread === Looper.getMainLooper().thread) runCatching { app.startActivity(intent(app)) }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    fun intent(context: Context): Intent = Intent(context, CrashActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    /** Monta o relatório: versão, aparelho, hora, processo e a pilha completa (com as causas). */
    fun format(thread: String, error: Throwable, process: String = Application.getProcessName(), now: Instant = Instant.now()): String {
        val time = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.forLanguageTag("pt-BR")).format(now.atZone(ZoneId.systemDefault()))
        val head = buildString {
            appendLine("L.I.L.Y AI ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) fechou por um erro")
            appendLine("Aparelho: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Quando: $time · processo $process · thread $thread")
            appendLine()
        }
        val stack = error.stackTraceToString()
        return head + if (stack.length > MAX_CHARS) stack.take(MAX_CHARS) + "\n…(cortado)" else stack
    }

    fun save(context: Context, thread: String, error: Throwable) {
        File(context.filesDir, FILE).writeText(format(thread, error))
    }

    /** O último relatório guardado, se houver. */
    fun last(context: Context): String? = File(context.filesDir, FILE).takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }

    private object Counter : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            started++
        }

        override fun onActivityStopped(activity: Activity) {
            started = (started - 1).coerceAtLeast(0)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
