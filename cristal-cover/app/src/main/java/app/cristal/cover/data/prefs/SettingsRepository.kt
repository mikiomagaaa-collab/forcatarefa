package app.cristal.cover.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cristal.cover.core.AppScope
import app.cristal.cover.core.Logx
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Chaves do DataStore. Enums são gravados pelo nome. */
object PrefKeys {
    val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")

    val THEME_MODE = stringPreferencesKey("theme_mode")
    val GLASS_QUALITY = stringPreferencesKey("glass_quality")
    val GLASS_INTENSITY = floatPreferencesKey("glass_intensity")
    val DYNAMIC_LIGHT = booleanPreferencesKey("dynamic_light")
    val ADAPTIVE_PERFORMANCE = booleanPreferencesKey("adaptive_performance")
    val REFRESH_MODE = stringPreferencesKey("refresh_mode")
    val HAPTICS = booleanPreferencesKey("haptics")

    val WALLPAPER_KIND = stringPreferencesKey("wallpaper_kind")
    val WALLPAPER_PRESET = intPreferencesKey("wallpaper_preset")
    val WALLPAPER_DIM = floatPreferencesKey("wallpaper_dim")
    val WALLPAPER_PARALLAX = booleanPreferencesKey("wallpaper_parallax")
    val WALLPAPER_BLUR = floatPreferencesKey("wallpaper_blur")
    val WALLPAPER_IMAGE_DARK = booleanPreferencesKey("wallpaper_image_dark")
    val WALLPAPER_VERSION = longPreferencesKey("wallpaper_version")

    val GRID_COLUMNS = intPreferencesKey("grid_columns")
    val GRID_ROWS = intPreferencesKey("grid_rows")
    val INNER_GRID_COLUMNS = intPreferencesKey("inner_grid_columns")
    val INNER_GRID_ROWS = intPreferencesKey("inner_grid_rows")
    val HOME_SEEDED_INNER = booleanPreferencesKey("home_seeded_inner")
    val HOME_SEEDED_COVER = booleanPreferencesKey("home_seeded_cover")
    val DOCK_ENABLED = booleanPreferencesKey("dock_enabled")
    val DOCK_COUNT = intPreferencesKey("dock_count")
    val SHOW_LABELS = booleanPreferencesKey("show_labels")
    val ICON_SCALE = floatPreferencesKey("icon_scale")
    val ICON_SHAPE = stringPreferencesKey("icon_shape")
    val ICON_STYLE = stringPreferencesKey("icon_style")
    val ICON_PACK = stringPreferencesKey("icon_pack")
    val USE_CUTOUT_AREA = booleanPreferencesKey("use_cutout_area")

    val DRAWER_SORT = stringPreferencesKey("drawer_sort")
    val DRAWER_COLUMNS = intPreferencesKey("drawer_columns")
    val DRAWER_SEARCH_ON_OPEN = booleanPreferencesKey("drawer_search_on_open")

    val GESTURE_SWIPE_UP = stringPreferencesKey("gesture_swipe_up")
    val GESTURE_SWIPE_DOWN = stringPreferencesKey("gesture_swipe_down")
    val GESTURE_DOUBLE_TAP = stringPreferencesKey("gesture_double_tap")

    val LOCK_MODE = stringPreferencesKey("lock_mode")
    val LOCK_COVER_ONLY = booleanPreferencesKey("lock_cover_only")
    val LOCK_SHOW_NOTIFICATIONS = booleanPreferencesKey("lock_show_notifications")
    val LOCK_HIDE_SENSITIVE = booleanPreferencesKey("lock_hide_sensitive")
    val LOCK_SHORTCUTS = booleanPreferencesKey("lock_shortcuts")
    val LOCK_MUSIC = booleanPreferencesKey("lock_music")
    val COVER_CLOCK_STYLE = stringPreferencesKey("cover_clock_style")
    val COVER_CLOCK_FONT = stringPreferencesKey("cover_clock_font")
    val COVER_CLOCK_LAYOUT = stringPreferencesKey("cover_clock_layout")
    val COVER_CLOCK_ADAPTIVE = booleanPreferencesKey("cover_clock_adaptive")
    val LOCK_CLOCK_FONT = stringPreferencesKey("lock_clock_font")
    val LOCK_CLOCK_LAYOUT = stringPreferencesKey("lock_clock_layout")
    val LOCK_CLOCK_ADAPTIVE = booleanPreferencesKey("lock_clock_adaptive")
    val LOCK_CLOCK_CARD = booleanPreferencesKey("lock_clock_card")
    val LOCK_CLOCK_SCALE = floatPreferencesKey("lock_clock_scale")
    val LOCK_MEDIA_POSITION = stringPreferencesKey("lock_media_position")
    val LOCK_WIDGETS = stringPreferencesKey("lock_widgets")
    val LOCK_WIDGETS_POSITION = stringPreferencesKey("lock_widgets_position")
    val COVER_CLOCK_SCALE = floatPreferencesKey("cover_clock_scale")
    val COVER_CLOCK_POSITION = stringPreferencesKey("cover_clock_position")
    val COVER_LOCK_MAX_NOTIFICATIONS = intPreferencesKey("cover_lock_max_notifications")
    val FLIP_TRANSITION = booleanPreferencesKey("flip_transition")

