package app.cristal.cover.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cristal.cover.data.home.WidgetKind
import app.cristal.cover.data.prefs.WidgetDensity
import app.cristal.cover.data.prefs.WidgetLook
import app.cristal.cover.ui.glass.GlassSurface
import app.cristal.cover.ui.glass.LocalGlassSpec

/** Onde o widget está: cada lugar tem composições próprias (a tela externa é pequena e quase quadrada). */
enum class WidgetScreen { INNER, COVER, LOCK, PANEL }

/**
 * O tamanho do widget, decidido pelo espaço real (não só pelas células): cada tamanho reorganiza o
 * conteúdo em vez de só aumentar ou diminuir.
 */
enum class WidgetSize {
    /** Faixa baixa (4×1): uma linha de informação. */
    STRIP,

    /** Pequeno (2×2): uma informação principal e um detalhe. */
    SMALL,

    /** Médio (4×2): a principal e uma coluna de detalhes. */
    MEDIUM,

    /** Grande (4×3, 4×4, 2×4): principal no alto e listas embaixo. */
    LARGE,

    /** Extra grande: tudo do grande com mais linhas. */
    EXTRA_LARGE,
    ;

    companion object {
        fun of(width: Dp, height: Dp): WidgetSize = when {
            height < 112.dp -> STRIP
            width < 236.dp && height < 236.dp -> SMALL
            height < 236.dp -> MEDIUM
            height >= 420.dp && width >= 300.dp -> EXTRA_LARGE
            else -> LARGE
        }
    }
}

/** A personalização dos widgets, vinda das configurações (e guardada nas coleções). */
@Immutable
data class WidgetStyle(
    val look: WidgetLook = WidgetLook.LIQUID,
    /** 0 = mais opaco, 1 = mais transparente. */
    val transparency: Float = 0.45f,
    /** Intensidade do desfoque do vidro (0 = quase nenhum, 1 = bem fosco). */
    val blur: Float = 0.55f,
    /** Cor de destaque (Unspecified = a do tema). */
    val accent: Color = Color.Unspecified,
    /** Multiplica os cantos (0,6 a 1,4). */
    val corner: Float = 1f,
    val density: WidgetDensity = WidgetDensity.BALANCED,
) {
    fun cornerRadius(base: Dp = 26.dp): Dp = (base * corner.coerceIn(0.6f, 1.4f)).coerceIn(10.dp, 40.dp)
}

val LocalWidgetStyle = staticCompositionLocalOf { WidgetStyle() }
val LocalWidgetScreen = staticCompositionLocalOf { WidgetScreen.INNER }

/** As cores de um widget, já resolvidas para a aparência, o tipo e o fundo. */
@Immutable
data class WidgetPalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val accent: Color,
    val onAccent: Color,
    val track: Color,
    val divider: Color,
    /** Preenchimento por cima do vidro (Unspecified = vidro puro). */
    val fill: Brush?,
    val dark: Boolean,
) {
    /** Uma cor de estado (bateria baixa, evento agora) adaptada à aparência. */
    fun status(color: Color): Color = if (accent == primary) primary else color
}

val LocalWidgetPalette = staticCompositionLocalOf {
    WidgetPalette(Color.White, Color.White.copy(alpha = 0.66f), Color.White.copy(alpha = 0.42f), Color(0xFFB79CFF), Color.Black, Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.12f), null, true)
}

object WidgetColors {
    /** A cor de cada tipo no estilo colorido: matizes do iOS, com a mesma saturação e brilho. */
    fun hue(kind: WidgetKind, accent: Color): Color = when (kind) {
        WidgetKind.WEATHER -> Color(0xFF2E7BEF)
        WidgetKind.MUSIC -> Color(0xFFE5426F)
        WidgetKind.BATTERY -> Color(0xFF23A866)
        WidgetKind.AGENDA, WidgetKind.DATE -> Color(0xFFE5533F)
        WidgetKind.SHORTCUTS -> Color(0xFF5856D6)
        WidgetKind.SEARCH -> Color(0xFF5E6472)
        WidgetKind.LILY -> Color(0xFF7C5CE6)
        WidgetKind.BRIEF -> Color(0xFFE08A12)
        else -> accent
    }

