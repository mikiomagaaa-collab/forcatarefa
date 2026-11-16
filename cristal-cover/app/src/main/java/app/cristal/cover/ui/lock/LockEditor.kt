package app.cristal.cover.ui.lock

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.cristal.cover.data.prefs.ClockBounds
import app.cristal.cover.data.prefs.ClockFont
import app.cristal.cover.data.prefs.ClockLayout
import app.cristal.cover.data.prefs.Settings
import app.cristal.cover.ui.design.Liquid
import app.cristal.cover.ui.design.LiquidButton
import app.cristal.cover.ui.design.LiquidButtonStyle
import app.cristal.cover.ui.design.LiquidChip
import app.cristal.cover.ui.design.LiquidColorPicker
import app.cristal.cover.ui.design.LiquidListGroup
import app.cristal.cover.ui.design.LiquidListRow
import app.cristal.cover.ui.design.LiquidQuickColors
import app.cristal.cover.ui.design.LiquidSectionHeader
import app.cristal.cover.ui.design.LiquidSegmented
import app.cristal.cover.ui.design.LiquidSlider
import app.cristal.cover.ui.design.LiquidToggle
import app.cristal.cover.ui.design.LiquidType
import app.cristal.cover.ui.glass.rememberHaptic
import app.cristal.cover.ui.settings.ClockFontPicker
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Um relógio sendo editado. X e Y em fração da tela, Z de 0 (atrás da foto) a 1 (na frente). */
@Immutable
data class ClockDraft(
    val x: Float,
    val y: Float,
    val z: Float,
    val scale: Float,
    val font: ClockFont,
    val color: Int,
    val glass: Boolean,
) {
    fun moved(dx: Float, dy: Float): ClockDraft = copy(
        x = (x + dx).coerceIn(-ClockBounds.X, ClockBounds.X),
        y = (y + dy).coerceIn(ClockBounds.Y_MIN, ClockBounds.Y_MAX),
    )
}

/** Rascunho do editor: os dois relógios (tela interna e externa) até "Concluído". */
@Immutable
data class EditorDraft(val inner: ClockDraft, val cover: ClockDraft) {
    fun of(cover: Boolean): ClockDraft = if (cover) this.cover else inner

    fun with(cover: Boolean, draft: ClockDraft): EditorDraft = if (cover) copy(cover = draft) else copy(inner = draft)

    /** As configurações como ficariam com o rascunho (usado na prévia e para salvar). */
    fun applyTo(s: Settings): Settings = s.copy(
        lockClockX = inner.x, lockClockY = inner.y, lockClockZ = inner.z, lockClockScale = inner.scale,
        lockClockFont = inner.font, lockClockColor = inner.color, lockClockGlass = inner.glass,
        coverClockX = cover.x, coverClockY = cover.y, coverClockZ = cover.z, coverClockScale = cover.scale,
        coverClockFont = cover.font, coverClockColor = cover.color, coverClockGlass = cover.glass,
    )

    companion object {
        fun from(s: Settings) = EditorDraft(
            inner = ClockDraft(s.lockClockX, s.lockClockY, s.lockClockZ, s.lockClockScale, s.lockClockFont, s.lockClockColor, s.lockClockGlass),
            cover = ClockDraft(s.coverClockX, s.coverClockY, s.coverClockZ, s.coverClockScale, s.coverClockFont, s.coverClockColor, s.coverClockGlass),
        )
    }
}

/** Tamanho real de cada tela do Razr 40 Ultra, em dp: a prévia é desenhada nele e reduzida. */
internal val InnerScreen = DpSize(411.dp, 1004.dp)
internal val CoverScreen = DpSize(422.dp, 426.dp)

/** Ímã: perto do centro (X) ou da posição original (Y), o relógio encaixa com um toque de vibração. */
internal object ClockSnap {
    const val X = 0.014f
    const val Y = 0.009f

    fun snap(raw: Float, threshold: Float): Float = if (abs(raw) < threshold) 0f else raw
}

class LockEditorActions(
    val onCancel: () -> Unit,
    val onDone: (EditorDraft) -> Unit,
    val onOpenCollections: () -> Unit,
    val onOpenWallpaper: (cover: Boolean) -> Unit,
)

