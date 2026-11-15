package app.cristal.cover.ui.theme

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
import androidx.compose.ui.unit.em
import app.cristal.cover.data.prefs.GlassQuality
import app.cristal.cover.data.prefs.Settings
import app.cristal.cover.data.prefs.ThemeMode
import app.cristal.cover.data.prefs.WallpaperKind
import app.cristal.cover.ui.components.WallpaperPresets
import app.cristal.cover.ui.glass.GlassSpec
import app.cristal.cover.ui.glass.LocalGlassSpec

/** `true` quando o tema efetivo é escuro. */
val LocalIsDark = staticCompositionLocalOf { true }

/** Se a vibração de toque está ligada nas configurações. */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

/**
 * Tipografia inspirada no Liquid Glass da Apple, usando a fonte do sistema (a San Francisco é
 * exclusiva da Apple e não pode ser distribuída):
 * - títulos e números em pesos firmes (semibold/bold), que continuam legíveis sobre vidro;
 * - espaçamento entre letras apertado nos tamanhos grandes e neutro nos pequenos, como na SF;
 * - nada de pesos finos, que somem sobre fundos coloridos.
 */
private fun TextStyle.glass(weight: FontWeight, tracking: Float): TextStyle =
    copy(fontFamily = FontFamily.SansSerif, fontWeight = weight, letterSpacing = tracking.em)

private val CristalTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.glass(FontWeight.Bold, -0.035f),
        displayMedium = base.displayMedium.glass(FontWeight.Bold, -0.03f),
        displaySmall = base.displaySmall.glass(FontWeight.SemiBold, -0.025f),
        headlineLarge = base.headlineLarge.glass(FontWeight.SemiBold, -0.022f),
        headlineMedium = base.headlineMedium.glass(FontWeight.SemiBold, -0.02f),
        headlineSmall = base.headlineSmall.glass(FontWeight.SemiBold, -0.018f),
        titleLarge = base.titleLarge.glass(FontWeight.SemiBold, -0.015f),
        titleMedium = base.titleMedium.glass(FontWeight.SemiBold, -0.01f),
        titleSmall = base.titleSmall.glass(FontWeight.SemiBold, -0.005f),
        bodyLarge = base.bodyLarge.glass(FontWeight.Normal, -0.008f),
        bodyMedium = base.bodyMedium.glass(FontWeight.Normal, -0.004f),
        bodySmall = base.bodySmall.glass(FontWeight.Normal, 0f),
        labelLarge = base.labelLarge.glass(FontWeight.SemiBold, -0.004f),
        labelMedium = base.labelMedium.glass(FontWeight.Medium, 0f),
        labelSmall = base.labelSmall.glass(FontWeight.Medium, 0.01f),
    )
}

/**
 * Números grandes (relógio, temperatura, bateria): semibold, bem juntos e com largura fixa por
 * dígito, para a hora não "dançar" quando os minutos mudam.
 */
val ClockTextStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.SemiBold,
    fontFeatureSettings = "tnum",
    letterSpacing = (-0.04).em,
)

/** Relógio da tela de bloqueio: mais pesado e com um degradê sutil, como numerais de vidro. */
val LockClockTextStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Bold,
    fontFeatureSettings = "tnum",
    letterSpacing = (-0.045).em,
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
    // "Automático" segue o papel de parede: fundo escuro pede texto claro sobre o vidro, e vice-versa.
    val wallpaperDark = if (settings.wallpaperKind == WallpaperKind.PRESET) {
        WallpaperPresets.get(settings.wallpaperPreset).dark
    } else {
        settings.wallpaperImageDark
    }
    val dark = when (settings.themeMode) {
        ThemeMode.AUTO -> wallpaperDark
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
