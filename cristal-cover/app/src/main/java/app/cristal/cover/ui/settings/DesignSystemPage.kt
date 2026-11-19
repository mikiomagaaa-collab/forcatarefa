package app.cristal.cover.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lens
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.cristal.cover.data.prefs.LiquidThemeId
import app.cristal.cover.data.prefs.PrefKeys
import app.cristal.cover.data.prefs.Settings
import app.cristal.cover.ui.components.WallpaperTextShadow
import app.cristal.cover.ui.design.Liquid
import app.cristal.cover.ui.design.LiquidButton
import app.cristal.cover.ui.design.LiquidButtonStyle
import app.cristal.cover.ui.design.LiquidChip
import app.cristal.cover.ui.design.LiquidListGroup
import app.cristal.cover.ui.design.LiquidListRow
import app.cristal.cover.ui.design.LiquidMaterial
import app.cristal.cover.ui.design.LiquidMotion
import app.cristal.cover.ui.design.LiquidRadius
import app.cristal.cover.ui.design.LiquidSectionHeader
import app.cristal.cover.ui.design.LiquidSegmented
import app.cristal.cover.ui.design.LiquidSlider
import app.cristal.cover.ui.design.LiquidSurface
import app.cristal.cover.ui.design.LiquidThemes
import app.cristal.cover.ui.design.LiquidToggle
import app.cristal.cover.ui.design.LiquidType
import app.cristal.cover.ui.design.VSpace
import app.cristal.cover.ui.glass.LocalGlassSpec
import app.cristal.cover.ui.glass.rememberHaptic

/** O que a prévia do Design System pode mudar nas configurações. */
internal class DesignSystemActions(
    val setTheme: (LiquidThemeId) -> Unit,
    val setCustomBase: (LiquidThemeId) -> Unit,
    val setCustomAccent: (Int) -> Unit,
    val setLiquid: (Float) -> Unit,
    val setFrosted: (Float) -> Unit,
    val setDark: (Float) -> Unit,
    val setColored: (Float) -> Unit,
)

@Composable
internal fun DesignSystemPage(vm: SettingsViewModel, settings: Settings, wallpaper: ImageBitmap?, onBack: () -> Unit) {
    DesignSystemShowcase(
        settings = settings,
        wallpaper = wallpaper,
        onBack = onBack,
        actions = DesignSystemActions(
            setTheme = { vm.setEnum(PrefKeys.LIQUID_THEME, it) },
            setCustomBase = { vm.setEnum(PrefKeys.LIQUID_CUSTOM_BASE, it) },
            setCustomAccent = { vm.set(PrefKeys.LIQUID_CUSTOM_ACCENT, it) },
            setLiquid = { vm.set(PrefKeys.GLASS_INTENSITY, it) },
            setFrosted = { vm.set(PrefKeys.MATERIAL_FROSTED, it) },
            setDark = { vm.set(PrefKeys.MATERIAL_DARK, it) },
            setColored = { vm.set(PrefKeys.MATERIAL_COLORED, it) },
        ),
    )
}

/**
 * Prévia do Design System: tema, materiais sobre o papel de parede, superfícies sólidas,
 * tipografia e molas. As telas adotam o sistema fase a fase; esta página mostra o resultado antes.
 */
@Composable
internal fun DesignSystemShowcase(settings: Settings, wallpaper: ImageBitmap?, onBack: () -> Unit, actions: DesignSystemActions) {
    SettingsScaffold("Design System", settings, wallpaper, onBack) {
        item {
            SettingsCard("Tema") {
                ThemeStrip(settings.liquidTheme, actions.setTheme)
                if (settings.liquidTheme == LiquidThemeId.CUSTOM) {
                    ChoiceRow(
                        "Partir de",
                        LiquidThemeId.entries.filter { it != LiquidThemeId.CUSTOM }.map { it to it.label },
                        settings.liquidCustomBase,
                        actions.setCustomBase,
                    )
                    RowTitle("Cor de destaque")
                    AccentSwatches(settings.liquidCustomAccent, actions.setCustomAccent)
                }
                InfoText("As telas passam a usar o tema fase a fase. Esta página já mostra o resultado.")
            }
        }
        item { MaterialGrid() }
        item {
            SettingsCard("Ajuste dos materiais") {
                SliderRow("Vidro líquido", settings.glassIntensity, 0f..1f, label = ::transparencyLabel) { actions.setLiquid(it) }
                SliderRow("Vidro fosco", settings.materialFrosted, 0f..1f, label = ::strengthLabel) { actions.setFrosted(it) }
                SliderRow("Vidro escuro", settings.materialDark, 0f..1f, label = ::strengthLabel) { actions.setDark(it) }
                SliderRow("Vidro colorido", settings.materialColored, 0f..1f, label = ::strengthLabel) { actions.setColored(it) }
            }
        }
        item { SolidSample() }
        item { TypeSample() }
        item { MotionSample() }
    }
}