/**
 * Editor da tela de bloqueio: a prévia real das duas telas, com o relógio arrastável em X e Y,
 * profundidade (Z), tamanho, fonte, cor e vidro. Nada é salvo até "Concluído".
 */
@Composable
fun LockEditorScreen(
    state: LockUiState,
    initial: Settings,
    startOnCover: Boolean,
    actions: LockEditorActions,
) {
    var onCover by remember { mutableStateOf(startOnCover) }
    var draft by remember { mutableStateOf(EditorDraft.from(initial)) }
    var clockRect by remember { mutableStateOf<Rect?>(null) }
    var previewRect by remember { mutableStateOf<Rect?>(null) }
    var raw by remember { mutableStateOf(Offset.Zero) }
    var showPicker by remember { mutableStateOf(false) }
    val haptic = rememberHaptic()
    val colors = Liquid.colors
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val density = LocalDensity.current
    val target = if (onCover) CoverScreen else InnerScreen
    val current = draft.of(onCover)
    val currentDraft by rememberUpdatedState(current)
    val previewState = state.copy(settings = draft.applyTo(state.settings)).forDisplay(onCover)

    fun update(next: ClockDraft) {
        draft = draft.with(onCover, next)
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background)) {
        val compact = maxHeight < 600.dp
        Column(Modifier.fillMaxSize().padding(insets)) {
            // Barra superior: cancelar, tela, concluído.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Cancelar",
                    style = LiquidType.body,
                    color = colors.accent,
                    modifier = Modifier.pointerInput(Unit) { detectTapGestures { actions.onCancel() } }.padding(6.dp),
                )
                LiquidSegmented(
                    listOf("Interna", "Externa"),
                    if (onCover) 1 else 0,
                    {
                        onCover = it == 1
                        clockRect = null
                        raw = Offset(draft.of(onCover).x, draft.of(onCover).y)
                    },
                    Modifier.weight(1f),
                )
                LiquidButton("Concluído", { actions.onDone(draft) }, Modifier.heightIn(max = 40.dp))
            }

            // Prévia na proporção real da tela escolhida, com o relógio arrastável.
            Box(
                Modifier
                    .weight(if (compact) 0.55f else 1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                PreviewFrame(
                    target = target,
                    onCover = onCover,
                    state = previewState,
                    onClockBounds = { clockRect = it },
                    onPreviewBounds = { previewRect = it },
                )
            }

            // Painel de ajustes.
            Column(
                Modifier
                    .weight(if (compact) 0.45f else 0.82f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                PositionRow(
                    draft = current,
                    target = target,
                    onNudge = { dxDp, dyDp ->
                        haptic()
                        update(current.moved(dxDp / target.width.value, dyDp / target.height.value))
                        raw = Offset(draft.of(onCover).x, draft.of(onCover).y)
                    },
                    onCenter = {
                        haptic()
                        update(current.copy(x = 0f))
                        raw = Offset(0f, raw.y)
                    },
                    onReset = {
                        haptic()
                        update(current.copy(x = 0f, y = 0f, z = 0.25f, scale = 1f))
                        raw = Offset.Zero
                    },
                )
                LiquidSectionHeader("Profundidade")
                LiquidListGroup {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Layers, null, tint = colors.contentSecondary, modifier = Modifier.size(18.dp))
                            Text(
                                if (current.z < 0.5f) "Atrás do objeto da foto" else "Na frente da foto",
                                style = LiquidType.subhead.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.content,
                                modifier = Modifier.padding(start = 8.dp).weight(1f),
                            )
                            Text("Z ${(current.z * 100).roundToInt()}", style = LiquidType.footnote, color = colors.contentSecondary)
                        }
                        LiquidSlider(
                            value = current.z,
                            onValueChange = { v ->
                                val crossed = (v >= 0.5f) != (current.z >= 0.5f)
                                if (crossed) haptic()
                                update(current.copy(z = v))
                            },
                            label = "Profundidade do relógio",
                            modifier = Modifier.padding(top = 10.dp),
                        )
                        if (previewState.subject == null) {
                            Text(
                                "Esta foto ainda não tem o objeto recortado. Ligue a Profundidade no Wallpaper Studio para o relógio " +
                                    "poder passar por trás dele.",
                                style = LiquidType.footnote,
                                color = colors.contentSecondary,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
                LiquidSectionHeader("Tamanho")
                LiquidListGroup {
                    Column(Modifier.padding(14.dp)) {
                        LiquidSlider(
                            value = current.scale,
                            onValueChange = { update(current.copy(scale = it)) },
                            valueRange = 0.6f..1.8f,
                            label = "Tamanho do relógio",
                        )
                    }
                }
                LiquidSectionHeader("Fonte")
                CompositionLocalProvider(LocalContentColor provides colors.content) {
                    ClockFontPicker(current.font, ClockLayout.AUTO) {
                        haptic()
                        update(current.copy(font = it))
                    }
                }
                LiquidSectionHeader("Cor")
                LiquidListGroup {
                    Column(Modifier.padding(14.dp)) {
                        ColorRow(
                            selected = current.color,
                            onSelect = { update(current.copy(color = it)) },
                            onSpectrum = { showPicker = !showPicker },
                            spectrumOpen = showPicker,
                        )
                        AnimatedVisibility(showPicker) {
                            LiquidColorPicker(
                                initial = if (current.color == 0) Color.White else Color(current.color),
                                onColorChange = { update(current.copy(color = it.toArgb())) },
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    }
                }
                LiquidSectionHeader("Estilo")
                LiquidListGroup {
                    LiquidListRow("Relógio de vidro", divider = false) {
                        LiquidToggle(current.glass, { update(current.copy(glass = it)) })
                    }
                    LiquidListRow("Papel de parede desta tela", icon = Icons.Rounded.Wallpaper, iconBackground = Color(0xFFC04BD8), chevron = true, onClick = { actions.onOpenWallpaper(onCover) })
                    LiquidListRow("Coleções", icon = Icons.Rounded.Collections, iconBackground = Color(0xFF3B6CF6), chevron = true, onClick = actions.onOpenCollections)
                }
                Text(
                    "Arraste o relógio na prévia. Perto do centro e da posição original ele encaixa sozinho; as setas movem 1 dp (segure para 10).",
                    style = LiquidType.footnote,
                    color = colors.contentSecondary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 24.dp, start = 4.dp, end = 4.dp),
                )
            }
        }

        // Camada de arrasto por cima da prévia (coordenadas da janela inteira).
        val preview = previewRect
        if (preview != null) {
            Box(
                Modifier
                    .offset { IntOffset(preview.left.roundToInt(), preview.top.roundToInt()) }
                    .size(with(density) { preview.width.toDp() }, with(density) { preview.height.toDp() })
                    .semantics { contentDescription = "Prévia: arraste para mover o relógio" }
                    .pointerInput(onCover, preview) {
                        detectDragGestures(
                            onDragStart = { raw = Offset(currentDraft.x, currentDraft.y) },
                        ) { change, drag ->
                            change.consume()
                            val next = Offset(
                                (raw.x + drag.x / preview.width).coerceIn(-ClockBounds.X, ClockBounds.X),
                                (raw.y + drag.y / preview.height).coerceIn(ClockBounds.Y_MIN, ClockBounds.Y_MAX),
                            )
                            raw = next
                            val snappedX = ClockSnap.snap(next.x, ClockSnap.X)
                            val snappedY = ClockSnap.snap(next.y, ClockSnap.Y)
                            val before = currentDraft
                            if ((snappedX == 0f && before.x != 0f) || (snappedY == 0f && before.y != 0f)) haptic()
                            update(before.copy(x = snappedX, y = snappedY))
                        }
                    }
                    .drawBehind {
                        // Guias quando o relógio está encaixado no centro ou na posição original.
                        val guide = Color.White.copy(alpha = 0.55f)
                        val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                        if (current.x == 0f) {
                            drawLine(guide, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1.5f, pathEffect = dash)
                        }
                    },
            )
        }
        val clock = clockRect
        if (clock != null) {
            // Moldura em volta do relógio, que acompanha o arrasto.
            Box(
                Modifier
                    .offset { IntOffset(clock.left.roundToInt() - 6, clock.top.roundToInt() - 6) }
                    .size(with(density) { (clock.width + 12).toDp() }, with(density) { (clock.height + 12).toDp() })
                    .border(1.5.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(14.dp)),
            )
        }
    }
}

/** A prévia do bloqueio desenhada no tamanho real da tela e reduzida para caber. */
@Composable
private fun PreviewFrame(
    target: DpSize,
    onCover: Boolean,
    state: LockUiState,
    onClockBounds: (Rect) -> Unit,
    onPreviewBounds: (Rect) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val scale = min(maxWidth / target.width, maxHeight / target.height)
        val corner: Dp = (if (onCover) 44.dp else 38.dp) * scale
        Box(
            Modifier
                .size(target.width * scale, target.height * scale)
                .clip(RoundedCornerShape(corner))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(corner))
                .onGloballyPositioned { onPreviewBounds(it.boundsInRoot()) },
        ) {
            Box(
                Modifier
                    .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                    .requiredSize(target)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = scale
                        scaleY = scale
                    },
            ) {
                LockScreen(
                    state = state,
                    secure = false,
                    resetKey = 0,
                    actions = NoLockActions,
                    coverMinimal = onCover,
                    editing = LockEditing(onClockBounds),
                )
            }
        }
    }
}

