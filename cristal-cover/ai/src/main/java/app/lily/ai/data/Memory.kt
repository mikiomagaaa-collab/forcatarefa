package app.lily.ai.data

import androidx.compose.runtime.Immutable
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Uma memória que a pessoa mandou guardar ("prefiro respostas curtas", "moro em Recife").
 * Nada vira memória sozinho: só pelo botão "Lembrar" ou digitando na tela de memória.
 */
@Immutable
@Serializable
data class Memory(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val at: Long = System.currentTimeMillis(),
    /** Desligada, fica guardada mas a IA não usa. */
    val enabled: Boolean = true,
    /** "preferencia" ou "fato". */
    val kind: String = "fato",
)

class MemoryStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _items = MutableStateFlow(read())
    val items: StateFlow<List<Memory>> = _items.asStateFlow()

    /** O que a IA pode usar agora (com a memória ligada nas configurações). */
    fun active(): List<String> = _items.value.filter { it.enabled }.map { it.text }

    fun add(text: String, kind: String = "fato"): Memory? {
        val clean = text.trim().take(400)
        if (clean.isEmpty() || Sensitive.looksSensitive(clean)) return null
        val m = Memory(text = clean, kind = kind)
        write(_items.value + m)
        return m
    }

    fun edit(id: String, text: String) = write(_items.value.map { if (it.id == id) it.copy(text = text.trim().take(400)) else it })

    fun toggle(id: String, enabled: Boolean) = write(_items.value.map { if (it.id == id) it.copy(enabled = enabled) else it })

    fun delete(id: String) = write(_items.value.filterNot { it.id == id })

    fun clear() = write(emptyList())

    @Synchronized
    private fun write(list: List<Memory>) {
        _items.value = list
        file.parentFile?.mkdirs()
        ConversationStore.atomicWrite(file, json.encodeToString(ListSerializer(Memory.serializer()), list))
    }

    private fun read(): List<Memory> = runCatching {
        if (file.exists()) json.decodeFromString(ListSerializer(Memory.serializer()), file.readText()) else emptyList()
    }.getOrElse { emptyList() }
}

/** Não guardar como memória o que parece senha, cartão, documento ou código. */
object Sensitive {
    private val patterns = listOf(
        Regex("\\b(senha|password|pin|token|c[oó]digo de (verifica|seguran)|cvv)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}\\b"),
        Regex("\\b(?:\\d[ -]?){13,19}\\b"),
    )

    fun looksSensitive(text: String): Boolean = patterns.any { it.containsMatchIn(text) }
}
