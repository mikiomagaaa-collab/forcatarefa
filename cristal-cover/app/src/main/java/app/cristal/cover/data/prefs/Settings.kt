package app.cristal.cover.data.prefs

import androidx.compose.runtime.Immutable

enum class ThemeMode { AUTO, LIGHT, DARK }

/** Nível de qualidade do vidro. Quanto maior, mais custo de GPU. */
enum class GlassQuality {
    /** Sem blur nem shader: só película translúcida e borda. */
    LIGHT,

    /** Blur real do fundo (RenderEffect), sem refração. */
    BLUR,

    /** Blur + refração, dispersão e brilho especular em AGSL. */
    LIQUID,
}

enum class IconShape { CIRCLE, SQUIRCLE, ROUNDED, SQUARE, TEARDROP, HEXAGON }

enum class IconStyle {
    /** Ícone original (ou do pacote de ícones). */
    ORIGINAL,

    /** Glifo monocromático sobre uma placa de vidro, quando o app oferece ícone temático. */
    MONO_GLASS,
}

enum class GestureAction {
    NONE,
    APP_DRAWER,
    QUICK_PANEL,
    SYSTEM_NOTIFICATIONS,
    SYSTEM_QUICK_SETTINGS,
    LOCK_SCREEN,
    RECENTS,
    SETTINGS,
    TORCH,
}

enum class RefreshMode {
    /** O sistema decide (respeita a taxa adaptativa da Motorola). */
    AUTO,

    /** Pede a maior taxa disponível (144 Hz na tela externa). */
    MAX,

    /** Limita a 60 Hz para economizar bateria. */
    SAVER,
}

enum class WallpaperKind { PRESET, IMAGE }

enum class LockMode {
    OFF,

    /** Activity exibida sobre o keyguard do sistema (recomendado). */
    ACTIVITY,

    /** Janela de acessibilidade sobre o keyguard (alternativa experimental). */
    OVERLAY,
}

enum class DrawerSort { NAME, USAGE }

enum class TempUnit { CELSIUS, FAHRENHEIT }

