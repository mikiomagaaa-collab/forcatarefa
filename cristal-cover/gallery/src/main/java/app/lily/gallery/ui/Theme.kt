package app.lily.gallery.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.lily.gallery.R

/** As cores da L.I.L.Y Gallery (as mesmas da família Liquid Flip). */
@Immutable
data class GalleryColors(
    val dark: Boolean,
    val background: Color,
    val glass: Color,
    val glassStrong: Color,
    val border: Color,
    val content: Color,
    val secondary: Color,
    val tertiary: Color,
    val accent: Color,
    val accent2: Color,
    val onAccent: Color,
    val danger: Color,
    val success: Color,
)

private val Dark = GalleryColors(
    dark = true,
    background = Color(0xFF09080F),
    glass = Color(0x1AFFFFFF),
    glassStrong = Color(0x2BFFFFFF),
    border = Color(0x24FFFFFF),
    content = Color(0xFFF4F2FA),
    secondary = Color(0xFFB4AFC6),
    tertiary = Color(0xFF7D7891),
    accent = Color(0xFFB79CFF),
    accent2 = Color(0xFF64D2FF),
    onAccent = Color(0xFF160F2E),
    danger = Color(0xFFFF6B7A),
    success = Color(0xFF34D399),
)

private val Light = GalleryColors(
    dark = false,
    background = Color(0xFFF6F4FB),
    glass = Color(0xB8FFFFFF),
    glassStrong = Color(0xE6FFFFFF),
    border = Color(0x1A170F2E),
    content = Color(0xFF17141F),
    secondary = Color(0xFF5A5468),
    tertiary = Color(0xFF8C869A),
    accent = Color(0xFF6C4BD6),
    accent2 = Color(0xFF1C8BD6),
    onAccent = Color.White,
    danger = Color(0xFFD63A4C),
    success = Color(0xFF0F9F6E),
)

val LocalGallery = staticCompositionLocalOf { Dark }

object G {
    val colors: GalleryColors
        @Composable @ReadOnlyComposable
        get() = LocalGallery.current
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private fun style(size: Float, weight: FontWeight, line: Float) = TextStyle(
    fontFamily = Inter,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = (-0.0223 + 0.185 * kotlin.math.exp(-0.1745 * size)).em,
)

object GType {
    val display = style(30f, FontWeight.SemiBold, 36f)
    val title = style(21f, FontWeight.SemiBold, 27f)
    val headline = style(17f, FontWeight.SemiBold, 22f)
    val body = style(16f, FontWeight.Normal, 23f)
    val callout = style(15f, FontWeight.Normal, 21f)
    val subhead = style(14f, FontWeight.Normal, 19f)
    val footnote = style(13f, FontWeight.Normal, 18f)
    val caption = style(12f, FontWeight.Medium, 15f)
}

@Composable
fun GalleryTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) Dark else Light
    val scheme = if (dark) {
        darkColorScheme(primary = colors.accent, onPrimary = colors.onAccent, background = colors.background, surface = colors.background, onSurface = colors.content)
    } else {
        lightColorScheme(primary = colors.accent, onPrimary = colors.onAccent, background = colors.background, surface = colors.background, onSurface = colors.content)
    }
    CompositionLocalProvider(LocalGallery provides colors) { MaterialTheme(colorScheme = scheme, content = content) }
}

fun Modifier.glass(c: GalleryColors, shape: Shape = RoundedCornerShape(22.dp), strong: Boolean = false): Modifier = this
    .clip(shape)
    .background(if (strong) c.glassStrong else c.glass)
    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (c.dark) 0.06f else 0.35f), Color.Transparent)))
    .border(1.dp, c.border, shape)

@Composable
fun Modifier.press(onClick: () -> Unit, enabled: Boolean = true, role: Role = Role.Button): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.86f, stiffness = 900f), label = "toque")
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = role, onClick = onClick)
}

@Composable
fun GIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 42.dp, tint: Color? = null, filled: Boolean = true, enabled: Boolean = true) {
    val c = G.colors
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .then(if (filled) Modifier.background(c.glass).border(1.dp, c.border, CircleShape) else Modifier)
            .press(onClick, enabled)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint ?: c.content, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun GPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, filled: Boolean = true, enabled: Boolean = true, height: Dp = 44.dp) {
    val c = G.colors
    Row(
        modifier
            .height(height)
            .clip(CircleShape)
            .background(if (filled) c.accent else c.glassStrong)
            .then(if (!filled) Modifier.border(1.dp, c.border, CircleShape) else Modifier)
            .press(onClick, enabled)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val fg = if (filled) c.onAccent else c.content
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = GType.headline.copy(fontSize = 15.sp), color = fg, maxLines = 1)
    }
}

@Composable
fun GChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val c = G.colors
    Row(
        modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(if (selected) c.accent else c.glass)
            .border(1.dp, if (selected) Color.Transparent else c.border, CircleShape)
            .press(onClick, role = Role.RadioButton)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (selected) c.onAccent else c.content, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = GType.footnote.copy(fontWeight = FontWeight.SemiBold), color = if (selected) c.onAccent else c.content, maxLines = 1)
    }
}

@Composable
fun GTopBar(title: String, onBack: (() -> Unit)?, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    val c = G.colors
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) GIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar", onBack)
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(title, style = GType.headline, color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = GType.caption, color = c.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@Composable
fun GConfirm(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, destructive: Boolean = false) {
    val c = G.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (c.dark) Color(0xFF1B1826) else Color.White,
        title = { Text(title, style = GType.headline, color = c.content) },
        text = { Text(text, style = GType.callout, color = c.secondary) },
        confirmButton = {
            Text(confirm, style = GType.callout.copy(fontWeight = FontWeight.SemiBold), color = if (destructive) c.danger else c.accent, modifier = Modifier.clip(CircleShape).clickable { onConfirm() }.padding(horizontal = 14.dp, vertical = 8.dp))
        },
        dismissButton = {
            Text("Cancelar", style = GType.callout, color = c.secondary, modifier = Modifier.clip(CircleShape).clickable { onDismiss() }.padding(horizontal = 14.dp, vertical = 8.dp))
        },
    )
}

fun sheetColor(c: GalleryColors): Color = if (c.dark) Color(0xFF16141E) else Color(0xFFFBFAFE)
