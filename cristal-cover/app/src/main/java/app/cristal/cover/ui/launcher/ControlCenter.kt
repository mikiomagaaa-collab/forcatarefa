package app.cristal.cover.ui.launcher

import android.text.format.DateFormat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LastPage
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.ScreenLockRotation
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cristal.cover.data.control.ControlGrid
import app.cristal.cover.data.control.ControlGroup
import app.cristal.cover.data.control.ControlKind
import app.cristal.cover.data.control.ControlLayout
import app.cristal.cover.data.control.ControlModule
import app.cristal.cover.data.control.ControlPreset
import app.cristal.cover.data.control.ModuleSize
import app.cristal.cover.data.home.WidgetKind
import app.cristal.cover.data.notifications.NotificationItem
import app.cristal.cover.data.system.QuickState
import app.cristal.cover.ui.components.PageDots
import app.cristal.cover.ui.components.SectionLabel
import app.cristal.cover.ui.components.SheetState
import app.cristal.cover.ui.components.WallpaperTextShadow
import app.cristal.cover.ui.glass.GlassIconButton
import app.cristal.cover.ui.glass.GlassSurface
import app.cristal.cover.ui.glass.LocalGlassSpec
import app.cristal.cover.ui.glass.rememberHaptic
import app.cristal.cover.ui.theme.ClockTextStyle
import app.cristal.cover.ui.widgets.WidgetActions
import app.cristal.cover.ui.widgets.WidgetCard
import app.cristal.cover.ui.widgets.WidgetData
import app.cristal.cover.ui.widgets.formatDate
import app.cristal.cover.ui.widgets.formatTime
import app.cristal.cover.ui.widgets.rememberMinuteTicker
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Stable
class ControlCenterActions(
    /** Toque num módulo (ações de UI já tratadas por quem chama; o resto vai para o ViewModel). */
    val onControl: (ControlKind) -> Unit,
    /** Toque longo: abre a tela do sistema ligada ao módulo. */
    val onControlSettings: (ControlKind) -> Unit,
    val onBrightness: (Float) -> Unit,
    val onBrightnessEnd: () -> Unit,
    val onAutoBrightness: () -> Unit,
    val onVolume: (Float) -> Unit,
    val onSaveLayout: (ControlLayout) -> Unit,
    val onPreset: (ControlPreset) -> Unit,
    val onSystemPanel: () -> Unit,
    val onOpenNotification: (NotificationItem) -> Unit,
    val onDismissNotification: (NotificationItem) -> Unit,
    val onClearAll: () -> Unit,
    val onRequestNotificationAccess: () -> Unit,
)

/**
 * Liquid Control Center: desce do topo com páginas de módulos de vidro (que o usuário edita,
 * redimensiona e reorganiza) e, por último, as notificações.
 */
