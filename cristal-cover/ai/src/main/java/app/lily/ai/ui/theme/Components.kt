package app.lily.ai.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Encolhe um pouco ao toque, como os controles do Liquid Flip. */
@Composable
fun Modifier.press(onClick: () -> Unit, enabled: Boolean = true, role: Role = Role.Button): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.86f, stiffness = 900f), label = "toque")
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = role, onClick = onClick)
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    val c = Lily.colors
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) IconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar", onBack)
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(title, style = Type.headline, color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = Type.caption, color = c.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

@Composable
fun IconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 40.dp, tint: Color? = null, filled: Boolean = true, enabled: Boolean = true) {
    val c = Lily.colors
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

enum class ButtonKind { FILLED, TINTED, GLASS }

@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, kind: ButtonKind = ButtonKind.FILLED, enabled: Boolean = true, height: Dp = 46.dp) {
    val c = Lily.colors
    val (bg, fg) = when (kind) {
        ButtonKind.FILLED -> c.accent to c.onAccent
        ButtonKind.TINTED -> c.accent.copy(alpha = 0.16f) to c.accent
        ButtonKind.GLASS -> c.glassStrong to c.content
    }
    Row(
        modifier
            .height(height)
            .clip(CircleShape)
            .background(bg)
            .then(if (kind == ButtonKind.GLASS) Modifier.border(1.dp, c.border, CircleShape) else Modifier)
            .press(onClick, enabled)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = Type.headline.copy(fontWeight = FontWeight.SemiBold), color = fg, maxLines = 1)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    val c = Lily.colors
    Row(
        modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(if (selected) c.accent else c.glass)
            .border(1.dp, if (selected) Color.Transparent else c.border, CircleShape)
            .press(onClick, enabled, Role.RadioButton)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (selected) c.onAccent else c.content, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = Type.footnote.copy(fontWeight = FontWeight.SemiBold), color = if (selected) c.onAccent else c.content, maxLines = 1)
    }
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, strong: Boolean = false, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    val c = Lily.colors
    Column(
        modifier
            .glass(c, RoundedCornerShape(24.dp), strong)
            .then(if (onClick != null) Modifier.press(onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    val c = Lily.colors
    Row(modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = Type.headline, color = c.content, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(action, style = Type.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.accent, modifier = Modifier.clip(CircleShape).clickable(onClick = onAction).padding(horizontal = 8.dp, vertical = 4.dp))
        }
    }
}

/** "● Gemma 4 E2B · No aparelho": qual modelo e onde roda. */
@Composable
fun StatusPill(text: String, dot: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = Lily.colors
    Row(
        modifier
            .clip(CircleShape)
            .background(c.glass)
            .border(1.dp, c.border, CircleShape)
            .then(if (onClick != null) Modifier.press(onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(7.dp))
        Text(text, style = Type.caption, color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Progress(fraction: Float, modifier: Modifier = Modifier, color: Color? = null, indeterminate: Boolean = false) {
    val c = Lily.colors
    val accent = color ?: c.accent
    Canvas(modifier.fillMaxWidth().height(6.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(c.content.copy(alpha = 0.12f), cornerRadius = r)
        if (!indeterminate) {
            drawRoundRect(
                Brush.horizontalGradient(listOf(accent, c.accent2)),
                size = Size(size.width * fraction.coerceIn(0f, 1f), size.height),
                cornerRadius = r,
            )
        }
    }
}

@Composable
fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val c = Lily.colors
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = c.accent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = c.content.copy(alpha = 0.18f),
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

@Composable
fun SettingRow(title: String, subtitle: String? = null, icon: ImageVector? = null, onClick: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null) {
    val c = Lily.colors
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(c.accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.callout.copy(fontWeight = FontWeight.Medium), color = c.content)
            if (subtitle != null) Text(subtitle, style = Type.footnote, color = c.secondary)
        }
        trailing?.invoke()
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, destructive: Boolean = false) {
    val c = Lily.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (c.dark) Color(0xFF1B1826) else Color.White,
        title = { Text(title, style = Type.headline, color = c.content) },
        text = { Text(text, style = Type.callout, color = c.secondary) },
        confirmButton = {
            Text(
                confirm,
                style = Type.callout.copy(fontWeight = FontWeight.SemiBold),
                color = if (destructive) c.danger else c.accent,
                modifier = Modifier.clip(CircleShape).clickable { onConfirm() }.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        },
        dismissButton = {
            Text("Cancelar", style = Type.callout, color = c.secondary, modifier = Modifier.clip(CircleShape).clickable { onDismiss() }.padding(horizontal = 14.dp, vertical = 8.dp))
        },
    )
}

/** Tela vazia com um convite claro. */
@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    val c = Lily.colors
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(title, style = Type.headline, color = c.content)
        Spacer(Modifier.height(4.dp))
        Text(text, style = Type.footnote, color = c.secondary, modifier = Modifier.padding(horizontal = 12.dp))
        if (action != null && onAction != null) {
            Spacer(Modifier.height(14.dp))
            PillButton(action, onAction, kind = ButtonKind.TINTED, height = 40.dp)
        }
    }
}
