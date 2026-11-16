package app.cristal.cover.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import app.cristal.cover.data.prefs.LiquidThemeId
import app.cristal.cover.data.prefs.Settings

/** O tema do Design System em uso: cores resolvidas e ajustes dos materiais. */
@Immutable
data class LiquidThemeState(
    val id: LiquidThemeId = LiquidThemeId.LIQUID_DARK,
    val colors: LiquidColors = LiquidThemes.LiquidDark,
    val tuning: MaterialTuning = MaterialTuning(),
)

val LocalLiquidTheme = staticCompositionLocalOf { LiquidThemeState() }

/** Atalhos para o tema atual: `Liquid.colors.accent`, `Liquid.type.headline`… */
object Liquid {
    val colors: LiquidColors
        @Composable @ReadOnlyComposable
        get() = LocalLiquidTheme.current.colors

    val theme: LiquidThemeState
        @Composable @ReadOnlyComposable
        get() = LocalLiquidTheme.current

    val type: LiquidTypography get() = LiquidType
}

/** Monta o tema do Design System a partir das configurações. */
fun Settings.liquidThemeState(): LiquidThemeState = LiquidThemeState(
    id = liquidTheme,
    colors = LiquidThemes.resolve(liquidTheme, liquidCustomBase, liquidCustomAccent.takeIf { it != 0 }?.let { Color(it) }),
    tuning = MaterialTuning(
        liquid = glassIntensity,
        frosted = materialFrosted,
        dark = materialDark,
        colored = materialColored,
    ),
)
