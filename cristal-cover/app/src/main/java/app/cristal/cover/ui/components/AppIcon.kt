package app.cristal.cover.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cristal.cover.data.apps.AppInfo
import app.cristal.cover.data.prefs.IconShape
import app.cristal.cover.data.prefs.IconStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** Superelipse ("squircle"), o formato de ícone do iOS. */
val SquircleShape: Shape = GenericShape { size, _ ->
    val rx = size.width / 2f
    val ry = size.height / 2f
    val exponent = 2.0 / 4.6
    val steps = 96
    for (i in 0..steps) {
        val t = 2.0 * PI * i / steps
        val c = cos(t)
        val s = sin(t)
        val x = rx + rx * (sign(c) * abs(c).pow(exponent)).toFloat()
        val y = ry + ry * (sign(s) * abs(s).pow(exponent)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

val HexagonShape: Shape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(w * 0.5f, 0f)
    lineTo(w * 0.96f, h * 0.25f)
    lineTo(w * 0.96f, h * 0.75f)
    lineTo(w * 0.5f, h)
    lineTo(w * 0.04f, h * 0.75f)
    lineTo(w * 0.04f, h * 0.25f)
    close()
}

fun IconShape.toShape(): Shape = when (this) {
    IconShape.CIRCLE -> CircleShape
    IconShape.SQUIRCLE -> SquircleShape
    IconShape.ROUNDED -> RoundedCornerShape(percent = 26)
    IconShape.SQUARE -> RoundedCornerShape(percent = 8)
    IconShape.TEARDROP -> RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomEndPercent = 14, bottomStartPercent = 50)
    IconShape.HEXAGON -> HexagonShape
}

/** Aparência dos ícones, vinda das configurações. */
data class IconLook(
    val shape: IconShape = IconShape.SQUIRCLE,
    val style: IconStyle = IconStyle.ORIGINAL,
    /** Cor do estilo colorido ([IconStyle.TINTED]). */
    val tint: Color = Color(0xFFB9A2FF),
)

val LocalIconLook = staticCompositionLocalOf { IconLook() }

/**
 * Ícone de um app na forma e no estilo escolhidos.
 * No estilo "vidro monocromático", apps com ícone temático viram um glifo branco sobre uma placa
 * translúcida; os demais mantêm o ícone original.
 */
@Composable
fun AppIconImage(
    app: AppInfo,
    size: Dp,
    modifier: Modifier = Modifier,
    look: IconLook = LocalIconLook.current,
) {
    val shape = look.shape.toShape()
    val mono = app.monoIcon
    if (look.style != IconStyle.ORIGINAL && mono != null) {
        // Estilos monocromáticos: placa + glifo (o temático do app ou um gerado a partir do ícone).
        val (plate, glyph, rim) = when (look.style) {
            IconStyle.TINTED -> Triple(
                Brush.linearGradient(listOf(lerp(Color(0xFF17151E), look.tint, 0.30f), lerp(Color(0xFF0E0D13), look.tint, 0.12f))),
                look.tint,
                look.tint.copy(alpha = 0.38f),
            )
            IconStyle.DARK -> Triple(
                Brush.verticalGradient(listOf(Color(0xFF2C2A33), Color(0xFF141318))),
                Color(app.accent),
                Color.White.copy(alpha = 0.10f),
            )
            else -> Triple(
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0.12f))),
                Color.White,
                Color.White.copy(alpha = 0.4f),
            )
        }
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(plate)
                .border(0.75.dp, rim, shape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = mono,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(glyph),
                filterQuality = FilterQuality.Medium,
                modifier = Modifier.fillMaxSize(),
            )
        }
    } else {
        Image(
            bitmap = app.icon,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.Medium,
            modifier = modifier
                .size(size)
                .then(if (app.maskable) Modifier.clip(shape) else Modifier.padding(1.dp)),
        )
    }
}
