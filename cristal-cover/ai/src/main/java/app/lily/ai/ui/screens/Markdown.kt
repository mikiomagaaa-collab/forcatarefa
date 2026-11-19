package app.lily.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lily.ai.ui.theme.IconButton
import app.lily.ai.ui.theme.Lily
import app.lily.ai.ui.theme.Type

/** Markdown simples das respostas: parágrafos, títulos, listas, citações, código, negrito, itálico e links. */
object Markdown {
    sealed interface Block {
        data class Paragraph(val text: String) : Block
        data class Heading(val level: Int, val text: String) : Block
        data class Item(val marker: String, val text: String, val depth: Int) : Block
        data class Code(val language: String, val code: String) : Block
        data class Quote(val text: String) : Block
        data object Rule : Block
    }

    private val heading = Regex("^(#{1,6})\\s+(.*)$")
    private val item = Regex("^(\\s*)([-*+•]|\\d{1,3}[.)])\\s+(.*)$")
    private val rule = Regex("^(-{3,}|\\*{3,}|_{3,})$")
    private val link = Regex("\\[([^\\]]+)]\\((https?://[^)\\s]+)\\)")

    fun parse(markdown: String): List<Block> {
        val out = ArrayList<Block>()
        val lines = markdown.replace("\r\n", "\n").split('\n')
        val paragraph = StringBuilder()
        fun flush() {
            if (paragraph.isNotBlank()) out += Block.Paragraph(paragraph.toString().trim())
            paragraph.setLength(0)
        }
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            when {
                trimmed.startsWith("```") -> {
                    flush()
                    val language = trimmed.removePrefix("```").trim()
                    val code = StringBuilder()
                    i++
                    // Um bloco ainda aberto (resposta chegando) vai até o fim.
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        code.append(lines[i]).append('\n')
                        i++
                    }
                    out += Block.Code(language, code.toString().trimEnd('\n'))
                }
                trimmed.isEmpty() -> flush()
                rule.matches(trimmed) -> {
                    flush()
                    out += Block.Rule
                }
                heading.matches(trimmed) -> {
                    flush()
                    val m = heading.find(trimmed)!!
                    out += Block.Heading(m.groupValues[1].length, m.groupValues[2].trim().trimEnd('#').trim())
                }
                item.matches(line) -> {
                    flush()
                    val m = item.find(line)!!
                    val marker = m.groupValues[2].let { if (it.length == 1 && it in "-*+•") "•" else it }
                    out += Block.Item(marker, m.groupValues[3], (m.groupValues[1].replace("\t", "  ").length / 2).coerceAtMost(3))
                }
                trimmed.startsWith(">") -> {
                    flush()
                    out += Block.Quote(trimmed.removePrefix(">").trim())
                }
                else -> {
                    if (paragraph.isNotEmpty()) paragraph.append('\n')
                    paragraph.append(line.trimEnd())
                }
            }
            i++
        }
        flush()
        return out
    }

    /** Negrito, itálico, código e links dentro de uma linha. */
    fun inline(text: String, accent: Color, codeBackground: Color): AnnotatedString = buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i + 2) {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(inline(text.substring(i + 2, end), accent, codeBackground)) }
                        i = end + 2
                    } else {
                        append("**")
                        i += 2
                    }
                }
                c == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i + 1) {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground, fontSize = 14.sp)) { append(text.substring(i + 1, end)) }
                        i = end + 1
                    } else {
                        append(c)
                        i++
                    }
                }
                c == '*' && i + 1 < text.length && !text[i + 1].isWhitespace() -> {
                    val end = text.indexOf('*', i + 1)
                    if (end > i + 1 && !text[end - 1].isWhitespace()) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                        i = end + 1
                    } else {
                        append(c)
                        i++
                    }
                }
                c == '[' -> {
                    val m = link.find(text, i)?.takeIf { it.range.first == i }
                    if (m != null) {
                        withLink(LinkAnnotation.Url(m.groupValues[2], TextLinkStyles(SpanStyle(color = accent, textDecoration = TextDecoration.Underline)))) {
                            append(m.groupValues[1])
                        }
                        i = m.range.last + 1
                    } else {
                        append(c)
                        i++
                    }
                }
                else -> {
                    append(c)
                    i++
                }
            }
        }
    }

    /** O texto sem marcações, para ler em voz alta (código fica de fora). */
    fun plain(markdown: String): String = parse(markdown).mapNotNull { b ->
        when (b) {
            is Block.Paragraph -> strip(b.text)
            is Block.Heading -> strip(b.text)
            is Block.Item -> strip(b.text)
            is Block.Quote -> strip(b.text)
            is Block.Code -> "Há um trecho de código na resposta."
            Block.Rule -> null
        }
    }.joinToString("\n")

    private fun strip(s: String): String = s.replace(link, "$1").replace(Regex("\\*\\*|`|(?<=\\s|^)\\*|\\*(?=\\s|$)"), "")
}

/** A resposta renderizada (selecionável, com blocos de código copiáveis). */
@Composable
fun RichText(text: String, modifier: Modifier = Modifier, color: Color = Lily.colors.content, style: TextStyle = Type.body) {
    val c = Lily.colors
    val codeBackground = c.content.copy(alpha = 0.08f)
    val blocks = remember(text) { Markdown.parse(text) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { b ->
            when (b) {
                is Markdown.Block.Paragraph -> Text(Markdown.inline(b.text, c.accent, codeBackground), style = style, color = color)
                is Markdown.Block.Heading -> Text(
                    Markdown.inline(b.text, c.accent, codeBackground),
                    style = if (b.level <= 2) Type.headline.copy(fontSize = 19.sp, lineHeight = 25.sp) else Type.headline,
                    color = color,
                    modifier = Modifier.padding(top = 4.dp),
                )
                is Markdown.Block.Item -> Row(Modifier.padding(start = (b.depth * 16).dp)) {
                    Text(b.marker, style = style.copy(fontWeight = FontWeight.SemiBold), color = c.accent, modifier = Modifier.width(if (b.marker.length > 2) 30.dp else 20.dp))
                    Text(Markdown.inline(b.text, c.accent, codeBackground), style = style, color = color, modifier = Modifier.weight(1f))
                }
                is Markdown.Block.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.fillMaxHeight().width(3.dp).clip(RoundedCornerShape(2.dp)).background(c.accent.copy(alpha = 0.7f)))
                    Spacer(Modifier.width(10.dp))
                    Text(Markdown.inline(b.text, c.accent, codeBackground), style = style, color = c.secondary)
                }
                is Markdown.Block.Code -> CodeBlock(b)
                Markdown.Block.Rule -> Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(c.border))
            }
        }
    }
}

@Composable
private fun CodeBlock(block: Markdown.Block.Code) {
    val c = Lily.colors
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (c.dark) Color(0x66000000) else Color(0x0D000000))) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(block.language.ifBlank { "código" }, style = Type.caption, color = c.secondary, modifier = Modifier.weight(1f))
            IconButton(Icons.Rounded.ContentCopy, "Copiar código", { copyToClipboard(context, block.code) }, size = 34.dp, filled = false, tint = c.secondary)
        }
        Text(
            block.code,
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp),
            color = c.content,
            softWrap = false,
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
        )
    }
}