    fun palette(style: WidgetStyle, kind: WidgetKind, glassDark: Boolean, themeAccent: Color): WidgetPalette {
        val accent = if (style.accent == Color.Unspecified) themeAccent else style.accent
        val t = style.transparency.coerceIn(0f, 1f)
        return when (style.look) {
            WidgetLook.LIQUID -> {
                val ink = if (glassDark) Color.White else Color(0xFF111216)
                WidgetPalette(
                    primary = ink,
                    secondary = ink.copy(alpha = 0.66f),
                    tertiary = ink.copy(alpha = 0.44f),
                    accent = readable(accent, glassDark),
                    onAccent = if (accent.luminance() > 0.55f) Color(0xFF111216) else Color.White,
                    track = ink.copy(alpha = 0.16f),
                    divider = ink.copy(alpha = 0.12f),
                    // Mais opaco = uma película levemente mais densa por cima do vidro.
                    fill = Brush.verticalGradient(
                        listOf(
                            (if (glassDark) Color.Black else Color.White).copy(alpha = 0.18f * (1f - t)),
                            (if (glassDark) Color.Black else Color.White).copy(alpha = 0.26f * (1f - t)),
                        ),
                    ),
                    dark = glassDark,
                )
            }
            WidgetLook.LIGHT -> {
                val ink = Color(0xFF111216)
                val a = 0.94f - 0.5f * t
                WidgetPalette(
                    ink, ink.copy(alpha = 0.6f), ink.copy(alpha = 0.4f), readable(accent, false), Color.White,
                    ink.copy(alpha = 0.1f), ink.copy(alpha = 0.08f),
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = a), Color(0xFFF2F2F7).copy(alpha = a))),
                    false,
                )
            }
            WidgetLook.DARK -> {
                val a = 0.92f - 0.5f * t
                WidgetPalette(
                    Color.White, Color.White.copy(alpha = 0.64f), Color.White.copy(alpha = 0.42f), readable(accent, true), Color(0xFF111216),
                    Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.1f),
                    Brush.verticalGradient(listOf(Color(0xFF26252E).copy(alpha = a), Color(0xFF15141B).copy(alpha = a))),
                    true,
                )
            }
            WidgetLook.COLORFUL -> {
                val base = hue(kind, accent)
                val a = 0.96f - 0.45f * t
                WidgetPalette(
                    Color.White, Color.White.copy(alpha = 0.78f), Color.White.copy(alpha = 0.56f), Color.White, base,
                    Color.White.copy(alpha = 0.24f), Color.White.copy(alpha = 0.18f),
                    Brush.verticalGradient(listOf(lerp(base, Color.White, 0.14f).copy(alpha = a), lerp(base, Color.Black, 0.12f).copy(alpha = a))),
                    true,
                )
            }
            WidgetLook.MONO -> {
                val a = 0.9f - 0.5f * t
                WidgetPalette(
                    Color.White, Color.White.copy(alpha = 0.62f), Color.White.copy(alpha = 0.4f), Color.White, Color(0xFF111216),
                    Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.1f),
                    Brush.verticalGradient(listOf(Color(0xFF2A2A2E).copy(alpha = a), Color(0xFF1A1A1D).copy(alpha = a))),
                    true,
                )
            }
        }
    }

    /** A cor de destaque com contraste suficiente sobre o fundo do widget. */
    private fun readable(c: Color, onDark: Boolean): Color = when {
        onDark && c.luminance() < 0.18f -> lerp(c, Color.White, 0.45f)
        !onDark && c.luminance() > 0.6f -> lerp(c, Color.Black, 0.45f)
        else -> c
    }

    fun argb(c: Color): Int = if (c == Color.Unspecified) 0 else c.toArgb()
}

/**
 * A superfície de todos os widgets: o vidro do sistema (com o desfoque escolhido) e, conforme a
 * aparência, uma película clara, escura ou colorida por cima. Fornece a paleta e o tamanho ao
 * conteúdo, que se reorganiza para cada tamanho.
 */
@Composable
fun WidgetSurface(
    kind: WidgetKind,
    modifier: Modifier = Modifier,
    cornerRadius: Dp? = null,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    gestureModifier: Modifier = Modifier,
    themeAccent: Color = Color(0xFFB79CFF),
    content: @Composable BoxScope.() -> Unit,
) {
    val style = LocalWidgetStyle.current
    val glass = LocalGlassSpec.current
    val dark = when (style.look) {
        WidgetLook.LIQUID -> glass.dark
        WidgetLook.LIGHT -> false
        else -> true
    }
    val spec = glass.copy(intensity = style.blur.coerceIn(0f, 1f), dark = dark)
    val palette = WidgetColors.palette(style, kind, dark, themeAccent)
    CompositionLocalProvider(LocalGlassSpec provides spec, LocalWidgetPalette provides palette) {
        GlassSurface(
            modifier = modifier,
            cornerRadius = cornerRadius ?: style.cornerRadius(),
            onClick = onClick,
            onLongClick = onLongClick,
            innerModifier = gestureModifier,
            clipContent = true,
        ) {
            val fill = palette.fill
            if (fill != null && kind != WidgetKind.PHOTO) Box(Modifier.fillMaxSize().background(fill))
            content()
        }
    }
}

/** A aparência dos widgets guardada nas configurações. */
fun app.cristal.cover.data.prefs.Settings.widgetStyle(): WidgetStyle = WidgetStyle(
    look = widgetLook,
    transparency = widgetTransparency,
    blur = widgetBlur,
    accent = if (widgetAccent == 0) Color.Unspecified else Color(widgetAccent),
    corner = widgetCorner,
    density = widgetDensity,
)
