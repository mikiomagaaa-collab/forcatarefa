package app.cristal.cover.ui.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.BoxScope
import app.cristal.cover.ui.design.LiquidSurface
import app.cristal.cover.ui.design.LiquidMaterial
import app.cristal.cover.data.prefs.DockStyle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import app.cristal.cover.data.apps.AppInfo
import app.cristal.cover.data.home.HomeItem
import app.cristal.cover.data.home.HomeLayout
import app.cristal.cover.data.home.WidgetKind
import app.cristal.cover.data.prefs.Settings
import app.cristal.cover.ui.components.AppIconImage
import app.cristal.cover.ui.components.LocalIconLook
import app.cristal.cover.ui.components.PageDots
import app.cristal.cover.ui.components.WallpaperTextShadow
import app.cristal.cover.ui.components.toShape
import app.cristal.cover.ui.glass.GlassSurface
import app.cristal.cover.ui.glass.Motion
import app.cristal.cover.ui.widgets.AppWidgetCell
import app.cristal.cover.ui.widgets.WidgetActions
import app.cristal.cover.ui.widgets.WidgetCard
import app.cristal.cover.ui.widgets.WidgetData

/** Callbacks da tela inicial, agrupados para não espalhar dezenas de parâmetros. */
@Stable
class HomeCallbacks(
    val onAppClick: (AppInfo) -> Unit,
    val onFolderClick: (HomeItem.Folder) -> Unit,
    /** Toque longo em um item: abre o menu e arma o arrasto. */
    val onItemHold: (item: HomeItem, inDock: Boolean, at: Offset) -> Unit,
    val onItemDrag: (delta: Offset) -> Unit,
    val onItemRelease: () -> Unit,
    val onBackgroundHold: () -> Unit,
    val onBackgroundDoubleTap: () -> Unit,
)

/** Páginas + indicador + dock, respeitando as margens seguras e o recorte das câmeras. */
@Composable
fun HomeContent(
    settings: Settings,
    layout: HomeLayout,
    pagerState: PagerState,
    safe: SafeLayout,
    drag: DragController,
    hover: DropTarget?,
    widgetData: WidgetData,
    widgetActions: WidgetActions,
    callbacks: HomeCallbacks,
    todayPanel: @Composable () -> Unit,
) {
    val band = safe.dockBand
    val dockInBand = settings.dockEnabled && band != null
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = safe.start, end = safe.end, top = safe.top, bottom = safe.bottom),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { drag.gridBounds = it.boundsInRoot() },
                // Todas as páginas ficam compostas: são poucas e isso mantém o arrasto vivo ao trocar de página.
                beyondViewportPageCount = 12,
                userScrollEnabled = !drag.moving,
                key = { it },
            ) { page ->
                if (page == 0) {
                    todayPanel()
                } else {
                    val index = page - 1
                    GridPage(
                        items = layout.pages.getOrElse(index) { emptyList() },
                        settings = settings,
                        draggingId = if (drag.moving) drag.item?.id else null,
                        hover = (hover as? DropTarget.Cell)?.takeIf { it.slot.page == index },
                        widgetData = widgetData,
                        widgetActions = widgetActions,
                        callbacks = callbacks,
                    )
                }
            }
            PageDots(
                count = pagerState.pageCount,
                position = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
                modifier = Modifier.padding(vertical = 6.dp),
            )
            if (settings.dockEnabled && !dockInBand) {
                Dock(
                    items = layout.dock,
                    settings = settings,
                    drag = drag,
                    highlighted = hover is DropTarget.Dock,
                    callbacks = callbacks,
                    modifier = Modifier
                        .padding(start = settings.dockMargin.dp, end = settings.dockMargin.dp, bottom = 8.dp)
                        .fillMaxWidth()
                        .height(settings.dockHeight.dp),
                )
            }
        }
        if (dockInBand) {
            Dock(
                items = layout.dock,
                settings = settings,
                drag = drag,
                highlighted = hover is DropTarget.Dock,
                callbacks = callbacks,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = band.offsetX)
                    .padding(bottom = band.bottomMargin)
                    .width(band.width)
                    .height((band.height - 12.dp).coerceAtMost(settings.dockHeight.dp)),
            )
        }
    }
}

