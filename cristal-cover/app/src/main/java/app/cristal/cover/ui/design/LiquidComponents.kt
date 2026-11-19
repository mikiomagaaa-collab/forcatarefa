package app.cristal.cover.ui.design

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.cristal.cover.ui.glass.rememberHaptic
import kotlin.math.roundToInt

/** Interruptor no estilo do iPhone: trilho de 51 × 31 dp e bolinha que desliza com mola. */
@Composable
fun LiquidToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = Liquid.colors
    val haptic = rememberHaptic()
    val progress by animateFloatAsState(if (checked) 1f else 0f, LiquidMotion.Snappy.spec(), label = "interruptor")
    val off = if (colors.dark) Color(0x33FFFFFF) else Color(0x29787880)
    Box(
        modifier
            .size(width = 51.dp, height = 31.dp)
            .clip(CircleShape)
            .background(lerp(off, colors.success, progress))
            .toggleable(
                value = checked,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Switch,
            ) {
                haptic()
                onCheckedChange(it)
            }
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .offset { IntOffset((20.dp.toPx() * progress).roundToInt(), 0) }
                .size(27.dp)
                .shadow(3.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

/**
 * Controle deslizante em cápsula, como os da central: a parte preenchida mostra o valor e o
 * ícone fica na ponta. Responde a toque e arrasto e expõe o valor para leitores de tela.
 */
@Composable
fun LiquidSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    icon: ImageVector? = null,
    label: String? = null,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val colors = Liquid.colors
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    val change by rememberUpdatedState(onValueChange)
    val finish by rememberUpdatedState(onValueChangeFinished)
    val track = if (colors.dark) Color(0x2EFFFFFF) else Color(0x1F787880)
    val fill = if (colors.dark) Color(0xFFF7F5EF) else colors.accent
    val iconTint = if (colors.dark) Color(0xFF14161A) else colors.onAccent
    fun valueAt(x: Float, width: Int): Float = valueRange.start + (x / width.coerceAtLeast(1)).coerceIn(0f, 1f) * span
    Box(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(CircleShape)
            .background(track)
            .pointerInput(valueRange) {
                detectTapGestures { offset ->
                    change(valueAt(offset.x, size.width))
                    finish?.invoke()
                }
            }
            .pointerInput(valueRange) {
                detectHorizontalDragGestures(onDragEnd = { finish?.invoke() }) { input, _ ->
                    input.consume()
                    change(valueAt(input.position.x, size.width))
                }
            }
            .semantics {
                if (label != null) contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(valueRange), valueRange)
                setProgress { target ->
                    change(target.coerceIn(valueRange))
                    true
                }
            },
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(fill))
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp).size(18.dp))
        }
    }
}

/** Seletor segmentado: a pílula clara desliza até a opção escolhida. */
@Composable
fun LiquidSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = Liquid.colors
    val haptic = rememberHaptic()
    val trackColor = if (colors.dark) Color(0x24FFFFFF) else Color(0x1F767680)
    val thumbColor = if (colors.dark) Color(0xFF5B5866) else Color.White
    val count = options.size.coerceAtLeast(1)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(trackColor)
            .padding(2.dp),
    ) {
        val segment = maxWidth / count
        val x by animateDpAsState(segment * selected.coerceIn(0, count - 1), LiquidMotion.Snappy.spec(), label = "segmento")
        Box(
            Modifier
                .offset { IntOffset(x.roundToPx(), 0) }
                .width(segment)
                .fillMaxHeight()
                .shadow(2.dp, RoundedCornerShape(7.dp))
                .background(thumbColor, RoundedCornerShape(7.dp)),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { index, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = index == selected,
                            interactionSource = remember(index) { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                        ) {
                            haptic()
                            onSelect(index)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = LiquidType.footnote.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Grupo de linhas sobre superfície sólida, com cantos de célula (como as listas do iPhone). */
@Composable
fun LiquidListGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LiquidRadius.cell))
            .background(Liquid.colors.surface),
        content = content,
    )
}

/**
 * Linha de lista: ícone em placa colorida, título, valor e seta ou um controle à direita.
 *
 * @param divider desenha a linha divisória acima (use `false` na primeira linha do grupo).
 */
