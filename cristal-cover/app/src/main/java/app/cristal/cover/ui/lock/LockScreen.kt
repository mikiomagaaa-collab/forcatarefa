package app.cristal.cover.ui.lock

import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cristal.cover.data.home.WidgetKind
import app.cristal.cover.data.notifications.NotificationItem
import app.cristal.cover.data.prefs.WallpaperKind
import app.cristal.cover.ui.components.WallpaperLayer
import app.cristal.cover.ui.components.WallpaperSubjectLayer
import app.cristal.cover.ui.components.WallpaperTextShadow
import app.cristal.cover.ui.glass.GlassIconButton
import app.cristal.cover.ui.glass.GlassSurface
import app.cristal.cover.ui.glass.LocalGlassBackdrop
import app.cristal.cover.ui.glass.LocalGlassSpec
import app.cristal.cover.ui.glass.Motion
import app.cristal.cover.ui.glass.glassBackdropSource
import app.cristal.cover.ui.glass.rememberDeviceTilt
import app.cristal.cover.ui.glass.rememberGlassBackdrop
import app.cristal.cover.ui.launcher.NotificationCard
import app.cristal.cover.ui.launcher.SwipeDismiss
import app.cristal.cover.ui.theme.clockStyle
import app.cristal.cover.data.prefs.LockPlacement
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import app.cristal.cover.ui.components.AdaptiveClock
import app.cristal.cover.ui.components.subjectTopFraction
import app.cristal.cover.ui.components.wallpaperImageRect
import androidx.compose.runtime.produceState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.cristal.cover.ui.widgets.WidgetActions
import app.cristal.cover.ui.widgets.WidgetCard
import app.cristal.cover.ui.widgets.WidgetData
import app.cristal.cover.ui.widgets.currentLocale
import app.cristal.cover.ui.widgets.formatDate
import app.cristal.cover.ui.widgets.formatTime
import app.cristal.cover.ui.widgets.rememberMinuteTicker
import kotlinx.coroutines.launch

@Stable
class LockActions(
    /** O usuário deslizou para destravar. */
    val onUnlock: () -> Unit,
    val onOpenNotification: (NotificationItem) -> Unit,
    val onDismissNotification: (NotificationItem) -> Unit,
    val onTorch: () -> Unit,
    val onCamera: () -> Unit,
    val onMediaPlayPause: () -> Unit,
    val onMediaNext: () -> Unit,
    val onMediaPrevious: () -> Unit,
)

/**
 * Tela de bloqueio do Cristal. É só visual: quem destrava de verdade é o sistema.
 *
 * @param secure se o aparelho tem PIN/padrão/senha; controla a ocultação de conteúdo sensível.
 * @param resetKey muda quando um pedido de desbloqueio é cancelado, para a tela voltar ao lugar.
 */
