package app.lily.ai.data

import androidx.compose.runtime.Immutable
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * O que foi medido NESTE aparelho para um modelo: tempo de carregamento e velocidade das respostas.
 * Nada é estimado: só aparece depois de usar o modelo de verdade.
 */
@Immutable
@Serializable
data class PerfEntry(
    val loads: Int = 0,
    val lastLoadMillis: Long = 0,
    val lastAccel: String = "",
    val answers: Int = 0,
    /** Média móvel de caracteres por segundo das respostas. */
    val charsPerSecond: Double = 0.0,
    val updatedAt: Long = 0,
)

class PerfStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = MapSerializer(String.serializer(), PerfEntry.serializer())
    private val _items = MutableStateFlow(read())
    val items: StateFlow<Map<String, PerfEntry>> = _items.asStateFlow()

    fun recordLoad(modelId: String, accel: String, millis: Long) = edit(modelId) {
        it.copy(loads = it.loads + 1, lastLoadMillis = millis, lastAccel = accel)
    }

    /** Uma resposta de [chars] caracteres gerada em [millis] (do primeiro ao último pedaço). */
    fun recordAnswer(modelId: String, chars: Int, millis: Long) {
        if (chars < 40 || millis < 300) return
        val speed = chars * 1000.0 / millis
        edit(modelId) {
            val avg = if (it.answers == 0) speed else it.charsPerSecond * 0.7 + speed * 0.3
            it.copy(answers = it.answers + 1, charsPerSecond = avg)
        }
    }

    fun clear() = write(emptyMap())

    @Synchronized
    private fun edit(id: String, change: (PerfEntry) -> PerfEntry) {
        val current = _items.value[id] ?: PerfEntry()
        write(_items.value + (id to change(current).copy(updatedAt = System.currentTimeMillis())))
    }

    @Synchronized
    private fun write(map: Map<String, PerfEntry>) {
        _items.value = map
        runCatching {
            file.parentFile?.mkdirs()
            ConversationStore.atomicWrite(file, json.encodeToString(serializer, map))
        }
    }

    private fun read(): Map<String, PerfEntry> = runCatching {
        if (file.exists()) json.decodeFromString(serializer, file.readText()) else emptyMap()
    }.getOrElse { emptyMap() }
}
