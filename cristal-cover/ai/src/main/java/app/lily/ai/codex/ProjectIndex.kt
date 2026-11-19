package app.lily.ai.codex

import androidx.compose.runtime.Immutable

/** O que o Codex entendeu do projeto antes de começar. */
@Immutable
data class ProjectSummary(
    val files: Int,
    val languages: List<Pair<String, Int>>,
    val buildSystems: List<String>,
    val dependencies: List<String>,
    val tree: String,
)

/**
 * Lê a estrutura do projeto: pastas, linguagens, sistemas de build e dependências declaradas — e
 * acha arquivos relacionados (imports, mesmo nome, mesmo pacote) para o Codex não editar às cegas.
 */
object ProjectIndex {
    /** Os arquivos de build e dependências que vale ler para o resumo (poucos e pequenos). */
    fun manifests(files: List<FileEntry>): List<String> = files.map { it.path }.filter { p ->
        p.endsWith("libs.versions.toml") || p.endsWith("build.gradle.kts") || p.endsWith("build.gradle") ||
            (p == "package.json" || p.endsWith("/package.json")) || p.endsWith("requirements.txt")
    }.take(14)

    fun summarize(files: List<FileEntry>, read: (String) -> String?): ProjectSummary {
        val langs = files.mapNotNull { Languages.of(it.path)?.name }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key to it.value }
        val names = files.map { it.path }
        val builds = buildList {
            if (names.any { it.endsWith("settings.gradle.kts") || it.endsWith("settings.gradle") }) add("Gradle")
            if (names.any { it.endsWith("AndroidManifest.xml") }) add("Android")
            if (names.any { it == "package.json" || it.endsWith("/package.json") }) add("npm")
            if (names.any { it.endsWith("requirements.txt") || it.endsWith("pyproject.toml") }) add("Python")
            if (names.any { it.endsWith("wrangler.toml") }) add("Cloudflare Workers")
            if (names.any { it.endsWith("pom.xml") }) add("Maven")
        }
        val deps = LinkedHashSet<String>()
        names.filter { it.endsWith("libs.versions.toml") }.take(2).forEach { p ->
            read(p)?.lineSequence()?.mapNotNull { Regex("""module\s*=\s*"([^"]+)"""").find(it)?.groupValues?.get(1) }?.take(80)?.forEach { deps += it }
        }
        names.filter { it.endsWith("build.gradle.kts") || it.endsWith("build.gradle") }.take(8).forEach { p ->
            read(p)?.lineSequence()?.mapNotNull { Regex("""(implementation|api|testImplementation)\(?\s*["']([^"']+)["']""").find(it)?.groupValues?.get(2) }?.take(40)?.forEach { deps += it }
        }
        names.filter { it == "package.json" || it.endsWith("/package.json") && !it.contains("node_modules") }.take(3).forEach { p ->
            read(p)?.let { text ->
                Regex(""""(dependencies|devDependencies)"\s*:\s*\{([^}]*)\}""").findAll(text).forEach { m ->
                    Regex(""""([^"]+)"\s*:\s*"([^"]+)"""").findAll(m.groupValues[2]).forEach { d -> deps += "${d.groupValues[1]}@${d.groupValues[2]}" }
                }
            }
        }
        names.filter { it.endsWith("requirements.txt") }.take(2).forEach { p ->
            read(p)?.lineSequence()?.map { it.trim() }?.filter { it.isNotEmpty() && !it.startsWith("#") }?.take(40)?.forEach { deps += it }
        }
        return ProjectSummary(files.size, langs, builds, deps.toList().take(120), tree(names))
    }

    /** As pastas com quantos arquivos têm (até 2 níveis), para o modelo se orientar. */
    fun tree(paths: List<String>, maxLines: Int = 60): String {
        val counts = paths.groupingBy { p -> p.split('/').let { parts -> if (parts.size <= 1) "." else parts.take(minOf(parts.size - 1, 3)).joinToString("/") } }.eachCount()
        return counts.entries.sortedBy { it.key }.take(maxLines).joinToString("\n") { "${it.key}/ (${it.value})" }
    }

    /** Arquivos relacionados: o que ele importa, quem tem o mesmo nome base e os vizinhos de pasta. */
    fun related(path: String, text: String, all: List<String>, limit: Int = 8): List<String> {
        val out = LinkedHashSet<String>()
        val base = path.substringAfterLast('/').substringBeforeLast('.')
        // Kotlin/Java: import a.b.C → .../a/b/C.kt
        Regex("""^\s*import\s+([\w.]+)""", RegexOption.MULTILINE).findAll(text).forEach { m ->
            val parts = m.groupValues[1].split('.')
            val cls = parts.last()
            val pkgPath = parts.dropLast(1).joinToString("/")
            all.firstOrNull { it.endsWith("$pkgPath/$cls.kt") || it.endsWith("$pkgPath/$cls.java") }?.let { out += it }
        }
        // JS/TS: import ... from './x'
        Regex("""(?:from\s+|require\()\s*['"](\.{1,2}/[^'"]+)['"]""").findAll(text).forEach { m ->
            val dir = path.substringBeforeLast('/', "")
            val target = normalize(if (dir.isEmpty()) m.groupValues[1] else "$dir/${m.groupValues[1]}")
            all.firstOrNull { it == target || it.startsWith("$target.") || it == "$target/index.js" || it == "$target/index.ts" }?.let { out += it }
        }
        // Python: from a.b import c → a/b.py
        Regex("""^\s*from\s+([\w.]+)\s+import""", RegexOption.MULTILINE).findAll(text).forEach { m ->
            val p = m.groupValues[1].replace('.', '/')
            all.firstOrNull { it.endsWith("$p.py") || it.endsWith("$p/__init__.py") }?.let { out += it }
        }
        // Testes do mesmo nome e arquivos com o mesmo nome base.
        all.filter { it != path && it.substringAfterLast('/').substringBeforeLast('.').let { n -> n == "${base}Test" || n == "${base}Tests" || n == "test_$base" || n == base } }.forEach { out += it }
        return out.filter { it != path }.take(limit)
    }

    private fun normalize(p: String): String {
        val out = ArrayList<String>()
        for (part in p.split('/')) {
            when (part) {
                ".", "" -> Unit
                ".." -> if (out.isNotEmpty()) out.removeAt(out.lastIndex)
                else -> out += part
            }
        }
        return out.joinToString("/")
    }

    /** Busca de texto (ou expressão regular) no projeto: arquivo:linha: trecho. */
    fun search(query: String, files: List<Pair<String, String>>, limit: Int = 40): List<String> {
        val regex = runCatching { Regex(query) }.getOrNull()
        val out = ArrayList<String>()
        for ((path, text) in files) {
            text.lineSequence().forEachIndexed { i, line ->
                if (out.size >= limit) return out
                val hit = if (regex != null) regex.containsMatchIn(line) else line.contains(query, ignoreCase = true)
                if (hit) out += "$path:${i + 1}: ${line.trim().take(160)}"
            }
        }
        return out
    }
}

/** Um erro entendido: onde aconteceu e uma assinatura para saber se é "o mesmo erro". */
@Immutable
data class ErrorReport(
    val signature: String,
    val type: String,
    val message: String,
    val locations: List<Pair<String, Int>>,
    val raw: String,
)

/**
 * Lê erros de compilação, pilhas de exceção (Kotlin/Java, Python, JavaScript), falhas de teste e o
 * relatório do L.I.L.Y Diagnóstico, e descobre os arquivos do projeto envolvidos.
 */
object ErrorParser {
    fun parse(raw: String, projectFiles: List<String>): ErrorReport {
        val text = raw.take(40_000)
        val locations = LinkedHashSet<Pair<String, Int>>()
        // Compilador Kotlin: e: file:///.../Arquivo.kt:97:18 mensagem
        Regex("""e:\s+(?:file://)?(\S+?\.(?:kt|kts|java)):(\d+)""").findAll(text).forEach { m -> match(m.groupValues[1], projectFiles)?.let { locations += it to m.groupValues[2].toInt() } }
        // javac / tsc / eslint: caminho/arquivo.ext:12[:3] ou (12,3)
        Regex("""([\w./-]+\.(?:java|ts|tsx|js|mjs|kt|py|xml|gradle|kts))[:(](\d+)""").findAll(text).forEach { m -> match(m.groupValues[1], projectFiles)?.let { locations += it to m.groupValues[2].toInt() } }
        // Pilha Java/Kotlin: at pacote.Classe.metodo(Arquivo.kt:42)
        Regex("""at\s+([\w.$]+)\.[\w$<>]+\((\w+\.(?:kt|java)):(\d+)\)""").findAll(text).forEach { m ->
            val cls = m.groupValues[1].substringBefore('$')
            val pkgPath = cls.substringBeforeLast('.').replace('.', '/')
            val file = m.groupValues[2]
            projectFiles.firstOrNull { it.endsWith("$pkgPath/$file") }?.let { locations += it to m.groupValues[3].toInt() }
        }
        // Python: File "x/y.py", line 10
        Regex("""File "([^"]+\.py)", line (\d+)""").findAll(text).forEach { m -> match(m.groupValues[1], projectFiles)?.let { locations += it to m.groupValues[2].toInt() } }
        val exception = Regex("""([A-Za-z_][\w.]*(?:Exception|Error))(?::\s*(.+))?""").find(text)
        val type = exception?.groupValues?.get(1)?.substringAfterLast('.') ?: Regex("""^e:\s""", RegexOption.MULTILINE).find(text)?.let { "Erro de compilação" } ?: "Erro"
        val message = exception?.groupValues?.getOrNull(2)?.trim()?.take(300)
            ?: Regex("""e:\s+\S+\s+(.+)""").find(text)?.groupValues?.get(1)?.take(300)
            ?: text.lineSequence().firstOrNull { it.isNotBlank() }?.take(300).orEmpty()
        val first = locations.firstOrNull()
        val signature = "$type@${first?.first?.substringAfterLast('/') ?: "?"}:${normalizeMessage(message)}"
        return ErrorReport(signature, type, message, locations.toList().take(10), text)
    }

    /** A mensagem sem números variáveis (linha, endereço) para comparar "o mesmo erro". */
    fun normalizeMessage(m: String): String = m.replace(Regex("""\d+"""), "#").replace(Regex("""0x[0-9a-fA-F#]+"""), "0x#").take(120)

    private fun match(path: String, files: List<String>): String? {
        val clean = path.removePrefix("file://").replace('\\', '/')
        return files.firstOrNull { clean.endsWith("/$it") || clean == it } ?: files.firstOrNull { it.endsWith("/" + clean.substringAfterLast('/')) && clean.substringAfterLast('/').length > 3 }
    }
}
