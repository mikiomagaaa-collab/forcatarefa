package app.cristal.cover.ui.lock

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import app.cristal.cover.data.prefs.ClockFill
import app.cristal.cover.data.prefs.Settings
import app.cristal.cover.ui.components.ClockFillSpec
import app.cristal.cover.ui.components.ClockGlass
import app.cristal.cover.ui.glass.GlassBackdrop

/**
 * O visual do relógio do bloqueio (interno ou externo) a partir das configurações: o de sempre
 * (vidro ou branco) ou o relógio colorido. [accent] é a cor escolhida, para harmonizar a data e os
 * widgets do bloqueio (null no original).
 */
@Immutable
data class ClockLook(val glass: ClockGlass?, val fill: ClockFillSpec?, val brush: Brush, val accent: Color?, val harmony: Boolean) {
    /** A cor dos textos em volta do relógio: a cor dele, clareada, com a harmonia ligada. */
    fun companion(base: Color): Color = if (harmony && accent != null) ClockColors.harmony(accent) else base
}

object ClockColors {
    /** Cor padrão do relógio colorido quando nenhuma foi escolhida (o violeta do Liquid Flip). */
    val Default = Color(0xFF9F86FF)

    fun first(argb: Int): Color = if (argb == 0) Default else Color(argb)

    fun second(argb: Int, first: Color): Color = if (argb != 0) Color(argb) else derived(first)

    /** Uma segunda cor que combina: o matiz andando 38° e um pouco mais clara. */
    fun derived(c: Color): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c.toArgb(), hsv)
        hsv[0] = (hsv[0] + 38f) % 360f
        hsv[1] = (hsv[1] * 0.9f).coerceIn(0f, 1f)
        hsv[2] = (hsv[2] * 1.1f + 0.05f).coerceIn(0f, 1f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }

    /** A cor clareada para a data e os widgets em volta (legível sobre a foto). */
    fun harmony(c: Color): Color = lerp(c, Color.White, 0.55f)
}

fun clockLook(
    fill: ClockFill,
    glassOn: Boolean,
    glassOpacity: Float,
    color: Int,
    color2: Int,
    opacity: Float,
    split: Boolean,
    harmony: Boolean,
    backdrop: GlassBackdrop,
): ClockLook {
    if (fill == ClockFill.ORIGINAL) {
        val tint = if (color == 0) Color.White else Color(color)
        return ClockLook(if (glassOn) ClockGlass(backdrop, glassOpacity, tint = tint) else null, null, clockBrush(color), null, harmony)
    }
    val c1 = ClockColors.first(color)
    val c2 = ClockColors.second(color2, c1)
    return when (fill) {
        // Vidro colorido: o fundo desfocado dentro dos números com uma película da cor, mais densa.
        ClockFill.LIQUID -> ClockLook(
            ClockGlass(backdrop, opacity = 0.18f + 0.5f * opacity.coerceIn(0f, 1f), tint = c1, tint2 = if (split) c2 else c1),
            null,
            clockBrush(c1.toArgb()),
            c1,
            harmony,
        )
        else -> ClockLook(null, ClockFillSpec(fill, c1, c2, opacity, split), clockBrush(c1.toArgb()), c1, harmony)
    }
}

fun Settings.lockClockLook(backdrop: GlassBackdrop): ClockLook =
    clockLook(lockClockFill, lockClockGlass, lockClockGlassOpacity, lockClockColor, lockClockColor2, lockClockFillOpacity, lockClockSplit, lockClockHarmony, backdrop)

fun Settings.coverClockLook(backdrop: GlassBackdrop): ClockLook =
    clockLook(coverClockFill, coverClockGlass, lockClockGlassOpacity, coverClockColor, coverClockColor2, coverClockFillOpacity, coverClockSplit, coverClockHarmony, backdrop)
