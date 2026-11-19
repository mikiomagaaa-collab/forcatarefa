package app.cristal.cover.ui.launcher

import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cristal.cover.data.home.WidgetKind
import app.cristal.cover.data.notifications.NotificationItem
import app.cristal.cover.data.system.QuickState
import app.cristal.cover.ui.components.SectionLabel
import app.cristal.cover.ui.components.SheetState
import app.cristal.cover.ui.components.WallpaperTextShadow
import app.cristal.cover.ui.glass.GlassIconButton
import app.cristal.cover.ui.glass.GlassSurface
import app.cristal.cover.ui.glass.LocalGlassSpec
import app.cristal.cover.ui.glass.Motion
import app.cristal.cover.ui.glass.rememberHaptic
import app.cristal.cover.ui.theme.ClockTextStyle
import app.cristal.cover.ui.widgets.WidgetActions
import app.cristal.cover.ui.widgets.WidgetCard
import app.cristal.cover.ui.widgets.WidgetData
import app.cristal.cover.ui.widgets.formatDate
import app.cristal.cover.ui.widgets.formatTime
import app.cristal.cover.ui.widgets.rememberMinuteTicker
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.launch

/** Controle deslizante em pílula de vidro; o preenchimento acompanha o dedo. */
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val spec = LocalGlassSpec.current
    val change by rememberUpdatedState(onValueChange)
    var width by remember { mutableIntStateOf(1) }
    GlassSurface(modifier = modifier, cornerRadius = 21.dp, elevation = 6.dp, reactToTouch = false, clipContent = true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { width = it.width.coerceAtLeast(1) }
                .pointerInput(Unit) {
                    detectTapGestures { offset -> change((offset.x / width).coerceIn(0f, 1f)) }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { pointer, _ ->
                        pointer.consume()
                        change((pointer.position.x / width).coerceIn(0f, 1f))
                    }
                }
                .drawBehind {
                    drawRoundRect(
                        color = spec.contentColor.copy(alpha = 0.26f),
                        size = Size(size.width * value.coerceIn(0f, 1f), size.height),
                        cornerRadius = CornerRadius(0f),
                    )
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Icon(icon, contentDescription = "Volume", modifier = Modifier.padding(start = 14.dp).size(20.dp))
        }
    }
}

/** Arrastar para o lado descarta o conteúdo. */
@Composable
fun SwipeDismiss(enabled: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var width by remember { mutableIntStateOf(1) }
    val dismiss by rememberUpdatedState(onDismiss)
    val dragState = rememberDraggableState { delta -> scope.launch { offsetX.snapTo(offsetX.value + delta) } }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            .graphicsLayer { alpha = 1f - (abs(offsetX.value) / width).coerceIn(0f, 1f) * 0.75f }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                enabled = enabled,
                onDragStopped = { velocity ->
                    if (abs(offsetX.value) > width * 0.35f || abs(velocity) > 1800f) {
                        val direction = if (offsetX.value != 0f) sign(offsetX.value) else sign(velocity)
                        offsetX.animateTo(direction * width * 1.15f, tween(170))
                        dismiss()
                    } else {
                        offsetX.animateTo(0f, Motion.pop)
                    }
                },
            ),
    ) {
        content()
    }
}

/**
 * Card de notificação.
 * @param concealed esconde título e texto (conteúdo sensível na tela de bloqueio).
 */
@Composable
fun NotificationCard(item: NotificationItem, concealed: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val spec = LocalGlassSpec.current
    GlassSurface(modifier = modifier.fillMaxWidth(), cornerRadius = 22.dp, elevation = 8.dp, onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(spec.contentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                val icon = if (concealed) item.appIcon else item.largeIcon ?: item.appIcon
                if (icon != null) {
                    Image(bitmap = icon, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Text(item.appLabel.take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.appLabel,
                        fontSize = 11.sp,
                        color = spec.secondaryContentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text("  ·  ${relativeTime(item.postTime)}", fontSize = 11.sp, color = spec.secondaryContentColor, maxLines = 1)
                }
                if (concealed) {
                    Text("Conteúdo oculto", fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    Text("Desbloqueie para ver", fontSize = 12.sp, color = spec.secondaryContentColor, maxLines = 1)
                } else {
                    if (item.title.isNotEmpty()) {
                        Text(item.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (item.text.isNotEmpty()) {
                        Text(
                            item.text,
                            fontSize = 12.sp,
                            color = spec.secondaryContentColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 15.sp,
                        )
                    }
                }
            }
        }
    }
}

/** "agora", "5 min", "2 h", "3 d". */
fun relativeTime(time: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = ((now - time) / 60_000L).coerceAtLeast(0)
    return when {
        minutes < 1 -> "agora"
        minutes < 60 -> "$minutes min"
        minutes < 60 * 24 -> "${minutes / 60} h"
        else -> "${minutes / (60 * 24)} d"
    }
}