@Composable
fun ControlCenter(
    state: SheetState,
    safe: SafeLayout,
    quick: QuickState,
    layout: ControlLayout,
    widgetData: WidgetData,
    widgetActions: WidgetActions,
    notifications: List<NotificationItem>,
    notificationAccess: Boolean,
    onCover: Boolean,
    actions: ControlCenterActions,
) {
    val visible by remember { derivedStateOf { state.progress.value > 0.001f } }
    if (!visible) return
    val scope = rememberCoroutineScope()
    val now = rememberMinuteTicker().value
    val is24h = DateFormat.is24HourFormat(LocalContext.current)

    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(layout) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var adding by remember { mutableStateOf(false) }
    var choosingPreset by remember { mutableStateOf(false) }
    // Fechar a central encerra a edição sem salvar.
    if (!state.isOpenOrOpening && editing && state.progress.value < 0.05f) {
        editing = false
        selectedId = null
    }

    val shown = if (editing) draft else layout
    val controlPages = shown.pages.ifEmpty { listOf(emptyList()) }
    val showNotifications = shown.notificationsPage && !editing
    val pageCount = controlPages.size + if (showNotifications) 1 else 0
    val pager = rememberPagerState { pageCount }

    fun editDraft(change: (ControlLayout) -> ControlLayout) {
        draft = change(draft)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val connection = rememberSheetNestedScroll(state, heightPx, closeSign = -1f)
        val dragState = rememberDraggableState { delta -> scope.launch { state.dragBy(delta / heightPx) } }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, (-(1f - state.progress.value) * heightPx).roundToInt()) }
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(start = safe.start, end = safe.end, top = safe.top, bottom = safe.bottom),
        ) {
            // Cabeçalho
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (editing) {
                    Text(
                        "Editar central",
                        color = Color.White,
                        style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, shadow = WallpaperTextShadow),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    GlassButton("Cancelar", Modifier.width(96.dp)) {
                        editing = false
                        selectedId = null
                    }
                    Spacer(Modifier.width(8.dp))
                    GlassButton("Concluir", Modifier.width(96.dp), primary = true) {
                        actions.onSaveLayout(draft.cleaned())
                        editing = false
                        selectedId = null
                    }
                } else {
                    Column(Modifier.weight(1f)) {
                        Text(
                            formatTime(now, is24h),
                            style = ClockTextStyle.copy(shadow = WallpaperTextShadow),
                            fontSize = 30.sp,
                            color = Color.White,
                            maxLines = 1,
                        )
                        Text(
                            formatDate(now),
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    GlassIconButton(Icons.Rounded.Edit, "Editar a central", size = 40.dp) {
                        draft = layout.cleaned()
                        selectedId = null
                        editing = true
                        if (pager.currentPage >= draft.pages.size) scope.launch { pager.scrollToPage(0) }
                    }
                    Spacer(Modifier.width(8.dp))
                    GlassIconButton(Icons.Rounded.Tune, "Painel do sistema", size = 40.dp, onClick = actions.onSystemPanel)
                }
            }

            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                key = { index -> if (index < controlPages.size) "controles-$index" else "notificacoes" },
            ) { page ->
                if (page < controlPages.size) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .nestedScroll(connection)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        ControlGridView(
                            modules = controlPages[page],
                            onCover = onCover,
                            quick = quick,
                            widgetData = widgetData,
                            widgetActions = widgetActions,
                            editing = editing,
                            selectedId = selectedId,
                            actions = actions,
                            onSelect = { id -> selectedId = if (selectedId == id) null else id },
                            onRemove = { id ->
                                editDraft { it.removeModule(id) }
                                if (selectedId == id) selectedId = null
                            },
                        )
                        if (editing && controlPages[page].isEmpty()) {
                            Text(
                                "Página vazia. Toque em \"Adicionar\" para colocar controles aqui.",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                            )
                        }
                    }
                } else {
                    NotificationsPage(notifications, notificationAccess, connection, actions)
                }
            }

            if (editing) {
                EditToolbar(
                    selected = draft.pages.flatten().firstOrNull { it.id == selectedId },
                    onAdd = { adding = true },
                    onPresets = { choosingPreset = true },
                    onNewPage = {
                        editDraft { it.copy(pages = it.pages + listOf(emptyList())) }
                        scope.launch { pager.animateScrollToPage(draft.pages.size - 1) }
                    },
                    onMove = { delta -> selectedId?.let { id -> editDraft { it.moveModule(id, delta) } } },
                    onResize = { selectedId?.let { id -> editDraft { it.resizeModule(id) } } },
                    onNextPage = {
                        selectedId?.let { id ->
                            editDraft { it.moveModuleToNextPage(id) }
                            val target = draft.pages.indexOfFirst { p -> p.any { m -> m.id == id } }
                            if (target >= 0) scope.launch { pager.animateScrollToPage(target) }
                        }
                    },
                    onRemove = {
                        selectedId?.let { id -> editDraft { it.removeModule(id) } }
                        selectedId = null
                    },
                    notificationsPage = draft.notificationsPage,
                    onToggleNotifications = { editDraft { it.copy(notificationsPage = !it.notificationsPage) } },
                )
            }

            Box(Modifier.fillMaxWidth().padding(top = 2.dp), contentAlignment = Alignment.Center) {
                PageDots(count = pageCount, position = { pager.currentPage + pager.currentPageOffsetFraction })
            }
            // Alça para fechar arrastando para cima.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity -> state.settle(velocity / heightPx) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.width(38.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.55f)))
            }
        }

        if (adding) {
            AddControlDialog(
                existing = draft.pages.flatten().map { it.kind }.toSet(),
                onDismiss = { adding = false },
            ) { kind ->
                val page = pager.currentPage.coerceIn(0, (draft.pages.size - 1).coerceAtLeast(0))
                editDraft { it.addModule(kind, page) }
                adding = false
            }
        }
        if (choosingPreset) {
            GlassDialog(onDismiss = { choosingPreset = false }) {
                Text("Predefinições", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (onCover) "Vale para a central da tela externa." else "Vale para a central da tela interna.",
                    fontSize = 12.sp,
                    color = LocalGlassSpec.current.secondaryContentColor,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                for (preset in ControlPreset.entries) {
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        cornerRadius = 18.dp,
                        elevation = 3.dp,
                        onClick = {
                            actions.onPreset(preset)
                            choosingPreset = false
                            editing = false
                            selectedId = null
                            scope.launch { pager.scrollToPage(0) }
                        },
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(preset.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(preset.description, fontSize = 12.sp, color = LocalGlassSpec.current.secondaryContentColor)
                        }
                    }
                }
            }
        }
    }
}