/** Ações vazias: na prévia nada destrava nem abre. */
internal val NoLockActions = LockActions({}, {}, {}, {}, {}, {}, {}, {})

@Composable
private fun PositionRow(
    draft: ClockDraft,
    target: DpSize,
    onNudge: (Float, Float) -> Unit,
    onCenter: () -> Unit,
    onReset: () -> Unit,
) {
    val colors = Liquid.colors
    LiquidSectionHeader("Posição")
    LiquidListGroup {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "X ${(draft.x * target.width.value).roundToInt()} dp",
                    style = LiquidType.headline,
                    color = colors.content,
                )
                Text(
                    "Y ${(draft.y * target.height.value).roundToInt()} dp",
                    style = LiquidType.headline,
                    color = colors.content,
                )
            }
            NudgeButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Mover para a esquerda") { onNudge(-1f, 0f) }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                NudgeButton(Icons.Rounded.KeyboardArrowUp, "Mover para cima") { onNudge(0f, -1f) }
                NudgeButton(Icons.Rounded.KeyboardArrowDown, "Mover para baixo") { onNudge(0f, 1f) }
            }
            NudgeButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Mover para a direita") { onNudge(1f, 0f) }
        }
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LiquidButton("Centralizar", onCenter, Modifier.weight(1f), style = LiquidButtonStyle.TINTED, icon = Icons.Rounded.CenterFocusStrong)
            LiquidButton("Original", onReset, Modifier.weight(1f), style = LiquidButtonStyle.TINTED, icon = Icons.Rounded.Restore)
        }
    }
}

