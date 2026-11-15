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
import app.cristal.cover.ui.components.WallpaperTextShadow
import app.cristal.cover.ui.glass.GlassIconButton
import app.cristal.cover.ui.glass.GlassSurface
import app.cristal.cover.ui.glass.LocalGlassBackdrop
import app.cristal.cover.ui.glass.LocalGlassSpec
import app.cristal.cover.ui.glass.Motion
import app.cristal.cover.ui.glass.glassBackdropSource
import app.cristal.cover.ui.glass.rememberGlassBackdrop
import app.cristal.cover.ui.launcher.NotificationCard
import app.cristal.cover.ui.launcher.SwipeDismiss
import app.cristal.cover.ui.theme.LockClockTextStyle
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
fun LockScreen(state: LockUiState, secure: Boolean, resetKey: Int, actions: LockActions) {
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
        WallpaperLayer(
            image = if (settings.wallpaperKind == WallpaperKind.IMAGE) state.wallpaper else null,
            presetIndex = settings.wallpaperPreset,
            dim = (settings.wallpaperDim + 0.12f).coerceAtMost(0.8f),
            modifier = Modifier.glassBackdropSource(backdrop),
        )
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
                // Relógio grande em vidro
                GlassSurface(
                    modifier = Modifier.fillMaxWidth().height(if (compact) 104.dp else 128.dp),
                    cornerRadius = 34.dp,
                    reactToTouch = false,
                    elevation = 16.dp,
                ) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        val ink = LocalGlassSpec.current.contentColor
                        Text(
                            formatTime(now, is24h),
                            style = LockClockTextStyle.copy(
                                brush = Brush.verticalGradient(listOf(ink, ink.copy(alpha = 0.62f))),
                            ),
                            fontSize = if (compact) 60.sp else 76.sp,
                            maxLines = 1,
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

                // Música e notificações
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.media != null) {
                        item(key = "media") {
                            WidgetCard(
                                kind = WidgetKind.MUSIC,
                                data = WidgetData(media = state.media, notificationAccess = true),
                                actions = WidgetActions(
                                    onMediaPlayPause = actions.onMediaPlayPause,
                                    onMediaNext = actions.onMediaNext,
                                    onMediaPrevious = actions.onMediaPrevious,
                                ),
                                modifier = Modifier.fillMaxWidth().height(96.dp),
                            )
                        }
                    }
                    items(state.notifications, key = { it.key }) { item ->
                        val concealed = secure && settings.lockHideSensitive && !item.isPublic
                        SwipeDismiss(enabled = item.isClearable && !concealed, onDismiss = { actions.onDismissNotification(item) }) {
                            NotificationCard(item = item, concealed = concealed, onClick = { actions.onOpenNotification(item) })
                        }
                    }
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