// --- Grade de módulos ---------------------------------------------------------------------------

@Composable
private fun ControlGridView(
    modules: List<ControlModule>,
    onCover: Boolean,
    quick: QuickState,
    widgetData: WidgetData,
    widgetActions: WidgetActions,
    editing: Boolean,
    selectedId: Long?,
    actions: ControlCenterActions,
    onSelect: (Long) -> Unit,
    onRemove: (Long) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = if (onCover) 9.dp else 12.dp
        val cell = ((maxWidth - gap * 3) / 4).coerceAtMost(if (onCover) 86.dp else 92.dp)
        val gridWidth = cell * 4 + gap * 3
        val (placements, rows) = remember(modules) { ControlGrid.pack(modules) }
        val height = if (rows == 0) 0.dp else cell * rows + gap * (rows - 1)
        Box(Modifier.align(Alignment.TopCenter).width(gridWidth).height(height)) {
            for (p in placements) {
                key(p.module.id) {
                    val w = cell * p.module.size.w + gap * (p.module.size.w - 1)
                    val h = cell * p.module.size.h + gap * (p.module.size.h - 1)
                    val x = (cell + gap) * p.x
                    val y = (cell + gap) * p.y
                    EditableModule(
                        module = p.module,
                        editing = editing,
                        selected = selectedId == p.module.id,
                        onSelect = { onSelect(p.module.id) },
                        onRemove = { onRemove(p.module.id) },
                        modifier = Modifier.offset(x, y).size(w, h),
                    ) {
                        ControlModuleView(
                            module = p.module,
                            cell = cell,
                            quick = quick,
                            widgetData = widgetData,
                            widgetActions = widgetActions,
                            enabled = !editing,
                            actions = actions,
                        )
                    }
                }
            }
        }
    }
}

