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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.material.icons.rounded.Event
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.border
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import app.cristal.cover.data.prefs.IslandStyle
import app.cristal.cover.data.island.BriefSuggestion
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
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.unit.lerp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

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
    /** Info Brief: confirmou a sugestão (criar o lembrete ou a tarefa). */
    val onBriefAccept: (BriefSuggestion) -> Unit = {},
    /** Info Brief: "agora não" (a sugestão vai para o histórico). */
    val onBriefLater: (BriefSuggestion) -> Unit = {},
)

/**
 * Espaço disponível na região da câmera, medido (modo de precisão) ou estimado.
 *
 * @param available largura livre entre os ícones da esquerda e da direita da barra de status,
 * centrada na câmera; null quando não se sabe (sem limite).
 * @param fullscreen o app em primeiro plano escondeu a barra de status (vídeo, jogo).
 */
@Immutable
data class IslandSpace(
    val available: Dp? = null,
    val fullscreen: Boolean = false,
    val statusBarHeight: Dp = 32.dp,
)

/** Nível de apresentação escolhido pelo espaço e pela atividade. */
enum class IslandLevel { HIDDEN, RING, MINIMAL, COMPACT, EXPANDED }

/**
 * A lente da apresentação expandida. [top] é medido do topo da cápsula e sempre fica abaixo da
 * barra de status: a lente nunca cobre os ícones do sistema.
 */
@Immutable
data class LensTarget(val width: Dp, val height: Dp, val top: Dp)

/** Tamanho que a cápsula (e a lente, na expandida) quer ter, para a janela acompanhar. */
@Immutable
data class IslandTarget(
    val width: Dp,
    val height: Dp,
    val corner: Dp,
    val visible: Boolean,
    val withBubble: Boolean,
    val level: IslandLevel = IslandLevel.COMPACT,
    /** Na expandida, a lente que se solta da cápsula (null nos outros níveis). */
    val lens: LensTarget? = null,
) {
    /** Largura ocupada pela cápsula e pela lente. */
    val totalWidth: Dp get() = maxOf(width, lens?.width ?: 0.dp)

    /** Altura ocupada, do topo da cápsula até o fim da lente. */
    val totalHeight: Dp get() = maxOf(height, lens?.let { it.top + it.height } ?: 0.dp)
}

object IslandSizing {
    val ALERT_HEIGHT = 126.dp
    val EXPANDED_HEIGHT = 182.dp
    val LIVE_HEIGHT = 168.dp
    val BRIEF_HEIGHT = 164.dp
    val BUBBLE_GAP = 6.dp

    /** Largura máxima da lente e a folga entre ela e a barra de status. */
    val LENS_MAX = 344.dp
    val LENS_GAP = 6.dp

    /** Abaixo disto, nem o ícone cabe ao lado da câmera: fica só o anel. */
    val MINIMAL_MIN = 76.dp
    private val COMPACT_MIN = 176.dp
    private val MINIMAL_MAX = 96.dp

    /** A atividade está na apresentação expandida (aviso, sugestão ou expandida pelo toque). */
    fun isExpanded(primary: IslandActivity?, expanded: Boolean): Boolean =
        primary is IslandActivity.Alert || primary is IslandActivity.Brief ||
            (expanded && (primary is IslandActivity.Media || primary is IslandActivity.Live))

