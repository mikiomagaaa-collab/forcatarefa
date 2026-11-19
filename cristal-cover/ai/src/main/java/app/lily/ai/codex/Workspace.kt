package app.lily.ai.codex

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.Immutable
import app.lily.ai.remote.GatewayClient
import app.lily.ai.remote.RepoChange
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Immutable
data class FileEntry(val path: String, val size: Long)

/** Uma mudança de arquivo: [before] null = arquivo novo; [after] null = apagar. */
@Immutable
@Serializable
data class FileChange(val path: String, val before: String?, val after: String?)

/** O que aconteceu ao aplicar mudanças (num ramo, quando é um repositório). */
data class ApplyResult(val where: String, val url: String? = null)

/** Como rodar os testes do projeto (só nos repositórios, pelo GitHub Actions). */
@Serializable
data class TestPlan(val runner: String = "gradle", val tasks: String = "test", val workdir: String = ".")

/**
 * Um projeto autorizado. O Codex só enxerga o que está aqui dentro: uma pasta escolhida pela pessoa
 * (permissão do Android) ou um repositório liberado no gateway.
 */
interface Workspace {
    val id: String
    val name: String
    /** "Pasta do aparelho", "Repositório (GitHub)"… */
    val kindLabel: String
    val tests: TestPlan?

    suspend fun files(): List<FileEntry>
    suspend fun read(path: String): String?

    /** Aplica as mudanças (já aprovadas). */
    suspend fun apply(changes: List<FileChange>, message: String): ApplyResult

    companion object {
        /** Pastas que nunca entram (geradas, enormes ou privadas). */
        val SKIP = Regex("""(^|/)(\.git|\.gradle|\.idea|build|node_modules|dist|\.next|\.kotlin|__pycache__|\.venv|venv|\.dart_tool|Pods)(/|$)""")
        const val MAX_FILE = 400_000L

        fun safePath(path: String): String {
            val p = path.trim().trimStart('/').replace('\\', '/')
            require(p.isNotEmpty() && !p.split('/').any { it == ".." || it == "." } && !SKIP.containsMatchIn(p)) { "Caminho não permitido: $path" }
            return p
        }
    }
}

/** Uma pasta comum (usada nos testes e para projetos guardados dentro do app). */
class FileWorkspace(private val root: File, override val name: String = root.name, override val tests: TestPlan? = null) : Workspace {
    override val id: String = "file:" + root.absolutePath
    override val kindLabel = "Pasta"

    override suspend fun files(): List<FileEntry> = withContext(Dispatchers.IO) {
        root.walkTopDown()
            .onEnter { it == root || !Workspace.SKIP.containsMatchIn(it.relativeTo(root).invariantSeparatorsPath + "/") }
            .filter { it.isFile }
            .take(8_000)
            .map { FileEntry(it.relativeTo(root).invariantSeparatorsPath, it.length()) }
            .toList()
    }

    override suspend fun read(path: String): String? = withContext(Dispatchers.IO) {
        File(root, Workspace.safePath(path)).takeIf { it.isFile && it.length() <= Workspace.MAX_FILE }?.readText()
    }

    override suspend fun apply(changes: List<FileChange>, message: String): ApplyResult = withContext(Dispatchers.IO) {
        for (c in changes) {
            val f = File(root, Workspace.safePath(c.path))
            if (c.after == null) f.delete() else f.apply { parentFile?.mkdirs() }.writeText(c.after)
        }
        ApplyResult("na pasta ${root.name}")
    }
}

/**
 * Uma pasta do aparelho escolhida no seletor do Android (Storage Access Framework). O app só tem
 * acesso a ela porque a pessoa escolheu; a permissão pode ser revogada nas configurações.
 */
class SafWorkspace(private val context: Context, private val tree: Uri, override val name: String, override val tests: TestPlan? = null) : Workspace {
    override val id: String = "saf:$tree"
    override val kindLabel = "Pasta do aparelho"
    private val resolver = context.contentResolver
    private val index = HashMap<String, String>()
    private val rootId = DocumentsContract.getTreeDocumentId(tree)

    private data class Child(val id: String, val name: String, val dir: Boolean, val size: Long)

