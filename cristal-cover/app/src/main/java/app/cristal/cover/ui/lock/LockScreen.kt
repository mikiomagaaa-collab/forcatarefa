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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.cristal.cover.ui.components.ClockGlass
import app.cristal.cover.ui.widgets.formatTemp
import app.cristal.cover.ui.widgets.weatherIcon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
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
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.gestures.detectTapGestures
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
    /** Segurou o fundo do bloqueio: autentica e abre a personalização. */
    val onCustomize: () -> Unit = {},
)

/**
 * Ganchos do editor do bloqueio: a prévia avisa onde o relógio foi desenhado (em pixels da própria
 * prévia), para o editor pôr a moldura de arrasto em volta dele.
 */
class LockEditing(val onClockBounds: (Rect) -> Unit)

/** Deslocamento do relógio (X/Y em fração da tela) e camada (Z), já limitados. */
internal data class ClockPlacement(val x: Float, val y: Float, val z: Float) {
    /** O relógio fica na frente do objeto recortado da foto. */
    val inFront: Boolean get() = z >= 0.5f
}

/** Pincel do relógio: o degradê branco de sempre ou a cor escolhida. */
internal fun clockBrush(argb: Int): Brush {
    val base = if (argb == 0) Color.White else Color(argb)
    return Brush.verticalGradient(listOf(base, base.copy(alpha = if (argb == 0) 0.78f else 0.84f)))
}

