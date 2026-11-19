package app.lily.keyboard.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lily.keyboard.ai.AiAction
import app.lily.keyboard.ai.AiAvailability
import app.lily.keyboard.ai.AiSource
import app.lily.keyboard.ai.KeyboardAi
import app.lily.keyboard.core.KeyboardTheme

/**
 * O painel da L.I.L.Y no teclado: ações sobre o texto selecionado (ou o que está antes do cursor),
 * com a resposta em prévia. Nada muda no campo sem um toque em "Substituir" ou "Inserir".
 */
@Composable
fun AiPanel(height: Dp, theme: KeyboardTheme, ai: KeyboardAi?, host: KeyboardHost, onClose: () -> Unit) {
    val ink = theme.textColor.asColor()
    val dim = ink.copy(alpha = 0.62f)
    val context = LocalContext.current
    val source = remember { host.aiSource() }
    var action by remember { mutableStateOf(ai?.ui?.value?.action) }
    var tone by remember { mutableStateOf("NEUTRAL") }
    var language by remember { mutableStateOf("en") }
    var notice by remember { mutableStateOf<String?>(null) }
    val ui = ai?.ui?.collectAsState()?.value

    Column(Modifier.fillMaxWidth().height(height).padding(horizontal = 10.dp, vertical = 6.dp)) {
        val availability = ai?.availability()
        val blocked = when {
            ai == null -> "A L.I.L.Y não funciona nesta prévia; use no teclado de verdade."
            availability == AiAvailability.NOT_INSTALLED -> "Instale a L.I.L.Y AI para usar a IA no teclado. Ela roda no aparelho, sem internet."
            availability == AiAvailability.NOT_ALLOWED -> "Esta L.I.L.Y AI não foi instalada com a mesma assinatura do teclado; a ponte entre eles não está liberada."
            source is AiSource.Blocked -> source.reason
            else -> null
        }
        if (blocked != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(blocked, color = ink, fontSize = 15.sp, fontFamily = KeyFont)
            }
            return@Column
        }
        val text = (source as AiSource.Text).text

        fun start(a: AiAction) {
            action = a
            notice = null
            val input = if (a == AiAction.REPLY) host.clipboardText() else text
            when {
                a == AiAction.REPLY && input.isNullOrBlank() -> notice = "Copie a mensagem que você recebeu e toque em Responder."
                input.isNullOrBlank() -> notice = "Escreva ou selecione um texto e toque numa ação."
                else -> {
                    val name = KeyboardAi.languages.firstOrNull { it.first == language }?.second?.lowercase() ?: "inglês"
                    ai!!.run(a, input, tone, language, name)
                }
            }
        }

        // Ações
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AiAction.entries.forEach { a -> Chip(a.label, theme, highlight = action == a) { start(a) } }
        }
        Spacer(Modifier.height(6.dp))
        // Opções da ação (tom e idioma) ou de onde vem o texto.
        when (action) {
            AiAction.REWRITE -> Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyboardAi.tones.forEach { (id, label) ->
                    Chip(label, theme, highlight = tone == id) {
                        tone = id
                        start(AiAction.REWRITE)
                    }
                }
            }
            AiAction.TRANSLATE -> Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyboardAi.languages.forEach { (code, label) ->
                    Chip(label, theme, highlight = language == code) {
                        language = code
                        start(AiAction.TRANSLATE)
                    }
                }
            }
            else -> Text(
                if (text.isBlank()) "Nenhum texto no campo ainda." else "${if (source.selection) "Selecionado" else "Antes do cursor"}: “${text.replace('\n', ' ').takeLast(90)}”",
                color = dim,
                fontSize = 12.sp,
                fontFamily = KeyFont,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(6.dp))
        // Resultado
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(ink.copy(alpha = 0.07f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            val body = when {
                notice != null -> notice!!
                ui?.error != null -> ui.error
                ui?.output?.isNotEmpty() == true -> ui.output
                ui?.running == true -> "Pensando no aparelho…"
                else -> action?.hint ?: "A IA só recebe o texto quando você toca numa ação. Tudo roda no aparelho."
            }
            Text(
                body,
                color = if (ui?.output?.isNotEmpty() == true && notice == null && ui.error == null) ink else dim,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontFamily = KeyFont,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        }
        Spacer(Modifier.height(6.dp))
        // Botões
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val output = ui?.output.orEmpty()
            val done = ui != null && !ui.running && output.isNotBlank() && ui.error == null
            when {
                ui?.running == true -> Chip("Parar", theme) { ai.stop() }
                ui?.noModel == true -> Chip("Abrir o Model Hub", theme, highlight = true) { host.openAiHub() }
                done -> {
                    Chip("Substituir", theme, highlight = true) {
                        if (host.replaceSource(source, output)) {
                            ai.reset()
                            onClose()
                        } else {
                            notice = "O texto mudou desde o pedido. Use Inserir ou peça de novo."
                        }
                    }
                    Chip("Inserir", theme) {
                        host.insertText(output)
                        ai.reset()
                        onClose()
                    }
                    Chip("Copiar", theme) {
                        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("L.I.L.Y", output))
                        notice = "Copiado."
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            val meta = listOfNotNull(ui?.model, ui?.millis?.let { String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.1f s", it / 1000.0) }).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, color = dim, fontSize = 11.sp, fontFamily = KeyFont, maxLines = 1, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(2.dp))
        }
    }
}
