package app.lily.ai.codex

import kotlinx.serialization.json.Json
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** Um problema encontrado por um verificador (linha começa em 1). */
data class Problem(val path: String, val line: Int, val message: String)

/** O resultado de verificar um arquivo. [checked] = false quando não há verificador para a linguagem. */
data class FileCheck(val path: String, val language: String, val checked: Boolean, val problems: List<Problem>, val kind: String)

/**
 * Uma linguagem que o Codex entende: extensões, comentários e um verificador. Novas linguagens entram
 * com uma linha em [Languages.all] (o resto do Codex não muda).
 */
data class Language(
    val id: String,
    val name: String,
    val extensions: Set<String>,
    val fileNames: Set<String> = emptySet(),
    val lineComment: String? = null,
    val blockComment: Pair<String, String>? = null,
    /** Verificação de sintaxe no aparelho (sem compilar). */
    val check: ((String, String) -> List<Problem>)? = null,
    val checkKind: String = "sintaxe",
)

object Languages {
    private val json = Json { allowTrailingComma = false }

    private fun cLike(id: String, name: String, ext: Set<String>, raw: Boolean = false, templates: Boolean = false, nested: Boolean = false) =
        Language(
            id, name, ext, lineComment = "//", blockComment = "/*" to "*/",
            check = { path, text -> Delimiters.check(path, text, Delimiters.Syntax(lineComment = "//", block = "/*" to "*/", nestedBlock = nested, rawStrings = raw, templates = templates, charLiterals = !templates, singleQuoteStrings = templates)) },
            checkKind = "chaves, parênteses e textos",
        )

    val all: List<Language> = listOf(
        cLike("kotlin", "Kotlin", setOf("kt", "kts"), raw = true, nested = true),
        cLike("java", "Java", setOf("java")),
        cLike("javascript", "JavaScript", setOf("js", "mjs", "cjs", "jsx"), templates = true),
        cLike("typescript", "TypeScript", setOf("ts", "tsx", "mts", "cts"), templates = true),
        Language(
            "groovy", "Gradle (Groovy)", setOf("gradle", "groovy"), lineComment = "//", blockComment = "/*" to "*/",
            check = { p, t -> Delimiters.check(p, t, Delimiters.Syntax(lineComment = "//", block = "/*" to "*/", charLiterals = false, singleQuoteStrings = true, tripleQuotes = true)) },
            checkKind = "chaves, parênteses e textos",
        ),
        Language(
            "css", "CSS", setOf("css", "scss"), blockComment = "/*" to "*/",
            check = { p, t -> Delimiters.check(p, t, Delimiters.Syntax(lineComment = if (p.endsWith(".scss")) "//" else null, block = "/*" to "*/", charLiterals = false, singleQuoteStrings = true)) },
            checkKind = "chaves e textos",
        ),
        Language("xml", "XML", setOf("xml", "svg", "xsd"), blockComment = "<!--" to "-->", check = ::checkXml, checkKind = "XML bem formado"),
        Language("html", "HTML", setOf("html", "htm"), blockComment = "<!--" to "-->", check = ::checkHtml, checkKind = "tags abertas e fechadas"),
        Language("json", "JSON", setOf("json"), check = ::checkJson, checkKind = "JSON válido"),
        Language("yaml", "YAML", setOf("yml", "yaml"), lineComment = "#", check = ::checkYaml, checkKind = "estrutura básica"),
        Language("python", "Python", setOf("py"), lineComment = "#", check = ::checkPython, checkKind = "indentação, parênteses e textos"),
        Language(
            "sql", "SQL", setOf("sql"), lineComment = "--", blockComment = "/*" to "*/",
            check = { p, t -> Delimiters.check(p, t, Delimiters.Syntax(lineComment = "--", block = "/*" to "*/", charLiterals = false, singleQuoteStrings = true)) },
            checkKind = "parênteses e textos",
        ),
        Language("toml", "TOML", setOf("toml"), lineComment = "#", check = ::checkToml, checkKind = "estrutura básica"),
        Language("properties", "Properties", setOf("properties"), lineComment = "#"),
        Language("markdown", "Markdown", setOf("md")),
        Language("shell", "Shell", setOf("sh", "bash"), lineComment = "#"),
    )

    fun of(path: String): Language? {
        val name = path.substringAfterLast('/')
        val ext = name.substringAfterLast('.', "").lowercase()
        return all.firstOrNull { name in it.fileNames || ext in it.extensions }
    }

