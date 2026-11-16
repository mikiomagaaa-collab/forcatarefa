package app.cristal.cover.data.collections

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cristal.cover.core.Logx
import app.cristal.cover.core.safely
import app.cristal.cover.data.prefs.PrefKeys
import app.cristal.cover.data.prefs.Settings
import app.cristal.cover.data.prefs.SettingsRepository
import app.cristal.cover.data.prefs.WallpaperKeys
import app.cristal.cover.data.prefs.WallpaperSlot
import app.cristal.cover.data.prefs.toSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Uma coleção da Wallpaper Library: a aparência completa do aparelho guardada com um nome.
 *
 * @param prefs as configurações de aparência no formato de [SettingsRepository.exportRaw].
 * @param images arquivos de imagem guardados junto (papéis de parede e recortes de profundidade).
 */
@Serializable
data class WallpaperCollection(
    val id: String,
    val name: String,
    val created: Long,
    val updated: Long,
    val prefs: Map<String, String>,
    val images: List<String> = emptyList(),
)

/** O que a galeria precisa para desenhar a prévia de uma coleção sem aplicá-la. */
class CollectionPreview(
    val settings: Settings,
    val images: Map<WallpaperSlot, ImageBitmap>,
    val subjects: Map<WallpaperSlot, ImageBitmap>,
)

/** Quais configurações fazem parte da aparência guardada numa coleção. */
object AppearanceKeys {
    private val prefixes = listOf(
        "wallpaper", // as quatro áreas, com enquadramento, efeitos, profundidade e espacial
        "lock_clock", "cover_clock", // estilo, fonte, tamanho, cor e posição X/Y/Z dos relógios
        "lock_widgets", "lock_media",
        "liquid_", "material_", // tema do Design System e materiais
        "icon_tint", "icon_style",
    )
    private val exact = setOf("glass_intensity", "glass_quality", "theme_mode")

    fun belongs(name: String): Boolean =
        name !in PrefKeys.transient && (name in exact || prefixes.any { name.startsWith(it) })
}

/**
 * Guarda as coleções no próprio aparelho, em `files/collections/<id>/`: um `meta.json` com as
 * configurações e cópias das imagens. Nada vai para a internet.
 */
