package app.cristal.cover.ui.lock

import android.text.format.DateFormat
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.cristal.cover.data.notifications.NotificationItem
import app.cristal.cover.data.prefs.CoverClockPosition
import app.cristal.cover.data.prefs.CoverClockStyle
import app.cristal.cover.data.prefs.WallpaperKind
import app.cristal.cover.ui.components.WallpaperLayer
import app.cristal.cover.ui.components.WallpaperSubjectLayer
import app.cristal.cover.ui.components.WallpaperTextShadow
import app.cristal.cover.ui.glass.GlassSurface
import app.cristal.cover.ui.glass.LocalGlassBackdrop
import app.cristal.cover.ui.glass.LocalGlassSpec
import app.cristal.cover.ui.glass.Motion
import app.cristal.cover.ui.glass.glassBackdropSource
import app.cristal.cover.ui.glass.rememberDeviceTilt
import app.cristal.cover.ui.glass.rememberGlassBackdrop
import app.cristal.cover.ui.launcher.GlassButton
import app.cristal.cover.ui.launcher.relativeTime
import app.cristal.cover.ui.widgets.currentLocale
import app.cristal.cover.ui.widgets.formatDate
import app.cristal.cover.ui.widgets.formatTime
import app.cristal.cover.ui.widgets.rememberMinuteTicker
import kotlinx.coroutines.launch

/**
 * Cover Clock: a tela de bloqueio da tela externa. De propósito, só três coisas: papel de parede,
 * relógio e notificações. Sem widgets, atalhos ou controles; sem notificações, sobra só o relógio.
 */
@Composable
fun CoverLockScreen(state: LockUiState, secure: Boolean, resetKey: Int, actions: LockActions) {
    val settings = state.settings
    val backdrop = rememberGlassBackdrop()
    val scope = rememberCoroutineScope()
    val now = rememberMinuteTicker().value
    val is24h = DateFormat.is24HourFormat(LocalContext.current)
    val locale = currentLocale()
    val slide = remember { Animatable(0f) }
    val unlock by rememberUpdatedState(actions.onUnlock)
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val ink = if (LocalGlassSpec.current.dark) Color.White else Color(0xFF14161A)

    LaunchedEffect(resetKey) { slide.animateTo(0f, Motion.release) }

    Box(
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
        val tilt = rememberDeviceTilt(settings.wallpaperSpatial)
        WallpaperLayer(
            image = if (settings.wallpaperKind == WallpaperKind.IMAGE) state.wallpaper else null,
            presetIndex = settings.wallpaperPreset,
            dim = settings.wallpaperDim,
            modifier = Modifier.glassBackdropSource(backdrop),
            blur = settings.wallpaperBlur,
            zoom = settings.wallpaperZoom,
            offsetX = settings.wallpaperOffsetX,
            offsetY = settings.wallpaperOffsetY,
            saturation = settings.wallpaperSaturation,
            tilt = { tilt.value },
            spatial = settings.wallpaperSpatial,
        )
        // O estado de "expandida" fica fora das camadas, para as duas medirem igual.
        var expandedKey by remember { mutableStateOf<String?>(null) }
        val column: @Composable (showClock: Boolean, showRest: Boolean) -> Unit = { showClock, showRest ->
            CoverClockColumn(
                state = state,
                secure = secure,
                now = now,
                is24h = is24h,
                locale = locale,
                ink = ink,
                insets = insets,
                slide = { slide.value },
                showClock = showClock,
                showRest = showRest,
                expandedKey = expandedKey,
                onToggle = { key -> expandedKey = if (expandedKey == key) null else key },
                onOpen = actions.onOpenNotification,
            )
        }
        val subject = state.subject
        if (subject != null) {
            // Efeito de profundidade: relógio, depois o objeto da foto, depois as notificações.
            column(true, false)
            WallpaperSubjectLayer(
                subject = subject,
                dim = settings.wallpaperDim,
                zoom = settings.wallpaperZoom,
                offsetX = settings.wallpaperOffsetX,
                offsetY = settings.wallpaperOffsetY,
                saturation = settings.wallpaperSaturation,
                tilt = { tilt.value },
                spatial = settings.wallpaperSpatial,
            )
            CompositionLocalProvider(LocalGlassBackdrop provides backdrop) { column(false, true) }
        } else {
            CompositionLocalProvider(LocalGlassBackdrop provides backdrop) { column(true, true) }
        }
    }
}

