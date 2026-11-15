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

    // Grade e ícones
    val gridColumns: Int = 4,
    val gridRows: Int = 4,
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
}

/** Limites aceitos para os valores numéricos, usados na UI e na restauração de backup. */
object SettingsLimits {
    val gridColumns = 3..6
    val gridRows = 3..6
    val dockCount = 3..6
    val drawerColumns = 3..6
    const val ICON_SCALE_MIN = 0.7f
    const val ICON_SCALE_MAX = 1.3f
}
