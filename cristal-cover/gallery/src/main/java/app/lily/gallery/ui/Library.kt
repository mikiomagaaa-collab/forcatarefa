package app.lily.gallery.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lily.gallery.GooglePhotos
import app.lily.gallery.LilyAi
import app.lily.gallery.data.Album
import app.lily.gallery.data.Edits
import app.lily.gallery.data.MediaItem
import app.lily.gallery.data.Thumbs
import app.lily.gallery.data.monthTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Miniatura de uma foto ou vídeo (do cache oficial do MediaStore). */
@Composable
fun Thumb(item: MediaItem, modifier: Modifier = Modifier, side: Int = 320) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(Thumbs.cached(item, side), item.id) {
        if (value == null) value = Thumbs.load(context, item, side)
    }
    Box(modifier.background(G.colors.glass)) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        if (item.video) {
            Row(
                Modifier.align(Alignment.BottomEnd).padding(5.dp).background(Color(0x99000000), CircleShape).padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Vídeo", tint = Color.White, modifier = Modifier.size(12.dp))
                Text(duration(item.durationMs), style = GType.caption, color = Color.White)
            }
        }
        if (item.favorite) {
            Icon(Icons.Rounded.Favorite, contentDescription = "Favorita", tint = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(5.dp).size(14.dp))
        }
    }
}

fun duration(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/**
 * A grade com títulos por mês, seleção por toque longo e as ações da seleção. Usada em Fotos, nos
 * álbuns, nas favoritas e na lixeira.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaGrid(
    items: List<MediaItem>,
    columns: Int,
    selected: Set<Long>,
    onOpen: (MediaItem) -> Unit,
    onSelect: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    contentBottom: Int = 110,
) {
    val c = G.colors
    val groups = remember(items) { items.groupBy { monthTitle(it.taken) } }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 2.dp, end = 2.dp, bottom = contentBottom.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (header != null) item(span = { GridItemSpan(maxLineSpan) }) { header() }
        groups.forEach { (month, list) ->
            item(key = "m-$month", span = { GridItemSpan(maxLineSpan) }) {
                Text(month, style = GType.headline, color = c.content, modifier = Modifier.padding(start = 12.dp, top = 18.dp, bottom = 8.dp))
            }
            items(list, key = { it.id }) { item ->
                val isSelected = item.id in selected
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(if (isSelected) 14.dp else 4.dp))
                        .combinedClickable(
                            onClick = { if (selected.isNotEmpty()) onSelect(item) else onOpen(item) },
                            onLongClick = { onSelect(item) },
                            onLongClickLabel = "Selecionar",
                        ),
                ) {
                    Thumb(item, Modifier.fillMaxSize().then(if (isSelected) Modifier.padding(6.dp).clip(RoundedCornerShape(10.dp)) else Modifier), side = if (columns <= 3) 360 else 240)
                    if (selected.isNotEmpty()) {
                        Icon(
                            if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.Photo,
                            contentDescription = if (isSelected) "Selecionada" else null,
                            tint = if (isSelected) c.accent else Color.White.copy(alpha = 0.0f),
                            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).background(if (isSelected) Color.White else Color.Transparent, CircleShape),
                        )
                    }
                }
            }
        }
    }
}

/** As ações sobre as fotos selecionadas (todas passam pela confirmação do Android quando mudam arquivos). */
@Composable
fun SelectionBar(items: List<MediaItem>, onClear: () -> Unit, trash: Boolean = false) {
    val c = G.colors
    val context = LocalContext.current
    val requests = LocalRequests.current
    val uris = items.map { it.uri }
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp).glass(c, CircleShape, strong = true).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GIconButton(Icons.Rounded.Close, "Cancelar a seleção", onClear, filled = false)
        Text("${items.size} ${if (items.size == 1) "selecionada" else "selecionadas"}", style = GType.headline, color = c.content, modifier = Modifier.weight(1f).padding(start = 4.dp))
        if (trash) {
            GIconButton(Icons.Rounded.Download, "Restaurar", { requests.trash(uris, false) { if (it) onClear() } }, filled = false)
            GIconButton(Icons.Rounded.Delete, "Apagar para sempre", { requests.delete(uris) { if (it) onClear() } }, filled = false, tint = c.danger)
        } else {
            GIconButton(Icons.Rounded.Share, "Compartilhar", { share(context, items) }, filled = false)
            val allFav = items.all { it.favorite }
            GIconButton(if (allFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (allFav) "Tirar das favoritas" else "Favoritar", {
                requests.favorite(uris, !allFav) { if (it) onClear() }
            }, filled = false)
            if (GooglePhotos.installed(context)) {
                GIconButton(Icons.Rounded.CloudUpload, "Enviar para o Google Fotos", { GooglePhotos.send(context, uris) }, filled = false)
            }
            GIconButton(Icons.Rounded.Delete, "Mover para a lixeira", { requests.trash(uris, true) { if (it) onClear() } }, filled = false)
        }
    }
}