/** Seta de ajuste fino: um toque move 1 dp; segurar move 10. */
@Composable
private fun NudgeButton(icon: ImageVector, description: String, onNudge: () -> Unit) {
    val colors = Liquid.colors
    val latest by rememberUpdatedState(onNudge)
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (colors.dark) Color(0x24FFFFFF) else Color(0x14000000))
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { latest() },
                    onLongPress = {
                        repeat(10) { latest() }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.content)
    }
}

@Composable
private fun ColorRow(selected: Int, onSelect: (Int) -> Unit, onSpectrum: () -> Unit, spectrumOpen: Boolean) {
    val haptic = rememberHaptic()
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        val quick = listOf(0) + LiquidQuickColors.drop(1).take(5).map { it.toArgb() }
        quick.forEach { argb ->
            val color = if (argb == 0) Color.White else Color(argb)
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(if (argb == selected) 3.dp else 1.dp, if (argb == selected) Liquid.colors.accent else Color.White.copy(alpha = 0.3f), CircleShape)
                    .pointerInput(argb) {
                        detectTapGestures {
                            haptic()
                            onSelect(argb)
                        }
                    },
            )
        }
        LiquidChip("Espectro", spectrumOpen, onSpectrum)
        Icon(Icons.Rounded.Palette, null, tint = Liquid.colors.contentSecondary, modifier = Modifier.width(18.dp))
    }
}