    fun check(path: String, text: String): FileCheck {
        val lang = of(path) ?: return FileCheck(path, "?", false, emptyList(), "sem verificador")
        val checker = lang.check ?: return FileCheck(path, lang.name, false, emptyList(), "sem verificador")
        val problems = runCatching { checker(path, text) }.getOrElse { listOf(Problem(path, 1, "Verificador falhou: ${it.message}")) }
        return FileCheck(path, lang.name, true, problems, lang.checkKind)
    }

    private fun lineOf(text: String, offset: Int): Int = text.substring(0, offset.coerceIn(0, text.length)).count { it == '\n' } + 1

    private fun checkJson(path: String, text: String): List<Problem> = try {
        json.parseToJsonElement(text)
        emptyList()
    } catch (e: Exception) {
        val at = Regex("""at offset (\d+)|at path""").find(e.message.orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull()
        listOf(Problem(path, at?.let { lineOf(text, it) } ?: 1, "JSON inválido: ${e.message?.lineSequence()?.firstOrNull()?.take(160)}"))
    }

    private fun checkXml(path: String, text: String): List<Problem> = try {
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
        parser.setInput(text.reader())
        while (parser.next() != XmlPullParser.END_DOCUMENT) Unit
        emptyList()
    } catch (e: Exception) {
        val line = Regex("""line (\d+)|@(\d+):""").find(e.message.orEmpty())?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }?.toIntOrNull() ?: 1
        listOf(Problem(path, line, "XML malformado: ${e.message?.take(160)}"))
    }

    private val VOID = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr", "!doctype")
    private val OPTIONAL_CLOSE = setOf("p", "li", "td", "th", "tr", "option", "thead", "tbody", "tfoot", "dt", "dd")

    private fun checkHtml(path: String, text: String): List<Problem> {
        val stack = ArrayList<Pair<String, Int>>()
        val problems = ArrayList<Problem>()
        // Tira comentários e o conteúdo de <script>/<style> (podem ter "<" à vontade).
        val clean = text.replace(Regex("<!--[\\s\\S]*?-->")) { m -> m.value.replace(Regex("[^\\n]"), " ") }
            .replace(Regex("(<(script|style)\\b[^>]*>)([\\s\\S]*?)(</\\2\\s*>)", RegexOption.IGNORE_CASE)) { m ->
                m.groupValues[1] + m.groupValues[3].replace(Regex("[^\\n]"), " ") + m.groupValues[4]
            }
        for (m in Regex("""<(/?)([A-Za-z!][A-Za-z0-9-]*)([^>]*?)(/?)>""").findAll(clean)) {
            val closing = m.groupValues[1] == "/"
            val tag = m.groupValues[2].lowercase()
            val selfClosing = m.groupValues[4] == "/"
            val line = lineOf(clean, m.range.first)
            if (tag in VOID || selfClosing) continue
            if (!closing) {
                stack += tag to line
                continue
            }
            // Fecha opcionais abertos (como o navegador faz) até achar o par.
            while (stack.isNotEmpty() && stack.last().first != tag && stack.last().first in OPTIONAL_CLOSE) stack.removeAt(stack.lastIndex)
            if (stack.isEmpty() || stack.last().first != tag) {
                problems += Problem(path, line, "</$tag> sem a abertura correspondente")
            } else {
                stack.removeAt(stack.lastIndex)
            }
        }
        stack.filter { it.first !in OPTIONAL_CLOSE && it.first !in setOf("html", "head", "body") }.forEach { problems += Problem(path, it.second, "<${it.first}> aberta e nunca fechada") }
        return problems.take(20)
    }