@Composable
private fun GridPage(
    items: List<HomeItem>,
    settings: Settings,
    draggingId: Long?,
    hover: DropTarget.Cell?,
    widgetData: WidgetData,
    widgetActions: WidgetActions,
    callbacks: HomeCallbacks,
) {
    val cols = settings.gridColumns
    val rows = settings.gridRows
    val onHold by rememberUpdatedState(callbacks.onBackgroundHold)
    val onDoubleTap by rememberUpdatedState(callbacks.onBackgroundDoubleTap)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { onDoubleTap() }, onLongPress = { onHold() })
            },
    ) {
        val cellW = maxWidth / cols
        val cellH = maxHeight / rows
        val cellWPx = constraints.maxWidth.toFloat() / cols
        val cellHPx = constraints.maxHeight.toFloat() / rows
        val labelHeight = if (settings.showLabels) 17.dp else 0.dp
        val iconSize = (min(cellW * 0.68f, (cellH - labelHeight) * 0.82f) * settings.iconScale).coerceAtMost(72.dp)

        if (hover != null) {
            Box(
                modifier = Modifier
                    .offset { IntOffset((hover.slot.x * cellWPx).toInt(), (hover.slot.y * cellHPx).toInt()) }
                    .size(cellW * hover.spanX, cellH * hover.spanY)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = 0.16f))
                    .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(22.dp)),
            )
        }

        for (item in items) {
            key(item.id) {
                val x = item.cellX.coerceIn(0, cols - 1)
                val y = item.cellY.coerceIn(0, rows - 1)
                val spanX = item.spanX.coerceIn(1, cols - x)
                val spanY = item.spanY.coerceIn(1, rows - y)
                val position by animateIntOffsetAsState(
                    targetValue = IntOffset((x * cellWPx).toInt(), (y * cellHPx).toInt()),
                    animationSpec = Motion.popOf(),
                    label = "posição do item",
                )
                Box(
                    modifier = Modifier
                        .offset { position }
                        .size(cellW * spanX, cellH * spanY)
                        .graphicsLayer { alpha = if (draggingId == item.id) 0f else 1f },
                    contentAlignment = Alignment.Center,
                ) {
                    when (item) {
                        is HomeItem.Widget -> WidgetHolder(item, widgetData, widgetActions, callbacks)
                        else -> HomeIcon(
                            item = item,
                            iconSize = iconSize,
                            showLabel = settings.showLabels,
                            inDock = false,
                            callbacks = callbacks,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetHolder(
    item: HomeItem.Widget,
    widgetData: WidgetData,
    widgetActions: WidgetActions,
    callbacks: HomeCallbacks,
) {
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val current by rememberUpdatedState(item)
    val onHold by rememberUpdatedState(callbacks.onItemHold)
    val onDrag by rememberUpdatedState(callbacks.onItemDrag)
    val onRelease by rememberUpdatedState(callbacks.onItemRelease)
    if (item.kind == WidgetKind.APP_WIDGET) {
        // A view do widget fica com os toques; o menu abre pelo botão de três pontos.
        AppWidgetCell(
            appWidgetId = item.appWidgetId,
            modifier = Modifier.fillMaxSize().padding(5.dp).onGloballyPositioned { coordinates = it },
            onMenu = {
                val c = coordinates?.takeIf { it.isAttached }
                val at = c?.localToRoot(Offset(c.size.width / 2f, c.size.height / 2f)) ?: Offset.Zero
                onHold(current, false, at)
                onRelease()
            },
        )
        return
    }
    // O detector de arrasto vai "por dentro" do clique do card, para receber os toques primeiro.
    val dragModifier = Modifier
        .onGloballyPositioned { coordinates = it }
        .pointerInput(item.id) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    val root = coordinates?.takeIf { it.isAttached }?.localToRoot(offset) ?: offset
                    onHold(current, false, root)
                },
                onDragEnd = { onRelease() },
                onDragCancel = { onRelease() },
                onDrag = { change, amount ->
                    change.consume()
                    onDrag(amount)
                },
            )
        }
    WidgetCard(
        kind = item.kind,
        data = widgetData,
        actions = widgetActions,
        modifier = Modifier.fillMaxSize().padding(5.dp),
        // Declarado só para que soltar depois de um toque longo não conte como clique.
        onLongClick = { },
        gestureModifier = dragModifier,
        extra = item.extra,
    )
}

/** Ícone de app ou pasta com rótulo, usado na grade e no dock. */
@Composable
fun HomeIcon(
    item: HomeItem,
    iconSize: Dp,
    showLabel: Boolean,
    inDock: Boolean,
    callbacks: HomeCallbacks,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = if (pressed) Motion.press else Motion.release,
        label = "escala do ícone",
    )
    val current by rememberUpdatedState(item)
    val onApp by rememberUpdatedState(callbacks.onAppClick)
    val onFolder by rememberUpdatedState(callbacks.onFolderClick)
    val onHold by rememberUpdatedState(callbacks.onItemHold)
    val onDrag by rememberUpdatedState(callbacks.onItemDrag)
    val onRelease by rememberUpdatedState(callbacks.onItemRelease)

    Column(
        modifier = modifier
            .onGloballyPositioned { coordinates = it }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(item.id) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    // O toque longo é tratado pelo detector de arrasto abaixo; declarar aqui só
                    // impede que soltar depois de um toque longo conte como toque simples.
                    onLongPress = { },
                    onTap = {
                        when (val target = current) {
                            is HomeItem.App -> onApp(target.app)
                            is HomeItem.Folder -> onFolder(target)
                            is HomeItem.Widget -> Unit
                        }
                    },
                )
            }
            .pointerInput(item.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val root = coordinates?.takeIf { it.isAttached }?.localToRoot(offset) ?: offset
                        onHold(current, inDock, root)
                    },
                    onDragEnd = { onRelease() },
                    onDragCancel = { onRelease() },
                    onDrag = { change, amount ->
                        change.consume()
                        onDrag(amount)
                    },
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (item) {
            is HomeItem.App -> AppIconImage(app = item.app, size = iconSize)
            is HomeItem.Folder -> FolderIcon(item, iconSize)
            is HomeItem.Widget -> Unit
        }
        if (showLabel) {
            val label = when (item) {
                is HomeItem.App -> item.label
                is HomeItem.Folder -> item.title.ifBlank { "Pasta" }
                is HomeItem.Widget -> ""
            }
            IconLabel(label, Modifier.padding(top = 3.dp).width(iconSize + 26.dp))
        }
    }
}

