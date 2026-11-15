package app.lily.keyboard.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.KeyboardReturn
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lily.keyboard.core.Key
import app.lily.keyboard.core.KeyCode
import app.lily.keyboard.core.KeyboardLayout
import app.lily.keyboard.core.KeyboardSettings
import app.lily.keyboard.core.KeyboardTheme
import app.lily.keyboard.engine.EnterKind
import app.lily.keyboard.engine.ShiftState
import app.lily.keyboard.engine.TracePoint
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** O que as teclas pedem para quem hospeda o teclado. */
@Stable
class KeyActions(
    /** Toque confirmado numa tecla (caractere, espaço, enter, shift, troca de página). */
    val onKey: (Key) -> Unit,
    val onDelete: () -> Unit,
    val onDeleteWord: () -> Unit,
    /** Caractere escolhido no toque longo. */
    val onAlternate: (String) -> Unit,
    val onCursor: (Int) -> Unit,
    val onGesture: (List<TracePoint>, KeyGeometry) -> Unit,
    val onSpaceLongPress: () -> Unit,
    /** Vibração/som: chamado no toque e em cada passo do cursor. */
    val onFeedback: (KeyCode) -> Unit,
)

private enum class TouchMode { TAP, ALTERNATES, CURSOR, DELETE_SWIPE, GESTURE, REPEAT, DONE }

private class Touch(val id: PointerId, var key: PlacedKey?, val down: Offset, val time: Long) {
    var mode = TouchMode.TAP
    var pos = down
    var timer: Job? = null
    var anchorX = down.x
    var altIndex = 0
    val trace = ArrayList<TracePoint>()
}

/** Popup de caracteres alternativos (toque longo). */
private class AltPopup(val key: PlacedKey, val options: List<String>, val cells: List<Rect>)

fun Long.asColor(): Color = Color(this.toInt())

/**
 * As teclas: desenhadas todas num só Canvas (rápido) e com um único tratador de toques, que cuida
 * de vários dedos ao mesmo tempo, toque longo, digitação por deslize, cursor no espaço e "deslizar
 * para apagar palavras".
 */