@Composable
private fun CoverClockColumn(
    state: LockUiState,
    secure: Boolean,
    now: Long,
    is24h: Boolean,
    locale: java.util.Locale,
    ink: Color,
    insets: PaddingValues,
    slide: () -> Float,
    showClock: Boolean,
    showRest: Boolean,
    expandedKey: String?,
    onToggle: (String) -> Unit,
    onOpen: (NotificationItem) -> Unit,
) {
    val settings = state.settings
    val clockAlpha = if (showClock) Modifier else Modifier.alpha(0f)
    val restAlpha = if (showRest) Modifier else Modifier.alpha(0f)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(insets)
            .graphicsLayer {
                val p = slide()
                translationY = p * size.height * 0.5f
                alpha = (1f + p * 1.6f).coerceIn(0f, 1f)
            }
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        val position = settings.coverClockPosition
        if (position != CoverClockPosition.TOP) Spacer(Modifier.weight(1f))

        val base = when (settings.coverClockStyle) {
            CoverClockStyle.LIQUID -> TextStyle(
                fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, letterSpacing = (-0.045).em,
                brush = Brush.verticalGradient(listOf(ink, ink.copy(alpha = 0.55f))),
            )
            CoverClockStyle.MINIMAL -> TextStyle(
                fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Thin, letterSpacing = (-0.02).em, color = ink,
            )
            CoverClockStyle.DIGITAL -> TextStyle(
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, letterSpacing = (-0.03).em, color = ink,
            )
            CoverClockStyle.CLASSIC -> TextStyle(
                fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, letterSpacing = (-0.01).em, color = ink,
            )
        }
        Text(
            formatTime(now, is24h),
            style = base.copy(fontFeatureSettings = "tnum", shadow = WallpaperTextShadow),
            fontSize = (104f * settings.coverClockScale.coerceIn(0.6f, 1.4f)).sp,
            maxLines = 1,
            softWrap = false,
            modifier = clockAlpha,
        )
        Text(
            formatDate(now).replaceFirstChar { it.titlecase(locale) },
            color = ink.copy(alpha = 0.9f),
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, shadow = WallpaperTextShadow),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp).then(clockAlpha),
        )

        val shown = state.notifications.take(settings.coverLockMaxNotifications.coerceIn(0, 4))
        if (shown.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = restAlpha) {
                for (item in shown) {
                    val concealed = secure && settings.lockHideSensitive && !item.isPublic
                    CompactNotification(
                        item = item,
                        concealed = concealed,
                        expanded = expandedKey == item.key,
                        interactive = showRest,
                        onToggle = { onToggle(item.key) },
                    ) { onOpen(item) }
                }
                val hidden = state.notifications.size - shown.size
                if (hidden > 0) {
                    Text(
                        "+$hidden",
                        color = ink.copy(alpha = 0.85f),
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, shadow = WallpaperTextShadow),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
        if (position != CoverClockPosition.BOTTOM) Spacer(Modifier.weight(1f))
    }
}

/** Notificação compacta: uma linha; ao tocar, expande e mostra o botão de abrir. */
@Composable
private fun CompactNotification(
    item: NotificationItem,
    concealed: Boolean,
    expanded: Boolean,
    interactive: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    val spec = LocalGlassSpec.current
    GlassSurface(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        cornerRadius = 20.dp,
        elevation = 6.dp,
        onClick = if (interactive) onToggle else null,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(spec.contentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    val icon = item.appIcon
                    if (icon != null) {
                        Image(bitmap = icon, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Text(item.appLabel.take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    if (concealed) item.appLabel else item.title.ifBlank { item.appLabel },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(relativeTime(item.postTime), fontSize = 11.sp, color = spec.secondaryContentColor, maxLines = 1)
            }
            if (expanded) {
                Text(
                    if (concealed) "Conteúdo oculto. Desbloqueie para ver." else item.text.ifBlank { item.appLabel },
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = spec.secondaryContentColor,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
                GlassButton("Abrir", Modifier.padding(top = 8.dp).fillMaxWidth(), primary = true, onClick = onOpen)
            }
        }
    }
}
