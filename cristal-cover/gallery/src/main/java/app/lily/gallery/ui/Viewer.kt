package app.lily.gallery.ui

import android.graphics.Bitmap
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lily.gallery.LilyAi
import app.lily.gallery.data.Edits
import app.lily.gallery.data.MediaItem
import app.lily.gallery.data.dayTitle
import app.lily.gallery.data.monthTitle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val PT = Locale.forLanguageTag("pt-BR")

fun sizeText(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(PT, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1L shl 20 -> String.format(PT, "%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format(PT, "%.0f KB", bytes / 1024.0)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(nav: GalleryNav, screen: Screen.Viewer) {
    val c = G.colors
    val repo = LocalRepo.current
    val requests = LocalRequests.current
    val context = LocalContext.current
    val all by repo.items.collectAsStateWithLifecycle()
    val trash by repo.trash.collectAsStateWithLifecycle()
    val list = remember(all, trash, screen.source) {
        when (val s = screen.source) {
            Source.All -> all
            is Source.Album -> all.filter { it.bucketId == s.id }
            Source.Favorites -> all.filter { it.favorite }
            Source.Trash -> trash
            is Source.Search -> s.query.trim().lowercase().let { q -> all.filter { it.name.lowercase().contains(q) || it.bucket.lowercase().contains(q) || monthTitle(it.taken).lowercase().contains(q) } }
        }
    }
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black)) { GTopBar("", onBack = { nav.back() }) }
        return
    }
    val start = remember(screen.startId) { list.indexOfFirst { it.id == screen.startId }.coerceAtLeast(0) }
    val pager = rememberPagerState(initialPage = start) { list.size }
    var chrome by remember { mutableStateOf(true) }
    var info by remember { mutableStateOf(false) }
    var ai by remember { mutableStateOf(false) }
    val current = list.getOrNull(pager.currentPage.coerceIn(0, list.lastIndex)) ?: list.first()
    val inTrash = screen.source == Source.Trash

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, key = { list[it].id }, beyondViewportPageCount = 1) { page ->
            val item = list[page]
            if (item.video) VideoPage(item) { chrome = !chrome } else ZoomableImage(item.uri) { chrome = !chrome }
        }
        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xAA000000), Color.Transparent)))) {
                    GTopBar(dayTitle(current.taken).substringBefore(" ·"), onBack = { nav.back() }, subtitle = current.bucket) {
                        GIconButton(Icons.Rounded.Info, "Informações", { info = true })
                    }
                }
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xAA000000))))
                        .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (inTrash) {
                        GPill("Restaurar", { requests.trash(listOf(current.uri), false) { if (it) nav.back() } }, height = 42.dp)
                        GPill("Apagar para sempre", { requests.delete(listOf(current.uri)) { if (it) nav.back() } }, filled = false, height = 42.dp)
                    } else {
                        GIconButton(Icons.Rounded.Share, "Compartilhar", { share(context, listOf(current)) }, tint = Color.White)
                        GIconButton(if (current.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (current.favorite) "Tirar das favoritas" else "Favoritar", {
                            requests.favorite(listOf(current.uri), !current.favorite) {}
                        }, tint = if (current.favorite) c.danger else Color.White)
                        if (!current.video) GIconButton(Icons.Rounded.Edit, "Editar", { nav.go(Screen.Editor(current.id)) }, tint = Color.White)
                        if (!current.video) {
                            Box(Modifier.size(50.dp).clip(CircleShape).background(Brush.linearGradient(listOf(c.accent, c.accent2))).press({ ai = true }), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = "L.I.L.Y", tint = Color.White)
                            }
                        }
                        GIconButton(Icons.Rounded.Delete, "Mover para a lixeira", { requests.trash(listOf(current.uri), true) {} }, tint = Color.White)
                    }
                }
            }
        }
    }

    if (info) InfoSheet(current) { info = false }
    if (ai) AiSheet(current) { ai = false }
}

/** Uma imagem de fora da biblioteca (aberta por outro app): só ver, compartilhar e mandar para a IA. */
@Composable
fun ExternalViewer(nav: GalleryNav, screen: Screen.External) {
    val context = LocalContext.current
    var ai by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        ZoomableImage(screen.uri) {}
        GTopBar("", onBack = { if (!nav.back()) (context as? android.app.Activity)?.finish() })
        if (LilyAi.installed(context)) {
            GPill("L.I.L.Y", { ai = true }, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp), icon = Icons.Rounded.AutoAwesome)
        }
    }
    if (ai) {
        val item = MediaItem(0, screen.uri, false, screen.mime, System.currentTimeMillis(), 0, 0, 0, 0, "", "", "", 0, false)
        AiSheet(item) { ai = false }
    }
}

