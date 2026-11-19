package app.lily.ai.codex

import androidx.compose.runtime.Immutable
import app.lily.ai.remote.GatewayClient
import app.lily.ai.remote.RemoteChunk
import app.lily.ai.remote.RemoteMessage
import app.lily.ai.remote.RemoteModel
import app.lily.ai.remote.SearchResult
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Uma resposta do modelo de programação (o texto e o custo informado pelo provedor). */
data class CodeReply(val text: String, val costUsd: Double?)

/** O modelo que o agente usa (online pelo gateway; os testes usam um roteiro). */
interface CodeLlm {
    val label: String
    val vendor: String
    suspend fun complete(messages: List<RemoteMessage>, maxTokens: Int = 6000): CodeReply
}

class RemoteCodeLlm(private val gateway: GatewayClient, private val model: RemoteModel) : CodeLlm {
    override val label: String = model.label
    override val vendor: String = model.vendor

    override suspend fun complete(messages: List<RemoteMessage>, maxTokens: Int): CodeReply {
        var text = ""
        var cost: Double? = null
        gateway.chat(model.id, messages, temperature = 0.1, maxTokens = maxTokens).collect { c ->
            when (c) {
                is RemoteChunk.Text -> text = c.total
                is RemoteChunk.Done -> cost = c.usage?.costUsd
            }
        }
        return CodeReply(text, cost)
    }
}

/** O que aparece na linha do tempo do Codex. */
@Immutable
sealed interface CodexEvent {
    val text: String

    data class Plan(override val text: String) : CodexEvent
    data class Thought(override val text: String) : CodexEvent
    data class Action(override val text: String, val detail: String = "") : CodexEvent
    data class Web(override val text: String, val sources: List<SearchResult>) : CodexEvent
    data class Staged(override val text: String, val path: String, val added: Int, val removed: Int) : CodexEvent
    data class Checks(override val text: String, val ok: Boolean) : CodexEvent
    data class Note(override val text: String) : CodexEvent
    data class Failure(override val text: String) : CodexEvent
}

/** O relatório final do agente. Verificado e hipótese ficam separados (nada inventado como fato). */
@Immutable
data class FinishReport(
    val summary: String,
    val changes: String,
    val verified: List<String>,
    val hypotheses: List<String>,
    val risks: List<String>,
    val sources: List<String>,
)

/** As mudanças propostas, ainda não aplicadas (a pessoa revisa o diff antes). */
class StagedChanges {
    private val originals = LinkedHashMap<String, String?>()
    private val current = LinkedHashMap<String, String?>()

    fun has(path: String) = path in current
    fun current(path: String): String? = current[path]

    fun stage(path: String, before: String?, after: String?) {
        if (path !in originals) originals[path] = before
        current[path] = after
        if (originals[path] == after) {
            originals.remove(path)
            current.remove(path)
        }
    }

    fun changes(): List<FileChange> = current.map { (p, after) -> FileChange(p, originals[p], after) }
    fun clear() {
        originals.clear()
        current.clear()
    }

    val size: Int get() = current.size
}

/**
 * O agente de programação: conversa com o modelo por ações em JSON (ler, listar, buscar, pesquisar,
 * editar, verificar, terminar), executa cada uma no projeto autorizado e devolve o resultado. As
 * edições ficam preparadas (não gravadas) até a aprovação.
 */
