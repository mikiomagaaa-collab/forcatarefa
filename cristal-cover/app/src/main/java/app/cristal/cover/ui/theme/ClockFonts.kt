package app.cristal.cover.ui.theme

import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.ui.text.TextStyle
import androidx.core.content.res.ResourcesCompat
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import app.cristal.cover.R
import app.cristal.cover.data.prefs.ClockFont

private val Outfit = FontFamily(Font(R.font.clock_outfit, FontWeight.SemiBold))
private val SpaceGrotesk = FontFamily(Font(R.font.clock_space_grotesk, FontWeight.Medium))
private val Bebas = FontFamily(Font(R.font.clock_bebas, FontWeight.Normal))
private val Anton = FontFamily(Font(R.font.clock_anton, FontWeight.Normal))
private val DmSerif = FontFamily(Font(R.font.clock_dm_serif, FontWeight.Normal))
private val Playfair = FontFamily(Font(R.font.clock_playfair, FontWeight.Bold))
private val Orbitron = FontFamily(Font(R.font.clock_orbitron, FontWeight.SemiBold))
private val JetBrains = FontFamily(Font(R.font.clock_jetbrains, FontWeight.Medium))
private val Dancing = FontFamily(Font(R.font.clock_dancing, FontWeight.Bold))
private val SoraLight = FontFamily(Font(R.font.clock_sora_light, FontWeight.Light))
private val Liquid = FontFamily(Font(R.font.clock_liquid, FontWeight.SemiBold))
private val Rounded = FontFamily(Font(R.font.clock_rounded, FontWeight.SemiBold))

/** Arquivo da fonte embutida (ou `null` para as do sistema). */
private fun ClockFont.fontRes(): Int? = when (this) {
    ClockFont.LIQUID -> R.font.clock_liquid
    ClockFont.ROUNDED -> R.font.clock_rounded
    ClockFont.LIGHT -> R.font.clock_sora_light
    ClockFont.GEOMETRIC -> R.font.clock_outfit
    ClockFont.GROTESK -> R.font.clock_space_grotesk
    ClockFont.TALL -> R.font.clock_bebas
    ClockFont.IMPACT -> R.font.clock_anton
    ClockFont.EDITORIAL -> R.font.clock_dm_serif
    ClockFont.CLASSIC -> R.font.clock_playfair
    ClockFont.FUTURE -> R.font.clock_orbitron
    ClockFont.DIGITAL -> R.font.clock_jetbrains
    ClockFont.SCRIPT -> R.font.clock_dancing
    ClockFont.SYSTEM, ClockFont.THIN -> null
}

private val inkCache = HashMap<ClockFont, FloatArray>()

/**
 * Onde os algarismos realmente ficam em relação à linha de base, como fração do tamanho da fonte:
 * [0] = topo (negativo), [1] = base. Serve para o relógio encostar no alto e no fim da moldura,
 * sem o espaço vazio que toda linha de texto tem acima dos números.
 */
fun ClockFont.inkBounds(context: Context): FloatArray = synchronized(inkCache) {
    inkCache.getOrPut(this) {
        runCatching {
            val typeface = fontRes()?.let { ResourcesCompat.getFont(context, it) }
                ?: Typeface.create(Typeface.SANS_SERIF, if (this == ClockFont.THIN) 100 else 700, false)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                textSize = 1000f
            }
            val r = Rect()
            paint.getTextBounds("0123456789", 0, 10, r)
            if (r.height() <= 0) null else floatArrayOf(r.top / 1000f, r.bottom / 1000f)
        }.getOrNull() ?: floatArrayOf(-0.72f, 0.02f)
    }
}

/** Estilo de texto de cada fonte de relógio (dígitos de largura fixa quando a fonte tem). */
fun ClockFont.clockStyle(): TextStyle = when (this) {
    ClockFont.LIQUID -> TextStyle(fontFamily = Liquid, fontWeight = FontWeight.SemiBold, letterSpacing = 0.01.em)
    ClockFont.ROUNDED -> TextStyle(fontFamily = Rounded, fontWeight = FontWeight.SemiBold, letterSpacing = 0.02.em)
    ClockFont.SYSTEM -> TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, letterSpacing = (-0.045).em)
    ClockFont.THIN -> TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Thin, letterSpacing = (-0.02).em)
    ClockFont.LIGHT -> TextStyle(fontFamily = SoraLight, fontWeight = FontWeight.Light, letterSpacing = (-0.04).em)
    ClockFont.GEOMETRIC -> TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.035).em)
    ClockFont.GROTESK -> TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium, letterSpacing = (-0.05).em)
    ClockFont.TALL -> TextStyle(fontFamily = Bebas, letterSpacing = 0.01.em)
    ClockFont.IMPACT -> TextStyle(fontFamily = Anton, letterSpacing = 0.em)
    ClockFont.EDITORIAL -> TextStyle(fontFamily = DmSerif, letterSpacing = (-0.02).em)
    ClockFont.CLASSIC -> TextStyle(fontFamily = Playfair, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em)
    ClockFont.FUTURE -> TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.SemiBold, letterSpacing = 0.em)
    ClockFont.DIGITAL -> TextStyle(fontFamily = JetBrains, fontWeight = FontWeight.Medium, letterSpacing = (-0.06).em)
    ClockFont.SCRIPT -> TextStyle(fontFamily = Dancing, fontWeight = FontWeight.Bold, letterSpacing = 0.em)
}.copy(fontFeatureSettings = "tnum, lnum")
