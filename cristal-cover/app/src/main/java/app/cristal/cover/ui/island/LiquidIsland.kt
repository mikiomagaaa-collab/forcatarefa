package app.cristal.cover.ui.island

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cristal.cover.data.island.IslandActivity
import app.cristal.cover.data.island.IslandState
import app.cristal.cover.data.media.MediaState
import app.cristal.cover.data.notifications.NotificationItem
import app.cristal.cover.ui.theme.UiFont
import kotlinx.coroutines.delay

/** Medidas da ilha para este aparelho (derivadas do furo da câmera). */
@Immutable
data class IslandGeometry(
    val pillHeight: Dp = 34.dp,
    val idleWidth: Dp = 104.dp,
    val compactWidth: Dp = 232.dp,
    val wideWidth: Dp = 380.dp,
    val topPadding: Dp = 6.dp,
)

@Stable
class IslandActions(
    val onMediaOpen: () -> Unit = {},
    val onPlayPause: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onPrevious: () -> Unit = {},
    val onOpenNotification: (NotificationItem) -> Unit = {},
    val onDismiss: () -> Unit = {},
)

/** Tamanho que a cápsula quer ter, para a janela acompanhar. */
@Immutable
data class IslandTarget(val width: Dp, val height: Dp, val corner: Dp, val visible: Boolean, val withBubble: Boolean)

object IslandSizing {
    val ALERT_HEIGHT = 76.dp
    val EXPANDED_HEIGHT = 172.dp
    val BUBBLE_GAP = 6.dp

    fun target(state: IslandState, geometry: IslandGeometry, expanded: Boolean, alwaysVisible: Boolean): IslandTarget {
        val primary = state.primary
        val h = geometry.pillHeight
        return when {
            primary == null -> IslandTarget(geometry.idleWidth, h, h / 2, alwaysVisible, false)
            primary is IslandActivity.Alert -> IslandTarget(geometry.wideWidth, ALERT_HEIGHT, 30.dp, true, false)
            expanded && primary is IslandActivity.Media -> IslandTarget(geometry.wideWidth, EXPANDED_HEIGHT, 42.dp, true, false)
            else -> IslandTarget(geometry.compactWidth, h, h / 2, true, state.secondary != null)
        }
    }
}

private val islandSpring = spring<Dp>(dampingRatio = 0.74f, stiffness = 380f)
private val Green = Color(0xFF34C759)
private val Orange = Color(0xFFFF9F0A)
private val Red = Color(0xFFFF453A)

/**
 * A Liquid Island: uma cápsula preta em volta da câmera que cresce, encolhe e se transforma como a
 * Dynamic Island do iPhone. Tocar abre o app (ou expande a música); deslizar para cima recolhe.
 */