fun share(context: android.content.Context, items: List<MediaItem>) {
    val uris = ArrayList(items.map { it.uri })
    val mime = if (items.all { it.video }) "video/*" else if (items.none { it.video }) "image/*" else "*/*"
    val intent = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first()) else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    context.startActivity(Intent.createChooser(intent.setType(mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
fun HomeScreen(nav: GalleryNav, access: Access) {
    val c = G.colors
    val repo = LocalRepo.current
    val requests = LocalRequests.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by repo.items.collectAsStateWithLifecycle()
    val trash by repo.trash.collectAsStateWithLifecycle()
    val loaded by repo.loaded.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var columns by rememberSaveable { mutableIntStateOf(4) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var menu by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf<String?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        importing = "Importando ${uris.size}…"
        scope.launch {
            val ok = withContext(Dispatchers.IO) { uris.count { u -> runCatching { Edits.importCopy(context, u) }.getOrNull() != null } }
            importing = if (ok == uris.size) "$ok importada(s) em Pictures/Importadas." else "$ok de ${uris.size} importadas (algumas não puderam ser lidas)."
            repo.refresh()
        }
    }

    val shown = remember(items, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) items else items.filter { it.name.lowercase().contains(q) || it.bucket.lowercase().contains(q) || monthTitle(it.taken).lowercase().contains(q) }
    }

    Box(Modifier.fillMaxSize()) {
        when (tab) {
            0 -> if (loaded && items.isEmpty()) {
                EmptyLibrary(access)
            } else {
                MediaGrid(
                    items = shown,
                    columns = columns,
                    selected = selected,
                    onOpen = { nav.go(Screen.Viewer(if (query.isBlank()) Source.All else Source.Search(query), it.id)) },
                    onSelect = { item -> selected = if (item.id in selected) selected - item.id else selected + item.id },
                    header = {
                        Column(Modifier.statusBarsPadding().padding(top = 64.dp)) {
                            if (access == Access.PARTIAL) PartialBanner()
                            if (searching) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).glass(c, CircleShape).padding(horizontal = 14.dp).height(44.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Rounded.Search, contentDescription = null, tint = c.secondary, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Box(Modifier.weight(1f)) {
                                        if (query.isEmpty()) Text("Nome, álbum ou mês (ex.: novembro)", style = GType.callout, color = c.tertiary)
                                        BasicTextField(query, { query = it }, singleLine = true, textStyle = GType.callout.copy(color = c.content), cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth())
                                    }
                                }
                            }
                            importing?.let { Text(it, style = GType.footnote, color = c.secondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
                        }
                    },
                )
            }
            else -> AlbumsGrid(nav, repo.albums(items), favorites = items.count { it.favorite }, trash = trash.size)
        }

        // Barra de cima: título e ações (ou a barra da seleção).
        if (selected.isNotEmpty()) {
            SelectionBar(items.filter { it.id in selected }, onClear = { selected = emptySet() })
        } else {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (tab == 0) "Fotos" else "Álbuns", style = GType.display, color = c.content)
                    if (tab == 0 && loaded) Text("${items.size} ${if (items.size == 1) "item" else "itens"}", style = GType.caption, color = c.secondary)
                }
                if (tab == 0) GIconButton(Icons.Rounded.Search, "Buscar", { searching = !searching; if (!searching) query = "" })
                Spacer(Modifier.width(8.dp))
                Box {
                    GIconButton(Icons.Rounded.MoreVert, "Mais opções", { menu = true })
                    DropdownMenu(menu, { menu = false }, containerColor = sheetColor(c)) {
                        MenuRow(Icons.Rounded.PhotoLibrary, "Importar fotos (inclui Google Fotos)") {
                            menu = false
                            pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        }
                        if (GooglePhotos.installed(context)) MenuRow(Icons.Rounded.CloudUpload, "Abrir o Google Fotos") { menu = false; GooglePhotos.open(context) }
                        if (LilyAi.installed(context)) MenuRow(Icons.Rounded.AutoAwesome, "Gerar imagem com a L.I.L.Y") { menu = false; LilyAi.openImageGen(context) }
                        MenuRow(Icons.Rounded.GridView, if (columns == 4) "Miniaturas maiores" else "Miniaturas menores") { menu = false; columns = if (columns == 4) 3 else 4 }
                        MenuRow(Icons.Rounded.Favorite, "Favoritas") { menu = false; nav.go(Screen.Favorites) }
                        MenuRow(Icons.Rounded.Delete, "Lixeira (${trash.size})") { menu = false; nav.go(Screen.Trash) }
                        if (!requests.canManageMedia()) MenuRow(Icons.AutoMirrored.Rounded.Label, "Lixeira e favoritos sem confirmar sempre") { menu = false; requests.openManageMedia() }
                    }
                }
            }
        }

        // Abas de baixo, em vidro.
        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp).glass(c, CircleShape, strong = true).padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TabPill("Fotos", Icons.Rounded.Photo, tab == 0) { tab = 0 }
            TabPill("Álbuns", Icons.Rounded.Collections, tab == 1) { tab = 1; selected = emptySet() }
        }
    }
}