/** Todas as preferências do app, já com os valores padrão. */
@Immutable
data class Settings(
    val onboardingDone: Boolean = false,

    // Aparência
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val glassQuality: GlassQuality = GlassQuality.LIQUID,
    /** 0 = mais transparente, 0,5 = padrão, 1 = mais fosco. */
    val glassIntensity: Float = 0.45f,
    val dynamicLight: Boolean = true,
    val adaptivePerformance: Boolean = true,
    val refreshMode: RefreshMode = RefreshMode.AUTO,
    val haptics: Boolean = true,

    // Papel de parede
    val wallpaperKind: WallpaperKind = WallpaperKind.PRESET,
    val wallpaperPreset: Int = 0,
    val wallpaperDim: Float = 0.1f,
    val wallpaperParallax: Boolean = true,
    val wallpaperBlur: Float = 0f,
    /** Se a imagem escolhida é escura (pede texto claro sobre o vidro). */
    val wallpaperImageDark: Boolean = true,
    /** Muda a cada troca de imagem para invalidar caches. */
    val wallpaperVersion: Long = 0,
    val wallpaperZoom: Float = 1f,
    val wallpaperOffsetX: Float = 0f,
    val wallpaperOffsetY: Float = 0f,
    val wallpaperSaturation: Float = 1f,
    /** Valores da área em uso (preenchidos por [forSlot]). */
    val wallpaperDepth: Boolean = true,
    val wallpaperSpatial: Boolean = false,
    /** Área cuja imagem está em uso nesta "visão" das preferências (ver [forSlot]). */
    val wallpaperSlot: WallpaperSlot = WallpaperSlot.HOME_INNER,
    /** Configuração bruta das quatro áreas. Os campos `wallpaper*` acima são a área resolvida. */
    val wallpapers: Map<WallpaperSlot, WallpaperConfig> = WallpaperSlot.entries.associateWith {
        WallpaperConfig(follows = WallpaperKeys.defaultFollows(it))
    },

    // Grade e ícones
    val gridColumns: Int = 4,
    val gridRows: Int = 4,
    /** Grade da tela interna; `gridColumns`/`gridRows` são da tela externa. */
    val innerGridColumns: Int = 4,
    val innerGridRows: Int = 8,
    val dockEnabled: Boolean = true,
    val dockCount: Int = 4,
    val showLabels: Boolean = true,
    val iconScale: Float = 1f,
    val iconShape: IconShape = IconShape.SQUIRCLE,
    val iconStyle: IconStyle = IconStyle.ORIGINAL,
    val iconPack: String = "",
    val useCutoutArea: Boolean = true,

    // Gaveta
    val drawerSort: DrawerSort = DrawerSort.NAME,
    val drawerColumns: Int = 4,
    val drawerSearchOnOpen: Boolean = false,

    // Gestos
    val gestureSwipeUp: GestureAction = GestureAction.APP_DRAWER,
    val gestureSwipeDown: GestureAction = GestureAction.QUICK_PANEL,
    val gestureDoubleTap: GestureAction = GestureAction.LOCK_SCREEN,

    // Tela de bloqueio
    val lockMode: LockMode = LockMode.OFF,
    val lockCoverOnly: Boolean = true,
    val lockShowNotifications: Boolean = true,
    val lockHideSensitive: Boolean = true,
    val lockShortcuts: Boolean = true,
    val lockMusic: Boolean = true,

    // Cover Clock (bloqueio da tela externa)
    val coverClockStyle: CoverClockStyle = CoverClockStyle.LIQUID,
    val coverClockFont: ClockFont = ClockFont.SYSTEM,
    val coverClockLayout: ClockLayout = ClockLayout.AUTO,
    val coverClockAdaptive: Boolean = true,
    /** Relógio do bloqueio interno (e do widget de relógio da tela inicial). */
    val lockClockFont: ClockFont = ClockFont.SYSTEM,
    val lockClockLayout: ClockLayout = ClockLayout.AUTO,
    val lockClockAdaptive: Boolean = true,
    /** Relógio dentro de um cartão de vidro (desligado: números grandes direto sobre a foto). */
    val lockClockCard: Boolean = true,
    /** Tamanho do relógio do bloqueio interno (também ajustável arrastando na pré-visualização). */
    val lockClockScale: Float = 1f,
    val lockMediaPosition: LockPlacement = LockPlacement.BELOW_CLOCK,
    val lockWidgets: List<LockWidget> = emptyList(),
    val lockWidgetsPosition: LockPlacement = LockPlacement.BELOW_CLOCK,
    val coverClockScale: Float = 1f,
    val coverClockPosition: CoverClockPosition = CoverClockPosition.TOP,
    val coverLockMaxNotifications: Int = 3,

    // Flip
    val flipTransition: Boolean = true,

    // Tela externa
    val coverTakeover: Boolean = false,
    val coverDisplayOverride: Int = -1,
    val autoAllowOnCover: Boolean = true,
    val ignoreOrientationOnCover: Boolean = false,
    val warnIncompatible: Boolean = true,

    // Clima
    val weatherUseLocation: Boolean = false,
    val weatherCity: String = "",
    val weatherLat: Double = Double.NaN,
    val weatherLon: Double = Double.NaN,
    val tempUnit: TempUnit = TempUnit.CELSIUS,
) {
    val hasWeatherPlace: Boolean get() = !weatherLat.isNaN() && !weatherLon.isNaN()

    fun columns(onCover: Boolean): Int = if (onCover) gridColumns else innerGridColumns
    fun rows(onCover: Boolean): Int = if (onCover) gridRows else innerGridRows

    /** Área que realmente fornece o papel de parede de [slot], seguindo "igual a outra área". */
    fun resolvedSlot(slot: WallpaperSlot): WallpaperSlot {
        var current = slot
        repeat(WallpaperSlot.entries.size) {
            val next = wallpapers[current]?.follows
            if (next == null || next == current) return current
            current = next
        }
        return current
    }

    /** Estas preferências "vistas" de uma área: os campos `wallpaper*` passam a ser os dela. */
    fun forSlot(slot: WallpaperSlot): Settings {
        val resolved = resolvedSlot(slot)
        val c = wallpapers[resolved] ?: WallpaperConfig()
        return copy(
            wallpaperSlot = resolved,
            wallpaperKind = c.kind,
            wallpaperPreset = c.preset,
            wallpaperDim = c.dim,
            wallpaperBlur = c.blur,
            wallpaperImageDark = c.imageDark,
            wallpaperVersion = c.version,
            wallpaperZoom = c.zoom,
            wallpaperOffsetX = c.offsetX,
            wallpaperOffsetY = c.offsetY,
            wallpaperSaturation = c.saturation,
            wallpaperDepth = c.depth,
            wallpaperSpatial = c.spatial,
        )
    }

    /** Preferências para a tela inicial de uma das telas: papel de parede e grade próprios. */
    fun forScreen(onCover: Boolean): Settings {
        val view = forSlot(WallpaperSlot.home(onCover))
        return if (onCover) view else view.copy(gridColumns = innerGridColumns, gridRows = innerGridRows)
    }
}

/** Limites aceitos para os valores numéricos, usados na UI e na restauração de backup. */
object SettingsLimits {
    val gridColumns = 3..6
    val gridRows = 3..6
    val innerGridRows = 5..9
    val dockCount = 3..6
    val drawerColumns = 3..6
    const val ICON_SCALE_MIN = 0.7f
    const val ICON_SCALE_MAX = 1.3f
}
