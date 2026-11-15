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
    /** Efeito de profundidade: o objeto principal da foto fica na frente do relógio do bloqueio. */
    val depth: Boolean = true,
    /** Papel de parede espacial: a imagem se move de leve quando o aparelho inclina. */
    val spatial: Boolean = false,
    /** Momento em que o recorte do objeto foi gerado (0 = sem recorte). */
    val subjectVersion: Long = 0,
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
    val depth = booleanPreferencesKey("${prefix}_depth")
    val spatial = booleanPreferencesKey("${prefix}_spatial")
    val subjectVersion = longPreferencesKey("${prefix}_subject_version")

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
            depth = prefs[depth] ?: d.depth,
            spatial = prefs[spatial] ?: d.spatial,
            subjectVersion = prefs[subjectVersion] ?: d.subjectVersion,
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
        prefs[depth] = config.depth
        prefs[spatial] = config.spatial
        prefs[subjectVersion] = config.subjectVersion
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

/**
 * Fontes do relógio. As do sistema não ocupam espaço; as outras vêm no APK (licença SIL OFL).
 * @param condensed letras altas e estreitas: aguentam esticar mais no formato adaptativo.
 */
enum class ClockFont(val label: String, val condensed: Boolean = false, val maxStretch: Float = 1.35f) {
    SYSTEM("Sistema"),
    THIN("Fina"),
    LIGHT("Leve"),
    GEOMETRIC("Geométrica"),
    GROTESK("Grotesca"),
    TALL("Alta", condensed = true, maxStretch = 1.6f),
    IMPACT("Impacto", condensed = true, maxStretch = 1.55f),
    EDITORIAL("Editorial"),
    CLASSIC("Clássica"),
    FUTURE("Futurista", maxStretch = 1.25f),
    DIGITAL("Digital"),
    SCRIPT("Manuscrita", maxStretch = 1.12f),
    ;

    companion object {
        /** Os quatro estilos antigos do Cover Clock viram fontes. */
        fun fromCoverStyle(style: CoverClockStyle): ClockFont = when (style) {
            CoverClockStyle.LIQUID -> SYSTEM
            CoverClockStyle.MINIMAL -> THIN
            CoverClockStyle.DIGITAL -> DIGITAL
            CoverClockStyle.CLASSIC -> EDITORIAL
        }
    }
}

/** Forma do relógio: automática (escolhe pelo espaço livre), numa linha ou com as horas sobre os minutos. */
enum class ClockLayout(val label: String) { AUTO("Automático"), LINE("Uma linha"), STACKED("Empilhado") }

/** Onde ficam os widgets (e o player de música) na tela de bloqueio interna. */
enum class LockPlacement(val label: String) {
    ABOVE_CLOCK("Acima do relógio"),
    BELOW_CLOCK("Abaixo do relógio"),
    BOTTOM("Embaixo"),
}

/** Widgets pequenos da tela de bloqueio (como os do iPhone). */
enum class LockWidget(val label: String) { BATTERY("Bateria"), WEATHER("Clima"), AGENDA("Agenda"), ALARM("Alarme") }
