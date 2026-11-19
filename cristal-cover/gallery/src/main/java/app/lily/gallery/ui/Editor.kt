package app.lily.gallery.ui

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.lily.gallery.LilyAi
import app.lily.gallery.data.Aspect
import app.lily.gallery.data.EditState
import app.lily.gallery.data.Edits
import app.lily.gallery.data.Filter
import app.lily.gallery.data.MediaItem
import app.lily.gallery.data.Thumbs
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun turn(src: Bitmap, rotation: Int, flip: Boolean): Bitmap {
    if (rotation % 360 == 0 && !flip) return src
    val m = android.graphics.Matrix().apply {
        if (flip) postScale(-1f, 1f)
        postRotate(rotation.toFloat())
    }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}

private fun composeMatrix(s: EditState): ColorMatrix = ColorMatrix(Edits.colorMatrix(s).array)

/** O maior corte centralizado com a proporção pedida (em coordenadas de 0 a 1). */
private fun fitAspect(aspect: Aspect, imgW: Int, imgH: Int): RectF {
    val r = when (val a = aspect.ratio) {
        null -> return RectF(0f, 0f, 1f, 1f)
        -1f -> imgW.toFloat() / imgH
        else -> a
    }
    var w = 1f
    var h = imgW / (imgH * r)
    if (h > 1f) {
        h = 1f
        w = imgH * r / imgW
    }
    return RectF((1 - w) / 2, (1 - h) / 2, (1 + w) / 2, (1 + h) / 2)
}

