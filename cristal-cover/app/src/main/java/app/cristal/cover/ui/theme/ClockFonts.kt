package app.cristal.cover.ui.theme

import androidx.compose.ui.text.TextStyle
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

/** Estilo de texto de cada fonte de relógio (dígitos de largura fixa quando a fonte tem). */
fun ClockFont.clockStyle(): TextStyle = when (this) {
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