    fun target(
        state: IslandState,
        geometry: IslandGeometry,
        expanded: Boolean,
        alwaysVisible: Boolean,
        space: IslandSpace = IslandSpace(),
    ): IslandTarget {
        val primary = state.primary
        val h = geometry.pillHeight
        val ring = IslandTarget(h, h, h / 2, true, false, IslandLevel.RING)
        if (space.fullscreen) {
            // Em tela cheia (vídeo, jogo) só uma chamada continua, como um anel em volta da câmera.
            return if (primary is IslandActivity.Call) ring else IslandTarget(h, h, h / 2, false, false, IslandLevel.HIDDEN)
        }
        val available = space.available
        if (primary == null) {
            val w = minOf(geometry.idleWidth, available ?: geometry.idleWidth).coerceAtLeast(h)
            return IslandTarget(w, h, h / 2, alwaysVisible, false, IslandLevel.COMPACT)
        }
        if (isExpanded(primary, expanded)) {
            val height = when (primary) {
                is IslandActivity.Alert -> ALERT_HEIGHT
                is IslandActivity.Brief -> BRIEF_HEIGHT
                is IslandActivity.Live -> LIVE_HEIGHT
                else -> EXPANDED_HEIGHT
            }
            // A lente se solta da cápsula e fica logo abaixo da barra de status, então cabe em
            // qualquer espaço sem cobrir ícones. A cápsula volta ao tamanho de repouso em volta da
            // câmera, dentro do espaço livre.
            val pill = minOf(geometry.idleWidth, available ?: geometry.idleWidth).coerceAtLeast(h)
            val top = maxOf(space.statusBarHeight - geometry.topPadding, h) + LENS_GAP
            val lens = LensTarget(minOf(LENS_MAX, geometry.wideWidth), height, top)
            return IslandTarget(pill, h, h / 2, true, false, IslandLevel.EXPANDED, lens)
        }
        return when {
            available == null || available >= geometry.compactWidth -> {
                val bubble = state.secondary != null &&
                    (available == null || available >= geometry.compactWidth + (h + BUBBLE_GAP) * 2)
                IslandTarget(geometry.compactWidth, h, h / 2, true, bubble, IslandLevel.COMPACT)
            }
            available >= COMPACT_MIN -> IslandTarget(available, h, h / 2, true, false, IslandLevel.COMPACT)
            available >= MINIMAL_MIN -> IslandTarget(minOf(available, MINIMAL_MAX), h, h / 2, true, false, IslandLevel.MINIMAL)
            else -> ring
        }
    }
}

private val islandSpring = spring<Dp>(dampingRatio = 0.74f, stiffness = 380f)

/** A gota cai depressa e para sem quicar; depois incha em lente com um pouco de elasticidade. */
private val dropFall = spring<Float>(dampingRatio = 0.9f, stiffness = 560f)
private val dropGrow = spring<Float>(dampingRatio = 0.72f, stiffness = 260f)
private val dropShrink = spring<Float>(dampingRatio = 1f, stiffness = 460f)
private val Green = Color(0xFF34C759)
private val Orange = Color(0xFFFF9F0A)
private val Red = Color(0xFFFF453A)

/** Cor de cada atividade (Unspecified: o espectro da lente). */
private fun tintFor(activity: IslandActivity?): Color = when (activity) {
    is IslandActivity.Media -> tintOf(activity.media)
    is IslandActivity.Live -> liveColor(activity.live)
    is IslandActivity.Brief -> BriefBlue
    is IslandActivity.Call -> Green
    else -> Color.Unspecified
}

/** Guarda a última lente, para ela recolher com a animação depois que a atividade acaba. */
private class LensMemory {
    var lens: LensTarget? = null
        private set
    var activity: IslandActivity? = null
        private set

    fun keep(lens: LensTarget, activity: IslandActivity?) {
        this.lens = lens
        this.activity = activity
    }
}