class CodexAgent(
    private val workspace: Workspace,
    private val llm: CodeLlm,
    private val gateway: GatewayClient?,
    private val webAllowed: Boolean,
    private val emit: (CodexEvent) -> Unit,
    private val fetchPage: suspend (String) -> String = ::fetchText,
) {
    val staged = StagedChanges()
    var totalCost = 0.0
        private set
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var cachedFiles: List<FileEntry>? = null

    private suspend fun files(): List<FileEntry> = cachedFiles ?: workspace.files().also { cachedFiles = it }

    /** O conteúdo atual de um arquivo, já com as edições preparadas. */
    suspend fun contentOf(path: String): String? = if (staged.has(path)) staged.current(path) else workspace.read(path)

    suspend fun run(task: String, context: String, maxSteps: Int): FinishReport? {
        val all = files()
        val manifests = ProjectIndex.manifests(all).associateWith { p -> if (all.any { it.path == p && it.size < 200_000 }) runCatching { workspace.read(p) }.getOrNull() else null }
        val summary = ProjectIndex.summarize(all) { p -> manifests[p] }
        emit(CodexEvent.Plan("Projeto: ${summary.files} ${if (summary.files == 1) "arquivo" else "arquivos"} · ${summary.languages.take(4).joinToString { "${it.first} (${it.second})" }}" + (summary.buildSystems.takeIf { it.isNotEmpty() }?.let { " · ${it.joinToString()}" } ?: "")))
        val messages = ArrayList<RemoteMessage>()
        messages += RemoteMessage("system", system(webAllowed && gateway != null))
        messages += RemoteMessage(
            "user",
            buildString {
                append("PROJECT: ").append(workspace.name).append(" (").append(workspace.kindLabel).append(")\n")
                append("Build: ").append(summary.buildSystems.joinToString().ifEmpty { "?" }).append('\n')
                append("Languages: ").append(summary.languages.joinToString { "${it.first}=${it.second}" }).append('\n')
                if (summary.dependencies.isNotEmpty()) append("Declared dependencies (sample): ").append(summary.dependencies.take(40).joinToString()).append('\n')
                append("Folders:\n").append(summary.tree).append("\n\n")
                if (context.isNotBlank()) append(context).append("\n\n")
                append("TASK (from the user, Brazilian Portuguese):\n").append(task)
            },
        )
        var invalid = 0
        for (step in 1..maxSteps) {
            val reply = llm.complete(messages)
            reply.costUsd?.let { totalCost += it }
            val action = parseAction(reply.text)
            messages += RemoteMessage("assistant", reply.text.take(12_000))
            if (action == null) {
                invalid++
                if (invalid >= 3) {
                    emit(CodexEvent.Failure("O modelo não seguiu o formato de ações três vezes seguidas."))
                    return null
                }
                messages += RemoteMessage("user", "Your reply was not a single valid JSON action. Reply with exactly one JSON object as specified.")
                continue
            }
            invalid = 0
            action.str("thought")?.takeIf { it.isNotBlank() }?.let { emit(CodexEvent.Thought(it)) }
            val name = action.str("action").orEmpty()
            if (name == "finish") {
                return FinishReport(
                    summary = action.str("summary").orEmpty(),
                    changes = action.str("changes").orEmpty(),
                    verified = action.list("verified"),
                    hypotheses = action.list("hypotheses"),
                    risks = action.list("risks"),
                    sources = action.list("sources"),
                )
            }
            val result = try {
                execute(name, action)
            } catch (e: Exception) {
                "ERROR: ${e.message ?: e.javaClass.simpleName}"
            }
            messages += RemoteMessage("user", "RESULT of $name (step $step/$maxSteps):\n" + result.take(14_000))
            // Contexto longo: resume os resultados antigos para não estourar (e não encarecer).
            if (messages.sumOf { it.content.length } > 180_000) compact(messages)
        }
        emit(CodexEvent.Failure("Chegou ao limite de $maxSteps passos sem terminar."))
        messages += RemoteMessage("user", "Step limit reached. Reply now with the finish action summarizing what was done and what remains.")
        val last = parseAction(llm.complete(messages).also { r -> r.costUsd?.let { totalCost += it } }.text)
        return last?.takeIf { it.str("action") == "finish" }?.let {
            FinishReport(it.str("summary").orEmpty(), it.str("changes").orEmpty(), it.list("verified"), it.list("hypotheses"), it.list("risks"), it.list("sources"))
        }
    }

    private fun compact(messages: MutableList<RemoteMessage>) {
        for (i in 2 until messages.size - 6) {
            val m = messages[i]
            if (m.role == "user" && m.content.length > 1_500) messages[i] = RemoteMessage("user", m.content.take(600) + "\n[... resultado antigo resumido ...]")
        }
    }

    private suspend fun execute(name: String, a: JsonObject): String = when (name) {
        "list" -> {
            val dir = a.str("path").orEmpty().trim('/').let { if (it.isEmpty()) "" else "$it/" }
            val list = files().filter { it.path.startsWith(dir) }.take(250)
            emit(CodexEvent.Action("Listou ${dir.ifEmpty { "o projeto" }}", "${list.size} ${if (list.size == 1) "arquivo" else "arquivos"}"))
            list.joinToString("\n") { "${it.path} (${it.size} B)" }.ifEmpty { "(nada aqui)" }
        }
        "read" -> {
            val path = Workspace.safePath(a.str("path").orEmpty())
            val text = contentOf(path) ?: return "File not found: $path"
            val lines = text.split('\n')
            val from = (a.int("from") ?: 1).coerceIn(1, maxOf(1, lines.size))
            val to = (a.int("to") ?: (from + 399)).coerceIn(from, lines.size)
            emit(CodexEvent.Action("Leu $path", "linhas $from–$to de ${lines.size}"))
            val related = ProjectIndex.related(path, text, files().map { it.path }, 6)
            buildString {
                for (n in from..to) append(n).append(": ").append(lines[n - 1]).append('\n')
                if (to < lines.size) append("... (${lines.size - to} more lines)\n")
                if (related.isNotEmpty()) append("Related files: ").append(related.joinToString())
            }
        }
        "search" -> {
            val query = a.str("query").orEmpty()
            require(query.isNotBlank()) { "empty query" }
            val dir = a.str("path").orEmpty().trim('/')
            val candidates = files().filter { (dir.isEmpty() || it.path.startsWith("$dir/")) && it.size < 200_000 && Languages.of(it.path) != null }.take(1_500)
            val texts = candidates.mapNotNull { f -> contentOf(f.path)?.let { f.path to it } }
            val hits = ProjectIndex.search(query, texts)
            emit(CodexEvent.Action("Buscou \"$query\"", "${hits.size} ${if (hits.size == 1) "resultado" else "resultados"}"))
            hits.joinToString("\n").ifEmpty { "(no matches)" }
        }
        "web_search" -> {
            val g = gateway
            if (!webAllowed || g == null) {
                "Web search is disabled by the user. Rely on the project files and list unverified points as hypotheses."
            } else {
                val query = a.str("query").orEmpty()
                val r = g.search(query)
                emit(CodexEvent.Web("Pesquisou: $query", r.results))
                r.results.joinToString("\n\n") { "${it.title}\n${it.url}\n${it.snippet}" }.ifEmpty { "(no results)" }
            }
        }
        "fetch" -> {
            if (!webAllowed) {
                "Web access is disabled by the user."
            } else {
                val url = a.str("url").orEmpty()
                require(url.startsWith("https://")) { "only https URLs" }
                val text = fetchPage(url)
                emit(CodexEvent.Web("Leu a página", listOf(SearchResult(url.take(80), url, ""))))
                text.take(12_000)
            }
        }
        "edit" -> {
            val path = Workspace.safePath(a.str("path").orEmpty())
            val find = a.str("find") ?: error("missing find")
            val replace = a.str("replace") ?: error("missing replace")
            val before = contentOf(path) ?: return "File not found: $path. Use write to create it."
            val count = before.windowedCount(find)
            when {
                find.isEmpty() -> "find must not be empty"
                count == 0 -> "find text not found in $path. Read the file again and copy the exact text (including indentation)."
                count > 1 -> "find text appears $count times in $path. Include more surrounding lines so it is unique."
                else -> stage(path, before.replaceFirst(find, replace))
            }
        }
        "write" -> {
            val path = Workspace.safePath(a.str("path").orEmpty())
            val content = a.str("content") ?: error("missing content")
            stage(path, content)
        }
        "delete" -> {
            val path = Workspace.safePath(a.str("path").orEmpty())
            if (contentOf(path) == null) "File not found: $path" else stage(path, null)
        }
        "check" -> runChecks().let { (ok, report) ->
            emit(CodexEvent.Checks(if (ok) "Verificação no aparelho: sem problemas nos arquivos alterados" else "Verificação encontrou problemas", ok))
            report
        }
        else -> "Unknown action \"$name\". Valid: list, read, search, web_search, fetch, edit, write, delete, check, finish."
    }

    private suspend fun stage(path: String, after: String?): String {
        val original = if (staged.has(path)) null else workspace.read(path)
        val before = if (staged.has(path)) staged.changes().first { it.path == path }.before else original
        staged.stage(path, before, after)
        val d = Diff.of(path, before, after)
        emit(CodexEvent.Staged(if (after == null) "Preparou apagar $path" else if (before == null) "Preparou criar $path" else "Preparou mudança em $path", path, d.added, d.removed))
        val check = after?.let { Languages.check(path, it) }
        val problems = check?.problems.orEmpty()
        return buildString {
            append("Staged (not yet applied). +${d.added} -${d.removed} lines.\n")
            if (problems.isNotEmpty()) append("Syntax check found problems:\n").append(problems.joinToString("\n") { "${it.path}:${it.line}: ${it.message}" })
            else if (check?.checked == true) append("Syntax check (${check.kind}): ok.")
        }
    }

    /** Verifica os arquivos preparados (sintaxe por linguagem). */
    fun runChecks(): Pair<Boolean, String> {
        val results = staged.changes().filter { it.after != null }.map { Languages.check(it.path, it.after!!) }
        val problems = results.flatMap { it.problems }
        val unchecked = results.filter { !it.checked }.map { it.path }
        val report = buildString {
            if (results.isEmpty()) append("No staged files to check.")
            results.filter { it.checked }.forEach { append("${it.path}: ${if (it.problems.isEmpty()) "ok" else "${it.problems.size} problem(s)"} (${it.kind})\n") }
            problems.forEach { append("${it.path}:${it.line}: ${it.message}\n") }
            if (unchecked.isNotEmpty()) append("No checker for: ${unchecked.joinToString()}\n")
            append("Note: project tests run only after approval (CI) — compiling alone is not proof.")
        }
        return problems.isEmpty() to report
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
    private fun JsonObject.list(key: String): List<String> = when (val v = this[key]) {
        is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
        is JsonPrimitive -> listOfNotNull(v.contentOrNull?.takeIf(String::isNotBlank))
        else -> emptyList()
    }

    /** O primeiro objeto JSON da resposta (aceita ```json ...``` e texto em volta). */
    fun parseAction(text: String): JsonObject? {
        var start = text.indexOf('{')
        while (start >= 0) {
            val end = matchingBrace(text, start)
            if (end > start) {
                val candidate = text.substring(start, end + 1)
                val obj = runCatching { json.parseToJsonElement(candidate).jsonObject }.getOrNull()
                if (obj != null && obj["action"] != null) return obj
            }
            start = text.indexOf('{', start + 1)
        }
        return null
    }

    private fun matchingBrace(text: String, from: Int): Int {
        var depth = 0
        var inString = false
        var i = from
        while (i < text.length) {
            val c = text[i]
            if (inString) {
                if (c == '\\') i++ else if (c == '"') inString = false
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> if (--depth == 0) return i
                }
            }
            i++
        }
        return -1
    }

    companion object {
        fun system(web: Boolean): String = """
You are L.I.L.Y Codex, a careful software engineering agent working inside a project the user AUTHORIZED.
You act ONLY through JSON actions. Reply with exactly ONE JSON object per turn and nothing else.
Every action may include "thought": one short sentence (Brazilian Portuguese) explaining why.

Actions:
{"action":"list","path":"dir"}                                  list files under a directory
{"action":"read","path":"file","from":1,"to":400}               numbered lines (plus related files)
{"action":"search","query":"text or regex","path":"optional dir"}
${if (web) """{"action":"web_search","query":"..."}                             official docs, API references, known errors
{"action":"fetch","url":"https://..."}                          read a documentation page""" else "(web search is disabled for this session)"}
{"action":"edit","path":"file","find":"exact existing text","replace":"new text"}   find must match exactly once
{"action":"write","path":"file","content":"complete file"}     create a file or rewrite a small one
{"action":"delete","path":"file"}
{"action":"check"}                                               syntax checks on the files you changed
{"action":"finish","summary":"...","changes":"...","verified":["..."],"hypotheses":["..."],"risks":["..."],"sources":["https://..."]}

Rules:
- Understand before editing: read the files you will change and the related ones; search for usages.
- Never invent APIs, functions, parameters or library versions. If you are not sure, search the project${if (web) " or the web" else ""}, or put it under "hypotheses".
- Keep changes minimal, focused and in the project's existing style. Prefer "edit" to "write" for existing files.
- After editing, run "check" and fix what it reports. Compiling is not proof: list in "risks" what only tests can confirm.
- Edits are staged; the user reviews a diff before anything is written. Never touch secrets, signing keys or CI workflows.
- "verified" lists only facts you confirmed by reading files, tool output or cited sources. Everything else goes to "hypotheses".
- Write summary, changes, verified, hypotheses and risks in Brazilian Portuguese.
""".trim()

        /** O texto de uma página (para a pesquisa técnica), sem HTML. */
        suspend fun fetchText(url: String): String = withContext(Dispatchers.IO) {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", "LILY-Codex-Android")
            try {
                if (conn.responseCode !in 200..299) return@withContext "HTTP ${conn.responseCode}"
                val html = conn.inputStream.bufferedReader().use { r -> CharArray(400_000).let { buf -> val n = r.read(buf); if (n <= 0) "" else String(buf, 0, n) } }
                html.replace(Regex("(?is)<(script|style|nav|footer|header)[^>]*>.*?</\\1>"), " ")
                    .replace(Regex("(?i)<br\\s*/?>|</p>|</h[1-6]>|</li>|</pre>"), "\n")
                    .replace(Regex("<[^>]+>"), " ")
                    .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
                    .replace(Regex("[ \\t]+"), " ")
                    .replace(Regex("\\n\\s*\\n+"), "\n\n")
                    .trim()
            } finally {
                conn.disconnect()
            }
        }
    }
}

private fun String.windowedCount(part: String): Int {
    if (part.isEmpty()) return 0
    var count = 0
    var idx = indexOf(part)
    while (idx >= 0) {
        count++
        idx = indexOf(part, idx + part.length)
    }
    return count
}
