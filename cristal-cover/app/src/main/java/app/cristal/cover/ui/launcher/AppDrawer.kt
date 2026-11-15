package app.cristal.cover.ui.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cristal.cover.data.apps.AppInfo
import app.cristal.cover.data.prefs.Settings
import app.cristal.cover.ui.components.AppIconImage
import app.cristal.cover.ui.components.GlassTextField
import app.cristal.cover.ui.components.SheetState
import app.cristal.cover.ui.glass.Motion
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Liga a rolagem de uma lista ao painel que a contém: quando a lista chega ao fim e o dedo continua
 * puxando no sentido de fechar, quem se move é o painel.
 *
 * @param closeSign +1 se arrastar para baixo fecha (gaveta), -1 se arrastar para cima fecha (painel do topo).
 */
@Composable
fun rememberSheetNestedScroll(state: SheetState, extentPx: Float, closeSign: Float): NestedScrollConnection {
    val scope = rememberCoroutineScope()
    return remember(state, extentPx, closeSign) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val closing = available.y * closeSign
                // Painel parcialmente fechado e o dedo voltou no sentido de abrir: reabre antes de rolar a lista.
                if (state.progress.value < 1f && closing < 0f && extentPx > 0f) {
                    scope.launch { state.dragBy(-closing / extentPx) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val closing = available.y * closeSign
                if (closing > 0f && source == NestedScrollSource.UserInput && extentPx > 0f) {
                    scope.launch { state.dragBy(-closing / extentPx) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (state.progress.value < 1f && extentPx > 0f) {
                    state.settle(-(available.y * closeSign) / extentPx)
                    return available
                }
                return Velocity.Zero
            }
        }
    }
}

/** Gaveta de apps: sobe do rodapé sobre a tela inicial borrada, com busca no topo. */
@Composable
fun AppDrawer(
    state: SheetState,
    apps: List<AppInfo>,
    settings: Settings,
    safe: SafeLayout,
    onAppClick: (AppInfo) -> Unit,
    onAppHold: (AppInfo, Offset) -> Unit,
) {
    val visible by remember { derivedStateOf { state.progress.value > 0.001f } }
    if (!visible) return

    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(apps, query) { filterApps(apps, query) }
    val gridState = rememberLazyGridState()
    val open by remember { derivedStateOf { state.progress.value > 0.98f } }

    LaunchedEffect(open) {
        if (open && settings.drawerSearchOnOpen) {
            runCatching { focusRequester.requestFocus() }
        }
        if (!open) {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }
    LaunchedEffect(query) { gridState.scrollToItem(0) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val connection = rememberSheetNestedScroll(state, heightPx, closeSign = 1f)
        val dragState = rememberDraggableState { delta -> scope.launch { state.dragBy(-delta / heightPx) } }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, ((1f - state.progress.value) * heightPx).roundToInt()) }
                // Engole toques no fundo, para que nada "vaze" para a tela inicial por baixo.
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(start = safe.start, end = safe.end, top = safe.top)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity -> state.settle(-velocity / heightPx) },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .padding(top = 8.dp, bottom = 8.dp)
                        .width(38.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.55f)),
                )
                GlassTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Buscar apps",
                    leadingIcon = Icons.Rounded.Search,
                    focusRequester = focusRequester,
                    onImeAction = { filtered.firstOrNull()?.let(onAppClick) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            if (filtered.isEmpty()) {
                Text(
                    text = if (apps.isEmpty()) "Carregando apps…" else "Nenhum app encontrado para \"$query\"",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(28.dp),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(settings.drawerColumns),
                    state = gridState,
                    modifier = Modifier.weight(1f).fillMaxWidth().nestedScroll(connection),
                    contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 12.dp, bottom = safe.bottom + 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filtered, key = { it.key }) { app ->
                        DrawerIcon(
                            app = app,
                            iconScale = settings.iconScale,
                            showLabel = true,
                            onClick = { onAppClick(app) },
                            onHold = { at -> onAppHold(app, at) },
                        )
                    }
                }
            }
        }
    }
}

/** Ícone clicável com rótulo, usado na gaveta e dentro das pastas. */
@Composable
fun DrawerIcon(
    app: AppInfo,
    iconScale: Float,
    showLabel: Boolean,
    onClick: () -> Unit,
    onHold: (Offset) -> Unit,
    modifier: Modifier = Modifier,
    label: String = app.label,
) {
    var pressed by remember { mutableStateOf(false) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = if (pressed) Motion.press else Motion.release,
        label = "escala do ícone da gaveta",
    )
    val click by rememberUpdatedState(onClick)
    val hold by rememberUpdatedState(onHold)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates = it }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(app.key) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onLongPress = { offset ->
                        hold(coordinates?.takeIf { it.isAttached }?.localToRoot(offset) ?: offset)
                    },
                    onTap = { click() },
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIconImage(app = app, size = (54 * iconScale).dp)
        if (showLabel) IconLabel(label, Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp).fillMaxWidth())
    }
}