@Composable
fun LockScreen(
    state: LockUiState,
    secure: Boolean,
    resetKey: Int,
    actions: LockActions,
    coverMinimal: Boolean = false,
    /** Só na pré-visualização: permite mudar o tamanho do relógio arrastando a alça. */
    onClockScale: ((Float) -> Unit)? = null,
) {
    if (coverMinimal) {
        // Na tela externa o bloqueio é o Cover Clock: só papel de parede, relógio e notificações.
        CoverLockScreen(state, secure, resetKey, actions, onClockScale)
        return
    }
    val settings = state.settings
    val backdrop = rememberGlassBackdrop()
    val scope = rememberCoroutineScope()
    val now = rememberMinuteTicker().value
    val is24h = DateFormat.is24HourFormat(LocalContext.current)
    val slide = remember { Animatable(0f) }
    val unlock by rememberUpdatedState(actions.onUnlock)
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val locale = currentLocale()

    LaunchedEffect(resetKey) { slide.animateTo(0f, Motion.release) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                val tracker = VelocityTracker()
                detectVerticalDragGestures(
                    onDragStart = { tracker.resetTracking() },
                    onDragEnd = {
                        val height = size.height.toFloat().coerceAtLeast(1f)
                        val velocity = tracker.calculateVelocity().y
                        scope.launch {
                            if (slide.value < -0.22f || velocity < -1400f) {
                                slide.animateTo(-1f, tween(180))
                                unlock()
                            } else {
                                slide.animateTo(0f, Motion.release, initialVelocity = velocity / height)
                            }
                        }
                    },
                    onDragCancel = { scope.launch { slide.animateTo(0f, Motion.release) } },
                    onVerticalDrag = { change, amount ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        change.consume()
                        val height = size.height.toFloat().coerceAtLeast(1f)
                        scope.launch { slide.snapTo((slide.value + amount / height).coerceIn(-1f, 0.06f)) }
                    },
                )
            },
    ) {
        val compact = maxHeight < 380.dp
        val tilt = rememberDeviceTilt(settings.wallpaperSpatial)
        val dim = (settings.wallpaperDim + 0.12f).coerceAtMost(0.8f)
        WallpaperLayer(
            image = if (settings.wallpaperKind == WallpaperKind.IMAGE) state.wallpaper else null,
            presetIndex = settings.wallpaperPreset,
            dim = dim,
            blur = settings.wallpaperBlur,
            zoom = settings.wallpaperZoom,
            offsetX = settings.wallpaperOffsetX,
            offsetY = settings.wallpaperOffsetY,
            saturation = settings.wallpaperSaturation,
            modifier = Modifier.glassBackdropSource(backdrop),
            tilt = { tilt.value },
            spatial = settings.wallpaperSpatial,
        )
        // Relógio grande direto sobre a foto: com o efeito de profundidade (fica entre a foto e o
        // objeto recortado dela) ou quando o cartão de vidro está desligado.
        val subject = state.subject
        val bigClock = subject != null || !settings.lockClockCard
        val subjectTop by produceState<Float?>(null, subject) {
            value = subject?.let { withContext(Dispatchers.Default) { subjectTopFraction(it) } }
        }
        val density = LocalDensity.current
        val dateLine = 26.dp
        val topGap = 18.dp
        var liveScale by remember(settings.lockClockScale) { mutableFloatStateOf(settings.lockClockScale) }
        val widgets = settings.lockWidgets
        val widgetsAbove = if (widgets.isNotEmpty() && settings.lockWidgetsPosition == LockPlacement.ABOVE_CLOCK) {
            LockWidgetRowHeight + 8.dp
        } else {
            0.dp
        }
        val baseClockHeight: Dp = run {
            val default = if (compact) 150.dp else 214.dp
            val top = subjectTop
            if (subject == null || top == null || !settings.lockClockAdaptive) return@run default
            // Formato adaptativo: o relógio cresce até passar um pouco por trás do objeto.
            with(density) {
                val rect = wallpaperImageRect(
                    subject.width, subject.height, Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()),
                    settings.wallpaperZoom, settings.wallpaperOffsetX, settings.wallpaperOffsetY, settings.wallpaperSpatial,
                )
                val subjectY = (rect.top + top * rect.height).toDp()
                val clockTop = insets.calculateTopPadding() + topGap + dateLine + widgetsAbove
                val free = subjectY - clockTop
                if (free < 56.dp) default else (free * 1.45f).coerceIn(110.dp, maxHeight * 0.46f)
            }
        }
        val clockHeight = (baseClockHeight * liveScale).coerceIn(70.dp, maxHeight * 0.62f)
        if (bigClock) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(insets)
                    .graphicsLayer {
                        val p = slide.value
                        translationY = p * size.height * 0.5f
                        alpha = (1f + p * 1.6f).coerceIn(0f, 1f)
                    }
                    .padding(top = topGap + widgetsAbove),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.height(dateLine), contentAlignment = Alignment.Center) {
                    Text(
                        formatDate(now).replaceFirstChar { it.titlecase(locale) },
                        color = Color.White.copy(alpha = 0.92f),
                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, shadow = WallpaperTextShadow),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AdaptiveClock(
                    time = formatTime(now, is24h),
                    font = settings.lockClockFont,
                    layout = settings.lockClockLayout,
                    adaptive = settings.lockClockAdaptive,
                    brush = Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0.74f))),
                    fixedSize = (if (compact) 96f else 128f).times(liveScale).sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(clockHeight),
                )
            }
            if (subject != null) {
                WallpaperSubjectLayer(
                    subject = subject,
                    dim = dim,
                    zoom = settings.wallpaperZoom,
                    offsetX = settings.wallpaperOffsetX,
                    offsetY = settings.wallpaperOffsetY,
                    saturation = settings.wallpaperSaturation,
                    tilt = { tilt.value },
                    spatial = settings.wallpaperSpatial,
                )
            }
        }
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(insets)
                    .graphicsLayer {
                        val p = slide.value
                        translationY = p * size.height * 0.5f
                        alpha = (1f + p * 1.6f).coerceIn(0f, 1f)
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val media = state.media
                val mediaCard: @Composable (Modifier) -> Unit = { m ->
                    if (media != null) {
                        WidgetCard(
                            kind = WidgetKind.MUSIC,
                            data = WidgetData(media = media, notificationAccess = true),
                            actions = WidgetActions(
                                onMediaPlayPause = actions.onMediaPlayPause,
                                onMediaNext = actions.onMediaNext,
                                onMediaPrevious = actions.onMediaPrevious,
                            ),
                            modifier = m.fillMaxWidth().height(96.dp),
                        )
                    }
                }
                if (widgetsAbove > 0.dp) {
                    LockWidgetsRow(widgets, state)
                    Spacer(Modifier.height(8.dp))
                }
                if (bigClock) {
                    // O relógio já foi desenhado (atrás do objeto, se houver); aqui só reserva o espaço.
                    Box(Modifier.fillMaxWidth().height(topGap + dateLine + clockHeight - 8.dp)) {
                        if (onClockScale != null) {
                            ClockResizeHandle(
                                baseHeight = baseClockHeight,
                                onDrag = { d -> liveScale = (liveScale + d).coerceIn(0.6f, 1.8f) },
                                onEnd = { onClockScale(liveScale) },
                            )
                        }
                    }
                } else {
                // Relógio grande em vidro
                val cardScale = liveScale.coerceIn(0.8f, 1.6f)
                Box(Modifier.fillMaxWidth()) {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth().height((if (compact) 104.dp else 128.dp) * cardScale),
                    cornerRadius = 34.dp,
                    reactToTouch = false,
                    elevation = 16.dp,
                ) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        val ink = LocalGlassSpec.current.contentColor
                        Text(
                            formatTime(now, is24h),
                            style = settings.lockClockFont.clockStyle().copy(
                                brush = Brush.verticalGradient(listOf(ink, ink.copy(alpha = 0.62f))),
                            ),
                            fontSize = (if (compact) 60f else 76f).times(cardScale).sp,
                            maxLines = 1,
                            softWrap = false,
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                formatDate(now).replaceFirstChar { it.titlecase(locale) },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                if (state.battery.charging) {
                                    Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(15.dp))
                                }
                                Text(
                                    "${state.battery.percent}%",
                                    fontSize = 13.sp,
                                    color = LocalGlassSpec.current.secondaryContentColor,
                                )
                            }
                        }
                        Icon(
                            if (secure) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                            contentDescription = if (secure) "Bloqueado" else "Sem bloqueio",
                            modifier = Modifier.size(20.dp),
                            tint = LocalGlassSpec.current.secondaryContentColor,
                        )
                    }
                }
                if (onClockScale != null) {
                    ClockResizeHandle(
                        baseHeight = if (compact) 104.dp else 128.dp,
                        onDrag = { d -> liveScale = (liveScale + d).coerceIn(0.6f, 1.8f) },
                        onEnd = { onClockScale(liveScale) },
                    )
                }
                }
                }
                if (widgets.isNotEmpty() && settings.lockWidgetsPosition == LockPlacement.BELOW_CLOCK) {
                    Spacer(Modifier.height(10.dp))
                    LockWidgetsRow(widgets, state)
                }

                // Música e notificações
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (media != null && settings.lockMediaPosition != LockPlacement.BOTTOM) {
                        item(key = "media") { mediaCard(Modifier) }
                    }
                    items(state.notifications, key = { it.key }) { item ->
                        val concealed = secure && settings.lockHideSensitive && !item.isPublic
                        SwipeDismiss(enabled = item.isClearable && !concealed, onDismiss = { actions.onDismissNotification(item) }) {
                            NotificationCard(item = item, concealed = concealed, onClick = { actions.onOpenNotification(item) })
                        }
                    }
                }

                if (media != null && settings.lockMediaPosition == LockPlacement.BOTTOM) {
                    mediaCard(Modifier.padding(bottom = 8.dp))
                }
                if (widgets.isNotEmpty() && settings.lockWidgetsPosition == LockPlacement.BOTTOM) {
                    LockWidgetsRow(widgets, state, Modifier.padding(bottom = 8.dp))
                }

                // Atalhos e dica de desbloqueio
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (settings.lockShortcuts && state.torchAvailable) {
                        GlassIconButton(
                            icon = if (state.torchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                            contentDescription = "Lanterna",
                            size = 48.dp,
                            active = state.torchOn,
                            onClick = actions.onTorch,
                        )
                    } else {
                        Spacer(Modifier.size(48.dp))
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        Text(
                            "Deslize para desbloquear",
                            color = Color.White,
                            style = TextStyle(fontSize = 12.sp, shadow = WallpaperTextShadow),
                            maxLines = 1,
                        )
                    }
                    if (settings.lockShortcuts) {
                        GlassIconButton(
                            icon = Icons.Rounded.PhotoCamera,
                            contentDescription = "Câmera",
                            size = 48.dp,
                            onClick = actions.onCamera,
                        )
                    } else {
                        Spacer(Modifier.size(48.dp))
                    }
                }
            }
        }
    }
}