@Composable
fun KeysPad(
    layout: KeyboardLayout,
    shift: ShiftState,
    enter: EnterKind,
    theme: KeyboardTheme,
    settings: KeyboardSettings,
    keyHeight: Dp,
    spaceLabel: String,
    actions: KeyActions,
    modifier: Modifier = Modifier,
    gestureEnabled: Boolean = true,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 96)
    val scope = rememberCoroutineScope()
    val act by rememberUpdatedState(actions)
    val currentSettings by rememberUpdatedState(settings)
    val upper = shift != ShiftState.OFF
    val locale = remember { Locale.forLanguageTag("pt-BR") }

    val icons = KeyIcons(
        delete = rememberVectorPainter(Icons.AutoMirrored.Rounded.Backspace),
        enter = rememberVectorPainter(
            when (enter) {
                EnterKind.NEWLINE -> Icons.AutoMirrored.Rounded.KeyboardReturn
                EnterKind.SEND -> Icons.AutoMirrored.Rounded.Send
                EnterKind.SEARCH -> Icons.Rounded.Search
                EnterKind.GO, EnterKind.NEXT -> Icons.AutoMirrored.Rounded.ArrowForward
                EnterKind.DONE -> Icons.Rounded.Check
                EnterKind.PREVIOUS -> Icons.AutoMirrored.Rounded.ArrowBack
            },
        ),
    )

    val rows = layout.rows.size
    val lastRowScale = if (settings.bigBottomRow) 1.12f else 1f
    val totalHeight = keyHeight * (rows - 1 + lastRowScale) + 8.dp

    BoxWithConstraints(modifier.fillMaxWidth().height(totalHeight)) {
        val widthPx = constraints.maxWidth.toFloat()
        val geometry = remember(layout, widthPx, keyHeight, theme.keyGap, lastRowScale) {
            with(density) {
                KeyGeometry.build(
                    layout = layout,
                    width = widthPx,
                    rowHeight = keyHeight.toPx(),
                    gapX = theme.keyGap.dp.toPx(),
                    gapY = (theme.keyGap + 4f).dp.toPx(),
                    sidePadding = 3.dp.toPx(),
                    lastRowScale = lastRowScale,
                )
            }
        }
        var pressed by remember { mutableStateOf(setOf<Int>()) }
        var preview by remember { mutableStateOf<PlacedKey?>(null) }
        var popup by remember { mutableStateOf<AltPopup?>(null) }
        var popupIndex by remember { mutableIntStateOf(0) }
        val trail = remember { ArrayList<Offset>() }
        var trailVersion by remember { mutableIntStateOf(0) }

        fun labelOf(key: Key): String = if (upper && key.code == KeyCode.CHAR && key.label.length == 1) key.label.uppercase(locale) else key.label

        fun buildPopup(p: PlacedKey): AltPopup? {
            val raw = p.key.alternates
            if (raw.isEmpty()) return null
            val options = raw.map { if (upper) it.uppercase(locale) else it }
            val cellW = geometry.keyWidth * 0.92f
            val cellH = geometry.rowHeight * 0.95f
            val total = cellW * options.size
            val left = (p.rect.center.x - cellW / 2f).coerceIn(2f, (widthPx - total - 2f).coerceAtLeast(2f))
            val top = p.rect.top - cellH - 6f
            return AltPopup(p, options, options.indices.map { i -> Rect(left + i * cellW, top, left + (i + 1) * cellW, top + cellH) })
        }

        val modifierTouch = Modifier.pointerInput(geometry) {
            val touches = HashMap<PointerId, Touch>()
            fun refreshPressed() {
                pressed = touches.values.filter { it.mode == TouchMode.TAP || it.mode == TouchMode.REPEAT || it.mode == TouchMode.ALTERNATES }
                    .mapNotNull { it.key?.index }.toSet()
            }
            fun finishTap(t: Touch) {
                val k = t.key ?: return
                if (k.key.code != KeyCode.DELETE) act.onKey(k.key)
            }
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    for (change in event.changes) {
                        val id = change.id
                        when {
                            change.pressed && !change.previousPressed -> {
                                // Rolagem: um segundo dedo confirma a tecla do primeiro na hora.
                                for (other in touches.values) {
                                    if (other.mode == TouchMode.TAP && other.key?.key?.code == KeyCode.CHAR) {
                                        finishTap(other)
                                        other.mode = TouchMode.DONE
                                        other.timer?.cancel()
                                    }
                                }
                                val key = geometry.keyAt(change.position.x, change.position.y)
                                val t = Touch(id, key, change.position, change.uptimeMillis)
                                touches[id] = t
                                if (key != null) {
                                    act.onFeedback(key.key.code)
                                    if (key.key.code == KeyCode.CHAR && currentSettings.keyPreview) preview = key
                                    when (key.key.code) {
                                        KeyCode.DELETE -> {
                                            act.onDelete()
                                            t.mode = TouchMode.REPEAT
                                            t.timer = scope.launch {
                                                delay(420)
                                                var interval = 70L
                                                while (t.mode == TouchMode.REPEAT) {
                                                    act.onDelete()
                                                    delay(interval)
                                                    interval = (interval - 4).coerceAtLeast(30)
                                                }
                                            }
                                        }
                                        KeyCode.CHAR -> if (key.key.alternates.isNotEmpty()) {
                                            t.timer = scope.launch {
                                                delay(currentSettings.longPressMs.toLong().coerceIn(150, 900))
                                                if (t.mode == TouchMode.TAP) {
                                                    val p = buildPopup(key) ?: return@launch
                                                    t.mode = TouchMode.ALTERNATES
                                                    preview = null
                                                    popup = p
                                                    popupIndex = 0
                                                    t.altIndex = 0
                                                    act.onFeedback(KeyCode.SHIFT)
                                                }
                                            }
                                        }
                                        KeyCode.SPACE -> t.timer = scope.launch {
                                            delay(currentSettings.longPressMs.toLong().coerceIn(250, 900) + 150)
                                            if (t.mode == TouchMode.TAP) {
                                                t.mode = TouchMode.DONE
                                                act.onSpaceLongPress()
                                            }
                                        }
                                        else -> Unit
                                    }
                                }
                                refreshPressed()
                            }

                            !change.pressed && change.previousPressed -> {
                                val t = touches.remove(id)
                                if (t != null) {
                                    t.timer?.cancel()
                                    when (t.mode) {
                                        TouchMode.TAP -> finishTap(t)
                                        TouchMode.ALTERNATES -> popup?.let { p -> p.options.getOrNull(t.altIndex)?.let { act.onAlternate(it) } }
                                        TouchMode.GESTURE -> act.onGesture(t.trace.toList(), geometry)
                                        else -> Unit
                                    }
                                    t.mode = TouchMode.DONE
                                    if (popup != null && touches.values.none { it.mode == TouchMode.ALTERNATES }) popup = null
                                    if (trail.isNotEmpty() && touches.values.none { it.mode == TouchMode.GESTURE }) {
                                        trail.clear()
                                        trailVersion++
                                    }
                                }
                                if (touches.values.none { it.mode == TouchMode.TAP && it.key?.key?.code == KeyCode.CHAR }) preview = null
                                refreshPressed()
                            }

                            change.pressed && change.positionChanged() -> {
                                val t = touches[id] ?: continue
                                val key = t.key ?: continue
                                val pos = change.position
                                t.pos = pos
                                val dx = pos.x - t.down.x
                                val dist = hypot(dx, pos.y - t.down.y)
                                val kw = geometry.keyWidth
                                when (t.mode) {
                                    TouchMode.TAP -> when {
                                        key.key.code == KeyCode.CHAR && key.key.label.firstOrNull()?.isLetter() == true &&
                                            gestureEnabled && currentSettings.gestureTyping && touches.size == 1 && dist > kw * 0.6f -> {
                                            t.mode = TouchMode.GESTURE
                                            t.timer?.cancel()
                                            preview = null
                                            t.trace.add(TracePoint(t.down.x, t.down.y))
                                            for (h in change.historical) t.trace.add(TracePoint(h.position.x, h.position.y))
                                            t.trace.add(TracePoint(pos.x, pos.y))
                                            trail.clear()
                                            trail.add(t.down)
                                            trail.add(pos)
                                            trailVersion++
                                        }
                                        key.key.code == KeyCode.SPACE && currentSettings.spaceCursor && abs(dx) > kw * 0.4f -> {
                                            t.mode = TouchMode.CURSOR
                                            t.timer?.cancel()
                                            t.anchorX = pos.x
                                        }
                                        key.key.code == KeyCode.CHAR && dist > kw * 0.55f -> {
                                            // Escorregou para outra tecla: vale a tecla onde o dedo está.
                                            val now = geometry.keyAt(pos.x, pos.y)
                                            if (now != null && now != key && now.key.code == KeyCode.CHAR) {
                                                t.key = now
                                                t.timer?.cancel()
                                                if (currentSettings.keyPreview) preview = now
                                            }
                                        }
                                    }
                                    TouchMode.REPEAT -> if (key.key.code == KeyCode.DELETE && currentSettings.deleteSwipe && dx < -kw * 0.7f) {
                                        t.mode = TouchMode.DELETE_SWIPE
                                        t.timer?.cancel()
                                        t.anchorX = pos.x
                                        act.onDeleteWord()
                                        act.onFeedback(KeyCode.DELETE)
                                    }
                                    TouchMode.DELETE_SWIPE -> if (t.anchorX - pos.x > kw * 0.8f) {
                                        t.anchorX = pos.x
                                        act.onDeleteWord()
                                        act.onFeedback(KeyCode.DELETE)
                                    }
                                    TouchMode.CURSOR -> {
                                        val step = kw * 0.34f
                                        val steps = ((pos.x - t.anchorX) / step).toInt()
                                        if (steps != 0) {
                                            t.anchorX += steps * step
                                            act.onCursor(steps)
                                            act.onFeedback(KeyCode.SPACE)
                                        }
                                    }
                                    TouchMode.ALTERNATES -> popup?.let { p ->
                                        val i = p.cells.indexOfFirst { pos.x >= it.left && pos.x < it.right }
                                        val idx = when {
                                            i >= 0 -> i
                                            pos.x < p.cells.first().left -> 0
                                            else -> p.cells.lastIndex
                                        }
                                        if (idx != t.altIndex) {
                                            t.altIndex = idx
                                            popupIndex = idx
                                        }
                                    }
                                    TouchMode.GESTURE -> {
                                        for (h in change.historical) t.trace.add(TracePoint(h.position.x, h.position.y))
                                        t.trace.add(TracePoint(pos.x, pos.y))
                                        trail.add(pos)
                                        if (trail.size > 90) trail.removeAt(0)
                                        trailVersion++
                                    }
                                    TouchMode.DONE -> Unit
                                }
                                refreshPressed()
                            }
                        }
                        change.consume()
                    }
                }
            }
        }

        val styleKey = TextStyle(color = theme.textColor.asColor(), fontSize = (22 * settings.fontScale).sp, fontWeight = FontWeight.Normal)
        val styleFn = TextStyle(color = theme.textColor.asColor(), fontSize = (14 * settings.fontScale).sp, fontWeight = FontWeight.Medium)
        val styleHint = TextStyle(color = theme.symbolColor.asColor(), fontSize = (10 * settings.fontScale).sp)
        val styleSpace = TextStyle(color = theme.symbolColor.asColor(), fontSize = (12 * settings.fontScale).sp)
        val stylePreview = TextStyle(color = theme.textColor.asColor(), fontSize = (30 * settings.fontScale).sp, fontWeight = FontWeight.Medium)

        androidx.compose.foundation.layout.Box(
            Modifier
                .fillMaxWidth()
                .height(totalHeight)
                .semantics { contentDescription = "Teclado" }
                .then(modifierTouch)
                .drawWithCache {
                    val texts = HashMap<String, TextLayoutResult>()
                    fun measure(text: String, style: TextStyle) =
                        texts.getOrPut(text + "|" + style.fontSize) { measurer.measure(text, style) }
                    val radius = theme.cornerRadius.dp.toPx()
                    onDrawBehind {
                        @Suppress("UNUSED_EXPRESSION") trailVersion
                        val pressedNow = pressed
                        for (p in geometry.keys) {
                            drawKey(p, theme, pressedNow.contains(p.index), shift, radius, icons, spaceLabel) { text, fn ->
                                if (text == spaceLabel && p.key.code == KeyCode.SPACE) measure(text, styleSpace)
                                else measure(text, if (fn) styleFn else styleKey)
                            }
                            val hint = p.key.hint
                            if (hint != null && p.key.code == KeyCode.CHAR) {
                                val h = measure(hint, styleHint)
                                drawText(h, topLeft = Offset(p.rect.right - h.size.width - 5.dp.toPx(), p.rect.top + 3.dp.toPx()))
                            }
                        }
                        // Rastro da digitação por deslize.
                        if (settings.gestureTrail && trail.size > 1) {
                            val path = Path().apply {
                                moveTo(trail[0].x, trail[0].y)
                                for (i in 1 until trail.size) lineTo(trail[i].x, trail[i].y)
                            }
                            drawPath(
                                path,
                                theme.accentColor.asColor().copy(alpha = 0.75f),
                                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                            )
                        }
                        // Prévia da tecla tocada.
                        preview?.let { p ->
                            val w = p.rect.width * 1.25f
                            val h = p.rect.height * 1.3f
                            val r = Rect(p.rect.center.x - w / 2, p.rect.top - h * 0.78f, p.rect.center.x + w / 2, p.rect.top - h * 0.78f + h)
                            drawBubble(r, theme, radius)
                            val t = measure(labelOf(p.key), stylePreview)
                            drawText(t, topLeft = Offset(r.center.x - t.size.width / 2f, r.top + (r.height * 0.62f - t.size.height) / 2f + 4f))
                        }
                        popup?.let { p ->
                            val bounds = Rect(p.cells.first().left, p.cells.first().top, p.cells.last().right, p.cells.last().bottom)
                            drawBubble(bounds.inflate(4f), theme, radius)
                            for ((i, cell) in p.cells.withIndex()) {
                                if (i == popupIndex) {
                                    drawRoundRect(
                                        theme.accentColor.asColor(),
                                        topLeft = cell.topLeft + Offset(3f, 3f),
                                        size = Size(cell.width - 6f, cell.height - 6f),
                                        cornerRadius = CornerRadius(radius * 0.8f),
                                    )
                                }
                                val t = measure(p.options[i], styleKey)
                                drawText(
                                    t,
                                    color = if (i == popupIndex) onAccent(theme) else theme.textColor.asColor(),
                                    topLeft = Offset(cell.center.x - t.size.width / 2f, cell.center.y - t.size.height / 2f),
                                )
                            }
                        }
                    }
                },
        )
    }
}

