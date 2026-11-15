package app.cristal.cover.data.prefs

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/** As quatro áreas que podem ter papel de parede próprio. */
enum class WallpaperSlot(val key: String, val label: String, val shortLabel: String, val isLock: Boolean, val onCover: Boolean) {
    // A chave vazia mantém os nomes antigos das preferências: o papel de parede que o usuário
    // já tinha continua valendo para a tela inicial interna.
    HOME_INNER("", "Início · tela interna", "Início interna", false, false),
    HOME_COVER("home_cover", "Início · tela externa", "Início externa", false, true),
    LOCK_INNER("lock_inner", "Bloqueio · tela interna", "Bloqueio interna", true, false),
    LOCK_COVER("lock_cover", "Bloqueio · tela externa", "Bloqueio externa", true, true);

    companion object {
        fun home(onCover: Boolean): WallpaperSlot = if (onCover) HOME_COVER else HOME_INNER
        fun lock(onCover: Boolean): WallpaperSlot = if (onCover) LOCK_COVER else LOCK_INNER
        fun fromName(name: String?): WallpaperSlot? = entries.firstOrNull { it.name == name }
    }
}

/** Configuração de uma área. Com [follows] preenchido, a área usa a configuração de outra. */
@Immutable
data class WallpaperConfig(
    val kind: WallpaperKind = WallpaperKind.PRESET,
    val preset: Int = 0,
    val dim: Float = 0.1f,
    val blur: Float = 0f,
    val zoom: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val saturation: Float = 1f,
    val imageDark: Boolean = true,
    val version: Long = 0,
    val follows: WallpaperSlot? = null,
)

/** Imagens escolhidas pelo usuário, por área (só as áreas com imagem própria aparecem). */
typealias WallpaperImages = Map<WallpaperSlot, ImageBitmap>

/** Chaves do DataStore de uma área. */
class WallpaperKeys private constructor(slot: WallpaperSlot) {
    private val prefix = if (slot.key.isEmpty()) "wallpaper" else "wallpaper_${slot.key}"

    val kind = stringPreferencesKey("${prefix}_kind")
    val preset = intPreferencesKey("${prefix}_preset")
    val dim = floatPreferencesKey("${prefix}_dim")
    val blur = floatPreferencesKey("${prefix}_blur")
    val zoom = floatPreferencesKey("${prefix}_zoom")
    val offsetX = floatPreferencesKey("${prefix}_offset_x")
    val offsetY = floatPreferencesKey("${prefix}_offset_y")
    val saturation = floatPreferencesKey("${prefix}_saturation")
    val imageDark = booleanPreferencesKey("${prefix}_image_dark")
    val version = longPreferencesKey("${prefix}_version")
    val follows = stringPreferencesKey("${prefix}_follows")

    /** @param defaultFollows usado enquanto o usuário nunca configurou esta área. */
    fun read(prefs: Preferences, defaultFollows: WallpaperSlot?): WallpaperConfig {
        val d = WallpaperConfig()
        val followsRaw = prefs[follows]
        return WallpaperConfig(
            kind = WallpaperKind.entries.firstOrNull { it.name == prefs[kind] } ?: d.kind,
            preset = prefs[preset] ?: d.preset,
            dim = (prefs[dim] ?: d.dim).coerceIn(0f, 0.8f),
            blur = (prefs[blur] ?: d.blur).coerceIn(0f, 1f),
            zoom = (prefs[zoom] ?: d.zoom).coerceIn(1f, 4f),
            offsetX = (prefs[offsetX] ?: d.offsetX).coerceIn(-1f, 1f),
            offsetY = (prefs[offsetY] ?: d.offsetY).coerceIn(-1f, 1f),
            saturation = (prefs[saturation] ?: d.saturation).coerceIn(0f, 2f),
            imageDark = prefs[imageDark] ?: d.imageDark,
            version = prefs[version] ?: d.version,
            follows = if (followsRaw == null) defaultFollows else WallpaperSlot.fromName(followsRaw),
        )
    }

    fun write(prefs: MutablePreferences, config: WallpaperConfig) {
        prefs[kind] = config.kind.name
        prefs[preset] = config.preset
        prefs[dim] = config.dim
        prefs[blur] = config.blur
        prefs[zoom] = config.zoom
        prefs[offsetX] = config.offsetX
        prefs[offsetY] = config.offsetY
        prefs[saturation] = config.saturation
        prefs[imageDark] = config.imageDark
        prefs[version] = config.version
        prefs[follows] = config.follows?.name.orEmpty()
    }

    companion object {
        private val all = WallpaperSlot.entries.associateWith { WallpaperKeys(it) }

        fun of(slot: WallpaperSlot): WallpaperKeys = all.getValue(slot)

        /** Enquanto não forem configuradas, as outras áreas acompanham a tela inicial interna. */
        fun defaultFollows(slot: WallpaperSlot): WallpaperSlot? =
            if (slot == WallpaperSlot.HOME_INNER) null else WallpaperSlot.HOME_INNER

        fun readAll(prefs: Preferences): Map<WallpaperSlot, WallpaperConfig> =
            WallpaperSlot.entries.associateWith { of(it).read(prefs, defaultFollows(it)) }
    }
}

/** Estilos do relógio da tela de bloqueio externa. */
enum class CoverClockStyle { LIQUID, MINIMAL, DIGITAL, CLASSIC }

enum class CoverClockPosition { TOP, CENTER, BOTTOM }
