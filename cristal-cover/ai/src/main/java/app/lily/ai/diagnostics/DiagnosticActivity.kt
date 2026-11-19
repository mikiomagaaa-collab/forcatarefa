package app.lily.ai.diagnostics

import android.app.Activity
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Process
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import app.lily.ai.ui.MainActivity
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * "L.I.L.Y Diagnóstico": abre pelo próprio ícone e também sozinho quando a L.I.L.Y cai com ela na
 * tela. Roda no processo ":relatorio" com as views do Android, sem o núcleo, os motores ou o
 * Compose (que podem ser justamente o que quebrou). Mostra a situação, o relatório completo e
 * deixa copiar, compartilhar, salvar em Downloads, abrir a L.I.L.Y (normal ou em modo seguro) e
 * apagar o histórico.
 */
class DiagnosticActivity : Activity() {
    private var report: String = ""
    private lateinit var body: TextView
    private lateinit var headline: TextView
    private lateinit var detail: TextView
    private lateinit var safeButton: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val crashed = intent?.getBooleanExtra(EXTRA_CRASHED, false) == true
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val title = TextView(this).apply {
            text = if (crashed) "A L.I.L.Y AI fechou por um erro" else "Diagnóstico da L.I.L.Y AI"
            setTextColor(INK)
            textSize = 22f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val hint = TextView(this).apply {
            text = "Copie ou compartilhe o relatório e mande para quem cuida do app. Ele fica só neste aparelho até você enviar."
            setTextColor(SOFT)
            textSize = 14f
            setPadding(0, dp(6), 0, dp(14))
        }
        headline = TextView(this).apply {
            text = "Lendo os registros…"
            setTextColor(INK)
            textSize = 16f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        detail = TextView(this).apply {
            setTextColor(SOFT)
            textSize = 13f
            setPadding(0, dp(4), 0, 0)
            visibility = View.GONE
        }
        val status = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(CARD, dp(18))
            setPadding(dp(16), dp(14), dp(16), dp(14))
            addView(headline)
            addView(detail)
        }
        body = TextView(this).apply {
            setTextColor(INK)
            textSize = 11.5f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        val scroll = ScrollView(this).apply {
            background = rounded(CARD, dp(18))
            addView(body)
        }
        fun button(label: String, filled: Boolean, action: () -> Unit) = TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(if (filled) BG else INK)
            background = rounded(if (filled) ACCENT else CARD, dp(22))
            minHeight = dp(46)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
        fun row(vararg views: View) = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            views.forEachIndexed { i, v ->
                addView(v, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { if (i < views.lastIndex) marginEnd = dp(8) })
            }
        }
        val copy = button("Copiar relatório", filled = true) { copy() }
        val share = button("Compartilhar", filled = false) { share() }
        val save = button("Salvar em Downloads", filled = false) { saveToDownloads() }
        val open = button("Abrir a L.I.L.Y", filled = false) { openLily(safe = false) }
        safeButton = button("", filled = false) { toggleSafe() }
        val clear = button("Apagar histórico", filled = false) { clearHistory() }
        // O Codex tenta achar a causa e corrigir no código-fonte autorizado (nunca no app instalado).
        val fix = button("Consertar com o Codex", filled = false) { openCodex() }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            addView(title)
            addView(hint)
            addView(status)
            addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f).apply { topMargin = dp(12) })
            addView(copy, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) })
            addView(row(share, save), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
            addView(row(open, safeButton), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
            addView(row(fix, clear), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) })
            // O app desenha de ponta a ponta: as barras do sistema viram margem.
            setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                v.setPadding(dp(18) + bars.left, dp(18) + bars.top, dp(18) + bars.right, dp(14) + bars.bottom)
                insets
            }
        }
        setContentView(root)
        refreshSafeButton()
        load()
    }

    /** Lê tudo fora da thread principal (a lápide de uma falha nativa pode ter centenas de KB). */
    private fun load() {
        Thread {
            val exits = ExitHistory.read(this)
            val text = runCatching { DiagnosticReport.build(this, exits) }.getOrElse { "Não deu para montar o relatório: $it" }
            val summary = runCatching { DiagnosticReport.summary(this, exits) }.getOrNull()
            runOnUiThread { show(text, summary) }
        }.start()
    }

    internal fun show(text: String, summary: DiagnosticReport.Summary?) {
        report = text
        body.text = text
        headline.text = summary?.headline?.replaceFirstChar { it.uppercase() } ?: "Relatório pronto"
        headline.setTextColor(if (summary?.problem == true) WARN else INK)
        detail.text = summary?.detail.orEmpty()
        detail.visibility = if (summary?.detail.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    private fun copy() {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Relatório da L.I.L.Y AI", report))
        toast("Relatório copiado")
    }

    private fun share() {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Diagnóstico da L.I.L.Y AI")
            .putExtra(Intent.EXTRA_TEXT, report)
        runCatching { startActivity(Intent.createChooser(send, "Enviar o relatório")) }.onFailure { toast("Nenhum app para compartilhar") }
    }

    private fun saveToDownloads() {
        val name = "lily-diagnostico-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}.txt"
        val ok = runCatching {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/")
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("sem destino")
            contentResolver.openOutputStream(uri)?.use { it.write(report.toByteArray()) } ?: error("sem arquivo")
        }.isSuccess
        toast(if (ok) "Salvo em Downloads: $name" else "Não foi possível salvar")
    }

    /** Abre a L.I.L.Y; para o modo seguro, fecha antes o processo principal para ela iniciar do zero. */
    private fun openLily(safe: Boolean) {
        if (safe) {
            DiagLog.forceSafeMode(this)
            restartMainProcess()
        }
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    /** Abre o Codex com o relatório como o erro a consertar (no projeto que a pessoa autorizar). */
    private fun openCodex() {
        val error = CrashReport.last(this) ?: report
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_ROUTE, "codex")
                .putExtra(MainActivity.EXTRA_CODEX_ERROR, error.take(40_000))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun toggleSafe() {
        if (DiagLog.isForcedSafe(this) || DiagLog.savedFailures(this) >= DiagLog.SAFE_AFTER) {
            DiagLog.leaveSafeMode(this)
            restartMainProcess()
            toast("Modo seguro desligado")
            refreshSafeButton()
        } else {
            openLily(safe = true)
        }
    }

    private fun refreshSafeButton() {
        val on = DiagLog.isForcedSafe(this) || DiagLog.savedFailures(this) >= DiagLog.SAFE_AFTER
        safeButton.text = if (on) "Sair do modo seguro" else "Abrir em modo seguro"
    }

    private fun clearHistory() {
        CrashReport.clear(this)
        runCatching { java.io.File(DiagLog.dir(this), "registro.log").delete() }
        toast("Histórico apagado")
        load()
    }

    private fun restartMainProcess() {
        val am = getSystemService(ActivityManager::class.java) ?: return
        am.runningAppProcesses?.filter { it.processName == packageName }?.forEach { Process.killProcess(it.pid) }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
    }

    companion object {
        const val EXTRA_CRASHED = "crashed"
        private const val BG = 0xFF0E0D14.toInt()
        private const val CARD = 0xFF1C1A26.toInt()
        private const val INK = 0xFFF2F0F7.toInt()
        private const val SOFT = 0xFFB9B4C7.toInt()
        private const val WARN = 0xFFFFB86B.toInt()
        private const val ACCENT = 0xFFB79CFF.toInt()
    }
}