@Composable
fun IconLabel(text: String, modifier: Modifier = Modifier, color: Color = Color.White) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = TextStyle(
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            shadow = if (color == Color.White) WallpaperTextShadow else null,
            textAlign = TextAlign.Center,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Ícone de pasta: placa de vidro na forma escolhida com a prévia dos quatro primeiros apps. */
@Composable
fun FolderIcon(folder: HomeItem.Folder, size: Dp, modifier: Modifier = Modifier) {
    val shape = LocalIconLook.current.shape.toShape()
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.22f))
            .border(0.75.dp, Color.White.copy(alpha = 0.42f), shape),
        contentAlignment = Alignment.Center,
    ) {
        val mini = size * 0.30f
        val gap = size * 0.06f
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            for (row in 0..1) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (col in 0..1) {
                        val child = folder.children.getOrNull(row * 2 + col)
                        if (child != null) AppIconImage(app = child.app, size = mini) else Box(Modifier.size(mini))
                    }
                }
            }
        }
    }
}

@Composable
private fun Dock(
    items: List<HomeItem>,
    settings: Settings,
    drag: DragController,
    highlighted: Boolean,
    callbacks: HomeCallbacks,
    modifier: Modifier = Modifier,
) {
    val draggingId = if (drag.moving) drag.item?.id else null
    val radius = settings.dockRadius.dp
    val material = when (settings.dockStyle) {
        DockStyle.GLASS -> LiquidMaterial.LIQUID
        DockStyle.FROSTED -> LiquidMaterial.FROSTED
        DockStyle.DARK -> LiquidMaterial.DARK
        DockStyle.SOLID -> LiquidMaterial.SOLID
        DockStyle.NONE -> null
    }
    val highlight = if (highlighted) Modifier.border(2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(radius)) else Modifier
    val content: @Composable BoxScope.() -> Unit = {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val slots = items.size.coerceAtLeast(1)
            // Com nomes, o ícone divide a altura com o texto.
            val heightShare = if (settings.dockLabels) 0.56f else 0.70f
            val iconSize = (maxHeight * heightShare * settings.dockIconScale).coerceAtMost(maxWidth / slots * 0.9f).coerceAtMost(maxHeight * 0.9f)
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (item in items) {
                    key(item.id) {
                        HomeIcon(
                            item = item,
                            iconSize = iconSize,
                            showLabel = settings.dockLabels,
                            inDock = true,
                            callbacks = callbacks,
                            modifier = Modifier
                                .fillMaxHeight()
                                .graphicsLayer { alpha = if (draggingId == item.id) 0f else 1f },
                        )
                    }
                }
            }
        }
    }
    val bounds = modifier.onGloballyPositioned { drag.dockBounds = it.boundsInRoot() }.then(highlight)
    if (material == null) {
        Box(bounds, content = content)
    } else {
        LiquidSurface(material, bounds, cornerRadius = radius, content = content)
    }
}

/** O item que acompanha o dedo durante o arrasto. */
@Composable
fun DragGhost(drag: DragController) {
    val item = drag.item ?: return
    if (!drag.moving) return
    val size = 62.dp
    Box(
        modifier = Modifier
            .offset {
                val half = (size.toPx() / 2f).toInt()
                IntOffset(drag.position.x.toInt() - half, drag.position.y.toInt() - half - 8.dp.roundToPx())
            }
            .size(size)
            .graphicsLayer {
                scaleX = 1.12f
                scaleY = 1.12f
                alpha = 0.94f
            },
        contentAlignment = Alignment.Center,
    ) {
        when (item) {
            is HomeItem.App -> AppIconImage(app = item.app, size = size)
            is HomeItem.Folder -> FolderIcon(item, size)
            is HomeItem.Widget -> Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.3f))
                    .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(18.dp)),
            )
        }
    }
}