/**
 * Tela de bloqueio do Cristal, no estilo do iOS 26: data e clima no alto, relógio de vidro alto
 * (que estica na vertical e pode ficar atrás do objeto da foto), widgets redondos e notificações
 * embaixo. É só visual: quem destrava de verdade é o sistema.
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
    /** Só na pré-visualização: permite esticar o relógio arrastando o canto. */
    onClockScale: ((Float) -> Unit)? = null,
    /** Só no editor do bloqueio. */
    editing: LockEditing? = null,
) {
    if (coverMinimal) {
        // Na tela externa o bloqueio é o Cover Clock: só papel de parede, relógio e notificações.
        CoverLockScreen(state, secure, resetKey, actions, onClockScale, editing)
        return
    }
    val settings = state.settings
    val backdrop = rememberGlassBackdrop()
    val scope = rememberCoroutineScope()
    val now = rememberMinuteTicker().value
    val is24h = DateFormat.is24HourFormat(LocalContext.current)
    val slide = remember { Animatable(0f) }
    val unlock by rememberUpdatedState(actions.onUnlock)
    val customize by rememberUpdatedState(actions.onCustomize)
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val locale = currentLocale()
    val density = LocalDensity.current

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
            }
            // Segurar o fundo abre a personalização (depois de autenticar), como no iPhone.
            .pointerInput(Unit) { detectTapGestures(onLongPress = { customize() }) },
    ) {
        val compact = maxHeight < 380.dp
        val tilt = rememberDeviceTilt(settings.wallpaperSpatial)
        val dim = (settings.wallpaperDim + 0.06f).coerceAtMost(0.8f)
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

        val subject = state.subject
        val subjectTop by produceState<Float?>(null, subject) {
            value = subject?.let { withContext(Dispatchers.Default) { subjectTopFraction(it) } }
        }
        val widgets = settings.lockWidgets
        val widgetsAbove = if (widgets.isNotEmpty() && settings.lockWidgetsPosition == LockPlacement.ABOVE_CLOCK) {
            LockWidgetRowHeight + 6.dp
        } else {
            0.dp
        }
        var liveScale by remember(settings.lockClockScale) { mutableFloatStateOf(settings.lockClockScale) }
        val topGap = 8.dp
        val dateLine = 30.dp
        // Altura do relógio com tamanho 100%: o terço de cima da tela, como no iOS 26; com o efeito
        // de profundidade, desce até passar um pouco por trás do objeto da foto.
        val baseClockHeight: Dp = run {
            val default = maxHeight * if (compact) 0.30f else 0.40f
            val top = subjectTop
            if (subject == null || top == null || !settings.lockClockAdaptive) return@run default
            with(density) {
                val rect = wallpaperImageRect(
                    subject.width, subject.height, Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()),
                    settings.wallpaperZoom, settings.wallpaperOffsetX, settings.wallpaperOffsetY, settings.wallpaperSpatial,
                )
                val subjectY = (rect.top + top * rect.height).toDp()
                val clockTop = insets.calculateTopPadding() + topGap + widgetsAbove + dateLine
                val free = subjectY - clockTop
                if (free < 56.dp) default else (free * 1.35f).coerceIn(110.dp, maxHeight * 0.5f)
            }
        }
        val clockHeight = (baseClockHeight * liveScale).coerceIn(72.dp, maxHeight * 0.64f)
        val bigClock = !settings.lockClockCard || subject != null
        val clockColor = if (settings.lockClockColor == 0) Color.White else Color(settings.lockClockColor)
        val clockGlass = if (settings.lockClockGlass) ClockGlass(backdrop, settings.lockClockGlassOpacity, tint = clockColor) else null
        val slideModifier = Modifier.graphicsLayer {
            val p = slide.value
            translationY = p * size.height * 0.5f
            alpha = (1f + p * 1.6f).coerceIn(0f, 1f)
        }
        val placement = ClockPlacement(settings.lockClockX, settings.lockClockY, settings.lockClockZ)
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        if (bigClock) {
            val subjectLayer: @Composable () -> Unit = {
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
            // Z: o relógio fica atrás do objeto recortado da foto (profundidade) ou na frente dele.
            if (placement.inFront) subjectLayer()
            // Data e relógio ficam numa camada própria; X e Y deslocam os dois juntos.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(insets)
                    .then(slideModifier)
                    .padding(top = topGap + widgetsAbove)
                    .clockDepth(placement, { tilt.value }, settings.wallpaperSpatial, widthPx, heightPx),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LockDateLine(now, locale, state, Modifier.height(dateLine))
                AdaptiveClock(
                    time = formatTime(now, is24h),
                    font = settings.lockClockFont,
                    layout = settings.lockClockLayout,
                    adaptive = settings.lockClockAdaptive,
                    glass = clockGlass,
                    brush = clockBrush(settings.lockClockColor),
                    fixedSize = (if (compact) 96f else 128f).times(liveScale).sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .height(clockHeight)
                        .then(if (editing != null) Modifier.onGloballyPositioned { editing.onClockBounds(it.boundsInRoot()) } else Modifier),
                )
            }
            if (!placement.inFront) subjectLayer()
        }

        CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(insets)
                    .then(slideModifier)
                    .padding(start = 14.dp, end = 14.dp, top = topGap, bottom = 6.dp),
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
                    Spacer(Modifier.height(6.dp))
                }
                if (bigClock) {
                    // O relógio já foi desenhado (atrás do objeto, se houver); aqui só reserva o espaço.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(dateLine + clockHeight)
                            .offset { IntOffset((placement.x * widthPx).roundToInt(), (placement.y * heightPx).roundToInt()) },
                    ) {
                        if (onClockScale != null) {
                            ClockResizeHandle(
                                baseHeight = baseClockHeight,
                                onDrag = { d -> liveScale = (liveScale + d).coerceIn(0.5f, 2.2f) },
                                onEnd = { onClockScale(liveScale) },
                                modifier = Modifier.padding(top = dateLine),
                            )
                        }
                    }
                } else {
                    GlassClockCard(state, secure, now, is24h, locale, compact, liveScale)
                }
                if (widgets.isNotEmpty() && settings.lockWidgetsPosition == LockPlacement.BELOW_CLOCK) {
                    Spacer(Modifier.height(12.dp))
                    LockWidgetsRow(widgets, state)
                }
                if (media != null && settings.lockMediaPosition == LockPlacement.BELOW_CLOCK) {
                    Spacer(Modifier.height(12.dp))
                    mediaCard(Modifier)
                }

                // Notificações: chegam por baixo, como no iPhone.
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
                ) {
                    items(state.notifications, key = { it.key }) { item ->
                        val concealed = secure && settings.lockHideSensitive && !item.isPublic
                        SwipeDismiss(enabled = item.isClearable && !concealed, onDismiss = { actions.onDismissNotification(item) }) {
                            NotificationCard(item = item, concealed = concealed, onClick = { actions.onOpenNotification(item) })
                        }
                    }
                }
                if (media != null && settings.lockMediaPosition == LockPlacement.BOTTOM) {
                    mediaCard(Modifier.padding(bottom = 10.dp))
                }
                if (widgets.isNotEmpty() && settings.lockWidgetsPosition == LockPlacement.BOTTOM) {
                    LockWidgetsRow(widgets, state, Modifier.padding(bottom = 14.dp))
                }
                LockBottomBar(state, actions)
            }
        }
    }
}

