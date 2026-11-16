package app.lily.ai.models

import android.util.Log
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class InstalledModel(val id: String, val revision: String, val installedAt: Long, val bytes: Long)

@Serializable
data class InstallEvent(val id: String, val name: String, val action: String, val at: Long, val detail: String = "")

@Serializable
data class StoreState(
    val installed: List<InstalledModel> = emptyList(),
    /** Categoria → id do modelo padrão. */
    val defaults: Map<String, String> = emptyMap(),
    val history: List<InstallEvent> = emptyList(),
)

/**
 * Os modelos no aparelho: uma pasta por modelo em `files/models/<id>`, e um arquivo de estado com o
 * que foi instalado (e conferido), os padrões por categoria e o histórico de instalações.
 */
class ModelStore(private val root: File, private val clock: () -> Long = System::currentTimeMillis) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    private val stateFile = File(root, "state.json")
    private val _state = MutableStateFlow(load())
    val state: StateFlow<StoreState> = _state.asStateFlow()

    init {
        root.mkdirs()
    }

    fun dir(spec: ModelSpec): File = File(root, spec.id)

    fun file(spec: ModelSpec, file: ModelFile): File = File(dir(spec), file.path)

    /** Instalado = registrado e com todos os arquivos no tamanho certo. */
    fun isInstalled(spec: ModelSpec): Boolean {
        if (spec.builtIn) return true
        if (_state.value.installed.none { it.id == spec.id }) return false
        return spec.files.all { file(spec, it).length() == it.bytes }
    }

    fun installedIds(): Set<String> = (_state.value.installed.map { it.id }.filter { id -> Catalog.byId(id)?.let(::isInstalled) == true } +
        Catalog.all.filter { it.builtIn }.map { it.id }).toSet()

    fun installedRevision(spec: ModelSpec): String? = _state.value.installed.firstOrNull { it.id == spec.id }?.revision

    fun updateAvailable(spec: ModelSpec): Boolean = installedRevision(spec)?.let { it != spec.revision } == true

    /** Bytes já no disco (arquivos completos e partes), para retomar e calcular o espaço que falta. */
    fun bytesOnDisk(spec: ModelSpec): Long = spec.files.sumOf { f ->
        val done = file(spec, f)
        if (done.length() == f.bytes) f.bytes else File(done.path + ".part").length()
    }

    fun markInstalled(spec: ModelSpec, update: Boolean) {
        edit { s ->
            s.copy(
                installed = s.installed.filterNot { it.id == spec.id } + InstalledModel(spec.id, spec.revision.orEmpty(), clock(), spec.totalBytes),
                history = (listOf(InstallEvent(spec.id, spec.name, if (update) "Atualizado" else "Instalado", clock(), spec.revision?.take(7).orEmpty())) + s.history).take(HISTORY),
                defaults = if (s.defaults.keys.none { k -> spec.categories.any { it.name == k } }) {
                    s.defaults + spec.categories.associate { it.name to spec.id }.filterKeys { it !in s.defaults }
                } else {
                    s.defaults
                },
            )
        }
    }

    fun uninstall(spec: ModelSpec) {
        dir(spec).deleteRecursively()
        edit { s ->
            s.copy(
                installed = s.installed.filterNot { it.id == spec.id },
                defaults = s.defaults.filterValues { it != spec.id },
                history = (listOf(InstallEvent(spec.id, spec.name, "Desinstalado", clock())) + s.history).take(HISTORY),
            )
        }
    }

    fun recordFailure(spec: ModelSpec, detail: String) {
        edit { s -> s.copy(history = (listOf(InstallEvent(spec.id, spec.name, "Falhou", clock(), detail.take(120))) + s.history).take(HISTORY)) }
    }

    fun setDefault(category: Category, spec: ModelSpec) {
        edit { s -> s.copy(defaults = s.defaults + (category.name to spec.id)) }
    }

    fun defaults(): Map<Category, String> =
        _state.value.defaults.mapNotNull { (k, v) -> Category.entries.firstOrNull { it.name == k }?.let { it to v } }.toMap()

    /** Apaga partes de downloads abandonados. */
    fun discardPartial(spec: ModelSpec) {
        spec.files.forEach { File(file(spec, it).path + ".part").delete() }
    }

    @Synchronized
    private fun edit(change: (StoreState) -> StoreState) {
        val next = change(_state.value)
        _state.value = next
        try {
            root.mkdirs()
            val tmp = File(root, "state.json.tmp")
            tmp.writeText(json.encodeToString(StoreState.serializer(), next))
            if (!tmp.renameTo(stateFile)) {
                stateFile.delete()
                tmp.renameTo(stateFile)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Não foi possível gravar o estado dos modelos", t)
        }
    }

    private fun load(): StoreState = try {
        if (stateFile.exists()) json.decodeFromString(StoreState.serializer(), stateFile.readText()) else StoreState()
    } catch (t: Throwable) {
        Log.w(TAG, "Estado dos modelos ilegível", t)
        StoreState()
    }

    private companion object {
        const val TAG = "LilyModels"
        const val HISTORY = 100
    }
}