    val COVER_TAKEOVER = booleanPreferencesKey("cover_takeover")
    val COVER_DISPLAY_OVERRIDE = intPreferencesKey("cover_display_override")
    val AUTO_ALLOW_ON_COVER = booleanPreferencesKey("auto_allow_on_cover")
    val IGNORE_ORIENTATION_ON_COVER = booleanPreferencesKey("ignore_orientation_on_cover")
    val WARN_INCOMPATIBLE = booleanPreferencesKey("warn_incompatible")

    val WEATHER_USE_LOCATION = booleanPreferencesKey("weather_use_location")
    val WEATHER_CITY = stringPreferencesKey("weather_city")
    val WEATHER_LAT = doublePreferencesKey("weather_lat")
    val WEATHER_LON = doublePreferencesKey("weather_lon")
    val TEMP_UNIT = stringPreferencesKey("temp_unit")

    /** Cache do clima (JSON); não entra em backups. */
    val WEATHER_CACHE = stringPreferencesKey("weather_cache")

    /** Chaves que não devem ser exportadas nem restauradas. */
    val transient: Set<String> = setOf(WEATHER_CACHE.name) + WallpaperSlot.entries.flatMap { WallpaperKeys.of(it).let { k -> listOf(k.version.name, k.subjectVersion.name) } }
}

private inline fun <reified E : Enum<E>> Preferences.enumOr(key: Preferences.Key<String>, default: E): E {
    val name = this[key] ?: return default
    return enumValues<E>().firstOrNull { it.name == name } ?: default
}

