package app.cristal.cover.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cristal.cover.data.prefs.Settings
import app.cristal.cover.data.prefs.WallpaperKind
import app.cristal.cover.ui.components.WallpaperLayer
import app.cristal.cover.ui.components.WallpaperTextShadow
import app.cristal.cover.ui.glass.GlassIconButton
import app.cristal.cover.ui.glass.GlassSurface
import app.cristal.cover.ui.glass.LocalGlassBackdrop
import app.cristal.cover.ui.glass.LocalGlassSpec
import app.cristal.cover.ui.glass.glassBackdropSource
import app.cristal.cover.ui.glass.rememberGlassBackdrop
import app.cristal.cover.ui.glass.rememberHaptic

/** Moldura das telas de configuração: papel de parede ao fundo, título e lista de cards de vidro. */
@Composable
fun SettingsScaffold(
    title: String,
    settings: Settings,
    wallpaper: ImageBitmap?,
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    val backdrop = rememberGlassBackdrop()
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        WallpaperLayer(
            image = if (settings.wallpaperKind == WallpaperKind.IMAGE) wallpaper else null,
            presetIndex = settings.wallpaperPreset,
            dim = (settings.wallpaperDim + 0.18f).coerceAtMost(0.8f),
            modifier = Modifier.glassBackdropSource(backdrop),
        )
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
            Column(Modifier.fillMaxSize().padding(insets).imePadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar", size = 40.dp, onClick = onBack)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        title,
                        color = Color.White,
                        style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, shadow = WallpaperTextShadow),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content,
                )
            }
        }
    }
}

/** Card de vidro que agrupa linhas de configuração. */
@Composable
fun SettingsCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp, elevation = 8.dp, reactToTouch = false) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (title != null) {
                Text(
                    title.uppercase(),
                    fontSize = 11.sp,
                    letterSpacing = 1.1.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalGlassSpec.current.secondaryContentColor,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            content()
        }
    }
}

@Composable
fun NavRow(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    val haptic = rememberHaptic()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                haptic()
                onClick()
            }
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, fontSize = 12.sp, lineHeight = 16.sp, color = LocalGlassSpec.current.secondaryContentColor)
            }
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = LocalGlassSpec.current.secondaryContentColor,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun InfoText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        color = LocalGlassSpec.current.secondaryContentColor,
        modifier = modifier.padding(vertical = 4.dp),
    )
}

@Composable
fun RowTitle(title: String, value: String? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (value != null) Text(value, fontSize = 13.sp, color = LocalGlassSpec.current.secondaryContentColor)
    }
}

/** Controle deslizante que só grava o valor quando o dedo solta (evita gravar em disco a cada pixel). */
@Composable
fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    label: (Float) -> String,
    steps: Int = 0,
    onCommit: (Float) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    RowTitle(title, label(local))
    Slider(
        value = local,
        onValueChange = { local = it },
        onValueChangeFinished = { onCommit(local) },
        valueRange = range,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = LocalContentColor.current,
            activeTrackColor = LocalContentColor.current,
            inactiveTrackColor = LocalContentColor.current.copy(alpha = 0.22f),
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
        ),
    )
}

/** Escolha única em forma de "chips" que quebram de linha. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceRow(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    RowTitle(title)
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for ((value, label) in options) Chip(label, value == selected) { onSelect(value) }
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val haptic = rememberHaptic()
    val content = LocalContentColor.current
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) content.copy(alpha = 0.92f) else content.copy(alpha = 0.12f))
            .border(0.75.dp, content.copy(alpha = if (selected) 0f else 0.22f), CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                haptic()
                onClick()
            }
            .padding(horizontal = 13.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            // Chip selecionado inverte: fundo na cor do texto, texto na cor oposta.
            color = if (selected) (if (LocalGlassSpec.current.dark) Color(0xFF14161A) else Color.White) else content,
            maxLines = 1,
        )
    }
}

/** Linha de permissão com estado (concedida ou pendente) e um botão de ação. */
@Composable
fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    optional: Boolean = false,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Icon(
            if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = if (granted) "Concedida" else "Pendente",
            tint = if (granted) Color(0xFF34C759) else LocalGlassSpec.current.secondaryContentColor,
            modifier = Modifier.padding(top = 2.dp).size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (optional) "$title (opcional)" else title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(description, fontSize = 12.sp, lineHeight = 16.sp, color = LocalGlassSpec.current.secondaryContentColor)
            if (!granted) {
                Box(Modifier.padding(top = 8.dp)) { Chip(actionLabel, selected = true, onClick = onAction) }
            }
        }
    }
}
