package app.lily.ai.diagnostics

import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import app.lily.ai.BuildConfig
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * O relatório de diagnóstico completo, montado sem o resto do app (lê só arquivos e o registro do
 * Android): situação, módulos, como o app terminou, os erros guardados e o registro da partida.
 */
object DiagnosticReport {

    data class Summary(val headline: String, val detail: String, val problem: Boolean)

    fun build(context: Context, exits: List<ExitHistory.Exit> = ExitHistory.read(context)): String = buildString {
        val time = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR")).format(Instant.now().atZone(ZoneId.systemDefault()))
        appendLine("Diagnóstico da L.I.L.Y AI ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · $time")
        appendLine("Aparelho: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.SUPPORTED_ABIS.firstOrNull()}")
        val s = summary(context, exits)
        appendLine("Situação: ${s.headline}")
        if (s.detail.isNotBlank()) appendLine(s.detail)
        appendLine()
        section("Módulos na última partida", DiagLog.modulesText(context).ifBlank { "(o núcleo ainda não chegou a iniciar os módulos)" })
        section("Como o app terminou (registro do Android)", ExitHistory.format(exits))
        val crashes = CrashReport.history(context)
        section("Erros guardados (${crashes.size})", if (crashes.isEmpty()) "Nenhum erro guardado." else crashes.joinToString("\n\n— — —\n\n"))
        section("Registro da partida (últimas linhas)", DiagLog.read(context).lines().takeLast(160).joinToString("\n").ifBlank { "(vazio)" })
    }.trimEnd()

    private fun StringBuilder.section(title: String, body: String) {
        appendLine("== $title ==")
        appendLine(body.trimEnd())
        appendLine()
    }

    /** O resumo de uma linha que a tela mostra no topo. */
    fun summary(context: Context, exits: List<ExitHistory.Exit>): Summary {
        val main = exits.firstOrNull { it.process == context.packageName }
        val failed = main?.reason in BAD
        val lastCrash = CrashReport.last(context)
        val safe = DiagLog.isForcedSafe(context) || DiagLog.savedFailures(context) >= DiagLog.SAFE_AFTER
        // A L.I.L.Y abriu bem depois do último problema (por exemplo, depois de uma atualização que corrige):
        // os problemas listados ficam como histórico, não como a situação de agora.
        val okAt = DiagLog.lastOk(context)
        val crashAt = CrashReport.lastTime(context)
        val recovered = okAt != null && (main == null || !failed || okAt > main.time) && (crashAt == null || okAt > crashAt)
        val hadProblem = failed || lastCrash != null
        val headline = when {
            hadProblem && recovered -> "a última abertura terminou bem (${stamp(okAt!!)}); os problemas abaixo são de antes"
            main != null && failed -> "a última vez, a L.I.L.Y terminou por: ${ExitHistory.reasonName(main.reason)}"
            lastCrash != null -> "há um erro guardado"
            else -> "nenhum problema registrado"
        }
        val detail = buildString {
            if (!recovered && main != null && failed) {
                main.step?.let { append("Etapa em andamento: ").append(it.removePrefix("etapa: ")).append('\n') }
                main.trace?.lineSequence()?.firstOrNull { it.startsWith("Sinal") || it.startsWith("Abort") || it.startsWith("  #00") }
                    ?.let { append(it.trim()).append('\n') }
            }
            lastCrash?.takeIf { !recovered }?.lineSequence()?.let { lines ->
                lines.firstOrNull { it.startsWith("Módulo provável") }?.let { append(it).append('\n') }
                lines.firstOrNull { it.contains("Exception") || it.contains("Error:") }?.let { append(it.trim().take(200)).append('\n') }
            }
            if (safe) append("Modo seguro ligado: a próxima abertura vem sem efeitos e módulos opcionais.\n")
        }.trimEnd()
        return Summary(headline, detail, hadProblem && !recovered)
    }

    private fun stamp(millis: Long): String =
        DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.forLanguageTag("pt-BR")).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    private val BAD = setOf(
        ApplicationExitInfo.REASON_CRASH,
        ApplicationExitInfo.REASON_CRASH_NATIVE,
        ApplicationExitInfo.REASON_ANR,
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
    )
}