@Stable
private class KeyIcons(val delete: VectorPainter, val enter: VectorPainter)

private fun onAccent(theme: KeyboardTheme): Color {
    val c = theme.accentColor.asColor()
    val lum = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
    return if (lum > 0.6f) Color(0xFF15131F) else Color.White
}

private fun DrawScope.drawBubble(r: Rect, theme: KeyboardTheme, radius: Float) {
    val base = if (theme.dark) Color(0xFF2B2836) else Color.White
    drawRoundRect(Color.Black.copy(alpha = 0.28f), topLeft = r.topLeft + Offset(0f, 3f), size = r.size, cornerRadius = CornerRadius(radius * 1.2f))
    drawRoundRect(base, topLeft = r.topLeft, size = r.size, cornerRadius = CornerRadius(radius * 1.2f))
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent), startY = r.top, endY = r.bottom),
        topLeft = r.topLeft,
        size = r.size,
        cornerRadius = CornerRadius(radius * 1.2f),
    )
}

/** Desenha uma tecla com o material do tema (vidro: reflexo no alto, borda clara e sombra). */
private fun DrawScope.drawKey(
    p: PlacedKey,
    theme: KeyboardTheme,
    pressed: Boolean,
    shift: ShiftState,
    radius: Float,
    icons: KeyIcons,
    spaceLabel: String,
    measure: (String, Boolean) -> TextLayoutResult,
) {
    val key = p.key
    val r = p.rect
    val functional = key.code in setOf(KeyCode.SHIFT, KeyCode.DELETE, KeyCode.TO_SYMBOLS, KeyCode.TO_LETTERS, KeyCode.TO_MORE_SYMBOLS, KeyCode.EMOJI)
    val accent = theme.accentColor.asColor()
    var fill = when {
        key.code == KeyCode.ENTER -> accent
        key.code == KeyCode.SHIFT && shift != ShiftState.OFF -> accent.copy(alpha = 0.85f)
        functional -> theme.functionKeyColor.asColor()
        else -> theme.keyColor.asColor()
    }
    fill = fill.copy(alpha = fill.alpha * theme.keyAlpha.coerceIn(0f, 1f))
    val corner = CornerRadius(radius)
    val scaleBy = if (pressed) 0.95f else 1f

    scale(scaleBy, scaleBy, pivot = r.center) {
        val glass = theme.glass.coerceIn(0f, 1f)
        if (glass > 0f && fill.alpha > 0.01f) {
            drawRoundRect(Color.Black.copy(alpha = 0.16f * glass), topLeft = r.topLeft + Offset(0f, 1.5f), size = r.size, cornerRadius = corner)
        }
        if (fill.alpha > 0.005f) drawRoundRect(fill, topLeft = r.topLeft, size = r.size, cornerRadius = corner)
        if (glass > 0f && fill.alpha > 0.01f) {
            drawRoundRect(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.20f * glass), Color.White.copy(alpha = 0.02f * glass), Color.Transparent),
                    startY = r.top,
                    endY = r.bottom,
                ),
                topLeft = r.topLeft,
                size = r.size,
                cornerRadius = corner,
            )
            drawRoundRect(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.42f * glass), Color.White.copy(alpha = 0.06f * glass), Color.White.copy(alpha = 0.16f * glass)),
                    startY = r.top,
                    endY = r.bottom,
                ),
                topLeft = r.topLeft + Offset(0.5f, 0.5f),
                size = Size(r.width - 1f, r.height - 1f),
                cornerRadius = corner,
                style = Stroke(width = 1.dp.toPx()),
            )
        } else if (theme.borders) {
            drawRoundRect(
                theme.textColor.asColor().copy(alpha = 0.10f),
                topLeft = r.topLeft,
                size = r.size,
                cornerRadius = corner,
                style = Stroke(width = 0.8.dp.toPx()),
            )
        }
        if (pressed) {
            drawRoundRect(
                if (theme.dark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.08f),
                topLeft = r.topLeft,
                size = r.size,
                cornerRadius = corner,
            )
        }

        val ink = if (key.code == KeyCode.ENTER || (key.code == KeyCode.SHIFT && shift != ShiftState.OFF)) onAccent(theme) else theme.textColor.asColor()
        val iconSize = (r.height * 0.42f).coerceAtMost(r.width * 0.6f)
        when (key.code) {
            KeyCode.DELETE -> drawIcon(icons.delete, r, iconSize, ink)
            KeyCode.ENTER -> drawIcon(icons.enter, r, iconSize, ink)
            KeyCode.SHIFT -> drawShift(r, iconSize, ink, shift)
            KeyCode.SPACE -> {
                val t = measure(spaceLabel, false)
                drawText(t, topLeft = Offset(r.center.x - t.size.width / 2f, r.center.y - t.size.height / 2f))
            }
            else -> {
                val label = if (shift != ShiftState.OFF && key.code == KeyCode.CHAR && key.label.length == 1) key.label.uppercase() else key.label
                val t = measure(label, key.code != KeyCode.CHAR || label.length > 2)
                drawText(t, color = ink, topLeft = Offset(r.center.x - t.size.width / 2f, r.center.y - t.size.height / 2f))
            }
        }
    }
}