@Composable
fun LiquidIsland(
    state: IslandState,
    geometry: IslandGeometry,
    alwaysVisible: Boolean,
    concealAlerts: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    actions: IslandActions,
    modifier: Modifier = Modifier,
) {
    val target = IslandSizing.target(state, geometry, expanded, alwaysVisible)
    val width by animateDpAsState(target.width, islandSpring, label = "largura")
    val height by animateDpAsState(target.height, islandSpring, label = "altura")
    val corner by animateDpAsState(target.corner, islandSpring, label = "canto")
    val alpha by animateFloatAsState(if (target.visible) 1f else 0f, tween(260), label = "visível")
    val primary = state.primary
    val setExpanded by rememberUpdatedState(onExpandedChange)

    // A música expandida volta sozinha ao compacto depois de um tempo sem toque.
    LaunchedEffect(expanded, primary?.id) {
        if (expanded) {
            delay(7_000)
            setExpanded(false)
        }
    }
    if (expanded && primary !is IslandActivity.Media) LaunchedEffect(Unit) { setExpanded(false) }

    Box(modifier.fillMaxWidth().padding(top = geometry.topPadding), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier
                .graphicsLayer {
                    this.alpha = alpha
                    val s = 0.7f + 0.3f * alpha
                    scaleX = s
                    scaleY = s
                }
                .size(width, height)
                .clip(RoundedCornerShape(corner))
                .background(Color.Black)
                .semantics { contentDescription = "Liquid Island" }
                .pointerInput(primary?.id, expanded) {
                    detectTapGestures(
                        onTap = {
                            when (primary) {
                                is IslandActivity.Media -> if (expanded) actions.onMediaOpen() else setExpanded(true)
                                is IslandActivity.Alert -> actions.onOpenNotification(primary.item)
                                is IslandActivity.Call -> actions.onOpenNotification(primary.item)
                                is IslandActivity.Timer -> actions.onOpenNotification(primary.item)
                                else -> actions.onDismiss()
                            }
                        },
                        onLongPress = { if (primary is IslandActivity.Media) setExpanded(!expanded) },
                    )
                }
                .pointerInput(primary?.id) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (total < -24.dp.toPx()) {
                                if (expanded) setExpanded(false) else actions.onDismiss()
                            } else if (total > 24.dp.toPx() && primary is IslandActivity.Media) {
                                setExpanded(true)
                            }
                        },
                    ) { change, amount ->
                        change.consume()
                        total += amount
                    }
                },
        ) {
            AnimatedContent(
                targetState = Triple(primary?.id, expanded && primary is IslandActivity.Media, primary),
                contentKey = { it.first to it.second },
                transitionSpec = {
                    (fadeIn(tween(240, delayMillis = 110)) + scaleIn(tween(320, delayMillis = 60), initialScale = 0.9f)) togetherWith
                        (fadeOut(tween(110)) + scaleOut(tween(160), targetScale = 0.94f))
                },
                label = "conteúdo",
            ) { (_, isExpanded, activity) ->
                when {
                    activity == null -> Spacer(Modifier.fillMaxSize())
                    activity is IslandActivity.Alert -> AlertContent(activity.item, concealAlerts)
                    isExpanded && activity is IslandActivity.Media -> ExpandedMedia(activity.media, actions)
                    else -> CompactContent(activity)
                }
            }
        }
        // Segunda atividade contínua: uma bolinha separada, à direita (a apresentação "mínima").
        AnimatedVisibility(
            visible = target.withBubble && state.secondary != null,
            enter = fadeIn(tween(200, delayMillis = 160)) + scaleIn(initialScale = 0.4f),
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.4f),
            modifier = Modifier.offset(x = width / 2 + IslandSizing.BUBBLE_GAP + geometry.pillHeight / 2),
        ) {
            Box(
                Modifier.size(geometry.pillHeight).clip(CircleShape).background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                state.secondary?.let { MinimalGlyph(it) }
            }
        }
    }
}

// --- Compacto -----------------------------------------------------------------------------------

@Composable
private fun CompactContent(activity: IslandActivity) {
    Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        when (activity) {
            is IslandActivity.Media -> {
                Artwork(activity.media, 22.dp, 7.dp)
                Spacer(Modifier.weight(1f))
                Waveform(activity.media.isPlaying, tintOf(activity.media), Modifier.size(width = 22.dp, height = 14.dp))
            }
            is IslandActivity.Charging -> {
                Icon(Icons.Rounded.Bolt, contentDescription = null, tint = Green, modifier = Modifier.size(18.dp))
                Text("Carregando", style = islandText(13.sp, FontWeight.SemiBold), color = Color.White, modifier = Modifier.padding(start = 2.dp))
                Spacer(Modifier.weight(1f))
                Text("${activity.percent}%", style = islandText(14.sp, FontWeight.SemiBold), color = Green)
                Spacer(Modifier.width(5.dp))
                BatteryGlyph(activity.percent / 100f)
            }
            is IslandActivity.Call -> {
                Icon(Icons.Rounded.Phone, contentDescription = null, tint = Green, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp))
                Elapsed(activity.item.chronometerBase ?: activity.item.postTime, false, Green)
                Spacer(Modifier.weight(1f))
                Waveform(true, Green, Modifier.size(width = 22.dp, height = 14.dp))
            }
            is IslandActivity.Timer -> {
                Icon(Icons.Rounded.Timer, contentDescription = null, tint = Orange, modifier = Modifier.size(18.dp))
                Spacer(Modifier.weight(1f))
                Elapsed(activity.item.chronometerBase ?: activity.item.postTime, activity.item.countDown, Orange)
            }
            is IslandActivity.Ringer -> {
                Box(
                    Modifier.size(24.dp).clip(CircleShape).background(if (activity.muted) Red else Color(0xFF3A3A3C)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (activity.muted) Icons.Rounded.NotificationsOff else Icons.Rounded.Notifications,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (activity.muted) "Silencioso" else "Som",
                    style = islandText(14.sp, FontWeight.SemiBold),
                    color = if (activity.muted) Red else Color.White,
                )
            }
            is IslandActivity.Alert -> Unit
        }
    }
}