    private fun checkYaml(path: String, text: String): List<Problem> {
        val problems = ArrayList<Problem>()
        text.lines().forEachIndexed { i, raw ->
            val line = i + 1
            if (raw.isBlank() || raw.trimStart().startsWith("#")) return@forEachIndexed
            val indent = raw.takeWhile { it == ' ' || it == '\t' }
            if ('\t' in indent) problems += Problem(path, line, "Tabulação na indentação (YAML só aceita espaços)")
            val content = stripComment(raw.trim(), "#")
            val quotes = content.count { it == '"' } - Regex("""\\"""").findAll(content).count()
            if (quotes % 2 != 0) problems += Problem(path, line, "Aspas duplas sem fechar")
        }
        return problems.take(20)
    }

    private fun checkToml(path: String, text: String): List<Problem> {
        val problems = ArrayList<Problem>()
        var inMultiline = false
        text.lines().forEachIndexed { i, raw ->
            val t = raw.trim()
            if (t.contains("\"\"\"")) inMultiline = !inMultiline
            if (inMultiline || t.isEmpty() || t.startsWith("#")) return@forEachIndexed
            val ok = t.startsWith("[") && t.endsWith("]") || Regex("""^[A-Za-z0-9_."'-]+\s*=""").containsMatchIn(t) || t.startsWith("]") || t.startsWith("}") || t.endsWith(",") || t.startsWith("\"")
            if (!ok) problems += Problem(path, i + 1, "Linha fora do formato chave = valor")
        }
        return problems.take(20) + Delimiters.check(path, text, Delimiters.Syntax(lineComment = "#", block = null, charLiterals = false, singleQuoteStrings = true))
    }

    private fun checkPython(path: String, text: String): List<Problem> {
        val problems = ArrayList<Problem>()
        var usesTabs = false
        var usesSpaces = false
        val lines = text.lines()
        lines.forEachIndexed { i, raw ->
            val indent = raw.takeWhile { it == ' ' || it == '\t' }
            if (raw.isBlank()) return@forEachIndexed
            if ('\t' in indent) usesTabs = true
            if (' ' in indent) usesSpaces = true
            val code = stripComment(raw.trim(), "#")
            if (code.endsWith(":") && Regex("""^(def|class|if|elif|else|for|while|try|except|finally|with|async def|async for|async with|match|case)\b""").containsMatchIn(code)) {
                val next = lines.drop(i + 1).firstOrNull { it.isNotBlank() && !it.trim().startsWith("#") }
                if (next != null && next.takeWhile { it == ' ' || it == '\t' }.length <= indent.length) {
                    problems += Problem(path, i + 1, "Bloco depois de \":\" sem indentação")
                }
            }
        }
        if (usesTabs && usesSpaces) problems += Problem(path, 1, "Mistura de tabulações e espaços na indentação")
        return problems.take(20) + Delimiters.check(path, text, Delimiters.Syntax(lineComment = "#", block = null, charLiterals = false, singleQuoteStrings = true, tripleQuotes = true))
    }

    private fun stripComment(line: String, marker: String): String {
        var inS = false
        var inD = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (ch == '\\') { i += 2; continue }
            if (ch == '\'' && !inD) inS = !inS
            if (ch == '"' && !inS) inD = !inD
            if (!inS && !inD && line.startsWith(marker, i)) return line.substring(0, i).trimEnd()
            i++
        }
        return line
    }
}

/**
 * Confere se parênteses, colchetes e chaves fecham, e se textos e comentários terminam — sabendo
 * pular o que está dentro de textos e comentários (o erro mais comum de uma edição incompleta).
 */
object Delimiters {
    data class Syntax(
        val lineComment: String?,
        val block: Pair<String, String>?,
        val nestedBlock: Boolean = false,
        val rawStrings: Boolean = false,
        val templates: Boolean = false,
        val charLiterals: Boolean = true,
        val singleQuoteStrings: Boolean = false,
        val tripleQuotes: Boolean = false,
    )

    private val PAIRS = mapOf(')' to '(', ']' to '[', '}' to '{')

    /** Uma "/" que começa uma expressão regular (e não uma divisão ou comentário). */
    private fun regexStart(text: String, i: Int): Boolean {
        if (i + 1 < text.length && (text[i + 1] == '/' || text[i + 1] == '*')) return false
        var j = i - 1
        while (j >= 0 && (text[j] == ' ' || text[j] == '\t')) j--
        if (j < 0 || text[j] == '\n') return true
        if (text[j] in "(,=:[!&|?{};+-*%<>~^") return true
        // depois de return/typeof etc.
        val word = text.substring(0, j + 1).takeLastWhile { it.isLetter() }
        return word in setOf("return", "typeof", "case", "in", "of", "delete", "void", "throw")
    }