private fun DrawScope.drawIcon(painter: VectorPainter, r: Rect, size: Float, color: Color) {
    translate(r.center.x - size / 2f, r.center.y - size / 2f) {
        with(painter) { draw(Size(size, size), colorFilter = ColorFilter.tint(color)) }
    }
}

/** Seta do shift: contorno (desligado), cheia (próxima letra) ou cheia com traço (caps lock). */
private fun DrawScope.drawShift(r: Rect, size: Float, color: Color, shift: ShiftState) {
    val cx = r.center.x
    val top = r.center.y - size * 0.5f
    val w = size * 0.9f
    val path = Path().apply {
        moveTo(cx, top)
        lineTo(cx + w / 2f, top + size * 0.48f)
        lineTo(cx + w * 0.22f, top + size * 0.48f)
        lineTo(cx + w * 0.22f, top + size * 0.82f)
        lineTo(cx - w * 0.22f, top + size * 0.82f)
        lineTo(cx - w * 0.22f, top + size * 0.48f)
        lineTo(cx - w / 2f, top + size * 0.48f)
        close()
    }
    if (shift == ShiftState.OFF) {
        drawPath(path, color, style = Stroke(width = 1.6.dp.toPx(), join = StrokeJoin.Round))
    } else {
        drawPath(path, color)
    }
    if (shift == ShiftState.LOCKED) {
        drawLine(color, Offset(cx - w * 0.3f, top + size * 0.98f), Offset(cx + w * 0.3f, top + size * 0.98f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    }
}
