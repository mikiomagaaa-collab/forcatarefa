package app.lily.ai.diagnostics

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import app.lily.ai.ui.MainActivity

/**
 * A tela do relatório de erro. Roda no processo ":relatorio" e usa só as views do Android, sem o
 * resto do app (que pode ser justamente o que quebrou): mostra o texto, copia e reabre o app.
 */
class CrashActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = CrashReport.last(this)
        if (report == null) {
            finish()
            return
        }
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val title = TextView(this).apply {
            text = "A L.I.L.Y AI fechou por um erro"
            setTextColor(INK)
            textSize = 22f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val hint = TextView(this).apply {
            text = "Toque em “Copiar relatório” e mande o texto para quem cuida do app. O relatório fica só neste aparelho."
            setTextColor(SOFT)
            textSize = 15f
            setPadding(0, dp(8), 0, dp(16))
        }
        val body = TextView(this).apply {
            text = report
            setTextColor(INK)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        val scroll = ScrollView(this).apply {
            background = GradientDrawable().apply {
                setColor(CARD)
                cornerRadius = dp(18).toFloat()
            }
            addView(body)
        }
        fun button(label: String, filled: Boolean, action: () -> Unit) = TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            textSize = 16f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(if (filled) BG else INK)
            background = GradientDrawable().apply {
                setColor(if (filled) ACCENT else CARD)
                cornerRadius = dp(24).toFloat()
            }
            minHeight = dp(48)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
        val copy = button("Copiar relatório", filled = true) {
            getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Relatório da L.I.L.Y AI", report))
            Toast.makeText(this, "Relatório copiado", Toast.LENGTH_SHORT).show()
        }
        val reopen = button("Abrir de novo", filled = false) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
        }
        val close = button("Fechar", filled = false) { finish() }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(reopen, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(10) })
            addView(close, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            addView(title)
            addView(hint)
            addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            addView(copy, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(16) })
            addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(10) })
            // O app desenha de ponta a ponta: as barras do sistema viram margem.
            setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                v.setPadding(dp(20) + bars.left, dp(20) + bars.top, dp(20) + bars.right, dp(16) + bars.bottom)
                insets
            }
        }
        setContentView(root)
    }

    private companion object {
        const val BG = 0xFF0E0D14.toInt()
        const val CARD = 0xFF1C1A26.toInt()
        const val INK = 0xFFF2F0F7.toInt()
        const val SOFT = 0xFFB9B4C7.toInt()
        const val ACCENT = 0xFFB79CFF.toInt()
    }
}
