package app.cristal.cover.ui.design

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.cristal.cover.ui.theme.UiFont
import kotlin.math.PI
import kotlin.math.exp

/*
 * Tokens do Design System Liquid Flip.
 *
 * Telas novas usam estes valores em vez de números soltos: a mesma escala de espaço, os mesmos
 * cantos concêntricos, a mesma tipografia e as mesmas molas no launcher, no bloqueio, na ilha, na
 * central, nas configurações, na galeria e no teclado.
 */

/** Paleta base. Os temas escolhem destas cores; as telas não inventam cores novas. */
object LiquidPalette {
    val Amoled = Color(0xFF000000)
    val Graphite900 = Color(0xFF0E0D13)
    val Graphite850 = Color(0xFF15131B)
    val Graphite800 = Color(0xFF1A1822)
    val Graphite700 = Color(0xFF24212D)
    val Mist400 = Color(0xFF8E8A9C)
    val Mist200 = Color(0xFFD8D5E2)
    val Snow = Color(0xFFF5F4F8)
    val White = Color(0xFFFFFFFF)
    val Plum900 = Color(0xFF1A1033)
    val Violet600 = Color(0xFF6544E8)
    val Violet400 = Color(0xFF9F86FF)
    val Lavender300 = Color(0xFFC9B8FF)
    val Lilac100 = Color(0xFFEEE8FF)

    // Cores de estado para fundos escuros e para fundos claros (como as do sistema no iPhone).
    val SuccessDark = Color(0xFF30D158)
    val WarningDark = Color(0xFFFF9F0A)
    val DangerDark = Color(0xFFFF453A)
    val InfoDark = Color(0xFF0A84FF)
    val SuccessLight = Color(0xFF34C759)
    val WarningLight = Color(0xFFFF9500)
    val DangerLight = Color(0xFFFF3B30)
    val InfoLight = Color(0xFF007AFF)
}

/** Grade de 4 dp. */
object LiquidSpace {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val s: Dp = 12.dp
    val m: Dp = 16.dp
    val l: Dp = 20.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val xxxl: Dp = 40.dp
    val huge: Dp = 48.dp

    /** Margem lateral das telas. */
    val gutter: Dp = 16.dp

    /** Menor área de toque aceitável. */
    val touch: Dp = 44.dp
}

/**
 * Cantos concêntricos: o canto de um elemento interno é o canto de fora menos a distância entre
 * as bordas. Tela 36 → dock 32 → widget 26 → cartão 22 → controle 18 → célula 14 → chip 10.
 */
object LiquidRadius {
    val screen: Dp = 36.dp
    val dock: Dp = 32.dp
    val sheet: Dp = 32.dp
    val widget: Dp = 26.dp
    val card: Dp = 22.dp
    val control: Dp = 18.dp
    val cell: Dp = 14.dp
    val chip: Dp = 10.dp

    /** Ícones de app: 23% do lado. */
    const val ICON_FRACTION: Float = 0.23f

    fun icon(size: Dp): Dp = size * ICON_FRACTION

    fun concentric(outer: Dp, inset: Dp): Dp = (outer - inset).coerceAtLeast(0.dp)
}

/**
 * Espaçamento entre letras da Inter para um tamanho, pela fórmula de métricas dinâmicas publicada
 * pela própria Inter: mais apertado nos tamanhos grandes e quase neutro nos pequenos, como a SF.
 * O resultado é em em.
 */
fun interTracking(sizeSp: Float): Float = (-0.0223 + 0.185 * exp(-0.1745 * sizeSp)).toFloat()

/** Escala tipográfica (tamanhos do iPhone, com a Inter). */
@Immutable
data class LiquidTypography(
    val display: TextStyle,
    val title1: TextStyle,
    val title2: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val subhead: TextStyle,
    val footnote: TextStyle,
    val caption: TextStyle,
) {
    /** Os estilos com os nomes usados no Design System, do maior para o menor. */
    val named: List<Pair<String, TextStyle>>
        get() = listOf(
            "Display" to display,
            "Título 1" to title1,
            "Título 2" to title2,
            "Destaque" to headline,
            "Corpo" to body,
            "Subtítulo" to subhead,
            "Nota" to footnote,
            "Legenda" to caption,
        )
}

private fun liquidStyle(size: Float, weight: FontWeight, lineHeight: Float) = TextStyle(
    fontFamily = UiFont,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = interTracking(size).em,
)

val LiquidType = LiquidTypography(
    display = liquidStyle(34f, FontWeight.Bold, 41f),
    title1 = liquidStyle(28f, FontWeight.Bold, 34f),
    title2 = liquidStyle(22f, FontWeight.SemiBold, 28f),
    headline = liquidStyle(17f, FontWeight.SemiBold, 22f),
    body = liquidStyle(17f, FontWeight.Normal, 22f),
    subhead = liquidStyle(15f, FontWeight.Normal, 20f),
    footnote = liquidStyle(13f, FontWeight.Normal, 18f),
    caption = liquidStyle(11f, FontWeight.Medium, 13f),
)

/** Números que não "dançam" quando mudam (horas, contagens, porcentagens). */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

/**
 * Mola descrita como no iPhone: amortecimento (1 = sem quicar) e tempo de resposta em segundos.
 * A rigidez equivalente, para massa 1, é (2π / resposta)².
 */
@Immutable
data class LiquidSpring(val damping: Float, val response: Float) {
    val stiffness: Float
        get() {
            val w = 2.0 * PI / response
            return (w * w).toFloat()
        }

    fun <T> spec(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = damping, stiffness = stiffness, visibilityThreshold = visibilityThreshold)
}

/** Molas e tempos do sistema. Tudo que se move no Liquid Flip usa uma destas. */
object LiquidMotion {
    /** Toques, botões e interruptores. */
    val Snappy = LiquidSpring(damping = 0.86f, response = 0.30f)

    /** Painéis, folhas e a central. */
    val Smooth = LiquidSpring(damping = 0.84f, response = 0.45f)

    /** Ilha e widgets: um pouco de elasticidade. */
    val Bouncy = LiquidSpring(damping = 0.70f, response = 0.50f)

    /** Papel de parede e mudanças lentas. */
    val Gentle = LiquidSpring(damping = 0.92f, response = 0.62f)

    const val FADE_FAST_MS = 120
    const val FADE_MS = 220

    val all: List<Pair<String, LiquidSpring>> = listOf("Rápida" to Snappy, "Suave" to Smooth, "Elástica" to Bouncy, "Calma" to Gentle)
}

/** Vibração pela intenção: leve ao tocar, média ao ligar algo, forte só em ações destrutivas. */
enum class HapticRole { TAP, TOGGLE, DESTRUCTIVE }