/** Zoom com pinça e toque duplo; sem zoom, o deslize de lado passa para a próxima foto. */
@Composable
fun ZoomableImage(uri: Uri, onTap: () -> Unit) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { runCatching { Edits.loadFull(context, uri, 2560) }.getOrNull() }
    }
    var scale by remember(uri) { mutableFloatStateOf(1f) }
    var offset by remember(uri) { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(uri) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { p ->
                        if (scale > 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            offset = Offset((size.width / 2f - p.x) * 1.5f, (size.height / 2f - p.y) * 1.5f)
                        }
                    },
                )
            }
            .pointerInput(uri) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        if (fingers > 1 || scale > 1.01f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            val maxX = size.width * (scale - 1) / 2f
                            val maxY = size.height * (scale - 1) / 2f
                            offset = Offset((offset.x + pan.x).coerceIn(-maxX, maxX), (offset.y + pan.y).coerceIn(-maxY, maxY))
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (scale <= 1.01f) {
                        scale = 1f
                        offset = Offset.Zero
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                it.asImageBitmap(),
                contentDescription = "Foto",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            )
        }
    }
}

/** Vídeo: a miniatura e, com um toque no play, o reprodutor do Android. */
@Composable
private fun VideoPage(item: MediaItem, onTap: () -> Unit) {
    var playing by remember(item.id) { mutableStateOf(false) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (playing) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoURI(item.uri)
                        val controller = MediaController(ctx)
                        controller.setAnchorView(this)
                        setMediaController(controller)
                        setOnPreparedListener { start() }
                    }
                },
                onRelease = { it.stopPlayback() },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Thumb(item, Modifier.fillMaxSize().pointerInput(item.id) { detectTapGestures(onTap = { onTap() }) }, side = 1024)
            Box(Modifier.size(72.dp).clip(CircleShape).background(Color(0x99000000)).press({ playing = true }), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Reproduzir", tint = Color.White, modifier = Modifier.size(40.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoSheet(item: MediaItem, onDismiss: () -> Unit) {
    val c = G.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = sheetColor(c)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
            Text(item.name, style = GType.title, color = c.content)
            Spacer(Modifier.height(10.dp))
            listOfNotNull(
                "Data" to dayTitle(item.taken),
                if (item.width > 0) "Tamanho" to "${item.width} × ${item.height} px · ${String.format(PT, "%.1f", item.width * item.height / 1_000_000.0)} MP" else null,
                "Arquivo" to "${sizeText(item.size)} · ${item.mime}",
                if (item.video) "Duração" to duration(item.durationMs) else null,
                "Álbum" to item.bucket,
                "Pasta" to item.path,
            ).forEach { (k, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text(k, style = GType.footnote, color = c.tertiary, modifier = Modifier.width(90.dp))
                    Text(v, style = GType.footnote, color = c.content, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** As ações da L.I.L.Y AI para a foto (a IA roda no aparelho, no app dela). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSheet(item: MediaItem, onDismiss: () -> Unit) {
    val c = G.colors
    val context = LocalContext.current
    val installed = remember { LilyAi.installed(context) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = sheetColor(c)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = c.accent)
                Spacer(Modifier.width(8.dp))
                Text("L.I.L.Y", style = GType.title, color = c.content)
            }
            Text(
                if (installed) "Tudo roda no aparelho. Os resultados vão para o álbum “L.I.L.Y”." else "Instale a L.I.L.Y AI para descrever, melhorar, ampliar e recortar fotos no aparelho.",
                style = GType.footnote,
                color = c.secondary,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            )
            if (installed) {
                LilyAi.actions.forEach { (tool, label) ->
                    Text(
                        label,
                        style = GType.body,
                        color = c.content,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).press({
                            onDismiss()
                            LilyAi.openTool(context, item.uri, item.mime, tool)
                        }).padding(horizontal = 12.dp, vertical = 13.dp),
                    )
                }
            }
        }
    }
}