@Composable
private fun MinimalGlyph(activity: IslandActivity) {
    when (activity) {
        is IslandActivity.Media -> Artwork(activity.media, 20.dp, 10.dp)
        is IslandActivity.Call -> Icon(Icons.Rounded.Phone, contentDescription = null, tint = Green, modifier = Modifier.size(16.dp))
        is IslandActivity.Timer -> Icon(Icons.Rounded.Timer, contentDescription = null, tint = Orange, modifier = Modifier.size(16.dp))
        is IslandActivity.Charging -> Icon(Icons.Rounded.Bolt, contentDescription = null, tint = Green, modifier = Modifier.size(16.dp))
        else -> Unit
    }
}

// --- Aviso de notificação e música expandida ---------------------------------------------------

@Composable
private fun AlertContent(item: NotificationItem, conceal: Boolean) {
    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2C2C2E)), contentAlignment = Alignment.Center) {
            val icon = if (conceal) item.appIcon else item.largeIcon ?: item.appIcon
            if (icon != null) {
                Image(icon, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(item.appLabel.take(1).uppercase(), style = islandText(17.sp, FontWeight.Bold), color = Color.White)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (conceal) item.appLabel else item.title.ifBlank { item.appLabel },
                style = islandText(15.sp, FontWeight.SemiBold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (conceal) "Nova notificação" else item.text,
                style = islandText(13.sp, FontWeight.Normal),
                color = Color.White.copy(alpha = 0.68f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text("agora", style = islandText(12.sp, FontWeight.Medium), color = Color.White.copy(alpha = 0.5f))
    }
}

@Composable
private fun ExpandedMedia(media: MediaState, actions: IslandActions) {
    val tint = tintOf(media)
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.pointerInput(Unit) { detectTapGestures { actions.onMediaOpen() } }) { Artwork(media, 54.dp, 14.dp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(media.title.ifBlank { "Tocando" }, style = islandText(16.sp, FontWeight.SemiBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(media.artist, style = islandText(14.sp, FontWeight.Normal), color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Waveform(media.isPlaying, tint, Modifier.size(width = 26.dp, height = 18.dp))
        }
        Progress(media)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            ControlButton(Icons.Rounded.SkipPrevious, "Anterior", 30.dp, media.canSkipPrevious, actions.onPrevious)
            ControlButton(
                if (media.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (media.isPlaying) "Pausar" else "Tocar",
                38.dp,
                true,
                actions.onPlayPause,
            )
            ControlButton(Icons.Rounded.SkipNext, "Próxima", 30.dp, media.canSkipNext, actions.onNext)
        }
    }
}

@Composable
private fun Progress(media: MediaState) {
    if (media.duration <= 0L) {
        Spacer(Modifier.height(6.dp))
        return
    }
    val now by produceState(SystemClock.elapsedRealtime(), media) {
        while (true) {
            value = SystemClock.elapsedRealtime()
            delay(500)
        }
    }
    val position = media.positionNow(now)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(formatClock(position), style = islandText(11.sp, FontWeight.Medium), color = Color.White.copy(alpha = 0.55f))
        Canvas(Modifier.weight(1f).height(6.dp).padding(horizontal = 8.dp)) {
            val r = CornerRadius(size.height / 2, size.height / 2)
            drawRoundRect(Color.White.copy(alpha = 0.22f), cornerRadius = r)
            drawRoundRect(Color.White, size = Size(size.width * position / media.duration.toFloat(), size.height), cornerRadius = r)
        }
        Text("-" + formatClock(media.duration - position), style = islandText(11.sp, FontWeight.Medium), color = Color.White.copy(alpha = 0.55f))
    }
}

@Composable
private fun ControlButton(icon: ImageVector, label: String, size: Dp, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size + 12.dp)
            .clip(CircleShape)
            .pointerInput(enabled) { if (enabled) detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White.copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(size))
    }
}

// --- Peças --------------------------------------------------------------------------------------

@Composable
private fun Artwork(media: MediaState, size: Dp, corner: Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(corner)).background(Color(0xFF2C2C2E)), contentAlignment = Alignment.Center) {
        val art = media.art
        if (art != null) {
            Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.6f))
        }
    }
}

