package app.cristal.cover.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cristal.cover.data.battery.BatteryState
import app.cristal.cover.data.collections.CollectionPreview
import app.cristal.cover.data.collections.WallpaperCollection
import app.cristal.cover.ui.design.Liquid
import app.cristal.cover.ui.design.LiquidButton
import app.cristal.cover.ui.design.LiquidButtonStyle
import app.cristal.cover.ui.design.LiquidCircleButton
import app.cristal.cover.ui.design.LiquidMaterial
import app.cristal.cover.ui.design.LiquidType
import app.cristal.cover.ui.lock.LockEditorActivity
import app.cristal.cover.ui.lock.LockScreen
import app.cristal.cover.ui.lock.LockUiState
import app.cristal.cover.ui.lock.NoLockActions
import kotlin.math.absoluteValue
import kotlin.math.min

/** Pedido de nome: coleção nova (id nulo) ou renomear uma existente. */
private data class NameRequest(val id: String?, val current: String)

/**
 * Wallpaper Library: galeria das coleções guardadas, como a galeria de telas de bloqueio do
 * iPhone. Deslize entre elas, veja a prévia real (bloqueio interno e externo) e aplique.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CollectionsPage(vm: SettingsViewModel, onBack: () -> Unit) {
    val collections by vm.collections.collectAsStateWithLifecycle()
    val active by vm.activeCollection.collectAsStateWithLifecycle()
    val previews by vm.collectionPreviews.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadCollections() }
    CollectionsContent(
        collections = collections,
        active = active,
        previews = previews,
        onBack = onBack,
        actions = CollectionActions(
            onNew = vm::newCollection,
            onApply = vm::applyCollection,
            onRename = vm::renameCollection,
            onDuplicate = vm::duplicateCollection,
            onOverwrite = vm::overwriteCollection,
            onDelete = vm::deleteCollection,
        ),
    )
}

internal class CollectionActions(
    val onNew: (String) -> Unit,
    val onApply: (String, (() -> Unit)?) -> Unit,
    val onRename: (String, String) -> Unit,
    val onDuplicate: (String) -> Unit,
    val onOverwrite: (String) -> Unit,
    val onDelete: (String) -> Unit,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CollectionsContent(
    collections: List<WallpaperCollection>,
    active: String?,
    previews: Map<String, CollectionPreview>,
    onBack: () -> Unit,
    actions: CollectionActions,
) {
    val colors = Liquid.colors
    val context = LocalContext.current
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    var naming by remember { mutableStateOf<NameRequest?>(null) }
    var deleting by remember { mutableStateOf<WallpaperCollection?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize().padding(insets)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiquidCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, "Voltar", onBack, size = 42.dp, material = LiquidMaterial.SOLID)
                Text("Coleções", style = LiquidType.title1, color = colors.content, modifier = Modifier.padding(start = 12.dp).weight(1f))
                LiquidCircleButton(Icons.Rounded.Add, "Nova coleção", { naming = NameRequest(null, "") }, size = 42.dp, material = LiquidMaterial.SOLID)
            }
            if (collections.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().weight(1f).padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Nenhuma coleção ainda", style = LiquidType.title2, color = colors.content)
                    Text(
                        "Uma coleção guarda a aparência inteira: os papéis de parede das quatro áreas, os relógios (fonte, tamanho, " +
                            "cor e posição), a profundidade, os efeitos e o tema. Depois é só trocar entre elas.",
                        style = LiquidType.subhead,
                        color = colors.contentSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
                    )
                    LiquidButton("Guardar a aparência atual", { naming = NameRequest(null, "") })
                }
            } else {
                val start = collections.indexOfFirst { it.id == active }.coerceAtLeast(0)
                val pager = rememberPagerState(initialPage = start) { collections.size }
                HorizontalPager(
                    state = pager,
                    contentPadding = PaddingValues(horizontal = 56.dp),
                    pageSpacing = 14.dp,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) { page ->
                    val collection = collections[page]
                    val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue
                    CollectionCard(
                        collection = collection,
                        preview = previews[collection.id],
                        isActive = collection.id == active,
                        modifier = Modifier.graphicsLayer {
                            val s = 1f - 0.08f * offset.coerceIn(0f, 1f)
                            scaleX = s
                            scaleY = s
                            alpha = 1f - 0.35f * offset.coerceIn(0f, 1f)
                        },
                    )
                }
                val current = collections.getOrNull(pager.currentPage)
                if (current != null) {
                    val isActive = current.id == active
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LiquidButton(
                            if (isActive) "Em uso" else "Aplicar",
                            { actions.onApply(current.id, null) },
                            Modifier.weight(1f),
                            icon = if (isActive) Icons.Rounded.Check else null,
                            enabled = !isActive,
                        )
                        LiquidButton(
                            "Personalizar",
                            {
                                actions.onApply(current.id) {
                                    val displayId = runCatching { context.display?.displayId }.getOrNull() ?: 0
                                    LockEditorActivity.open(context, cover = false, displayId = displayId)
                                }
                            },
                            Modifier.weight(1f),
                            style = LiquidButtonStyle.TINTED,
                        )
                        Box {
                            LiquidCircleButton(Icons.Rounded.MoreHoriz, "Mais opções", { menuOpen = true }, size = 48.dp, material = LiquidMaterial.SOLID)
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(text = { Text("Renomear") }, onClick = {
                                    menuOpen = false
                                    naming = NameRequest(current.id, current.name)
                                })
                                DropdownMenuItem(text = { Text("Duplicar") }, onClick = {
                                    menuOpen = false
                                    actions.onDuplicate(current.id)
                                })
                                DropdownMenuItem(text = { Text("Atualizar com a aparência atual") }, onClick = {
                                    menuOpen = false
                                    actions.onOverwrite(current.id)
                                })
                                DropdownMenuItem(text = { Text("Excluir", color = colors.danger) }, onClick = {
                                    menuOpen = false
                                    deleting = current
                                })
                            }
                        }
                    }
                    Text(
                        "${pager.currentPage + 1} de ${collections.size}",
                        style = LiquidType.footnote,
                        color = colors.contentSecondary,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 10.dp),
                    )
                }
            }
        }
    }

    naming?.let { request ->
        var text by remember(request) { mutableStateOf(request.current) }
        AlertDialog(
            onDismissRequest = { naming = null },
            title = { Text(if (request.id == null) "Nova coleção" else "Renomear coleção") },
            text = {
                Column {
                    if (request.id == null) {
                        Text("Guarda a aparência de agora: papéis de parede, relógios, profundidade, efeitos e tema.", style = LiquidType.footnote)
                        Spacer(Modifier.height(10.dp))
                    }
                    TextField(value = text, onValueChange = { text = it.take(40) }, singleLine = true, placeholder = { Text("Nome") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = text.trim().ifEmpty { "Coleção ${collections.size + 1}" }
                    if (request.id == null) actions.onNew(name) else actions.onRename(request.id, name)
                    naming = null
                }) { Text(if (request.id == null) "Guardar" else "Renomear") }
            },
            dismissButton = { TextButton(onClick = { naming = null }) { Text("Cancelar") } },
        )
    }
    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Excluir \"${target.name}\"?") },
            text = { Text("A aparência em uso não muda. Só esta coleção é apagada do aparelho.") },
            confirmButton = {
                TextButton(onClick = {
                    actions.onDelete(target.id)
                    deleting = null
                }) { Text("Excluir", color = colors.danger) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }
}

/** Cartão de uma coleção: o bloqueio interno de verdade, com o externo no canto. */
@Composable
private fun CollectionCard(collection: WallpaperCollection, preview: CollectionPreview?, isActive: Boolean, modifier: Modifier = Modifier) {
    val colors = Liquid.colors
    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val inner = DpSize(411.dp, 1004.dp)
            val scale = min(maxWidth / inner.width, maxHeight / inner.height)
            val w = inner.width * scale
            val h = inner.height * scale
            Box(Modifier.size(w, h)) {
                MiniLock(preview, inner, scale, cover = false, corner = 38.dp * scale, Modifier.border(if (isActive) 2.5.dp else 1.dp, if (isActive) colors.accent else Color.White.copy(alpha = 0.16f), RoundedCornerShape(38.dp * scale)))
                val coverSize = DpSize(422.dp, 426.dp)
                val coverScale = (w * 0.42f) / coverSize.width
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .size(coverSize.width * coverScale, coverSize.height * coverScale),
                ) {
                    MiniLock(preview, coverSize, coverScale, cover = true, corner = 44.dp * coverScale, Modifier.border(1.5.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(44.dp * coverScale)))
                }
            }
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(collection.name, style = LiquidType.headline, color = colors.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (isActive) {
                Box(Modifier.padding(start = 8.dp).clip(CircleShape).background(colors.accent).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text("Em uso", style = LiquidType.caption, color = colors.onAccent)
                }
            }
        }
    }
}

/** Bloqueio desenhado no tamanho real da tela e reduzido; os toques não chegam nele. */
@Composable
private fun MiniLock(preview: CollectionPreview?, size: DpSize, scale: Float, cover: Boolean, corner: Dp, modifier: Modifier) {
    Box(modifier.size(size.width * scale, size.height * scale).clip(RoundedCornerShape(corner)).background(Color.Black)) {
        if (preview != null) {
            val state = LockUiState(
                settings = preview.settings,
                battery = BatteryState(percent = 76),
                images = preview.images,
                subjects = preview.subjects,
            ).forDisplay(cover)
            Box(
                Modifier
                    .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                    .requiredSize(size)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = scale
                        scaleY = scale
                    },
            ) {
                LockScreen(state = state, secure = false, resetKey = 0, actions = NoLockActions, coverMinimal = cover)
            }
        }
        // Camada que segura os toques: deslizar troca de coleção, mas nada no bloqueio reage.
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } })
    }
}
