package app.lily.ai.data

import android.util.Log
import androidx.compose.runtime.Immutable
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
enum class Role { USER, ASSISTANT }

@Serializable
enum class MsgStatus { DONE, ERROR, STOPPED }

/** Anexo de uma mensagem: arquivo dentro do app (imagem, áudio ou texto de um documento) e, se veio de fora, o URI. */
@Immutable
@Serializable
data class Attachment(val kind: String, val path: String, val sourceUri: String? = null, val name: String? = null)

@Immutable
@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val text: String,
    val at: Long = System.currentTimeMillis(),
    val attachments: List<Attachment> = emptyList(),
    /** Ferramenta que produziu a mensagem (OCR, Melhorar foto…), quando houver. */
    val tool: String? = null,
    val model: String? = null,
    val status: MsgStatus = MsgStatus.DONE,
    /** Tempo real da resposta em milissegundos. */
    val millis: Long? = null,
    /** Falta um modelo desta categoria (nome de [app.lily.ai.models.Category]): a conversa oferece o Model Hub. */
    val install: String? = null,
)

@Immutable
@Serializable
data class ConversationMeta(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean = false,
    val projectId: String? = null,
    /** Modelo escolhido à mão para esta conversa (vence o roteador). */
    val modelId: String? = null,
    val preview: String = "",
)

@Immutable
@Serializable
data class Project(val id: String, val name: String, val color: Long)

@Serializable
private data class IndexFile(val conversations: List<ConversationMeta> = emptyList(), val projects: List<Project> = emptyList())

/**
 * O histórico de conversas: um índice (títulos, fixadas, projetos) e um arquivo por conversa.
 * Histórico não é memória: a IA só usa as mensagens da conversa aberta.
 */
class ConversationStore(private val dir: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val indexFile = File(dir, "index.json")
    private val _index = MutableStateFlow(readIndex())
    val conversations: StateFlow<List<ConversationMeta>> get() = _metas
    private val _metas = MutableStateFlow(_index.value.conversations.sortedByDescending { it.updatedAt })
    private val _projects = MutableStateFlow(_index.value.projects)
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    suspend fun messages(id: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        val f = File(dir, "$id.json")
        if (!f.exists()) return@withContext emptyList()
        runCatching { json.decodeFromString(ListSerializer(ChatMessage.serializer()), f.readText()) }.getOrElse {
            Log.w(TAG, "Conversa $id ilegível", it)
            emptyList()
        }
    }

    suspend fun create(title: String, projectId: String? = null): ConversationMeta {
        val now = System.currentTimeMillis()
        val meta = ConversationMeta(UUID.randomUUID().toString(), title, now, now, projectId = projectId)
        editIndex { it.copy(conversations = listOf(meta) + it.conversations) }
        return meta
    }

    /** Grava as mensagens e atualiza a prévia e a data no índice. */
    suspend fun save(id: String, messages: List<ChatMessage>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            dir.mkdirs()
            atomicWrite(File(dir, "$id.json"), json.encodeToString(ListSerializer(ChatMessage.serializer()), messages))
        }
        val last = messages.lastOrNull { it.text.isNotBlank() }
        editIndex { idx ->
            idx.copy(conversations = idx.conversations.map {
                if (it.id == id) it.copy(updatedAt = System.currentTimeMillis(), preview = last?.text?.take(140).orEmpty()) else it
            })
        }
    }

    suspend fun rename(id: String, title: String) = update(id) { it.copy(title = title.trim().ifBlank { it.title }) }

    suspend fun pin(id: String, pinned: Boolean) = update(id) { it.copy(pinned = pinned) }

    suspend fun move(id: String, projectId: String?) = update(id) { it.copy(projectId = projectId) }

    suspend fun setModel(id: String, modelId: String?) = update(id) { it.copy(modelId = modelId) }

    /** Pasta das imagens e áudios anexados (cópias reduzidas, apagadas junto com a conversa). */
    val attachments: File get() = File(dir, "attachments").apply { mkdirs() }

    suspend fun delete(id: String) {
        withContext(Dispatchers.IO) {
            messages(id).flatMap { it.attachments }.forEach { a ->
                File(a.path).takeIf { it.path.startsWith(dir.path) }?.delete()
            }
            File(dir, "$id.json").delete()
        }
        editIndex { it.copy(conversations = it.conversations.filterNot { c -> c.id == id }) }
    }

    suspend fun deleteAll() {
        withContext(Dispatchers.IO) { dir.listFiles()?.filter { it.name != "index.json" }?.forEach { it.deleteRecursively() } }
        editIndex { it.copy(conversations = emptyList()) }
    }

    suspend fun addProject(name: String, color: Long): Project {
        val p = Project(UUID.randomUUID().toString(), name.trim(), color)
        editIndex { it.copy(projects = it.projects + p) }
        return p
    }

    suspend fun deleteProject(id: String) {
        editIndex { idx ->
            idx.copy(
                projects = idx.projects.filterNot { it.id == id },
                conversations = idx.conversations.map { if (it.projectId == id) it.copy(projectId = null) else it },
            )
        }
    }

    fun meta(id: String): ConversationMeta? = _metas.value.firstOrNull { it.id == id }

    /** Busca no título, na prévia e no texto das mensagens (sem diferenciar acentos e maiúsculas). */
    suspend fun search(query: String): List<ConversationMeta> = withContext(Dispatchers.IO) {
        val q = fold(query.trim())
        if (q.isEmpty()) return@withContext _metas.value
        _metas.value.filter { meta ->
            fold(meta.title).contains(q) || fold(meta.preview).contains(q) ||
                messages(meta.id).any { fold(it.text).contains(q) }
        }
    }

    private suspend fun update(id: String, change: (ConversationMeta) -> ConversationMeta) =
        editIndex { idx -> idx.copy(conversations = idx.conversations.map { if (it.id == id) change(it) else it }) }

    private suspend fun editIndex(change: (IndexFile) -> IndexFile) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = change(_index.value)
            _index.value = next
            _metas.value = next.conversations.sortedByDescending { it.updatedAt }
            _projects.value = next.projects
            dir.mkdirs()
            atomicWrite(indexFile, json.encodeToString(IndexFile.serializer(), next))
        }
    }

    private fun readIndex(): IndexFile = runCatching {
        if (indexFile.exists()) json.decodeFromString(IndexFile.serializer(), indexFile.readText()) else IndexFile()
    }.getOrElse { IndexFile() }

    companion object {
        private const val TAG = "LilyConversations"

        fun fold(text: String): String =
            java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

        fun atomicWrite(file: File, text: String) {
            val tmp = File(file.path + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }
}