private fun transparencyLabel(value: Float) = when {
    value < 0.3f -> "transparente"
    value < 0.65f -> "padrão"
    else -> "fosco"
}

private fun strengthLabel(value: Float) = "${(value * 100).toInt()}%"

@Composable
private fun ThemeStrip(selected: LiquidThemeId, onSelect: (LiquidThemeId) -> Unit) {
    val haptic = rememberHaptic()
    val ink = LocalGlassSpec.current.contentColor
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LiquidThemeId.entries.forEach { id ->
            val colors = LiquidThemes.base(id)
            val isSelected = id == selected
            val fill = if (id == LiquidThemeId.CUSTOM) {
                Brush.sweepGradient(listOf(Color(0xFF6544E8), Color(0xFFE067C9), Color(0xFF2F6BFF), Color(0xFF30D158), Color(0xFF6544E8)))
            } else {
                Brush.linearGradient(colors.preview)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(70.dp)
                    .selectable(
                        selected = isSelected,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.RadioButton,
                    ) {
                        haptic()
                        onSelect(id)
                    },
            ) {
                Box(
                    Modifier
                        .size(width = 62.dp, height = 86.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(fill)
                        .border(if (isSelected) 2.5.dp else 1.dp, if (isSelected) ink else ink.copy(alpha = 0.22f), RoundedCornerShape(14.dp)),
                ) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(6.dp)
                            .fillMaxWidth()
                            .height(22.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surface),
                    ) {
                        Box(Modifier.align(Alignment.CenterEnd).padding(end = 5.dp).size(10.dp).clip(CircleShape).background(colors.accent))
                    }
                }
                Text(
                    id.label,
                    style = LiquidType.caption,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun AccentSwatches(selected: Int, onSelect: (Int) -> Unit) {
    val haptic = rememberHaptic()
    val ink = LocalGlassSpec.current.contentColor
    val options = listOf(0) + LiquidThemes.customAccents.map { it.toArgbInt() }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        options.forEach { argb ->
            val isSelected = argb == selected
            val swatch = if (argb == 0) Brush.linearGradient(listOf(Color(0xFFB9A2FF), Color(0xFF6544E8))) else Brush.linearGradient(listOf(Color(argb), Color(argb)))
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(swatch)
                    .border(if (isSelected) 3.dp else 0.dp, ink, CircleShape)
                    .selectable(
                        selected = isSelected,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.RadioButton,
                    ) {
                        haptic()
                        onSelect(argb)
                    },
            )
        }
    }
}

private fun Color.toArgbInt(): Int {
    val a = (alpha * 255f + 0.5f).toInt()
    val r = (red * 255f + 0.5f).toInt()
    val g = (green * 255f + 0.5f).toInt()
    val b = (blue * 255f + 0.5f).toInt()
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

/** Os seis materiais lado a lado, direto sobre o papel de parede. */
@Composable
private fun MaterialGrid() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Materiais",
            color = Color.White,
            style = LiquidType.headline.copy(shadow = WallpaperTextShadow),
            modifier = Modifier.padding(start = 6.dp, top = 4.dp),
        )
        LiquidMaterial.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { material ->
                    LiquidSurface(material, Modifier.weight(1f).aspectRatio(0.86f), cornerRadius = LiquidRadius.widget) {
                        val ink = LocalContentColor.current
                        Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                            Text(material.label, style = LiquidType.footnote.copy(fontWeight = FontWeight.SemiBold), color = ink, maxLines = 1)
                            Text(material.usage, style = LiquidType.caption, color = ink.copy(alpha = 0.75f), maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SampleBlock(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    val colors = Liquid.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LiquidRadius.card))
            .background(colors.background)
            .padding(14.dp),
    ) {
        Text(title, style = LiquidType.title2, color = colors.content, modifier = Modifier.padding(start = 2.dp))
        if (subtitle != null) Text(subtitle, style = LiquidType.footnote, color = colors.contentSecondary, modifier = Modifier.padding(start = 2.dp, top = 2.dp))
        content()
    }
}

/** Uma lista de configurações como ela vai ficar: superfície sólida, grupos e controles. */
@Composable
private fun SolidSample() {
    var island by remember { mutableStateOf(true) }
    var haptics by remember { mutableStateOf(false) }
    var mode by remember { mutableIntStateOf(1) }
    var brightness by remember { mutableFloatStateOf(0.62f) }
    var filter by remember { mutableIntStateOf(0) }
    SampleBlock("Superfícies sólidas", "Configurações, galeria e listas usam o material sólido do tema.") {
        LiquidSectionHeader("Exemplo")
        LiquidListGroup {
            LiquidListRow("Aparência", icon = Icons.Rounded.Palette, iconBackground = Color(0xFF7C5CFF), value = Liquid.theme.id.label, divider = false, chevron = true, onClick = {})
            LiquidListRow("Liquid Island", icon = Icons.Rounded.Lens, iconBackground = Color(0xFF111111)) { LiquidToggle(island, { island = it }) }
            LiquidListRow("Vibração", icon = Icons.Rounded.Vibration, iconBackground = Color(0xFFFF9F0A)) { LiquidToggle(haptics, { haptics = it }) }
        }
        VSpace(14.dp)
        LiquidSegmented(listOf("Dia", "Automático", "Noite"), mode, { mode = it })
        VSpace(14.dp)
        LiquidSlider(brightness, { brightness = it }, icon = Icons.Rounded.LightMode, label = "Brilho")
        VSpace(14.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Todos", "Fotos", "Vídeos").forEachIndexed { index, label -> LiquidChip(label, filter == index, { filter = index }) }
        }
        VSpace(14.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LiquidButton("Aplicar", {}, Modifier.weight(1f))
            LiquidButton("Cancelar", {}, Modifier.weight(1f), style = LiquidButtonStyle.TINTED)
        }
    }
}

@Composable
private fun TypeSample() {
    val colors = Liquid.colors
    SampleBlock("Tipografia", "Inter, com o espaçamento ajustado a cada tamanho.") {
        VSpace(8.dp)
        LiquidType.named.forEach { (name, style) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                Text(name, style = LiquidType.caption, color = colors.contentSecondary, modifier = Modifier.width(78.dp))
                Text("Liquid Flip 09:41", style = style, color = colors.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun MotionSample() {
    val colors = Liquid.colors
    val haptic = rememberHaptic()
    SampleBlock("Movimento", "Toque em uma linha para ver a mola.") {
        VSpace(6.dp)
        LiquidMotion.all.forEach { (name, spring) ->
            key(name) {
                var moved by remember { mutableStateOf(false) }
                val fraction by animateFloatAsState(if (moved) 1f else 0f, spring.spec(), label = name)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                            haptic()
                            moved = !moved
                        },
                ) {
                    Text(name, style = LiquidType.body, color = colors.content, modifier = Modifier.width(84.dp))
                    BoxWithConstraints(
                        Modifier
                            .weight(1f)
                            .height(30.dp)
                            .clip(CircleShape)
                            .background(if (colors.dark) Color(0x24FFFFFF) else Color(0x1F767680)),
                    ) {
                        val travel = maxWidth - 30.dp
                        Box(
                            Modifier
                                .padding(4.dp)
                                .offset { IntOffset((travel * fraction).roundToPx(), 0) }
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(colors.accent),
                        )
                    }
                }
            }
        }
    }
}
