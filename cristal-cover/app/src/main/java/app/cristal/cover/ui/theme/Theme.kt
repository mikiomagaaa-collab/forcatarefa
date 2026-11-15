package app.cristal.cover.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.cristal.cover.data.prefs.GlassQuality
import app.cristal.cover.data.prefs.Settings
import app.cristal.cover.data.prefs.ThemeMode
import app.cristal.cover.ui.glass.GlassSpec
import app.cristal.cover.ui.glass.LocalGlassSpec

/** `true` quando o tema efetivo é escuro. */
val LocalIsDark = staticCompositionLocalOf { true }

/** Se a vibração de toque está ligada nas configurações. */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

private val CristalTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Light, letterSpacing = (-1.5).sp),
        displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Light, letterSpacing = (-1).sp),
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Normal),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(letterSpacing = 0.sp),
    )
}

/** Estilo dos números grandes do relógio: finos e com largura fixa para não "dançar". */
val ClockTextStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Light,
    fontFeatureSettings = "tnum",
    letterSpacing = (-2).sp,
)

/**
 * Tema do app. Usa as cores dinâmicas do sistema (Material You) e publica a especificação do
 * vidro para todos os componentes.
 *
 * @param qualityOverride qualidade efetiva do vidro, quando o desempenho adaptativo a reduziu.
 */
@Composable
fun CristalTheme(
    settings: Settings,
    qualityOverride: GlassQuality? = null,
    content: @Composable () -> Unit,
) {
    val dark = when (settings.themeMode) {
        ThemeMode.AUTO -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    val spec = GlassSpec(
        quality = qualityOverride ?: settings.glassQuality,
        intensity = settings.glassIntensity,
        dark = dark,
    )
    MaterialTheme(colorScheme = scheme, typography = CristalTypography) {
        CompositionLocalProvider(
            LocalIsDark provides dark,
            LocalGlassSpec provides spec,
            LocalHapticsEnabled provides settings.haptics,
            content = content,
        )
    }
}
