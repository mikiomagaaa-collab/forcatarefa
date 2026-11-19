package app.cristal.cover.ui.glass

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Superfície de vidro pronta para uso: sombra, vidro líquido, reação ao toque e clique.
 *
 * @param innerModifier ver o parâmetro; usado para detectar arrasto em cards clicáveis.
 * @param clipContent recorta o conteúdo no formato do vidro (use quando houver imagens ou listas
 * que possam vazar pelos cantos; custa uma camada extra).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    tint: Color = Color.Unspecified,
    elevation: Dp = 10.dp,
    clipContent: Boolean = false,
    reactToTouch: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    /** Modificador aplicado por último (mais "por dentro"): recebe os toques antes do clique. */
    innerModifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    val spec = LocalGlassSpec.current
    val haptic = rememberHaptic()
    val interactive = onClick != null || onLongClick != null
    val touch = if (reactToTouch) rememberGlassTouch() else null

    var m = modifier
    if (touch != null) m = m.glassTouch(touch, pressedScale = if (interactive) 0.965f else 0.99f)
    m = m.glassShadow(cornerRadius, elevation).liquidGlass(cornerRadius = cornerRadius, tint = tint, touch = touch)
    if (clipContent) m = m.clip(RoundedCornerShape(cornerRadius))
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
    m = m.then(innerModifier)
    CompositionLocalProvider(LocalContentColor provides spec.contentColor) {
        Box(modifier = m, contentAlignment = contentAlignment, content = content)
    }
}

/** Botão redondo de vidro com um ícone. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    active: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val spec = LocalGlassSpec.current
    // Ativo: película clara e ícone escuro, como um botão "aceso".
    val tint = if (active) Color.White.copy(alpha = 0.82f) else Color.Unspecified
    GlassSurface(
        modifier = modifier.size(size),
        cornerRadius = size / 2,
        tint = tint,
        elevation = 6.dp,
        onLongClick = onLongClick,
        onClick = onClick,
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) Color(0xFF14161A) else spec.contentColor,
            modifier = Modifier.size(size * 0.46f),
        )
    }
}
