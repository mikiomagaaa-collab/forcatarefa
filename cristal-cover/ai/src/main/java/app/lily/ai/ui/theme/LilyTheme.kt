package app.lily.ai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.lily.ai.R
import app.lily.ai.data.LilyTheme

/** As cores da L.I.L.Y num tema. */
@Immutable
data class LilyColors(
    val dark: Boolean,
    val background: Color,
    /** As três manchas de luz do fundo aurora. */
    val aurora: List<Color>,
    val glass: Color,
    val glassStrong: Color,
    val border: Color,
    val content: Color,
    val secondary: Color,
    val tertiary: Color,
    val accent: Color,
    val accent2: Color,
    val onAccent: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    /** As cores do orbe. */
    val orb: List<Color>,
)

object LilyPalettes {
    val Liquid = LilyColors(
        dark = true,
        background = Color(0xFF0B0A14),
        aurora = listOf(Color(0xFF3A2A8C), Color(0xFF0E4A6E), Color(0xFF6A1F6B)),
        glass = Color(0x14FFFFFF),
        glassStrong = Color(0x24FFFFFF),
        border = Color(0x1FFFFFFF),
        content = Color(0xFFF4F2FA),
        secondary = Color(0xFFB4AFC6),
        tertiary = Color(0xFF7D7891),
        accent = Color(0xFFB79CFF),
        accent2 = Color(0xFF64D2FF),
        onAccent = Color(0xFF160F2E),
        success = Color(0xFF34D399),
        warning = Color(0xFFFFB347),
        danger = Color(0xFFFF6B7A),
        orb = listOf(Color(0xFF7B61FF), Color(0xFF3EC6FF), Color(0xFFFF6AD5), Color(0xFFFFB36B)),
    )
    val Amoled = Liquid.copy(
        background = Color.Black,
        aurora = listOf(Color(0xFF1A1433), Color(0xFF0A1C26), Color(0xFF1E0E22)),
        glass = Color(0xFF101014),
        glassStrong = Color(0xFF18181E),
        border = Color(0xFF24242C),
        accent = Color(0xFF9D8CFF),
    )
    val Light = LilyColors(
        dark = false,
        background = Color(0xFFF5F3FB),
        aurora = listOf(Color(0xFFD9CCFF), Color(0xFFC9EEFF), Color(0xFFFFD6F1)),
        glass = Color(0xB3FFFFFF),
        glassStrong = Color(0xE6FFFFFF),
        border = Color(0x1A170F2E),
        content = Color(0xFF17141F),
        secondary = Color(0xFF5A5468),
        tertiary = Color(0xFF8C869A),
        accent = Color(0xFF6C4BD6),
        accent2 = Color(0xFF1C8BD6),
        onAccent = Color.White,
        success = Color(0xFF0F9F6E),
        warning = Color(0xFFC77800),
        danger = Color(0xFFD63A4C),
        orb = listOf(Color(0xFF6C4BD6), Color(0xFF1CA6E8), Color(0xFFE04FB0), Color(0xFFFF9A4D)),
    )
    val Purple = Liquid.copy(
        background = Color(0xFF12082A),
        aurora = listOf(Color(0xFF5B1FA8), Color(0xFF2A0E5C), Color(0xFF8C1F7A)),
        accent = Color(0xFFE3B5FF),
        accent2 = Color(0xFFFF9BD8),
        onAccent = Color(0xFF2A0B45),
    )

    fun of(theme: LilyTheme, accent: Long, systemDark: Boolean): LilyColors = when (theme) {
        LilyTheme.LIQUID -> Liquid
        LilyTheme.AMOLED -> Amoled
        LilyTheme.LIGHT -> Light
        LilyTheme.PURPLE -> Purple
        LilyTheme.CUSTOM -> {
            val a = Color(accent.toInt())
            val base = if (systemDark) Liquid else Light
            base.copy(
                accent = a,
                onAccent = if (a.luminance() > 0.45f) Color(0xFF15121E) else Color.White,
                aurora = listOf(a.copy(alpha = 0.9f), base.aurora[1], a.copy(alpha = 0.55f)),
                orb = listOf(a, base.orb[1], base.orb[2], base.orb[3]),
            )
        }
    }
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Tipografia: Inter com o tracking óptico do Liquid Flip (mais apertado nos tamanhos grandes). */
@Immutable
data class LilyType(
    val display: TextStyle,
    val title: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val callout: TextStyle,
    val subhead: TextStyle,
    val footnote: TextStyle,
    val caption: TextStyle,
)

private fun style(size: Float, weight: FontWeight, line: Float) = TextStyle(
    fontFamily = Inter,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = (-0.0223 + 0.185 * kotlin.math.exp(-0.1745 * size)).em,
)

val Type = LilyType(
    display = style(32f, FontWeight.SemiBold, 38f),
    title = style(22f, FontWeight.SemiBold, 28f),
    headline = style(17f, FontWeight.SemiBold, 22f),
    body = style(16f, FontWeight.Normal, 23f),
    callout = style(15f, FontWeight.Normal, 21f),
    subhead = style(14f, FontWeight.Normal, 19f),
    footnote = style(13f, FontWeight.Normal, 18f),
    caption = style(12f, FontWeight.Medium, 15f),
)

val LocalLily = staticCompositionLocalOf { LilyPalettes.Liquid }

object Lily {
    val colors: LilyColors
        @Composable @ReadOnlyComposable
        get() = LocalLily.current
}

@Composable
fun LilyTheme(theme: LilyTheme, accent: Long, content: @Composable () -> Unit) {
    val colors = LilyPalettes.of(theme, accent, isSystemInDarkTheme())
    val scheme = if (colors.dark) {
        darkColorScheme(primary = colors.accent, onPrimary = colors.onAccent, background = colors.background, surface = colors.background, onSurface = colors.content)
    } else {
        lightColorScheme(primary = colors.accent, onPrimary = colors.onAccent, background = colors.background, surface = colors.background, onSurface = colors.content)
    }
    CompositionLocalProvider(LocalLily provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
