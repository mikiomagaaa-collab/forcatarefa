package app.cristal.cover.ui.design

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cristal.cover.data.prefs.GlassQuality
import app.cristal.cover.ui.glass.GlassSpec
import app.cristal.cover.ui.glass.GlassTouchState
import app.cristal.cover.ui.glass.LocalGlassSpec
import app.cristal.cover.ui.glass.glassShadow
import app.cristal.cover.ui.glass.glassTouch
import app.cristal.cover.ui.glass.liquidGlass
import app.cristal.cover.ui.glass.rememberGlassTouch
import app.cristal.cover.ui.glass.rememberHaptic

/**
 * Os seis materiais do Liquid Flip. Cada superfície usa o material da sua função, em vez de
 * vidro em tudo.
 */
enum class LiquidMaterial(val label: String, val usage: String) {
    LIQUID("Liquid Glass", "Botões soltos, dock e controles"),
    CLEAR("Clear Glass", "Sobre fotos e ícones tingidos"),
    FROSTED("Frosted Glass", "Widgets, notificações e folhas"),
    DARK("Dark Glass", "Central, menus e ilha em vidro"),
    COLORED("Colored Glass", "Seleção e widgets tingidos"),
    SOLID("Sólido", "Configurações, galeria e listas"),
}

/** Ajustes que a pessoa pode fazer em cada material, de 0 a 1. */
@Immutable
data class MaterialTuning(
    /** Vidro líquido: transparente ↔ fosco (é o ajuste de vidro que já existia). */
    val liquid: Float = 0.45f,
    /** Vidro fosco: força da película. */
    val frosted: Float = 0.5f,
    /** Vidro escuro: opacidade. */
    val dark: Float = 0.55f,
    /** Vidro colorido: força da cor de destaque. */
    val colored: Float = 0.35f,
)

/**
 * Um material resolvido para o tema e os ajustes atuais.
 *
 * @param glass especificação do vidro, ou `null` para superfícies sólidas.
 * @param film película pintada sobre o fundo desfocado ([Color.Unspecified] = a padrão do vidro).
 * @param solid cor da superfície sólida.
 * @param content cor do texto e dos ícones por cima.
 */
@Immutable
data class MaterialSpec(
    val material: LiquidMaterial,
    val glass: GlassSpec?,
    val film: Color,
    val solid: Color,
    val content: Color,
    val elevation: Dp,
)

object LiquidMaterials {
    /**
     * Converte um material em parâmetros de desenho. Só o Liquid e o Clear usam a refração do
     * shader; os outros usam o desfoque simples, que custa menos e lê melhor como painel.
     */
    fun resolve(material: LiquidMaterial, base: GlassSpec, colors: LiquidColors, tuning: MaterialTuning): MaterialSpec {
        val panelQuality = if (base.quality == GlassQuality.LIQUID) GlassQuality.BLUR else base.quality
        return when (material) {
            LiquidMaterial.LIQUID -> MaterialSpec(
                material, base.copy(intensity = tuning.liquid.coerceIn(0f, 1f)), Color.Unspecified, Color.Unspecified, base.contentColor, 10.dp,
            )
            LiquidMaterial.CLEAR -> MaterialSpec(
                material,
                base.copy(intensity = (tuning.liquid * 0.2f).coerceIn(0f, 0.2f)),
                if (base.dark) Color(0f, 0f, 0f, 0.03f) else Color(1f, 1f, 1f, 0.08f),
                Color.Unspecified,
                base.contentColor,
                4.dp,
            )
            LiquidMaterial.FROSTED -> MaterialSpec(
                material,
                base.copy(quality = panelQuality, intensity = 0.9f),
                frostFilm(base.dark, tuning.frosted.coerceIn(0f, 1f)),
                Color.Unspecified,
                base.contentColor,
                8.dp,
            )
            LiquidMaterial.DARK -> MaterialSpec(
                material,
                base.copy(quality = panelQuality, intensity = 0.85f, dark = true),
                colors.darkGlassFilm.copy(alpha = (0.3f + 0.6f * tuning.dark.coerceIn(0f, 1f)).coerceAtMost(0.95f)),
                Color.Unspecified,
                Color.White,
                6.dp,
            )
            LiquidMaterial.COLORED -> MaterialSpec(
                material,
                base.copy(intensity = 0.6f),
                colors.accent.copy(alpha = 0.16f + 0.42f * tuning.colored.coerceIn(0f, 1f)),
                Color.Unspecified,
                base.contentColor,
                8.dp,
            )
            LiquidMaterial.SOLID -> MaterialSpec(material, null, Color.Unspecified, colors.surface, colors.content, 0.dp)
        }
    }

    private fun frostFilm(dark: Boolean, amount: Float): Color =
        if (dark) Color(0.12f, 0.10f, 0.17f, 0.22f + 0.5f * amount) else Color(1f, 1f, 1f, 0.28f + 0.5f * amount)
}

@Composable
fun rememberMaterialSpec(material: LiquidMaterial): MaterialSpec {
    val base = LocalGlassSpec.current
    val theme = LocalLiquidTheme.current
    return remember(material, base, theme.colors, theme.tuning) { LiquidMaterials.resolve(material, base, theme.colors, theme.tuning) }
}

/** Pinta o elemento com um material. Vidros amostram o fundo de [app.cristal.cover.ui.glass.LocalGlassBackdrop]. */
@Composable
fun Modifier.liquidMaterial(material: LiquidMaterial, cornerRadius: Dp, touch: GlassTouchState? = null): Modifier {
    val spec = rememberMaterialSpec(material)
    val glass = spec.glass
    if (glass == null) {
        val shape = RoundedCornerShape(cornerRadius)
        return this.clip(shape).background(spec.solid).border(Dp.Hairline, Liquid.colors.separator, shape)
    }
    return this.liquidGlass(cornerRadius = cornerRadius, tint = spec.film, touch = touch, spec = glass)
}

/**
 * Superfície pronta em qualquer material: sombra (nos vidros), reação ao toque, clique com
 * vibração e cor de conteúdo correta.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiquidSurface(
    material: LiquidMaterial,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = LiquidRadius.card,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    val spec = rememberMaterialSpec(material)
    val haptic = rememberHaptic()
    val interactive = onClick != null || onLongClick != null
    val touch = rememberGlassTouch()
    val isGlass = spec.glass != null
    var m = modifier
    if (isGlass) m = m.glassTouch(touch, pressedScale = if (interactive) 0.965f else 0.99f)
    if (isGlass && spec.elevation > 0.dp) m = m.glassShadow(cornerRadius, spec.elevation)
    m = m.liquidMaterial(material, cornerRadius, if (isGlass) touch else null)
    if (interactive) {
        m = m.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Button,
            onLongClick = onLongClick?.let { action -> { haptic(); action() } },
            onClick = {
                haptic()
                onClick?.invoke()
            },
        )
    }
    CompositionLocalProvider(LocalContentColor provides spec.content) {
        Box(modifier = m, contentAlignment = contentAlignment, content = content)
    }
}