/** "Dom., 21 de nov. · ☁ 24°": data e clima na mesma linha, como no iOS 26. */
@Composable
fun LockDateLine(now: Long, locale: java.util.Locale, state: LockUiState, modifier: Modifier = Modifier, color: Color = Color.White) {
    val date = remember(now / 60_000L, locale) {
        val pattern = DateFormat.getBestDateTimePattern(locale, "EEEdMMM")
        java.time.format.DateTimeFormatter.ofPattern(pattern, locale)
            .format(java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()))
            .replaceFirstChar { it.titlecase(locale) }
    }
    val weather = state.weather.data
    val style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, shadow = WallpaperTextShadow, color = color.copy(alpha = 0.94f))
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(date, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (weather != null) {
            Spacer(Modifier.width(8.dp))
            Icon(weatherIcon(weather.code, weather.isDay), contentDescription = null, tint = color.copy(alpha = 0.94f), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(formatTemp(weather.tempC, state.settings.tempUnit), style = style, maxLines = 1)
        }
    }
}

/** Relógio dentro de um cartão de vidro (quando o usuário prefere o visual antigo). */
@Composable
private fun GlassClockCard(state: LockUiState, secure: Boolean, now: Long, is24h: Boolean, locale: java.util.Locale, compact: Boolean, scale: Float) {
    val settings = state.settings
    val cardScale = scale.coerceIn(0.8f, 1.6f)
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
                style = settings.lockClockFont.clockStyle().copy(brush = Brush.verticalGradient(listOf(ink, ink.copy(alpha = 0.62f)))),
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
                    if (state.battery.charging) Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(15.dp))
                    Text("${state.battery.percent}%", fontSize = 13.sp, color = LocalGlassSpec.current.secondaryContentColor)
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
}

/** Lanterna e câmera nos cantos e a barrinha de deslizar no meio, como no iPhone. */
@Composable
private fun LockBottomBar(state: LockUiState, actions: LockActions) {
    val settings = state.settings
    Box(Modifier.fillMaxWidth().height(60.dp)) {
        if (settings.lockShortcuts && state.torchAvailable) {
            GlassIconButton(
                icon = if (state.torchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                contentDescription = "Lanterna",
                size = 52.dp,
                active = state.torchOn,
                onClick = actions.onTorch,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 22.dp),
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 2.dp)
                .semantics { contentDescription = "Deslize para desbloquear" }
                .size(width = 128.dp, height = 5.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.82f)),
        )
        if (settings.lockShortcuts) {
            GlassIconButton(
                icon = Icons.Rounded.PhotoCamera,
                contentDescription = "Câmera",
                size = 52.dp,
                onClick = actions.onCamera,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 22.dp),
            )
        }
    }
}

/**
 * Posição livre e profundidade do relógio: X e Y deslocam (em fração da tela). Com o papel de
 * parede espacial, a camada se move com a inclinação proporcionalmente à profundidade: na frente
 * se move mais, atrás quase nada. Atrás do objeto o relógio também perde um pouco de brilho.
 */
internal fun Modifier.clockDepth(
    placement: ClockPlacement,
    tilt: () -> androidx.compose.ui.geometry.Offset,
    spatial: Boolean,
    widthPx: Float,
    heightPx: Float,
): Modifier = this
    .offset { IntOffset((placement.x * widthPx).roundToInt(), (placement.y * heightPx).roundToInt()) }
    .graphicsLayer {
        if (spatial) {
            val t = tilt()
            val amount = (placement.z - 0.5f) * 14.dp.toPx()
            translationX = t.x * amount
            translationY = t.y * amount
        }
        alpha = 0.86f + 0.14f * placement.z.coerceIn(0f, 1f)
    }
