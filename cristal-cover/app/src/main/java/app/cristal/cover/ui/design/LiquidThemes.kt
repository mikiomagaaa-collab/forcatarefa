package app.cristal.cover.ui.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.cristal.cover.data.prefs.LiquidThemeId

/**
 * Cores de um tema completo. Valem para superfícies sólidas (configurações, galeria, listas,
 * folhas) e para o que for desenhado por cima do vidro escuro; o texto sobre o vidro claro continua
 * seguindo o papel de parede.
 */
@Immutable
data class LiquidColors(
    val dark: Boolean,
    /** Fundo das telas sólidas. */
    val background: Color,
    /** Grupos e cartões sólidos sobre o fundo. */
    val surface: Color,
    /** Folhas e menus sólidos, um degrau acima. */
    val surfaceRaised: Color,
    val content: Color,
    val contentSecondary: Color,
    val contentTertiary: Color,
    val separator: Color,
    val accent: Color,
    /** Texto e ícones sobre a cor de destaque. */
    val onAccent: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
    /** Película do vidro escuro (central, menus, ilha em vidro). */
    val darkGlassFilm: Color,
    /** Escurecimento atrás da central e das folhas. */
    val scrim: Color,
    /** Três cores que resumem o papel de parede do tema (para miniaturas). */
    val preview: List<Color>,
)

object LiquidThemes {
    val LiquidDark = LiquidColors(
        dark = true,
        background = LiquidPalette.Graphite900,
        surface = LiquidPalette.Graphite800,
        surfaceRaised = LiquidPalette.Graphite700,
        content = Color(0xFFF3F1F8),
        contentSecondary = Color(0xFFA29DB2),
        contentTertiary = Color(0xFF6E6A7C),
        separator = Color(0x17FFFFFF),
        accent = Color(0xFFB9A2FF),
        onAccent = Color(0xFF17102E),
        success = LiquidPalette.SuccessDark,
        warning = LiquidPalette.WarningDark,
        danger = LiquidPalette.DangerDark,
        info = LiquidPalette.InfoDark,
        darkGlassFilm = Color(0x8C100E18),
        scrim = Color(0x52000000),
        preview = listOf(Color(0xFF0E0B22), Color(0xFF6A4BE6), Color(0xFFC14FD6)),
    )

    val LiquidLight = LiquidColors(
        dark = false,
        background = Color(0xFFF2F1F6),
        surface = LiquidPalette.White,
        surfaceRaised = LiquidPalette.White,
        content = Color(0xFF15131B),
        contentSecondary = Color(0xFF6F6A7D),
        contentTertiary = Color(0xFFA29DB0),
        separator = Color(0x1A15131B),
        accent = LiquidPalette.Violet600,
        onAccent = LiquidPalette.White,
        success = LiquidPalette.SuccessLight,
        warning = LiquidPalette.WarningLight,
        danger = LiquidPalette.DangerLight,
        info = LiquidPalette.InfoLight,
        darkGlassFilm = Color(0x661C162E),
        scrim = Color(0x75181030),
        preview = listOf(Color(0xFFEEE9FF), Color(0xFFC8B7FF), Color(0xFFFFC5E9)),
    )

    val Amoled = LiquidColors(
        dark = true,
        background = LiquidPalette.Amoled,
        surface = Color(0xFF111114),
        surfaceRaised = Color(0xFF1A1A1E),
        content = Color(0xFFF3F1F8),
        contentSecondary = Color(0xFF9C97AB),
        contentTertiary = Color(0xFF66626F),
        separator = Color(0x17FFFFFF),
        accent = Color(0xFFA98BFF),
        onAccent = Color(0xFF120B26),
        success = LiquidPalette.SuccessDark,
        warning = LiquidPalette.WarningDark,
        danger = LiquidPalette.DangerDark,
        info = LiquidPalette.InfoDark,
        darkGlassFilm = Color(0xD1121216),
        scrim = Color(0x80000000),
        preview = listOf(LiquidPalette.Amoled, LiquidPalette.Amoled, Color(0xFF6E46FF)),
    )

