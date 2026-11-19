package app.lily.ai.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.lily.ai.ui.Nav
import app.lily.ai.ui.theme.GlassCard
import app.lily.ai.ui.theme.TopBar

/** Os documentos do projeto que vão junto do app (os mesmos arquivos de cristal-cover/legal). */
enum class LegalDoc(val title: String, val file: String) {
    TERMS("Termos de Uso", "TERMOS-DE-USO.md"),
    PRIVACY("Política de Privacidade", "PRIVACIDADE.md"),
    CREDITS("Créditos", "CREDITOS.md"),
}

object LegalText {
    /** Tabelas viram listas (a tela do celular é estreita): "• coluna 1 — coluna 2 — coluna 3". */
    fun forPhone(markdown: String): String {
        val out = StringBuilder()
        var header: List<String>? = null
        for (line in markdown.lines()) {
            val t = line.trim()
            if (t.startsWith("|") && t.endsWith("|")) {
                val cells = t.trim('|').split('|').map { it.trim() }
                if (cells.all { it.matches(Regex(":?-{3,}:?")) }) continue
                if (header == null) {
                    header = cells
                    continue
                }
                out.append("- **").append(cells.first()).append("**")
                cells.drop(1).forEach { out.append(" — ").append(it) }
                out.append('\n')
            } else {
                if (header != null) out.append('\n')
                header = null
                out.append(line).append('\n')
            }
        }
        return out.toString()
    }
}

@Composable
fun LegalScreen(nav: Nav, doc: LegalDoc) {
    val context = LocalContext.current
    val text = remember(doc) {
        runCatching { context.assets.open(doc.file).bufferedReader().use { it.readText() } }.getOrDefault("Documento não encontrado neste app.")
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(doc.title, onBack = { nav.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            GlassCard(Modifier.fillMaxWidth()) { RichText(LegalText.forPhone(text).lines().drop(1).joinToString("\n").trim()) }
            Spacer(Modifier.height(24.dp))
        }
    }
}