/** Barrinhas de áudio animadas (paradas quando a música está pausada). */
@Composable
private fun Waveform(playing: Boolean, color: Color, modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "onda")
    val phases = listOf(0, 140, 280, 90).mapIndexed { i, delayMs ->
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(420 + i * 60, delayMillis = delayMs), RepeatMode.Reverse),
            label = "barra$i",
        )
    }
    Canvas(modifier) {
        val bars = phases.size
        val gap = size.width / (bars * 2f - 1f)
        for (i in 0 until bars) {
            val f = if (playing) phases[i].value else 0.18f
            val h = size.height * f
            val x = i * gap * 2 + gap / 2
            drawLine(color, Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2), strokeWidth = gap, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun BatteryGlyph(fraction: Float) {
    Canvas(Modifier.size(width = 24.dp, height = 12.dp)) {
        val stroke = 1.2.dp.toPx()
        val body = Size(size.width - 3.dp.toPx(), size.height)
        drawRoundRect(Color.White.copy(alpha = 0.45f), size = body, cornerRadius = CornerRadius(3.dp.toPx()), style = Stroke(stroke))
        val inset = 2.dp.toPx()
        drawRoundRect(
            Green,
            topLeft = Offset(inset, inset),
            size = Size((body.width - inset * 2) * fraction.coerceIn(0.05f, 1f), body.height - inset * 2),
            cornerRadius = CornerRadius(1.5.dp.toPx()),
        )
        drawRoundRect(
            Color.White.copy(alpha = 0.45f),
            topLeft = Offset(body.width + 1.dp.toPx(), size.height * 0.32f),
            size = Size(1.6.dp.toPx(), size.height * 0.36f),
            cornerRadius = CornerRadius(1.dp.toPx()),
        )
    }
}

/** Tempo de chamada ou timer, atualizado a cada segundo. */
@Composable
private fun Elapsed(base: Long, countDown: Boolean, color: Color) {
    val now by produceState(System.currentTimeMillis(), base) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val ms = if (countDown) (base - now).coerceAtLeast(0L) else (now - base).coerceAtLeast(0L)
    Text(formatClock(ms), style = islandText(14.sp, FontWeight.SemiBold), color = color)
}

private fun tintOf(media: MediaState): Color = media.tint?.let { Color(it) }?.let { c ->
    // Mais claro para aparecer sobre o preto.
    Color(red = 0.4f + 0.6f * c.red, green = 0.4f + 0.6f * c.green, blue = 0.4f + 0.6f * c.blue)
} ?: Color.White

fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun islandText(size: androidx.compose.ui.unit.TextUnit, weight: FontWeight) =
    TextStyle(fontFamily = UiFont, fontSize = size, fontWeight = weight, fontFeatureSettings = "tnum")
