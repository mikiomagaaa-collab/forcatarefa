package app.cristal.cover.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cristal.cover.ui.glass.GlassSurface
import app.cristal.cover.ui.glass.LocalGlassSpec
import app.cristal.cover.ui.glass.Motion
import app.cristal.cover.ui.glass.rememberHaptic
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import kotlin.math.abs

/**
 * Estado de um painel deslizante (gaveta, painel rápido). `progress` vai de 0 (fechado) a 1
 * (aberto) e pode ser arrastado pelo dedo ou animado por mola.
 */
@Stable
class SheetState {
    val progress = Animatable(0f)

    val isVisible: Boolean get() = progress.value > 0.001f
    val isOpenOrOpening: Boolean get() = progress.targetValue > 0.5f

    suspend fun open() {
        progress.animateTo(1f, Motion.sheet)
    }

    suspend fun close() {
        progress.animateTo(0f, Motion.sheet)
    }

    suspend fun dragBy(delta: Float) {
        progress.snapTo((progress.value + delta).coerceIn(0f, 1f))
    }

    /** Termina um arrasto: [velocity] em "frações por segundo", positivo abrindo. */
    suspend fun settle(velocity: Float) {
        val target = when {
            velocity > 1.2f -> 1f
            velocity < -1.2f -> 0f
            progress.value > 0.4f -> 1f
            else -> 0f
        }
        progress.animateTo(target, Motion.sheet, initialVelocity = velocity.coerceIn(-12f, 12f))
    }
}

/** Sombra usada em textos desenhados direto sobre o papel de parede. */
val WallpaperTextShadow = Shadow(color = Color.Black.copy(alpha = 0.55f), offset = Offset(0f, 1.5f), blurRadius = 5f)

/**
 * Indicador de páginas. A página atual vira uma pílula mais larga.
 *
 * @param position página atual com fração (ex.: 1,4 = 40% do caminho entre a 1 e a 2). É lida só
 * na fase de desenho, então rolar as páginas não provoca recomposição.
 */
@Composable
fun PageDots(
    count: Int,
    position: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
) {
    if (count <= 1) return
    Canvas(modifier = modifier.size(width = (count * 10 + 4).dp, height = 5.dp)) {
        val current = position()
        val dot = 5.dp.toPx()
        val extra = 9.dp.toPx()
        val gap = 5.dp.toPx()
        var x = 0f
        for (index in 0 until count) {
            val weight = 1f - abs(index - current).coerceIn(0f, 1f)
            val width = dot + extra * weight
            drawRoundRect(
                color = color.copy(alpha = 0.4f + 0.6f * weight),
                topLeft = Offset(x, 0f),
                size = Size(width, dot),
                cornerRadius = CornerRadius(dot / 2f, dot / 2f),
            )
            x += width + gap
        }
    }
}

/** Campo de texto em pílula de vidro. */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    focusRequester: FocusRequester? = null,
    height: Dp = 46.dp,
    imeAction: ImeAction = ImeAction.Search,
    onImeAction: (() -> Unit)? = null,
) {
    val spec = LocalGlassSpec.current
    GlassSurface(
        modifier = modifier.height(height),
        cornerRadius = height / 2,
        elevation = 6.dp,
        reactToTouch = false,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterStart)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(20.dp), tint = spec.secondaryContentColor)
                Spacer(Modifier.width(8.dp))
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = spec.secondaryContentColor,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(color = spec.contentColor, fontSize = 15.sp),
                    cursorBrush = SolidColor(spec.contentColor),
                    keyboardOptions = KeyboardOptions(imeAction = imeAction),
                    keyboardActions = KeyboardActions(
                        onSearch = { onImeAction?.invoke() },
                        onDone = { onImeAction?.invoke() },
                        onGo = { onImeAction?.invoke() },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
                )
            }
            if (value.isNotEmpty()) {
                val haptic = rememberHaptic()
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Limpar",
                    tint = spec.secondaryContentColor,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            haptic()
                            onValueChange("")
                        }
                        .padding(4.dp),
                )
            }
        }
    }
}

/** Título pequeno de seção, em maiúsculas discretas. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        color = color.copy(alpha = 0.7f),
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 1.2.sp,
        maxLines = 1,
    )
}