@Composable
fun EditorScreen(nav: GalleryNav, id: Long) {
    val c = G.colors
    val repo = LocalRepo.current
    val requests = LocalRequests.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val item = remember(id) { repo.byId(id) }
    if (item == null) {
        Column(Modifier.fillMaxSize()) {
            GTopBar("Editar", onBack = { nav.back() })
            Text("Esta foto não está mais na biblioteca.", style = GType.callout, color = c.secondary, modifier = Modifier.padding(24.dp))
        }
        return
    }
    var state by remember { mutableStateOf(EditState()) }
    var tab by remember { mutableIntStateOf(0) }
    var aspect by remember { mutableStateOf(Aspect.FREE) }
    var saving by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    var leave by remember { mutableStateOf(false) }
    val preview by produceState<Bitmap?>(null, id) {
        value = withContext(Dispatchers.IO) { runCatching { Edits.loadFull(context, item.uri, 1600) }.getOrNull() }
    }
    val turned = remember(preview, state.rotation, state.flip) { preview?.let { turn(it, state.rotation, state.flip) } }

    fun save(overwrite: Boolean) {
        saving = "Salvando…"
        scope.launch {
            try {
                val out = withContext(Dispatchers.Default) { Edits.render(Edits.loadFull(context, item.uri, 4096), state) }
                if (overwrite) {
                    withContext(Dispatchers.IO) { Edits.overwrite(context, out, item) }
                    Thumbs.clear()
                    repo.refresh()
                    nav.back()
                } else {
                    val uri = withContext(Dispatchers.IO) { Edits.saveCopy(context, out, item) }
                    repo.refresh()
                    val copy = uri?.let { repo.byUri(it) }
                    nav.back()
                    if (copy != null && nav.current is Screen.Viewer) nav.replace(Screen.Viewer(Source.All, copy.id))
                }
            } catch (t: Throwable) {
                saving = "Não foi possível salvar: ${t.message ?: "erro"}"
                return@launch
            }
            saving = null
        }
    }

    Column(Modifier.fillMaxSize()) {
        GTopBar("Editar", onBack = { if (state.changed) leave = true else nav.back() }) {
            Box {
                GPill("Salvar", { menu = true }, enabled = state.changed && saving == null, height = 40.dp)
                DropdownMenu(menu, { menu = false }, containerColor = sheetColor(c)) {
                    MenuRow(Icons.Rounded.AutoFixHigh, "Salvar como cópia") {
                        menu = false
                        save(overwrite = false)
                    }
                    MenuRow(Icons.Rounded.RestartAlt, "Substituir a original") {
                        menu = false
                        requests.write(listOf(item.uri)) { ok -> if (ok) save(overwrite = true) }
                    }
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            val bmp = turned
            if (bmp != null) {
                val image = remember(bmp) { bmp.asImageBitmap() }
                if (tab == 2) {
                    CropView(image, state.crop, aspect) { state = state.copy(crop = it) }
                } else {
                    EditedPreview(image, state)
                }
            }
            saving?.let {
                Text(it, style = GType.footnote, color = c.content, modifier = Modifier.align(Alignment.BottomCenter).glass(c, RoundedCornerShape(50), strong = true).padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Ajustes", "Filtros", "Cortar").forEachIndexed { i, t -> GChip(t, tab == i, { tab = i }) }
        }
        Box(Modifier.fillMaxWidth().height(230.dp).navigationBarsPadding()) {
            when (tab) {
                0 -> AdjustPanel(state, onChange = { state = it }, onAuto = { preview?.let { p -> state = Edits.auto(p).copy(rotation = state.rotation, flip = state.flip, crop = state.crop, filter = state.filter) } }, item = item)
                1 -> FiltersPanel(preview, state) { state = state.copy(filter = it) }
                else -> CropPanel(
                    aspect = aspect,
                    onAspect = { a ->
                        aspect = a
                        turned?.let { t -> state = state.copy(crop = fitAspect(a, t.width, t.height)) }
                    },
                    onRotate = { state = state.copy(rotation = (state.rotation + 90) % 360, crop = RectF(0f, 0f, 1f, 1f)).also { aspect = Aspect.FREE } },
                    onFlip = { state = state.copy(flip = !state.flip, crop = RectF(0f, 0f, 1f, 1f)).also { aspect = Aspect.FREE } },
                    onReset = {
                        state = state.copy(rotation = 0, flip = false, crop = RectF(0f, 0f, 1f, 1f))
                        aspect = Aspect.FREE
                    },
                )
            }
        }
    }
    if (leave) {
        GConfirm("Descartar as alterações?", "A foto continua como estava.", "Descartar", onConfirm = {
            leave = false
            nav.back()
        }, onDismiss = { leave = false }, destructive = true)
    }
}

/** A foto já cortada, com as cores e a vinheta (o mesmo cálculo que vai para o arquivo). */
@Composable
private fun EditedPreview(image: ImageBitmap, s: EditState) {
    val src = IntOffset((s.crop.left * image.width).roundToInt(), (s.crop.top * image.height).roundToInt())
    val size = IntSize(
        (s.crop.width() * image.width).roundToInt().coerceIn(1, image.width - src.x),
        (s.crop.height() * image.height).roundToInt().coerceIn(1, image.height - src.y),
    )
    val painter = remember(image, src, size) { BitmapPainter(image, src, size) }
    val matrix = remember(s) { composeMatrix(s) }
    Image(
        painter,
        contentDescription = "Prévia da edição",
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.colorMatrix(matrix),
        modifier = Modifier
            .aspectRatio(size.width.toFloat() / size.height)
            .clip(RoundedCornerShape(6.dp))
            .drawWithContent {
                drawContent()
                if (s.vignette > 0f) {
                    drawRect(
                        Brush.radialGradient(
                            0f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = s.vignette * 0.78f),
                            center = center,
                            radius = max(this.size.width, this.size.height) * 0.75f,
                        ),
                    )
                }
            },
    )
}

private enum class Handle { NONE, MOVE, TL, TR, BL, BR }

/** O corte: arraste os cantos ou o meio; com proporção fixa, os cantos mantêm a proporção. */
@Composable
private fun CropView(image: ImageBitmap, crop: RectF, aspect: Aspect, onCrop: (RectF) -> Unit) {
    val c = G.colors
    var handle by remember { mutableStateOf(Handle.NONE) }
    val ratio = when (val a = aspect.ratio) {
        null -> null
        -1f -> image.width.toFloat() / image.height
        else -> a
    }
    Box(Modifier.aspectRatio(image.width.toFloat() / image.height)) {
        Image(image, contentDescription = "Foto para cortar", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        Canvas(
            Modifier.fillMaxSize().pointerInput(ratio) {
                var rect = RectF(crop)
                detectDragGestures(
                    onDragStart = { p ->
                        rect = RectF(crop)
                        val x = p.x / size.width
                        val y = p.y / size.height
                        val tol = 36.dp.toPx() / size.width
                        handle = when {
                            abs(x - rect.left) < tol && abs(y - rect.top) < tol * size.width / size.height -> Handle.TL
                            abs(x - rect.right) < tol && abs(y - rect.top) < tol * size.width / size.height -> Handle.TR
                            abs(x - rect.left) < tol && abs(y - rect.bottom) < tol * size.width / size.height -> Handle.BL
                            abs(x - rect.right) < tol && abs(y - rect.bottom) < tol * size.width / size.height -> Handle.BR
                            rect.contains(x, y) -> Handle.MOVE
                            else -> Handle.NONE
                        }
                    },
                    onDragEnd = { handle = Handle.NONE },
                    onDrag = { change, drag ->
                        change.consume()
                        val dx = drag.x / size.width
                        val dy = drag.y / size.height
                        val min = 0.08f
                        val r = RectF(rect)
                        when (handle) {
                            Handle.MOVE -> {
                                val w = r.width()
                                val h = r.height()
                                r.left = (r.left + dx).coerceIn(0f, 1f - w)
                                r.top = (r.top + dy).coerceIn(0f, 1f - h)
                                r.right = r.left + w
                                r.bottom = r.top + h
                            }
                            Handle.TL -> { r.left = (r.left + dx).coerceIn(0f, r.right - min); r.top = (r.top + dy).coerceIn(0f, r.bottom - min) }
                            Handle.TR -> { r.right = (r.right + dx).coerceIn(r.left + min, 1f); r.top = (r.top + dy).coerceIn(0f, r.bottom - min) }
                            Handle.BL -> { r.left = (r.left + dx).coerceIn(0f, r.right - min); r.bottom = (r.bottom + dy).coerceIn(r.top + min, 1f) }
                            Handle.BR -> { r.right = (r.right + dx).coerceIn(r.left + min, 1f); r.bottom = (r.bottom + dy).coerceIn(r.top + min, 1f) }
                            Handle.NONE -> return@detectDragGestures
                        }
                        if (ratio != null && handle != Handle.MOVE) {
                            // Mantém a proporção: a altura segue a largura (em pixels da imagem).
                            val h = (r.width() * image.width / ratio) / image.height
                            if (handle == Handle.TL || handle == Handle.TR) r.top = (r.bottom - h) else r.bottom = (r.top + h)
                            if (r.top < 0f || r.bottom > 1f) return@detectDragGestures
                        }
                        rect = r
                        onCrop(RectF(r))
                    },
                )
            },
        ) {
            val l = crop.left * size.width
            val t = crop.top * size.height
            val r = crop.right * size.width
            val b = crop.bottom * size.height
            val shade = Color.Black.copy(alpha = 0.55f)
            drawRect(shade, Offset.Zero, Size(size.width, t))
            drawRect(shade, Offset(0f, b), Size(size.width, size.height - b))
            drawRect(shade, Offset(0f, t), Size(l, b - t))
            drawRect(shade, Offset(r, t), Size(size.width - r, b - t))
            drawRect(Color.White, Offset(l, t), Size(r - l, b - t), style = Stroke(1.5.dp.toPx()))
            for (i in 1..2) {
                val x = l + (r - l) * i / 3f
                val y = t + (b - t) * i / 3f
                drawLine(Color.White.copy(alpha = 0.4f), Offset(x, t), Offset(x, b), 1f)
                drawLine(Color.White.copy(alpha = 0.4f), Offset(l, y), Offset(r, y), 1f)
            }
            val k = 18.dp.toPx()
            val w = 4.dp.toPx()
            listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b)).forEachIndexed { i, p ->
                val sx = if (i % 2 == 0) 1f else -1f
                val sy = if (i < 2) 1f else -1f
                drawRoundRect(c.accent, Offset(if (sx > 0) p.x else p.x - k, if (sy > 0) p.y else p.y - w), Size(k, w), CornerRadius(w / 2))
                drawRoundRect(c.accent, Offset(if (sx > 0) p.x else p.x - w, if (sy > 0) p.y else p.y - k), Size(w, k), CornerRadius(w / 2))
            }
        }
    }
}

@Composable
private fun AdjustPanel(s: EditState, onChange: (EditState) -> Unit, onAuto: () -> Unit, item: MediaItem) {
    val c = G.colors
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GChip("Automático", false, onAuto, icon = Icons.Rounded.AutoFixHigh)
            GChip("Redefinir", false, { onChange(EditState(rotation = s.rotation, flip = s.flip, crop = s.crop)) }, icon = Icons.Rounded.RestartAlt)
            if (LilyAi.installed(context)) GChip("Melhorar com a L.I.L.Y", false, { LilyAi.openTool(context, item.uri, item.mime, "ENHANCE") }, icon = Icons.Rounded.AutoAwesome)
        }
        Spacer(Modifier.height(6.dp))
        AdjustSlider("Exposição", s.exposure) { onChange(s.copy(exposure = it)) }
        AdjustSlider("Contraste", s.contrast) { onChange(s.copy(contrast = it)) }
        AdjustSlider("Saturação", s.saturation) { onChange(s.copy(saturation = it)) }
        AdjustSlider("Temperatura", s.warmth) { onChange(s.copy(warmth = it)) }
        AdjustSlider("Matiz", s.tint) { onChange(s.copy(tint = it)) }
        AdjustSlider("Vinheta", s.vignette, 0f..1f) { onChange(s.copy(vignette = it)) }
        Text(" ", style = GType.caption, color = c.tertiary)
    }
}