    fun check(path: String, text: String, syntax: Syntax): List<Problem> {
        val problems = ArrayList<Problem>()
        val stack = ArrayList<Pair<Char, Int>>()
        var i = 0
        var line = 1
        // Pilha de templates (JS) e de ${ } (Kotlin): quantas chaves abertas dentro de cada interpolação.
        val interpolation = ArrayList<Int>()
        fun at(s: String) = text.startsWith(s, i)
        while (i < text.length) {
            val ch = text[i]
            when {
                ch == '\n' -> { line++; i++ }
                syntax.lineComment != null && at(syntax.lineComment) -> {
                    while (i < text.length && text[i] != '\n') i++
                }
                syntax.block != null && at(syntax.block.first) -> {
                    val start = line
                    var depth = 1
                    i += syntax.block.first.length
                    while (i < text.length && depth > 0) {
                        if (text[i] == '\n') line++
                        if (syntax.nestedBlock && at(syntax.block.first)) { depth++; i += syntax.block.first.length; continue }
                        if (at(syntax.block.second)) { depth--; i += syntax.block.second.length; continue }
                        i++
                    }
                    if (depth > 0) problems += Problem(path, start, "Comentário de bloco sem fechar")
                }
                (syntax.rawStrings || syntax.tripleQuotes) && (at("\"\"\"") || (syntax.tripleQuotes && at("'''"))) -> {
                    val q = text.substring(i, i + 3)
                    val start = line
                    i += 3
                    var closed = false
                    while (i < text.length) {
                        if (text[i] == '\n') line++
                        if (at(q)) { i += 3; closed = true; break }
                        i++
                    }
                    if (!closed) problems += Problem(path, start, "Texto de várias linhas ($q) sem fechar")
                }
                ch == '"' || (ch == '\'' && (syntax.singleQuoteStrings || syntax.templates)) || (ch == '`' && syntax.templates) -> {
                    val start = line
                    val quote = ch
                    i++
                    var closed = false
                    while (i < text.length) {
                        val c = text[i]
                        if (c == '\\') { i += 2; continue }
                        if (c == '\n') {
                            if (quote != '`') break
                            line++
                        }
                        if (quote == '"' && syntax.rawStrings && at("\${")) {
                            // Kotlin: "${expressão}" pode ter textos e chaves dentro.
                            var depth = 1
                            i += 2
                            while (i < text.length && depth > 0) {
                                when (text[i]) {
                                    '{' -> depth++
                                    '}' -> depth--
                                    '"' -> { i++; while (i < text.length && text[i] != '"' && text[i] != '\n') { if (text[i] == '\\') i++; i++ } }
                                }
                                i++
                            }
                            continue
                        }
                        if (quote == '`' && at("\${")) {
                            // Interpolação: o resto é código até a chave que fecha.
                            interpolation += 0
                            i += 2
                            closed = true
                            break
                        }
                        if (c == quote) { i++; closed = true; break }
                        i++
                    }
                    if (!closed) problems += Problem(path, start, "Texto (${quote}) sem fechar na linha")
                }
                ch == '\'' && syntax.charLiterals -> {
                    // Caractere ('a', '\n', 'A'); um apóstrofo solto em Kotlin/Java é erro.
                    val end = text.indexOf('\'', i + 1).takeIf { it in (i + 2)..(i + 8) && '\n' !in text.substring(i, it) }
                    if (end == null) {
                        problems += Problem(path, line, "Apóstrofo sem fechar")
                        i++
                    } else {
                        i = end + 1
                    }
                }
                ch == '/' && syntax.templates && regexStart(text, i) -> {
                    // Expressão regular do JavaScript (/.../): pode ter aspas e chaves dentro.
                    i++
                    var inClass = false
                    while (i < text.length && text[i] != '\n') {
                        val c = text[i]
                        if (c == '\\') { i += 2; continue }
                        if (c == '[') inClass = true
                        if (c == ']') inClass = false
                        if (c == '/' && !inClass) { i++; break }
                        i++
                    }
                }
                ch == '(' || ch == '[' || ch == '{' -> {
                    stack += ch to line
                    if (ch == '{' && interpolation.isNotEmpty()) interpolation[interpolation.lastIndex]++
                    i++
                }
                ch == ')' || ch == ']' || ch == '}' -> {
                    if (ch == '}' && interpolation.isNotEmpty() && interpolation.last() == 0) {
                        // Fim de uma interpolação de template: volta para dentro do texto `...`.
                        interpolation.removeAt(interpolation.lastIndex)
                        i++
                        var closed = false
                        while (i < text.length) {
                            val c = text[i]
                            if (c == '\\') { i += 2; continue }
                            if (c == '\n') line++
                            if (at("\${")) { interpolation += 0; i += 2; closed = true; break }
                            if (c == '`') { i++; closed = true; break }
                            i++
                        }
                        if (!closed) problems += Problem(path, line, "Template (`) sem fechar")
                        continue
                    }
                    if (ch == '}' && interpolation.isNotEmpty()) interpolation[interpolation.lastIndex]--
                    val open = stack.lastOrNull()
                    if (open == null || open.first != PAIRS[ch]) {
                        problems += Problem(path, line, "\"$ch\" sem a abertura correspondente" + (open?.let { " (aberto: \"${it.first}\" na linha ${it.second})" } ?: ""))
                        if (open != null && PAIRS.values.contains(open.first) && stack.any { it.first == PAIRS[ch] }) {
                            while (stack.isNotEmpty() && stack.last().first != PAIRS[ch]) stack.removeAt(stack.lastIndex)
                            if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
                        }
                    } else {
                        stack.removeAt(stack.lastIndex)
                    }
                    i++
                }
                else -> i++
            }
            if (problems.size >= 20) break
        }
        stack.takeLast(5).forEach { problems += Problem(path, it.second, "\"${it.first}\" aberto e nunca fechado") }
        return problems.take(20)
    }
}