    val Lavender = LiquidColors(
        dark = false,
        background = Color(0xFFF3EEFF),
        surface = LiquidPalette.White,
        surfaceRaised = LiquidPalette.White,
        content = Color(0xFF1F1433),
        contentSecondary = Color(0xFF6C5E86),
        contentTertiary = Color(0xFFA195B8),
        separator = Color(0x1A1F1433),
        accent = Color(0xFF7C3AED),
        onAccent = LiquidPalette.White,
        success = LiquidPalette.SuccessLight,
        warning = LiquidPalette.WarningLight,
        danger = LiquidPalette.DangerLight,
        info = LiquidPalette.InfoLight,
        darkGlassFilm = Color(0x6B3C246E),
        scrim = Color(0x66241448),
        preview = listOf(Color(0xFFCDBDFE), Color(0xFFB59BFF), Color(0xFFF3A6DC)),
    )

    val ClassicIos = LiquidColors(
        dark = false,
        background = Color(0xFFF2F2F7),
        surface = LiquidPalette.White,
        surfaceRaised = LiquidPalette.White,
        content = Color(0xFF000000),
        contentSecondary = Color(0xFF6C6C70),
        contentTertiary = Color(0xFFAEAEB2),
        separator = Color(0x293C3C43),
        accent = LiquidPalette.InfoLight,
        onAccent = LiquidPalette.White,
        success = LiquidPalette.SuccessLight,
        warning = LiquidPalette.WarningLight,
        danger = LiquidPalette.DangerLight,
        info = LiquidPalette.InfoLight,
        darkGlassFilm = Color(0x6B0A1E46),
        scrim = Color(0x4D000000),
        preview = listOf(Color(0xFF0B5BD8), Color(0xFF3BA2FF), Color(0xFF2CD3C3)),
    )

    val OneUi = LiquidColors(
        dark = false,
        background = Color(0xFFF4F4F8),
        surface = LiquidPalette.White,
        surfaceRaised = LiquidPalette.White,
        content = Color(0xFF111111),
        contentSecondary = Color(0xFF6E6E78),
        contentTertiary = Color(0xFFA8A8B0),
        separator = Color(0x14000000),
        accent = Color(0xFF3E7BFA),
        onAccent = LiquidPalette.White,
        success = LiquidPalette.SuccessLight,
        warning = LiquidPalette.WarningLight,
        danger = LiquidPalette.DangerLight,
        info = LiquidPalette.InfoLight,
        darkGlassFilm = Color(0xA8141628),
        scrim = Color(0x59000000),
        preview = listOf(Color(0xFF2A3A8F), Color(0xFF6E8BFF), Color(0xFF9E7BFF)),
    )

    /** Destaques sugeridos para o tema Personalizado. */
    val customAccents: List<Color> = listOf(
        Color(0xFFB9A2FF), // lavanda
        LiquidPalette.Violet600,
        Color(0xFFE067C9), // orquídea
        Color(0xFFFF6B8B), // rosa
        Color(0xFFFF9F0A), // laranja
        Color(0xFF30D158), // verde
        Color(0xFF40C8E0), // ciano
        Color(0xFF0A84FF), // azul
        Color(0xFF8E8A9C), // grafite
    )

    /** As cores prontas de um tema (o Personalizado devolve o Liquid Dark). */
    fun base(id: LiquidThemeId): LiquidColors = when (id) {
        LiquidThemeId.LIQUID_DARK -> LiquidDark
        LiquidThemeId.LIQUID_LIGHT -> LiquidLight
        LiquidThemeId.AMOLED -> Amoled
        LiquidThemeId.LAVENDER -> Lavender
        LiquidThemeId.CLASSIC_IOS -> ClassicIos
        LiquidThemeId.ONE_UI -> OneUi
        LiquidThemeId.CUSTOM -> LiquidDark
    }

    /**
     * Cores efetivas. No Personalizado, parte de [customBase] e troca o destaque por [customAccent]
     * (quando informado), escolhendo texto claro ou escuro por cima dele conforme a luminância.
     */
    fun resolve(id: LiquidThemeId, customBase: LiquidThemeId = LiquidThemeId.LIQUID_DARK, customAccent: Color? = null): LiquidColors {
        if (id != LiquidThemeId.CUSTOM) return base(id)
        val start = base(if (customBase == LiquidThemeId.CUSTOM) LiquidThemeId.LIQUID_DARK else customBase)
        val accent = customAccent ?: return start
        return start.copy(accent = accent, onAccent = onColorFor(accent))
    }

    /**
     * Texto legível sobre uma cor: escuro em cores claras, branco nas escuras. O corte em 0,2 de
     * luminância é onde os dois contrastes se igualam.
     */
    fun onColorFor(color: Color): Color = if (color.luminance() > 0.2f) Color(0xFF15131B) else LiquidPalette.White
}