/**
 * A Liquid Island: uma cápsula em volta da câmera que cresce, encolhe e se transforma como a
 * Dynamic Island do iPhone, sempre dentro do espaço livre entre os ícones da barra de status
 * ([space]). Na apresentação expandida, uma gota se solta da cápsula, desce até logo abaixo da
 * barra de status e incha numa lente oval (topo preto, faixa de luz, corpo de vidro): nenhum ícone
 * do sistema fica coberto. Tocar abre o app (ou expande); segurar alterna a expandida; deslizar
 * para cima recolhe.
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
    space: IslandSpace = IslandSpace(),
    style: IslandStyle = IslandStyle.NATIVE,
) {
    val target = IslandSizing.target(state, geometry, expanded, alwaysVisible, space)
    val width by animateDpAsState(target.width, islandSpring, label = "largura")
    val height by animateDpAsState(target.height, islandSpring, label = "altura")
    val alpha by animateFloatAsState(if (target.visible) 1f else 0f, tween(260), label = "visível")
    val primary = state.primary
    val setExpanded by rememberUpdatedState(onExpandedChange)

    // A expandida pelo toque volta sozinha ao compacto depois de um tempo sem toque.
    LaunchedEffect(expanded, primary?.id) {
        if (expanded) {
            delay(7_000)
            setExpanded(false)
        }
    }
    if (expanded && primary !is IslandActivity.Media && primary !is IslandActivity.Live) LaunchedEffect(Unit) { setExpanded(false) }

    val memory = remember { LensMemory() }
    target.lens?.let { memory.keep(it, primary) }
    val open = target.lens != null
    val lens = target.lens ?: memory.lens
    val lensActivity = if (open) primary else memory.activity
    val fall = remember { Animatable(0f) }
    val grow = remember { Animatable(0f) }
    LaunchedEffect(open) {
        if (open) {
            launch { fall.animateTo(1f, dropFall) }
            delay(60)
            grow.animateTo(1f, dropGrow)
        } else {
            launch { grow.animateTo(0f, dropShrink) }
            delay(140)
            fall.animateTo(0f, dropFall)
        }
    }

    // A faixa de luz só se move enquanto a lente está aberta (economiza bateria). Animação
    // infinita "oficial", que o modo de economia e os testes sabem pausar.
    val clock = remember { mutableFloatStateOf(0f) }
    if (open) {
        val seconds by rememberInfiniteTransition(label = "luz").animateFloat(
            initialValue = 0f,
            targetValue = 600f,
            animationSpec = infiniteRepeatable(tween(600_000, easing = LinearEasing)),
            label = "tempo",
        )
        clock.floatValue = seconds
    }
    val pillTint = tintFor(primary)
    val pill = remember { IslandLensShape(0f) }

    Box(modifier.fillMaxWidth().padding(top = geometry.topPadding), contentAlignment = Alignment.TopCenter) {
        if (lens != null && lensActivity != null && (fall.value > 0.002f || grow.value > 0.002f)) {
            LensDrop(
                lens = lens,
                activity = lensActivity,
                fall = fall.value,
                grow = grow.value,
                geometry = geometry,
                style = style,
                time = { clock.floatValue },
                concealAlerts = concealAlerts,
                actions = actions,
                expanded = expanded,
                setExpanded = setExpanded,
            )
        }
        Box(
            Modifier
                .graphicsLayer {
                    this.alpha = alpha
                    val s = 0.7f + 0.3f * alpha
                    scaleX = s
                    scaleY = s
                }
                .size(width, height)
                .islandFill(style, pill, { 0f }, { 0f }, pillTint, 0.3f)
                .then(
                    if (target.level == IslandLevel.RING && pillTint != Color.Unspecified) {
                        Modifier.border(2.dp, pillTint, CircleShape)
                    } else {
                        Modifier
                    },
                )
                .clip(pill)
                .semantics { contentDescription = "Liquid Island" }
                .pointerInput(primary?.id, expanded, open) {
                    detectTapGestures(
                        onTap = {
                            when {
                                open && expanded -> setExpanded(false)
                                open && primary is IslandActivity.Alert -> actions.onDismiss()
                                open -> Unit
                                primary is IslandActivity.Media || primary is IslandActivity.Live -> setExpanded(true)
                                primary is IslandActivity.Call -> actions.onOpenNotification(primary.item)
                                primary is IslandActivity.Timer -> actions.onOpenNotification(primary.item)
                                primary is IslandActivity.Brief -> Unit
                                else -> actions.onDismiss()
                            }
                        },
                        onLongPress = {
                            if (primary is IslandActivity.Media || primary is IslandActivity.Live) setExpanded(!expanded)
                        },
                    )
                }
                .pointerInput(primary?.id, expanded) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (total < -24.dp.toPx()) {
                                when {
                                    primary is IslandActivity.Brief -> actions.onBriefLater(primary.suggestion)
                                    expanded -> setExpanded(false)
                                    else -> actions.onDismiss()
                                }
                            } else if (total > 24.dp.toPx() && (primary is IslandActivity.Media || primary is IslandActivity.Live)) {
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
                targetState = Triple(primary?.id, target.level, primary),
                contentKey = { it.first to it.second },
                transitionSpec = {
                    (fadeIn(tween(240, delayMillis = 110)) + scaleIn(tween(320, delayMillis = 60), initialScale = 0.9f)) togetherWith
                        (fadeOut(tween(110)) + scaleOut(tween(160), targetScale = 0.94f))
                },
                label = "conteúdo",
            ) { (_, level, activity) ->
                when {
                    activity == null || level == IslandLevel.RING || level == IslandLevel.HIDDEN || level == IslandLevel.EXPANDED ->
                        Spacer(Modifier.fillMaxSize())
                    level == IslandLevel.MINIMAL -> Box(Modifier.fillMaxSize().padding(horizontal = 9.dp), contentAlignment = Alignment.CenterStart) {
                        MinimalGlyph(activity)
                    }
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
                Modifier.size(geometry.pillHeight).clip(CircleShape).background(if (style == IslandStyle.GLASS) IslandPalette.Glass else Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                state.secondary?.let { MinimalGlyph(it) }
            }
        }
    }
}

/**
 * A gota: nasce atrás da cápsula, cai até logo abaixo da barra de status (alongando-se um pouco,
 * como um pingo) e incha até virar a lente. O conteúdo é medido no tamanho final e a forma o revela
 * enquanto cresce; ao recolher, o caminho é o inverso.
 */