/** No modo de edição: balança de leve, mostra o "−" e marca o módulo escolhido. */
@Composable
private fun EditableModule(
    module: ControlModule,
    editing: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    if (!editing) {
        Box(modifier) { content() }
        return
    }
    val wiggle = rememberInfiniteTransition(label = "balanço")
    val angle by wiggle.animateFloat(
        initialValue = -0.9f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(260 + (module.id % 5).toInt() * 18, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ângulo",
    )
    val haptic = rememberHaptic()
    Box(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = if (selected) 0f else angle }
                // Passo "Initial": o toque chega aqui antes dos controles de dentro (que ficam inertes).
                .pointerInput(module.id) {
                    awaitEachGesture {
                        val down = awaitFirstDown(pass = PointerEventPass.Initial)
                        down.consume()
                        val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                        if (up != null) {
                            up.consume()
                            haptic()
                            onSelect()
                        }
                    }
                },
        ) {
            content()
            if (selected) {
                Box(Modifier.fillMaxSize().border(2.dp, Color.White, RoundedCornerShape(24.dp)))
            }
        }
        // O "−" é irmão da área de seleção (e fica por cima), então recebe o próprio toque.
        Box(
            Modifier
                .offset((-6).dp, (-6).dp)
                .size(26.dp)
                .clip(CircleShape)
                .background(Color(0xE6202228))
                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                .pointerInput(module.id) { detectTapGestures { onRemove() } },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Remove, contentDescription = "Remover ${module.kind.label}", tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

// --- Módulos ------------------------------------------------------------------------------------

@Composable
private fun ControlModuleView(
    module: ControlModule,
    cell: Dp,
    quick: QuickState,
    widgetData: WidgetData,
    widgetActions: WidgetActions,
    enabled: Boolean,
    actions: ControlCenterActions,
) {
    val kind = module.kind
    val tap: (() -> Unit)? = if (enabled) ({ actions.onControl(kind) }) else null
    val hold: (() -> Unit)? = if (enabled) ({ actions.onControlSettings(kind) }) else null
    when (kind) {
        ControlKind.CONNECTIVITY -> ConnectivityModule(module.size, cell, quick, enabled, actions)
        ControlKind.BRIGHTNESS -> SliderModule(
            moduleSize = module.size,
            value = quick.brightness,
            icon = if (quick.autoBrightness) Icons.Rounded.BrightnessAuto else Icons.Rounded.BrightnessMedium,
            label = "Brilho",
            enabled = enabled,
            onChange = actions.onBrightness,
            onEnd = actions.onBrightnessEnd,
            onIconClick = actions.onAutoBrightness,
        )
        ControlKind.VOLUME -> SliderModule(
            moduleSize = module.size,
            value = quick.mediaVolume,
            icon = Icons.AutoMirrored.Rounded.VolumeUp,
            label = "Volume",
            enabled = enabled,
            onChange = actions.onVolume,
            onEnd = {},
            onIconClick = null,
        )
        ControlKind.MEDIA -> MediaModule(module.size, widgetData, widgetActions, enabled)
        ControlKind.BATTERY -> BatteryModule(module.size, widgetData, tap, hold)
        else -> ToggleModule(
            kind = kind,
            size = module.size,
            cell = cell,
            active = isActive(kind, quick),
            status = statusOf(kind, quick),
            onClick = tap,
            onLongClick = hold,
        )
    }
}

@Composable
private fun ToggleModule(
    kind: ControlKind,
    size: ModuleSize,
    cell: Dp,
    active: Boolean,
    status: String,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
) {
    val accent = accentOf(kind)
    val icon = iconOf(kind, active)
    if (size == ModuleSize.SMALL) {
        // Botão redondo, como no iOS 26: aceso com a cor do controle.
        GlassSurface(
            modifier = Modifier.fillMaxSize(),
            cornerRadius = cell / 2,
            tint = if (active) accent.copy(alpha = 0.88f) else Color.Unspecified,
            elevation = 6.dp,
            onClick = onClick,
            onLongClick = onLongClick,
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = kind.label, tint = if (active) onAccent(accent) else LocalGlassSpec.current.contentColor, modifier = Modifier.size(cell * 0.36f))
        }
        return
    }
    GlassSurface(
        modifier = Modifier.fillMaxSize(),
        cornerRadius = cell / 2,
        elevation = 6.dp,
        onClick = onClick,
        onLongClick = onLongClick,
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(cell * 0.56f)
                    .clip(CircleShape)
                    .background(if (active) accent else LocalGlassSpec.current.contentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = if (active) onAccent(accent) else LocalGlassSpec.current.contentColor, modifier = Modifier.size(cell * 0.3f))
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(kind.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(status, fontSize = 11.sp, color = LocalGlassSpec.current.secondaryContentColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ConnectivityModule(size: ModuleSize, cell: Dp, quick: QuickState, enabled: Boolean, actions: ControlCenterActions) {
    val kinds = listOf(ControlKind.AIRPLANE, ControlKind.MOBILE_DATA, ControlKind.WIFI, ControlKind.BLUETOOTH)
    GlassSurface(
        modifier = Modifier.fillMaxSize(),
        cornerRadius = if (size == ModuleSize.LARGE) cell * 0.42f else cell / 2,
        elevation = 8.dp,
        reactToTouch = false,
    ) {
        val button = cell * 0.66f
        @Composable
        fun Item(kind: ControlKind) {
            val active = isActive(kind, quick)
            val accent = accentOf(kind)
            GlassSurface(
                modifier = Modifier.size(button),
                cornerRadius = button / 2,
                tint = if (active) accent.copy(alpha = 0.9f) else Color.Unspecified,
                elevation = 3.dp,
                onClick = if (enabled) ({ actions.onControl(kind) }) else null,
                onLongClick = if (enabled) ({ actions.onControlSettings(kind) }) else null,
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    iconOf(kind, active),
                    contentDescription = kind.label,
                    tint = if (active) onAccent(accent) else LocalGlassSpec.current.contentColor,
                    modifier = Modifier.size(button * 0.46f),
                )
            }
        }
        if (size == ModuleSize.LARGE) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { Item(kinds[0]); Item(kinds[1]) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { Item(kinds[2]); Item(kinds[3]) }
            }
        } else {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                for (k in kinds) Item(k)
            }
        }
    }
}

/** Controle deslizante: vertical (alto, como no iOS) ou horizontal (faixa). */
@Composable
private fun SliderModule(
    moduleSize: ModuleSize,
    value: Float,
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onChange: (Float) -> Unit,
    onEnd: () -> Unit,
    onIconClick: (() -> Unit)?,
) {
    if (moduleSize != ModuleSize.TALL) {
        Box(Modifier.fillMaxSize()) {
            GlassSlider(
                value = value,
                onValueChange = { if (enabled) onChange(it) },
                icon = icon,
                modifier = Modifier.fillMaxSize(),
            )
            if (onIconClick != null && enabled) {
                // Toque no ícone: liga/desliga o automático.
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .size(48.dp)
                        .pointerInput(Unit) { detectTapGestures { onIconClick() } },
                )
            }
        }
        return
    }
    val spec = LocalGlassSpec.current
    val change by rememberUpdatedState(onChange)
    val end by rememberUpdatedState(onEnd)
    var heightPx by remember { mutableIntStateOf(1) }
    GlassSurface(
        modifier = Modifier.fillMaxSize(),
        cornerRadius = 30.dp,
        elevation = 6.dp,
        reactToTouch = false,
        clipContent = true,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { heightPx = it.height.coerceAtLeast(1) }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset ->
                        change((1f - offset.y / heightPx).coerceIn(0f, 1f))
                        end()
                    }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectVerticalDragGestures(
                        onDragEnd = { end() },
                        onDragCancel = { end() },
                    ) { pointer, _ ->
                        pointer.consume()
                        change((1f - pointer.position.y / heightPx).coerceIn(0f, 1f))
                    }
                }
                .drawBehind {
                    val fill = size.height * value.coerceIn(0f, 1f)
                    drawRoundRect(
                        color = spec.contentColor.copy(alpha = 0.85f),
                        topLeft = Offset(0f, size.height - fill),
                        size = Size(size.width, fill),
                        cornerRadius = CornerRadius(0f),
                    )
                },
            contentAlignment = Alignment.BottomCenter,
        ) {
            val iconTint = if (value > 0.16f) Color(0xFF16181D) else spec.contentColor
            Icon(
                icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier
                    .padding(bottom = 14.dp)
                    .size(24.dp)
                    .then(
                        if (onIconClick != null && enabled) {
                            Modifier.pointerInput(Unit) { detectTapGestures { onIconClick() } }
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }
}

@Composable
private fun MediaModule(size: ModuleSize, data: WidgetData, actions: WidgetActions, enabled: Boolean) {
    val media = data.media
    val spec = LocalGlassSpec.current
    GlassSurface(
        modifier = Modifier.fillMaxSize(),
        cornerRadius = if (size == ModuleSize.BAR) 40.dp else 30.dp,
        elevation = 6.dp,
        onClick = if (enabled) actions.onMediaOpen else null,
        clipContent = true,
    ) {
        @Composable
        fun Art(side: Dp) {
            Box(
                Modifier.size(side).clip(RoundedCornerShape(side * 0.22f)).background(spec.contentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                val art = media?.art
                if (art != null) {
                    androidx.compose.foundation.Image(
                        bitmap = art,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(side * 0.5f))
                }
            }
        }

        @Composable
        fun Titles(modifier: Modifier) {
            Column(modifier) {
                Text(
                    media?.title?.ifBlank { null } ?: "Nada tocando",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    media?.artist?.ifBlank { null } ?: if (data.notificationAccess) "Toque para abrir o player" else "Precisa do acesso às notificações",
                    fontSize = 12.sp,
                    color = spec.secondaryContentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        @Composable
        fun Controls(modifier: Modifier, button: Dp) {
            Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                MediaButton(Icons.Rounded.SkipPrevious, "Anterior", button, enabled && media?.canSkipPrevious == true, actions.onMediaPrevious)
                MediaButton(
                    if (media?.isPlaying == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (media?.isPlaying == true) "Pausar" else "Tocar",
                    button * 1.2f,
                    enabled && media != null,
                    actions.onMediaPlayPause,
                )
                MediaButton(Icons.Rounded.SkipNext, "Próxima", button, enabled && media?.canSkipNext == true, actions.onMediaNext)
            }
        }

        when (size) {
            ModuleSize.BAR -> Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Art(44.dp)
                Spacer(Modifier.width(10.dp))
                Titles(Modifier.weight(1f))
                Controls(Modifier.width(132.dp), 30.dp)
            }
            ModuleSize.PANEL -> Row(Modifier.fillMaxSize().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Art(112.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Titles(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    Controls(Modifier.fillMaxWidth(), 36.dp)
                }
            }
            else -> Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Art(46.dp)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = spec.secondaryContentColor, modifier = Modifier.size(16.dp))
                }
                Titles(Modifier.fillMaxWidth())
                Controls(Modifier.fillMaxWidth(), 30.dp)
            }
        }
    }
}

@Composable
private fun MediaButton(icon: ImageVector, label: String, size: Dp, enabled: Boolean, onClick: () -> Unit) {
    val spec = LocalGlassSpec.current
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .then(if (enabled) Modifier.pointerInput(onClick) { detectTapGestures { onClick() } } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (enabled) spec.contentColor else spec.secondaryContentColor.copy(alpha = 0.5f),
            modifier = Modifier.size(size * 0.8f),
        )
    }
}

@Composable
private fun BatteryModule(size: ModuleSize, data: WidgetData, onClick: (() -> Unit)?, onLongClick: (() -> Unit)?) {
    if (size == ModuleSize.LARGE) {
        WidgetCard(kind = WidgetKind.BATTERY, data = data, actions = WidgetActions(), modifier = Modifier.fillMaxSize(), cornerRadius = 30.dp)
        return
    }
    val battery = data.battery
    GlassSurface(
        modifier = Modifier.fillMaxSize(),
        cornerRadius = 40.dp,
        elevation = 6.dp,
        onClick = onClick,
        onLongClick = onLongClick,
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            Icon(
                if (battery.charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
                contentDescription = null,
                tint = if (battery.percent <= 15 && !battery.charging) Color(0xFFFF453A) else LocalGlassSpec.current.contentColor,
                modifier = Modifier.size(22.dp),
            )
            Text("${battery.percent}%", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (size == ModuleSize.WIDE) {
                Spacer(Modifier.width(6.dp))
                Text(
                    if (battery.charging) "Carregando" else "Bateria",
                    fontSize = 12.sp,
                    color = LocalGlassSpec.current.secondaryContentColor,
                    maxLines = 1,
                )
            }
        }
    }
}

// --- Barra de edição ----------------------------------------------------------------------------

@Composable
private fun EditToolbar(
    selected: ControlModule?,
    onAdd: () -> Unit,
    onPresets: () -> Unit,
    onNewPage: () -> Unit,
    onMove: (Int) -> Unit,
    onResize: () -> Unit,
    onNextPage: () -> Unit,
    onRemove: () -> Unit,
    notificationsPage: Boolean,
    onToggleNotifications: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
        if (selected != null) {
            Text(
                "${selected.kind.label} · ${selected.size.label}",
                color = Color.White,
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, shadow = WallpaperTextShadow),
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                GlassIconButton(Icons.Rounded.ChevronLeft, "Mover para trás", size = 44.dp) { onMove(-1) }
                GlassIconButton(Icons.Rounded.ChevronRight, "Mover para frente", size = 44.dp) { onMove(1) }
                GlassIconButton(Icons.Rounded.OpenInFull, "Mudar tamanho", size = 44.dp, onClick = onResize)
                GlassIconButton(Icons.Rounded.LastPage, "Mandar para a próxima página", size = 44.dp, onClick = onNextPage)
                GlassIconButton(Icons.Rounded.Delete, "Remover", size = 44.dp, onClick = onRemove)
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton("Adicionar", Modifier.weight(1f), primary = true, onClick = onAdd)
                GlassButton("Nova página", Modifier.weight(1f), onClick = onNewPage)
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton("Predefinições", Modifier.weight(1f), onClick = onPresets)
                GlassButton(if (notificationsPage) "Notificações: sim" else "Notificações: não", Modifier.weight(1f), onClick = onToggleNotifications)
            }
            Text(
                "Toque num controle para movê-lo, mudar o tamanho ou removê-lo.",
                color = Color.White.copy(alpha = 0.8f),
                style = TextStyle(fontSize = 11.sp, shadow = WallpaperTextShadow),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun AddControlDialog(existing: Set<ControlKind>, onDismiss: () -> Unit, onAdd: (ControlKind) -> Unit) {
    GlassDialog(onDismiss = onDismiss, maxWidth = 380.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Adicionar controle", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            GlassIconButton(Icons.Rounded.Close, "Fechar", size = 36.dp, onClick = onDismiss)
        }
        LazyColumn(Modifier.fillMaxWidth().height(340.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            for (group in ControlGroup.entries) {
                val kinds = ControlKind.entries.filter { it.group == group }
                item(key = "g-${group.name}") {
                    SectionLabel(group.label, Modifier.padding(top = 10.dp, bottom = 4.dp, start = 4.dp))
                }
                items(kinds, key = { it.name }) { kind ->
                    val already = kind in existing
                    GlassSurface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        cornerRadius = 18.dp,
                        elevation = 2.dp,
                        onClick = { onAdd(kind) },
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(iconOf(kind, false), contentDescription = null, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(kind.label, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
                            if (already) {
                                Text("na central", fontSize = 11.sp, color = LocalGlassSpec.current.secondaryContentColor)
                            } else {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Página de notificações ---------------------------------------------------------------------

@Composable
private fun NotificationsPage(
    notifications: List<NotificationItem>,
    notificationAccess: Boolean,
    connection: androidx.compose.ui.input.nestedscroll.NestedScrollConnection,
    actions: ControlCenterActions,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().nestedScroll(connection),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Notificações", Modifier.weight(1f), color = Color.White)
                if (notifications.any { it.isClearable }) {
                    GlassSurface(cornerRadius = 15.dp, elevation = 4.dp, onClick = actions.onClearAll) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.ClearAll, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Limpar", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
        when {
            !notificationAccess -> item(key = "no-access") {
                GlassSurface(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp, onClick = actions.onRequestNotificationAccess) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.NotificationsNone, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Toque para ativar o acesso às notificações e vê-las aqui.", fontSize = 13.sp)
                    }
                }
            }
            notifications.isEmpty() -> item(key = "empty") {
                Text(
                    "Nenhuma notificação",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                )
            }
            else -> items(notifications, key = { it.key }) { item ->
                SwipeDismiss(enabled = item.isClearable, onDismiss = { actions.onDismissNotification(item) }) {
                    NotificationCard(item = item, concealed = false, onClick = { actions.onOpenNotification(item) })
                }
            }
        }
    }
}

// --- Estado, ícones e cores ---------------------------------------------------------------------

private fun isActive(kind: ControlKind, q: QuickState): Boolean = when (kind) {
    ControlKind.WIFI -> q.wifiOn == true
    ControlKind.BLUETOOTH -> q.bluetoothOn == true
    ControlKind.MOBILE_DATA -> q.mobileDataOn == true
    ControlKind.AIRPLANE -> q.airplaneOn
    ControlKind.NFC -> q.nfcOn == true
    ControlKind.LOCATION -> q.locationOn
    ControlKind.TORCH -> q.torchOn
    ControlKind.RINGER -> q.ringerMuted
    ControlKind.DND -> q.dndOn
    ControlKind.ROTATION -> q.autoRotate
    ControlKind.DARK_MODE -> q.darkModeOn
    ControlKind.BATTERY_SAVER -> q.batterySaverOn
    else -> false
}

private fun statusOf(kind: ControlKind, q: QuickState): String {
    val needsShell = kind in setOf(
        ControlKind.WIFI, ControlKind.BLUETOOTH, ControlKind.MOBILE_DATA, ControlKind.AIRPLANE, ControlKind.NFC,
        ControlKind.LOCATION, ControlKind.DARK_MODE, ControlKind.BATTERY_SAVER,
    )
    return when {
        kind == ControlKind.NFC && q.nfcOn == null -> "Sem NFC"
        kind == ControlKind.RINGER -> if (q.ringerMuted) "Vibrar" else "Som"
        kind == ControlKind.ROTATION && !q.canWriteSettings && !q.shizukuReady -> "Toque para permitir"
        kind == ControlKind.DND && !q.dndAccess && !q.shizukuReady -> "Toque para permitir"
        kind.toggle && needsShell && !q.shizukuReady -> if (isActive(kind, q)) "Ligado · abre o ajuste" else "Abre o ajuste"
        kind.toggle -> if (isActive(kind, q)) "Ligado" else "Desligado"
        else -> "Abrir"
    }
}

private fun iconOf(kind: ControlKind, active: Boolean): ImageVector = when (kind) {
    ControlKind.CONNECTIVITY, ControlKind.WIFI -> Icons.Rounded.Wifi
    ControlKind.BLUETOOTH -> Icons.Rounded.Bluetooth
    ControlKind.MOBILE_DATA -> Icons.Rounded.SignalCellularAlt
    ControlKind.AIRPLANE -> Icons.Rounded.AirplanemodeActive
    ControlKind.HOTSPOT -> Icons.Rounded.WifiTethering
    ControlKind.NFC -> Icons.Rounded.Nfc
    ControlKind.LOCATION -> Icons.Rounded.LocationOn
    ControlKind.TORCH -> if (active) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff
    ControlKind.RINGER -> if (active) Icons.Rounded.Vibration else Icons.Rounded.Notifications
    ControlKind.DND -> Icons.Rounded.DoNotDisturbOn
    ControlKind.ROTATION -> if (active) Icons.Rounded.ScreenRotation else Icons.Rounded.ScreenLockRotation
    ControlKind.DARK_MODE -> Icons.Rounded.DarkMode
    ControlKind.BATTERY_SAVER -> Icons.Rounded.BatterySaver
    ControlKind.SCREENSHOT -> Icons.Rounded.Screenshot
    ControlKind.LOCK -> Icons.Rounded.Lock
    ControlKind.RECENTS -> Icons.Rounded.History
    ControlKind.SYSTEM_PANEL -> Icons.Rounded.Tune
    ControlKind.BRIGHTNESS -> Icons.Rounded.BrightnessMedium
    ControlKind.VOLUME -> Icons.AutoMirrored.Rounded.VolumeUp
    ControlKind.MEDIA -> Icons.Rounded.MusicNote
    ControlKind.BATTERY -> Icons.Rounded.BatteryStd
    ControlKind.TIMER -> Icons.Rounded.Timer
    ControlKind.ALARM -> Icons.Rounded.Alarm
    ControlKind.CALCULATOR -> Icons.Rounded.Calculate
    ControlKind.CAMERA -> Icons.Rounded.PhotoCamera
    ControlKind.SEARCH -> Icons.Rounded.Search
    ControlKind.WALLPAPER -> Icons.Rounded.Wallpaper
    ControlKind.SETTINGS -> Icons.Rounded.Settings
}

/** Cor do controle aceso (a mesma lógica de cores do iOS: azul para rádios, laranja para avião…). */
private fun accentOf(kind: ControlKind): Color = when (kind) {
    ControlKind.WIFI, ControlKind.BLUETOOTH, ControlKind.NFC, ControlKind.LOCATION, ControlKind.ROTATION -> Color(0xFF0A84FF)
    ControlKind.MOBILE_DATA -> Color(0xFF30D158)
    ControlKind.AIRPLANE -> Color(0xFFFF9F0A)
    ControlKind.DND -> Color(0xFF7D6BFF)
    ControlKind.RINGER -> Color(0xFFFF453A)
    ControlKind.BATTERY_SAVER -> Color(0xFFFFD60A)
    ControlKind.DARK_MODE -> Color(0xFFB8A6FF)
    else -> Color.White
}

private fun onAccent(accent: Color): Color =
    if (accent == Color.White || accent == Color(0xFFFFD60A) || accent == Color(0xFFB8A6FF)) Color(0xFF16181D) else Color.White

// --- Edição do layout (funções puras) -----------------------------------------------------------

internal fun ControlLayout.removeModule(id: Long): ControlLayout =
    copy(pages = pages.map { page -> page.filterNot { it.id == id } })

internal fun ControlLayout.addModule(kind: ControlKind, page: Int): ControlLayout {
    val list = pages.ifEmpty { listOf(emptyList()) }.toMutableList()
    val target = page.coerceIn(0, list.size - 1)
    list[target] = list[target] + ControlModule(nextId, kind)
    return copy(pages = list)
}

internal fun ControlLayout.moveModule(id: Long, delta: Int): ControlLayout = copy(
    pages = pages.map { page ->
        val index = page.indexOfFirst { it.id == id }
        if (index < 0) return@map page
        val target = (index + delta).coerceIn(0, page.size - 1)
        if (target == index) return@map page
        page.toMutableList().apply { add(target, removeAt(index)) }
    },
)

internal fun ControlLayout.resizeModule(id: Long): ControlLayout = copy(
    pages = pages.map { page ->
        page.map { m ->
            if (m.id != id) {
                m
            } else {
                val sizes = m.kind.sizes
                m.copy(size = sizes[(sizes.indexOf(m.size) + 1).mod(sizes.size)])
            }
        }
    },
)

internal fun ControlLayout.moveModuleToNextPage(id: Long): ControlLayout {
    val from = pages.indexOfFirst { page -> page.any { it.id == id } }
    if (from < 0) return this
    val module = pages[from].first { it.id == id }
    val list = pages.toMutableList()
    list[from] = list[from].filterNot { it.id == id }
    if (from + 1 >= list.size) list.add(emptyList())
    list[from + 1] = listOf(module) + list[from + 1]
    return copy(pages = list)
}