@Composable
fun LiquidListRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconBackground: Color = Liquid.colors.accent,
    value: String? = null,
    divider: Boolean = true,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = Liquid.colors
    val haptic = rememberHaptic()
    val dividerStart = if (icon != null) 56.dp else 16.dp
    val click = if (onClick != null) {
        Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
            haptic()
            onClick()
        }
    } else {
        Modifier
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(click)
            .drawBehind {
                if (divider) {
                    val y = 0.5f
                    drawLine(colors.separator, Offset(dividerStart.toPx(), y), Offset(size.width, y), strokeWidth = 1f)
                }
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(iconBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        Text(title, style = LiquidType.body, color = colors.content, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (value != null) Text(value, style = LiquidType.subhead, color = colors.contentSecondary, maxLines = 1)
        trailing?.invoke()
        if (chevron) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.contentTertiary)
    }
}

/** Título de seção acima de um grupo, em caixa alta discreta. */
@Composable
fun LiquidSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = LiquidType.footnote.copy(letterSpacing = 0.04.em),
        color = Liquid.colors.contentSecondary,
        modifier = modifier.padding(start = 16.dp, top = 18.dp, bottom = 6.dp),
    )
}

enum class LiquidButtonStyle {
    /** Preenchido com a cor de destaque: a ação principal da tela. */
    FILLED,

    /** Destaque translúcido: ações secundárias. */
    TINTED,

    /** Vidro líquido: botões soltos sobre fotos e papel de parede. */
    GLASS,
}

/** Botão em cápsula, com encolhida suave ao toque. */
@Composable
fun LiquidButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: LiquidButtonStyle = LiquidButtonStyle.FILLED,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = Liquid.colors
    val label: @Composable (Color) -> Unit = { tint ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 20.dp)) {
            if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Text(text, style = LiquidType.headline, color = tint, maxLines = 1)
        }
    }
    if (style == LiquidButtonStyle.GLASS) {
        LiquidSurface(
            LiquidMaterial.LIQUID,
            modifier.height(48.dp).graphicsLayer { alpha = if (enabled) 1f else 0.4f },
            cornerRadius = 24.dp,
            onClick = if (enabled) onClick else null,
            contentAlignment = Alignment.Center,
        ) { label(androidx.compose.material3.LocalContentColor.current) }
        return
    }
    val haptic = rememberHaptic()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, LiquidMotion.Snappy.spec(), label = "botão")
    val background = if (style == LiquidButtonStyle.FILLED) colors.accent else colors.accent.copy(alpha = 0.16f)
    val content = if (style == LiquidButtonStyle.FILLED) colors.onAccent else colors.accent
    Box(
        modifier
            .height(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.4f
            }
            .clip(CircleShape)
            .background(background)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button) {
                haptic()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) { label(content) }
}

/**
 * Botão redondo com um ícone. Desligado, usa o material pedido; ligado, fica branco com o ícone na
 * cor da função, como os controles da central do iOS 26.
 */
@Composable
fun LiquidCircleButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    material: LiquidMaterial = LiquidMaterial.LIQUID,
    active: Boolean = false,
    activeTint: Color = Liquid.colors.accent,
) {
    if (active) {
        val haptic = rememberHaptic()
        Box(
            modifier
                .size(size)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.94f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                    haptic()
                    onClick()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = contentDescription, tint = activeTint, modifier = Modifier.size(size * 0.44f))
        }
    } else {
        LiquidSurface(material, modifier.size(size), cornerRadius = size / 2, onClick = onClick, contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(size * 0.44f))
        }
    }
}

/** Chip de escolha: selecionado fica na cor de destaque. */
@Composable
fun LiquidChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Liquid.colors
    val haptic = rememberHaptic()
    val background = if (selected) colors.accent else if (colors.dark) Color(0x1FFFFFFF) else Color(0x14000000)
    Box(
        modifier
            .height(34.dp)
            .clip(RoundedCornerShape(LiquidRadius.chip))
            .background(background)
            .selectable(selected = selected, interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton) {
                haptic()
                onClick()
            }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = LiquidType.footnote.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) colors.onAccent else colors.content,
            maxLines = 1,
        )
    }
}

/** Espaço vertical de [LiquidSpace]. */
@Composable
fun VSpace(height: Dp) = Spacer(Modifier.height(height))