    private fun children(docId: String): List<Child> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE)
        val out = ArrayList<Child>()
        resolver.query(uri, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                out += Child(c.getString(0), c.getString(1) ?: "", c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR, if (c.isNull(3)) 0 else c.getLong(3))
            }
        }
        return out
    }

    override suspend fun files(): List<FileEntry> = withContext(Dispatchers.IO) {
        index.clear()
        val out = ArrayList<FileEntry>()
        fun walk(docId: String, prefix: String) {
            for (ch in children(docId)) {
                if (out.size >= 8_000) return
                val path = if (prefix.isEmpty()) ch.name else "$prefix/${ch.name}"
                if (ch.dir) {
                    if (!Workspace.SKIP.containsMatchIn("$path/")) {
                        index["$path/"] = ch.id
                        walk(ch.id, path)
                    }
                } else {
                    index[path] = ch.id
                    out += FileEntry(path, ch.size)
                }
            }
        }
        walk(rootId, "")
        out
    }

    private suspend fun ensureIndex() {
        if (index.isEmpty()) files()
    }

    override suspend fun read(path: String): String? = withContext(Dispatchers.IO) {
        ensureIndex()
        val id = index[Workspace.safePath(path)] ?: return@withContext null
        resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, id))?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > Workspace.MAX_FILE) return@withContext null
            }
            out.toString(Charsets.UTF_8.name())
        }
    }

    private fun dirFor(path: String): String {
        val parts = path.split('/').dropLast(1)
        var current = rootId
        var prefix = ""
        for (part in parts) {
            prefix = if (prefix.isEmpty()) part else "$prefix/$part"
            current = index["$prefix/"] ?: run {
                val created = DocumentsContract.createDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, current), DocumentsContract.Document.MIME_TYPE_DIR, part)
                    ?: error("Não consegui criar a pasta $prefix")
                DocumentsContract.getDocumentId(created).also { index["$prefix/"] = it }
            }
        }
        return current
    }

    override suspend fun apply(changes: List<FileChange>, message: String): ApplyResult = withContext(Dispatchers.IO) {
        ensureIndex()
        for (c in changes) {
            val path = Workspace.safePath(c.path)
            val existing = index[path]
            if (c.after == null) {
                existing?.let { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, it)) }
                index.remove(path)
                continue
            }
            val docUri = if (existing != null) {
                DocumentsContract.buildDocumentUriUsingTree(tree, existing)
            } else {
                val parent = dirFor(path)
                // application/octet-stream: o provedor não acrescenta extensão ao nome.
                val created = DocumentsContract.createDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, parent), "application/octet-stream", path.substringAfterLast('/'))
                    ?: error("Não consegui criar $path")
                index[path] = DocumentsContract.getDocumentId(created)
                created
            }
            resolver.openOutputStream(docUri, "wt")?.use { it.write(c.after.toByteArray(Charsets.UTF_8)) } ?: error("Não consegui gravar $path")
        }
        ApplyResult("na pasta $name")
    }

    companion object {
        /** Guarda a permissão da pasta (sobrevive a reinícios). */
        fun persist(context: Context, uri: Uri) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }
}

/**
 * Um repositório liberado no gateway. Lê do ramo do Codex (se já existir) ou do de origem; as
 * mudanças aprovadas viram um commit no ramo lily-codex/... — o ramo principal nunca é tocado.
 */
class RepoWorkspace(
    private val gateway: GatewayClient,
    val repo: String,
    val base: String,
    val branch: String,
    override val tests: TestPlan?,
) : Workspace {
    override val id: String = "repo:$repo"
    override val name: String = repo
    override val kindLabel = "Repositório (GitHub)"
    private var ref: String = base
    private var branchExists = false
    private var listing: List<FileEntry>? = null

    override suspend fun files(): List<FileEntry> {
        listing?.let { return it }
        val tree = try {
            gateway.tree(repo, branch).also {
                ref = branch
                branchExists = true
            }
        } catch (e: Exception) {
            gateway.tree(repo, base).also { ref = base }
        }
        return tree.files.map { FileEntry(it.path, it.size) }.also { listing = it }
    }

    override suspend fun read(path: String): String? {
        files()
        val f = gateway.file(repo, ref, Workspace.safePath(path))
        return if (f.exists) f.content else null
    }

    override suspend fun apply(changes: List<FileChange>, message: String): ApplyResult {
        val r = gateway.commit(repo, base, branch, message, changes.map { RepoChange(Workspace.safePath(it.path), it.after) })
        ref = branch
        branchExists = true
        listing = null
        return ApplyResult("no ramo ${r.branch} (commit ${r.commit.take(7)})", r.url)
    }
}

/** Um projeto guardado (o que a pessoa autorizou). */
@Immutable
@Serializable
data class Project(
    val id: String,
    val name: String,
    /** "folder" ou "repo". */
    val kind: String,
    val uri: String? = null,
    val repo: String? = null,
    val base: String? = null,
    val tests: TestPlan? = null,
    val addedAt: Long = System.currentTimeMillis(),
)

class Projects(context: Context) {
    private val file = File(context.filesDir, "codex/projetos.json").apply { parentFile?.mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<Project>> = _items.asStateFlow()

    private fun load(): List<Project> = runCatching { json.decodeFromString(ListSerializer(Project.serializer()), file.readText()) }.getOrDefault(emptyList())

    @Synchronized
    fun save(list: List<Project>) {
        _items.value = list
        runCatching { file.writeText(json.encodeToString(ListSerializer(Project.serializer()), list)) }
    }

    fun add(p: Project) = save(listOf(p) + _items.value.filterNot { it.id == p.id })
    fun remove(id: String) = save(_items.value.filterNot { it.id == id })
}