@Singleton
class CollectionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val mutex = Mutex()
    private val root: File get() = File(context.filesDir, "collections").apply { mkdirs() }

    private val _collections = MutableStateFlow<List<WallpaperCollection>>(emptyList())
    val collections: StateFlow<List<WallpaperCollection>> = _collections.asStateFlow()

    /** A coleção aplicada por último (para marcar "Em uso"). */
    private val _active = MutableStateFlow<String?>(null)
    val active: StateFlow<String?> = _active.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        mutex.withLock { reload() }
    }

    private fun reload() {
        val list = root.listFiles()?.mapNotNull { dir ->
            safely("ler coleção ${dir.name}") {
                json.decodeFromString(WallpaperCollection.serializer(), File(dir, META).readText())
            }
        }.orEmpty().sortedBy { it.created }
        _collections.value = list
        _active.value = safely("ler coleção ativa") { File(root, ACTIVE).takeIf { it.exists() }?.readText()?.trim() }
            ?.takeIf { id -> list.any { it.id == id } }
    }

    /** Guarda a aparência atual como uma coleção nova. */
    suspend fun saveCurrent(name: String): WallpaperCollection = withContext(Dispatchers.IO) {
        mutex.withLock {
            val id = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val collection = snapshot(id, name.ifBlank { "Coleção" }, now, now)
            File(root, id).mkdirs()
            write(collection)
            markActive(id)
            reload()
            collection
        }
    }

    /** Troca o conteúdo de uma coleção pela aparência atual, mantendo o nome. */
    suspend fun overwrite(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val old = _collections.value.firstOrNull { it.id == id } ?: return@withLock
            File(root, id).listFiles()?.forEach { if (it.name != META) it.delete() }
            write(snapshot(id, old.name, old.created, System.currentTimeMillis()))
            markActive(id)
            reload()
        }
    }

    suspend fun rename(id: String, name: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val old = _collections.value.firstOrNull { it.id == id } ?: return@withLock
            write(old.copy(name = name.ifBlank { old.name }, updated = System.currentTimeMillis()))
            reload()
        }
    }

    suspend fun duplicate(id: String): WallpaperCollection? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val old = _collections.value.firstOrNull { it.id == id } ?: return@withLock null
            val newId = UUID.randomUUID().toString()
            val dir = File(root, newId).apply { mkdirs() }
            for (name in old.images) {
                safely("copiar $name") { File(File(root, id), name).copyTo(File(dir, name), overwrite = true) }
            }
            val now = System.currentTimeMillis()
            val copy = old.copy(id = newId, name = "${old.name} (cópia)", created = now, updated = now)
            write(copy)
            reload()
            copy
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            File(root, id).deleteRecursively()
            if (_active.value == id) File(root, ACTIVE).delete()
            reload()
        }
    }

    /**
     * Aplica uma coleção: troca só as configurações de aparência (o resto do aparelho fica como
     * está), devolve as imagens às áreas e avisa o Wallpaper Studio para recarregar.
     */
    suspend fun apply(id: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val collection = _collections.value.firstOrNull { it.id == id } ?: return@withLock false
            val dir = File(root, id)
            val now = System.currentTimeMillis()
            // 1. Imagens: copia de volta (ou apaga a da área, se a coleção não tinha).
            for (slot in WallpaperSlot.entries) {
                for (name in listOf(imageName(slot), subjectName(slot))) {
                    val target = File(context.filesDir, name)
                    val source = File(dir, name)
                    if (name in collection.images && source.exists()) {
                        safely("restaurar $name") {
                            val tmp = File(context.filesDir, "$name.tmp")
                            source.copyTo(tmp, overwrite = true)
                            if (!tmp.renameTo(target)) {
                                tmp.copyTo(target, overwrite = true)
                                tmp.delete()
                            }
                        }
                    } else {
                        target.delete()
                    }
                }
            }
            // 2. Configurações de aparência + versões novas, para as imagens serem recarregadas.
            settingsRepository.edit { prefs ->
                prefs.asMap().keys.filter { AppearanceKeys.belongs(it.name) }.forEach { prefs.remove(it) }
                for ((name, encoded) in collection.prefs) {
                    if (!AppearanceKeys.belongs(name)) continue
                    decodeInto(prefs, name, encoded)
                }
                for (slot in WallpaperSlot.entries) {
                    val keys = WallpaperKeys.of(slot)
                    prefs[keys.version] = if (imageName(slot) in collection.images) now else 0L
                    prefs[keys.subjectVersion] = if (subjectName(slot) in collection.images) now else 0L
                }
            }
            markActive(id)
            reload()
            true
        }
    }

    /** Monta a prévia de uma coleção (configurações e imagens reduzidas), sem aplicar nada. */
    suspend fun preview(collection: WallpaperCollection, maxWidth: Int = 540): CollectionPreview = withContext(Dispatchers.IO) {
        val prefs = mutablePreferencesOf()
        for ((name, encoded) in collection.prefs) decodeInto(prefs, name, encoded)
        val dir = File(root, collection.id)
        val images = HashMap<WallpaperSlot, ImageBitmap>()
        val subjects = HashMap<WallpaperSlot, ImageBitmap>()
        for (slot in WallpaperSlot.entries) {
            decodeScaled(File(dir, imageName(slot)), maxWidth)?.let { images[slot] = it }
            decodeScaled(File(dir, subjectName(slot)), maxWidth)?.let { subjects[slot] = it }
        }
        CollectionPreview(prefs.toSettings(), images, subjects)
    }

    private suspend fun snapshot(id: String, name: String, created: Long, updated: Long): WallpaperCollection {
        val raw = settingsRepository.exportRaw().filterKeys(AppearanceKeys::belongs)
        val dir = File(root, id).apply { mkdirs() }
        val images = ArrayList<String>()
        for (slot in WallpaperSlot.entries) {
            for (fileName in listOf(imageName(slot), subjectName(slot))) {
                val source = File(context.filesDir, fileName)
                if (source.exists() && source.length() > 0) {
                    val ok = safely("guardar $fileName") { source.copyTo(File(dir, fileName), overwrite = true); true } == true
                    if (ok) images += fileName
                }
            }
        }
        return WallpaperCollection(id, name, created, updated, raw, images)
    }

    private fun write(collection: WallpaperCollection) {
        val dir = File(root, collection.id).apply { mkdirs() }
        val tmp = File(dir, "$META.tmp")
        tmp.writeText(json.encodeToString(WallpaperCollection.serializer(), collection))
        if (!tmp.renameTo(File(dir, META))) {
            File(dir, META).writeText(tmp.readText())
            tmp.delete()
        }
    }

    private fun markActive(id: String) {
        safely("marcar coleção ativa") { File(root, ACTIVE).writeText(id) }
        _active.value = id
    }

    private fun decodeScaled(file: File, maxWidth: Int): ImageBitmap? {
        if (!file.exists()) return null
        return safely("ler prévia ${file.name}") {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
            BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            )?.asImageBitmap()
        }
    }

    companion object {
        private const val META = "meta.json"
        private const val ACTIVE = "active.txt"

        fun imageName(slot: WallpaperSlot): String = if (slot.key.isEmpty()) "wallpaper.jpg" else "wallpaper_${slot.key}.jpg"

        fun subjectName(slot: WallpaperSlot): String = if (slot.key.isEmpty()) "wallpaper_subject.png" else "wallpaper_${slot.key}_subject.png"

        /** Grava um valor no formato de [SettingsRepository.exportRaw] ("b:true", "f:0.5"…). */
        internal fun decodeInto(prefs: androidx.datastore.preferences.core.MutablePreferences, name: String, encoded: String) {
            if (encoded.length < 2 || encoded[1] != ':') return
            val body = encoded.substring(2)
            try {
                when (encoded[0]) {
                    'b' -> body.toBooleanStrictOrNull()?.let { prefs[booleanPreferencesKey(name)] = it }
                    'i' -> body.toIntOrNull()?.let { prefs[intPreferencesKey(name)] = it }
                    'l' -> body.toLongOrNull()?.let { prefs[longPreferencesKey(name)] = it }
                    'f' -> body.toFloatOrNull()?.let { prefs[floatPreferencesKey(name)] = it }
                    'd' -> body.toDoubleOrNull()?.let { prefs[doublePreferencesKey(name)] = it }
                    's' -> prefs[stringPreferencesKey(name)] = body
                }
            } catch (t: Throwable) {
                // Mesma chave gravada com outro tipo numa versão antiga: ignora esse valor.
                Logx.w("Valor de coleção ignorado: $name", t)
            }
        }

        /** Só para testes: as preferências de uma coleção. */
        internal fun prefsOf(collection: WallpaperCollection): Preferences {
            val prefs = mutablePreferencesOf()
            for ((name, encoded) in collection.prefs) decodeInto(prefs, name, encoded)
            return prefs
        }
    }
}