@Composable
private fun LensDrop(
    lens: LensTarget,
    activity: IslandActivity,
    fall: Float,
    grow: Float,
    geometry: IslandGeometry,
    style: IslandStyle,
    time: () -> Float,
    concealAlerts: Boolean,
    actions: IslandActions,
    expanded: Boolean,
    setExpanded: (Boolean) -> Unit,
) {
    val lensW by animateDpAsState(lens.width, islandSpring, label = "lente largura")
    val lensH by animateDpAsState(lens.height, islandSpring, label = "lente altura")
    val lensTop by animateDpAsState(lens.top, islandSpring, label = "lente topo")
    val f = fall.coerceIn(0f, 1f)
    val g = grow.coerceIn(0f, 1.04f)
    val swell = g.coerceIn(0f, 1f)
    val drop = geometry.pillHeight * 0.78f
    val stretch = sin(PI * f).toFloat() * (1f - swell)
    val w = lerp(drop * (1f - 0.12f * stretch), lensW, g)
    val h = lerp(drop * (1f + 0.34f * stretch), lensH, g)
    val y = lerp((geometry.pillHeight - drop) / 2, lensTop, f)
    val shape = IslandLensShape(swell)
    val contentAlpha = ((g - 0.6f) / 0.4f).coerceIn(0f, 1f)
    Box(
        Modifier
            .offset(y = y)
            .size(w, h)
            .islandFill(style, shape, { swell }, time, tintFor(activity), LensLayout.band(lensH))
            .clip(shape)
            .semantics { contentDescription = LENS_DESCRIPTION }
            .pointerInput(activity.id) {
                detectTapGestures(
                    onTap = {
                        when (activity) {
                            is IslandActivity.Media -> actions.onMediaOpen()
                            is IslandActivity.Live -> actions.onOpenNotification(activity.live.item)
                            is IslandActivity.Alert -> actions.onOpenNotification(activity.item)
                            else -> Unit
                        }
                    },
                )
            }
            .pointerInput(activity.id, expanded) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total < -24.dp.toPx()) {
                            when {
                                activity is IslandActivity.Brief -> actions.onBriefLater(activity.suggestion)
                                expanded -> setExpanded(false)
                                else -> actions.onDismiss()
                            }
                        }
                    },
                ) { change, amount ->
                    change.consume()
                    total += amount
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        if (contentAlpha > 0f) {
            Box(
                Modifier
                    .wrapContentSize(Alignment.TopCenter, unbounded = true)
                    .requiredSize(lensW, lensH)
                    .graphicsLayer {
                        alpha = contentAlpha
                        val s = 0.96f + 0.04f * contentAlpha
                        scaleX = s
                        scaleY = s
                    },
            ) {
                AnimatedContent(
                    targetState = activity,
                    contentKey = { it.id },
                    transitionSpec = { fadeIn(tween(220, delayMillis = 90)) togetherWith fadeOut(tween(110)) },
                    label = "lente",
                ) { act ->
                    when (act) {
                        is IslandActivity.Alert -> AlertLens(act.item, concealAlerts)
                        is IslandActivity.Media -> MediaLens(act.media, actions)
                        is IslandActivity.Live -> LiveExpanded(act.live)
                        is IslandActivity.Brief -> BriefExpanded(
                            act.suggestion,
                            onAccept = { actions.onBriefAccept(act.suggestion) },
                            onLater = { actions.onBriefLater(act.suggestion) },
                        )
                        else -> Spacer(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

/** Descrição da lente para acessibilidade (e para os testes a encontrarem). */
internal const val LENS_DESCRIPTION = "Liquid Island expandida"

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
            is IslandActivity.Live -> Box(Modifier.weight(1f).fillMaxHeight()) { LiveCompact(activity.live) }
            is IslandActivity.Brief -> Box(Modifier.weight(1f).fillMaxHeight()) { BriefCompact(activity.suggestion) }
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
        is IslandActivity.Live -> LiveMinimal(activity.live)
        is IslandActivity.Brief -> Icon(Icons.Rounded.Event, contentDescription = null, tint = BriefBlue, modifier = Modifier.size(16.dp))
        else -> Unit
    }
}

// --- Aviso de notificação e música expandida ---------------------------------------------------

/** Aviso na lente: app no topo, título e texto no corpo (só o app quando bloqueado). */
@Composable
private fun AlertLens(item: NotificationItem, conceal: Boolean) {
    Column(Modifier.fillMaxSize()) {
        LensHeader(
            leading = { AppGlyph(item.appIcon, item.appLabel, 18.dp) },
            label = item.appLabel,
            chip = "agora",
        )
        Row(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = LensLayout.SIDE, end = LensLayout.SIDE, top = LensLayout.BODY_GAP, bottom = LensLayout.BOTTOM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val large = if (conceal) null else item.largeIcon
            if (large != null) {
                Image(large, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)))
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (conceal) "Nova notificação" else item.title.ifBlank { item.appLabel },
                    style = islandText(15.sp, FontWeight.SemiBold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!conceal && item.text.isNotBlank()) {
                    Text(
                        item.text,
                        style = islandText(13.sp, FontWeight.Normal),
                        color = Color.White.copy(alpha = 0.68f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp,
                    )
                }
            }
        }
    }
}

/** Ícone do app em quadrado arredondado, ou a inicial quando não há ícone. */
@Composable
private fun AppGlyph(icon: androidx.compose.ui.graphics.ImageBitmap?, label: String, size: Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(Color(0xFF2C2C2E)), contentAlignment = Alignment.Center) {
        if (icon != null) {
            Image(icon, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(label.take(1).uppercase(), style = islandText(size.value.times(0.55f).sp, FontWeight.Bold), color = Color.White)
        }
    }
}

/** Música na lente: o estado no topo; capa, título, progresso e controles no corpo. */
@Composable
private fun MediaLens(media: MediaState, actions: IslandActions) {
    val tint = tintOf(media)
    Column(Modifier.fillMaxSize()) {
        LensHeader(
            leading = { Waveform(media.isPlaying, tint, Modifier.size(width = 18.dp, height = 13.dp)) },
            label = if (media.isPlaying) "Tocando agora" else "Pausado",
            chip = null,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = LensLayout.SIDE, end = LensLayout.SIDE, top = LensLayout.BODY_GAP, bottom = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.pointerInput(Unit) { detectTapGestures { actions.onMediaOpen() } }) { Artwork(media, 44.dp, 12.dp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(media.title.ifBlank { "Tocando" }, style = islandText(16.sp, FontWeight.SemiBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (media.artist.isNotBlank()) {
                        Text(media.artist, style = islandText(14.sp, FontWeight.Normal), color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Box(Modifier.padding(horizontal = 6.dp)) { Progress(media) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                ControlButton(Icons.Rounded.SkipPrevious, "Anterior", 26.dp, media.canSkipPrevious, actions.onPrevious)
                ControlButton(
                    if (media.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (media.isPlaying) "Pausar" else "Tocar",
                    34.dp,
                    true,
                    actions.onPlayPause,
                )
                ControlButton(Icons.Rounded.SkipNext, "Próxima", 26.dp, media.canSkipNext, actions.onNext)
            }
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

internal fun tintOf(media: MediaState): Color = media.tint?.let { Color(it) }?.let { c ->
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

internal fun islandText(size: androidx.compose.ui.unit.TextUnit, weight: FontWeight) =
    TextStyle(fontFamily = UiFont, fontSize = size, fontWeight = weight, fontFeatureSettings = "tnum")