internal fun Preferences.toSettings(): Settings {
    val d = Settings()
    return Settings(
        onboardingDone = this[PrefKeys.ONBOARDING_DONE] ?: d.onboardingDone,
        themeMode = enumOr(PrefKeys.THEME_MODE, d.themeMode),
        glassQuality = enumOr(PrefKeys.GLASS_QUALITY, d.glassQuality),
        glassIntensity = (this[PrefKeys.GLASS_INTENSITY] ?: d.glassIntensity).coerceIn(0f, 1f),
        dynamicLight = this[PrefKeys.DYNAMIC_LIGHT] ?: d.dynamicLight,
        adaptivePerformance = this[PrefKeys.ADAPTIVE_PERFORMANCE] ?: d.adaptivePerformance,
        refreshMode = enumOr(PrefKeys.REFRESH_MODE, d.refreshMode),
        haptics = this[PrefKeys.HAPTICS] ?: d.haptics,
        wallpaperKind = enumOr(PrefKeys.WALLPAPER_KIND, d.wallpaperKind),
        wallpaperPreset = this[PrefKeys.WALLPAPER_PRESET] ?: d.wallpaperPreset,
        wallpaperDim = (this[PrefKeys.WALLPAPER_DIM] ?: d.wallpaperDim).coerceIn(0f, 0.8f),
        wallpaperParallax = this[PrefKeys.WALLPAPER_PARALLAX] ?: d.wallpaperParallax,
        wallpaperBlur = (this[PrefKeys.WALLPAPER_BLUR] ?: d.wallpaperBlur).coerceIn(0f, 1f),
        wallpaperImageDark = this[PrefKeys.WALLPAPER_IMAGE_DARK] ?: d.wallpaperImageDark,
        wallpaperVersion = this[PrefKeys.WALLPAPER_VERSION] ?: d.wallpaperVersion,
        gridColumns = (this[PrefKeys.GRID_COLUMNS] ?: d.gridColumns).coerceIn(SettingsLimits.gridColumns),
        gridRows = (this[PrefKeys.GRID_ROWS] ?: d.gridRows).coerceIn(SettingsLimits.gridRows),
        innerGridColumns = (this[PrefKeys.INNER_GRID_COLUMNS] ?: d.innerGridColumns).coerceIn(SettingsLimits.gridColumns),
        innerGridRows = (this[PrefKeys.INNER_GRID_ROWS] ?: d.innerGridRows).coerceIn(SettingsLimits.innerGridRows),
        dockEnabled = this[PrefKeys.DOCK_ENABLED] ?: d.dockEnabled,
        dockCount = (this[PrefKeys.DOCK_COUNT] ?: d.dockCount).coerceIn(SettingsLimits.dockCount),
        showLabels = this[PrefKeys.SHOW_LABELS] ?: d.showLabels,
        iconScale = (this[PrefKeys.ICON_SCALE] ?: d.iconScale)
            .coerceIn(SettingsLimits.ICON_SCALE_MIN, SettingsLimits.ICON_SCALE_MAX),
        iconShape = enumOr(PrefKeys.ICON_SHAPE, d.iconShape),
        iconStyle = enumOr(PrefKeys.ICON_STYLE, d.iconStyle),
        iconPack = this[PrefKeys.ICON_PACK] ?: d.iconPack,
        useCutoutArea = this[PrefKeys.USE_CUTOUT_AREA] ?: d.useCutoutArea,
        drawerSort = enumOr(PrefKeys.DRAWER_SORT, d.drawerSort),
        drawerColumns = (this[PrefKeys.DRAWER_COLUMNS] ?: d.drawerColumns).coerceIn(SettingsLimits.drawerColumns),
        drawerSearchOnOpen = this[PrefKeys.DRAWER_SEARCH_ON_OPEN] ?: d.drawerSearchOnOpen,
        gestureSwipeUp = enumOr(PrefKeys.GESTURE_SWIPE_UP, d.gestureSwipeUp),
        gestureSwipeDown = enumOr(PrefKeys.GESTURE_SWIPE_DOWN, d.gestureSwipeDown),
        gestureDoubleTap = enumOr(PrefKeys.GESTURE_DOUBLE_TAP, d.gestureDoubleTap),
        lockMode = enumOr(PrefKeys.LOCK_MODE, d.lockMode),
        lockCoverOnly = this[PrefKeys.LOCK_COVER_ONLY] ?: d.lockCoverOnly,
        lockShowNotifications = this[PrefKeys.LOCK_SHOW_NOTIFICATIONS] ?: d.lockShowNotifications,
        lockHideSensitive = this[PrefKeys.LOCK_HIDE_SENSITIVE] ?: d.lockHideSensitive,
        lockShortcuts = this[PrefKeys.LOCK_SHORTCUTS] ?: d.lockShortcuts,
        lockMusic = this[PrefKeys.LOCK_MUSIC] ?: d.lockMusic,
        coverClockStyle = enumOr(PrefKeys.COVER_CLOCK_STYLE, d.coverClockStyle),
        coverClockFont = enumOr(PrefKeys.COVER_CLOCK_FONT, ClockFont.fromCoverStyle(enumOr(PrefKeys.COVER_CLOCK_STYLE, d.coverClockStyle))),
        coverClockLayout = enumOr(PrefKeys.COVER_CLOCK_LAYOUT, d.coverClockLayout),
        coverClockAdaptive = this[PrefKeys.COVER_CLOCK_ADAPTIVE] ?: d.coverClockAdaptive,
        lockClockFont = enumOr(PrefKeys.LOCK_CLOCK_FONT, d.lockClockFont),
        lockClockLayout = enumOr(PrefKeys.LOCK_CLOCK_LAYOUT, d.lockClockLayout),
        lockClockAdaptive = this[PrefKeys.LOCK_CLOCK_ADAPTIVE] ?: d.lockClockAdaptive,
        lockClockCard = this[PrefKeys.LOCK_CLOCK_CARD] ?: d.lockClockCard,
        lockClockScale = (this[PrefKeys.LOCK_CLOCK_SCALE] ?: d.lockClockScale).coerceIn(0.6f, 1.8f),
        lockMediaPosition = enumOr(PrefKeys.LOCK_MEDIA_POSITION, d.lockMediaPosition),
        lockWidgets = this[PrefKeys.LOCK_WIDGETS]?.split(',')?.mapNotNull { n -> LockWidget.entries.firstOrNull { it.name == n } }?.distinct()
            ?: d.lockWidgets,
        lockWidgetsPosition = enumOr(PrefKeys.LOCK_WIDGETS_POSITION, d.lockWidgetsPosition),
        coverClockScale = (this[PrefKeys.COVER_CLOCK_SCALE] ?: d.coverClockScale).coerceIn(0.6f, 1.8f),
        coverClockPosition = enumOr(PrefKeys.COVER_CLOCK_POSITION, d.coverClockPosition),
        coverLockMaxNotifications = (this[PrefKeys.COVER_LOCK_MAX_NOTIFICATIONS] ?: d.coverLockMaxNotifications).coerceIn(0, 4),
        flipTransition = this[PrefKeys.FLIP_TRANSITION] ?: d.flipTransition,
        coverTakeover = this[PrefKeys.COVER_TAKEOVER] ?: d.coverTakeover,
        coverDisplayOverride = this[PrefKeys.COVER_DISPLAY_OVERRIDE] ?: d.coverDisplayOverride,
        autoAllowOnCover = this[PrefKeys.AUTO_ALLOW_ON_COVER] ?: d.autoAllowOnCover,
        ignoreOrientationOnCover = this[PrefKeys.IGNORE_ORIENTATION_ON_COVER] ?: d.ignoreOrientationOnCover,
        warnIncompatible = this[PrefKeys.WARN_INCOMPATIBLE] ?: d.warnIncompatible,
        weatherUseLocation = this[PrefKeys.WEATHER_USE_LOCATION] ?: d.weatherUseLocation,
        weatherCity = this[PrefKeys.WEATHER_CITY] ?: d.weatherCity,
        weatherLat = this[PrefKeys.WEATHER_LAT] ?: d.weatherLat,
        weatherLon = this[PrefKeys.WEATHER_LON] ?: d.weatherLon,
        tempUnit = enumOr(PrefKeys.TEMP_UNIT, d.tempUnit),
        wallpapers = WallpaperKeys.readAll(this),
    ).forSlot(WallpaperSlot.HOME_INNER)
}

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @AppScope scope: CoroutineScope,
) {
    private val safeData: Flow<Preferences> = dataStore.data.catch { e ->
        if (e is IOException) {
            Logx.w("Falha ao ler preferências; usando padrões", e)
            emit(emptyPreferences())
        } else {
            throw e
        }
    }

    /** Preferências cruas, para quem guarda dados próprios (ex.: layouts da central de controle). */
    val raw: Flow<Preferences> = safeData

    /** Preferências sempre atualizadas. Começa com os padrões até o disco responder. */
    val settings: StateFlow<Settings> = safeData
        .map { it.toSettings() }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, Settings())

    /** Lê do disco, sem depender do valor inicial do [settings]. */
    suspend fun current(): Settings = safeData.first().toSettings()

    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        edit { it[key] = value }
    }

    suspend fun <E : Enum<E>> setEnum(key: Preferences.Key<String>, value: E) {
        edit { it[key] = value.name }
    }

    suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        try {
            dataStore.edit { block(it) }
        } catch (e: IOException) {
            Logx.e("Falha ao gravar preferências", e)
        }
    }

    suspend fun getString(key: Preferences.Key<String>): String? = safeData.first()[key]

    suspend fun <T> get(key: Preferences.Key<T>): T? = safeData.first()[key]

    /** Exporta tudo como pares nome → valor com marcador de tipo, para o backup. */
    suspend fun exportRaw(): Map<String, String> {
        val prefs = safeData.first()
        val out = LinkedHashMap<String, String>()
        for ((key, value) in prefs.asMap()) {
            if (key.name in PrefKeys.transient) continue
            val encoded = when (value) {
                is Boolean -> "b:$value"
                is Int -> "i:$value"
                is Long -> "l:$value"
                is Float -> "f:$value"
                is Double -> "d:$value"
                is String -> "s:$value"
                else -> null
            }
            if (encoded != null) out[key.name] = encoded
        }
        return out
    }

    /** Restaura um mapa gerado por [exportRaw]. Entradas inválidas são ignoradas. */
    suspend fun importRaw(raw: Map<String, String>) {
        edit { prefs ->
            val keep = PrefKeys.transient.mapNotNull { name ->
                prefs.asMap().entries.firstOrNull { it.key.name == name }
            }.map { it.key to it.value }
            prefs.clear()
            for ((name, encoded) in raw) {
                if (name in PrefKeys.transient || encoded.length < 2 || encoded[1] != ':') continue
                val body = encoded.substring(2)
                when (encoded[0]) {
                    'b' -> body.toBooleanStrictOrNull()?.let { prefs[booleanPreferencesKey(name)] = it }
                    'i' -> body.toIntOrNull()?.let { prefs[intPreferencesKey(name)] = it }
                    'l' -> body.toLongOrNull()?.let { prefs[longPreferencesKey(name)] = it }
                    'f' -> body.toFloatOrNull()?.let { prefs[floatPreferencesKey(name)] = it }
                    'd' -> body.toDoubleOrNull()?.let { prefs[doublePreferencesKey(name)] = it }
                    's' -> prefs[stringPreferencesKey(name)] = body
                }
            }
            for ((key, value) in keep) {
                @Suppress("UNCHECKED_CAST")
                when (value) {
                    is String -> prefs[key as Preferences.Key<String>] = value
                    is Long -> prefs[key as Preferences.Key<Long>] = value
                }
            }
        }
    }
}