@Composable
private fun TabPill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    val c = G.colors
    Row(
        Modifier.clip(CircleShape).background(if (selected) c.accent.copy(alpha = 0.22f) else Color.Transparent).press(onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) c.accent else c.secondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = GType.subhead.copy(fontWeight = FontWeight.SemiBold), color = if (selected) c.content else c.secondary)
    }
}

@Composable
fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, style = GType.callout, color = G.colors.content) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = G.colors.accent) },
        onClick = onClick,
    )
}

@Composable
private fun PartialBanner() {
    val c = G.colors
    val requests = LocalRequests.current
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(12.dp).glass(c).padding(14.dp)) {
        Text("Você liberou só algumas fotos", style = GType.headline, color = c.content)
        Text("A Gallery mostra apenas o que você escolheu. Dá para escolher mais ou liberar tudo.", style = GType.footnote, color = c.secondary)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GPill("Escolher mais", { requests.requestMedia() }, height = 38.dp)
            GPill("Liberar tudo", {
                runCatching {
                    context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }, filled = false, height = 38.dp)
        }
    }
}

@Composable
private fun EmptyLibrary(access: Access) {
    val c = G.colors
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, tint = c.accent, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text("Nenhuma foto por aqui", style = GType.title, color = c.content)
        Text(
            if (access == Access.PARTIAL) "Você liberou só algumas fotos e nenhuma foi escolhida." else "As fotos e vídeos do aparelho aparecem aqui.",
            style = GType.footnote,
            color = c.secondary,
            textAlign = TextAlign.Center,
        )
        if (access == Access.PARTIAL) {
            Spacer(Modifier.height(12.dp))
            PartialBanner()
        }
    }
}

@Composable
private fun AlbumsGrid(nav: GalleryNav, albums: List<Album>, favorites: Int, trash: Int) {
    val c = G.colors
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.statusBarsPadding().height(70.dp)) }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GChip("Favoritas · $favorites", false, { nav.go(Screen.Favorites) }, icon = Icons.Rounded.Favorite)
                GChip("Lixeira · $trash", false, { nav.go(Screen.Trash) }, icon = Icons.Rounded.Delete)
            }
        }
        items(albums, key = { it.id }) { a ->
            Column(Modifier.press({ nav.go(Screen.Album(a.id, a.name)) })) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)).border(1.dp, c.border, RoundedCornerShape(20.dp))) {
                    Thumb(a.cover, Modifier.fillMaxSize(), side = 400)
                    if (a.lily) {
                        Row(
                            Modifier.align(Alignment.TopStart).padding(8.dp).background(Color(0x99000000), CircleShape).padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Criadas pela L.I.L.Y", style = GType.caption, color = Color.White)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(a.name, style = GType.subhead.copy(fontWeight = FontWeight.SemiBold), color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${a.count}", style = GType.caption, color = c.secondary)
            }
        }
    }
}

@Composable
fun AlbumScreen(nav: GalleryNav, screen: Screen.Album) {
    val repo = LocalRepo.current
    val items by repo.items.collectAsStateWithLifecycle()
    val list = remember(items, screen.id) { items.filter { it.bucketId == screen.id } }
    GridPage(nav, screen.name, list, Source.Album(screen.id))
}

@Composable
fun FavoritesScreen(nav: GalleryNav) {
    val repo = LocalRepo.current
    val items by repo.items.collectAsStateWithLifecycle()
    val list = remember(items) { items.filter { it.favorite } }
    GridPage(nav, "Favoritas", list, Source.Favorites)
}

@Composable
private fun GridPage(nav: GalleryNav, title: String, list: List<MediaItem>, source: Source) {
    val c = G.colors
    var selected by remember { mutableStateOf(setOf<Long>()) }
    Box(Modifier.fillMaxSize()) {
        MediaGrid(
            items = list,
            columns = 4,
            selected = selected,
            onOpen = { nav.go(Screen.Viewer(source, it.id)) },
            onSelect = { item -> selected = if (item.id in selected) selected - item.id else selected + item.id },
            header = { Spacer(Modifier.statusBarsPadding().height(64.dp)) },
            contentBottom = 40,
        )
        if (list.isEmpty()) Text("Nada aqui ainda.", style = GType.callout, color = c.secondary, modifier = Modifier.align(Alignment.Center))
        if (selected.isNotEmpty()) {
            SelectionBar(list.filter { it.id in selected }, onClear = { selected = emptySet() })
        } else {
            Box(Modifier.background(c.background.copy(alpha = 0.6f))) { GTopBar(title, onBack = { nav.back() }, subtitle = "${list.size} ${if (list.size == 1) "item" else "itens"}") }
        }
    }
}