@Composable
private fun AdjustSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float> = -1f..1f, onValue: (Float) -> Unit) {
    val c = G.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = GType.footnote, color = c.content, modifier = Modifier.width(96.dp))
        Slider(
            value = value,
            onValueChange = onValue,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.content.copy(alpha = 0.15f)),
            modifier = Modifier.weight(1f),
        )
        Text(String.format(Locale.forLanguageTag("pt-BR"), "%+.0f", value * 100), style = GType.caption, color = c.secondary, modifier = Modifier.width(40.dp))
    }
}

@Composable
private fun FiltersPanel(preview: Bitmap?, s: EditState, onPick: (Filter) -> Unit) {
    val c = G.colors
    val small = remember(preview) { preview?.let { Bitmap.createScaledBitmap(it, 160, max(1, 160 * it.height / max(1, it.width)), true).asImageBitmap() } }
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(Filter.entries) { f ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.press({ onPick(f) })) {
                val m = remember(f) { composeMatrix(EditState(filter = f)) }
                Box(Modifier.size(78.dp).clip(RoundedCornerShape(16.dp)).border(if (s.filter == f) 2.5.dp else 1.dp, if (s.filter == f) c.accent else c.border, RoundedCornerShape(16.dp))) {
                    small?.let { Image(it, contentDescription = f.label, contentScale = ContentScale.Crop, colorFilter = ColorFilter.colorMatrix(m), modifier = Modifier.fillMaxSize()) }
                }
                Spacer(Modifier.height(6.dp))
                Text(f.label, style = GType.caption, color = if (s.filter == f) c.accent else c.secondary)
            }
        }
    }
}

@Composable
private fun CropPanel(aspect: Aspect, onAspect: (Aspect) -> Unit, onRotate: () -> Unit, onFlip: () -> Unit, onReset: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(vertical = 12.dp)) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Aspect.entries) { a -> GChip(a.label, aspect == a, { onAspect(a) }) }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GPill("Girar", onRotate, icon = Icons.Rounded.RotateRight, filled = false, height = 42.dp)
            GPill("Espelhar", onFlip, icon = Icons.Rounded.Flip, filled = false, height = 42.dp)
            GPill("Redefinir", onReset, icon = Icons.Rounded.RestartAlt, filled = false, height = 42.dp)
        }
    }
}
