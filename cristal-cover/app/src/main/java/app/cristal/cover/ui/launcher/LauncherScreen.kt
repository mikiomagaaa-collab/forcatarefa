package app.cristal.cover.ui.launcher

import app.cristal.cover.data.control.ControlKind
import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings as SystemSettings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cristal.cover.data.apps.AppInfo
import app.cristal.cover.data.home.CellSlot
import app.cristal.cover.data.home.HomeItem
import app.cristal.cover.data.home.HomeProfile
import app.cristal.cover.data.prefs.GestureAction
import app.cristal.cover.data.prefs.GlassQuality
import app.cristal.cover.data.prefs.WallpaperKind
import app.cristal.cover.data.shizuku.ShizukuStatus
import app.cristal.cover.data.weather.WeatherStatus
import app.cristal.cover.data.widgets.WidgetProviderEntry
import app.cristal.cover.ui.components.SheetState
import app.cristal.cover.ui.components.WallpaperLayer
import app.cristal.cover.ui.glass.LocalGlassBackdrop
import app.cristal.cover.ui.glass.Motion
import app.cristal.cover.ui.glass.glassBackdropSource
import app.cristal.cover.ui.glass.rememberDeviceTilt
import app.cristal.cover.ui.glass.rememberGlassBackdrop
import app.cristal.cover.ui.glass.rememberHaptic
import app.cristal.cover.ui.settings.SettingsActivity
import app.cristal.cover.ui.widgets.WidgetActions
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A tela inicial completa.
 *
 * Camadas, de baixo para cima:
 * 1. cena: papel de parede + páginas + dock (borra e recua quando algo abre por cima);
 * 2. véu escuro;
 * 3. sobreposições: pasta, gaveta, painel rápido, menus, diálogos e mensagens;
 * 4. o item sendo arrastado.
 *
 * O vidro da cena amostra só o papel de parede; o vidro das sobreposições amostra a cena inteira.
 */
@Composable
fun LauncherScreen(viewModel: LauncherViewModel, quality: GlassQuality) {
    val rawSettings by viewModel.settings.collectAsStateWithLifecycle()
    val home by viewModel.home.collectAsStateWithLifecycle()
    val widgetData by viewModel.widgetData.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val quick by viewModel.quick.collectAsStateWithLifecycle()
    val controlLayout by viewModel.controlLayout.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val cover by viewModel.cover.collectAsStateWithLifecycle()
    val wallpaperImages by viewModel.wallpapers.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val strongHaptic = rememberHaptic(strong = true)
    // Lidos por estado para que gestos antigos (já em andamento) usem sempre a tela atual.
    val displayIdNow = rememberDisplayId()
    val displayId by rememberUpdatedState(displayIdNow)
    val onCover by rememberUpdatedState(cover.cover?.id == displayIdNow)
    // Cada tela tem papel de parede, grade e tela inicial próprios.
    val settings = remember(rawSettings, onCover) { rawSettings.forScreen(onCover) }
    val wallpaper = wallpaperImages[settings.wallpaperSlot]
    LaunchedEffect(onCover) { viewModel.setProfile(HomeProfile.of(onCover)) }

    // Flip Transition: a tela "chega" com uma mola ao trocar de tela física, e recolhe conforme a
    // dobradiça fecha (quando o aparelho informa o ângulo).
    val arrival = remember { Animatable(1f) }
    LaunchedEffect(displayIdNow) {
        if (rawSettings.flipTransition) {
            arrival.snapTo(0f)
            arrival.animateTo(1f, Motion.pop)
        }
    }
    val hinge = cover.hingeAngle
    val foldTarget = if (rawSettings.flipTransition && !onCover && hinge != null) ((150f - hinge) / 90f).coerceIn(0f, 1f) else 0f
    val foldAmount by animateFloatAsState(foldTarget, Motion.gentle, label = "dobra")
    val safe = rememberSafeLayout(settings.useCutoutArea)
    // Papel de parede espacial: só com o vidro completo (no modo leve, economiza bateria).
    val homeTilt = rememberDeviceTilt(settings.wallpaperSpatial && quality != GlassQuality.LIGHT)
    val shizukuReady = permissions.shizuku == ShizukuStatus.READY

    val layout = home.layout
    val layoutState = rememberUpdatedState(layout)
    val settingsState = rememberUpdatedState(settings)
    val drag = remember { DragController() }
    val pagerState = rememberPagerState(initialPage = 1) { 1 + layout.pageCount + if (drag.moving) 1 else 0 }
    val drawer = remember { SheetState() }
    val panel = remember { SheetState() }
    val snackbar = remember { SnackbarController() }
    val snackJob = remember { arrayOfNulls<Job>(1) }

    var openFolderId by remember { mutableStateOf<Long?>(null) }
    var menu by remember { mutableStateOf<MenuState?>(null) }
    var homeMenu by remember { mutableStateOf(false) }
    var widgetPicker by remember { mutableStateOf(false) }
    var compat by remember { mutableStateOf<LauncherEvent.ConfirmCompat?>(null) }
    var appOptions by remember { mutableStateOf<AppInfo?>(null) }
    var blockedHelp by remember { mutableStateOf<AppInfo?>(null) }
    var appPicker by remember { mutableStateOf(false) }
    var pendingWidget by remember { mutableStateOf<Pair<Int, WidgetProviderEntry>?>(null) }

    val openFolder = openFolderId?.let { layout.findFolder(it) }
    val dialogOpen = menu != null || homeMenu || widgetPicker || appPicker || compat != null || appOptions != null || blockedHelp != null
    val overlayOpen = dialogOpen || openFolder != null

    val sceneBackdrop = rememberGlassBackdrop()
    val overlayBackdrop = rememberGlassBackdrop()
    val folderAmount by animateFloatAsState(if (openFolder != null) 1f else 0f, Motion.sheet, label = "pasta")
    val dialogAmount by animateFloatAsState(if (dialogOpen) 1f else 0f, Motion.sheet, label = "diálogo")

    fun showMessage(text: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        snackJob[0]?.cancel()
        snackJob[0] = scope.launch { snackbar.showAndWait(text, actionLabel, action) }
    }

    fun openCristalSettings(page: String? = null) {
        val intent = Intent(context, SettingsActivity::class.java)
        if (page != null) intent.putExtra(SettingsActivity.EXTRA_PAGE, page)
        context.startSafely(intent, displayId)
    }

    fun openExternalIntent(intent: Intent) {
        if (!context.startSafely(intent, displayId)) showMessage("Não foi possível abrir essa tela do sistema.")
    }

    fun launchApp(app: AppInfo) {
        viewModel.launchApp(app, displayId, onCover)
        scope.launch {
            // Fecha a gaveta e a pasta depois que a transição do app começou.
            delay(350)
            drawer.progress.snapTo(0f)
            openFolderId = null
        }
    }

    fun runGesture(action: GestureAction) {
        when (viewModel.performGesture(action)) {
            GestureEffect.OPEN_DRAWER -> scope.launch { drawer.open() }
            GestureEffect.OPEN_QUICK_PANEL -> scope.launch { panel.open() }
            GestureEffect.OPEN_SETTINGS -> openCristalSettings()
            GestureEffect.NONE -> Unit
        }
    }

    fun closeEverything() {
        menu = null
        homeMenu = false
        widgetPicker = false
        compat = null
        appOptions = null
        blockedHelp = null
        appPicker = false
        openFolderId = null
        scope.launch { drawer.close() }
        scope.launch { panel.close() }
    }

    // --- Eventos vindos do ViewModel ---------------------------------------------------------------
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is LauncherEvent.Message -> showMessage(event.text)
                is LauncherEvent.ConfirmCompat -> compat = event
                is LauncherEvent.OpenSystemPanel -> openExternalIntent(Intent(event.action))
                is LauncherEvent.OpenIntent -> openExternalIntent(event.intent)
                is LauncherEvent.CheckLaunched -> launch {
                    // Se depois de um tempo o launcher continua em primeiro plano, o app não apareceu nesta tela.
                    delay(1_800)
                    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        showMessage("${event.app.label} não abriu nesta tela.", "Por quê?") { blockedHelp = event.app }
                    }
                }

                LauncherEvent.GoHome -> {
                    closeEverything()
                    if (pagerState.currentPage != 1) pagerState.scrollToPage(1)
                }
            }
        }
    }

    // --- Arrastar e soltar ------------------------------------------------------------------------
    val slopPx = with(density) { 12.dp.toPx() }
    val gridPadPx = with(density) { 8.dp.toPx() }

    fun dropTargetAt(position: Offset): DropTarget? {
        val item = drag.item ?: return null
        val s = settingsState.value
        val dock = drag.dockBounds
        val grid = drag.gridBounds
        if (s.dockEnabled && item !is HomeItem.Widget && dock.width > 0f && dock.contains(position)) {
            val others = layoutState.value.dock.count { it.id != item.id }
            val index = ((position.x - dock.left) / dock.width * (others + 1)).toInt().coerceIn(0, others)
            return DropTarget.Dock(index)
        }
        val page = pagerState.currentPage - 1
        val usableWidth = grid.width - 2 * gridPadPx
        if (page < 0 || usableWidth <= 0f || grid.height <= 0f || !grid.contains(position)) return null
        val cols = s.gridColumns
        val rows = s.gridRows
        val spanX = item.spanX.coerceIn(1, cols)
        val spanY = item.spanY.coerceIn(1, rows)
        val fx = (position.x - grid.left - gridPadPx) / (usableWidth / cols)
        val fy = (position.y - grid.top) / (grid.height / rows)
        val x = if (spanX == 1) fx.toInt() else (fx - spanX / 2f).roundToInt()
        val y = if (spanY == 1) fy.toInt() else (fy - spanY / 2f).roundToInt()
        return DropTarget.Cell(CellSlot(page, x.coerceIn(0, cols - spanX), y.coerceIn(0, rows - spanY)), spanX, spanY)
    }

    val hover by remember { derivedStateOf { if (drag.moving) dropTargetAt(drag.position) else null } }

    // Encostar o item na borda por um instante troca de página.
    LaunchedEffect(drag.moving) {
        if (!drag.moving) return@LaunchedEffect
        val edge = with(density) { 26.dp.toPx() }
        var side = 0
        var since = 0L
        while (true) {
            val grid = drag.gridBounds
            val x = drag.position.x
            val nowSide = when {
                x < grid.left + edge -> -1
                x > grid.right - edge -> 1
                else -> 0
            }
            val now = System.currentTimeMillis()
            if (nowSide != side) {
                side = nowSide
                since = now
            }
            if (side != 0 && now - since > 550) {
                val target = (pagerState.currentPage + side).coerceIn(1, pagerState.pageCount - 1)
                if (target != pagerState.currentPage) pagerState.animateScrollToPage(target)
                since = System.currentTimeMillis()
            }
            delay(60)
        }
    }

    val callbacks = HomeCallbacks(
        onAppClick = ::launchApp,
        onFolderClick = { openFolderId = it.id },
        onItemHold = { item, inDock, at ->
            strongHaptic()
            drag.begin(item, inDock, at)
            menu = MenuState(MenuTarget.Home(item, inDock), at)
        },
        onItemDrag = { delta -> if (drag.moveBy(delta, slopPx)) menu = null },
        onItemRelease = {
            val item = drag.item
            if (item != null && drag.moving) {
                when (val target = dropTargetAt(drag.position)) {
                    is DropTarget.Cell -> viewModel.dropOnCell(item.id, target.slot.page, target.slot.x, target.slot.y)
                    is DropTarget.Dock -> viewModel.dropOnDock(item.id, target.index)
                    null -> Unit
                }
            }
            drag.finish()
        },
        onBackgroundHold = {
            strongHaptic()
            homeMenu = true
        },
        onBackgroundDoubleTap = { runGesture(settings.gestureDoubleTap) },
    )

    // --- Widgets ----------------------------------------------------------------------------------
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onResume()
    }
    // Widgets de outros apps: reserva um id, pede a permissão ao sistema se preciso e coloca na página.
    fun placeAppWidget(id: Int, entry: WidgetProviderEntry) {
        viewModel.addAppWidget(id, entry.spanX, entry.spanY, (pagerState.currentPage - 1).coerceAtLeast(0))
        (context as? Activity)?.let { viewModel.widgetHost.configureIfNeeded(it, id, entry.info) }
    }
    val bindWidget = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val pending = pendingWidget
        pendingWidget = null
        if (pending != null) {
            if (result.resultCode == Activity.RESULT_OK) {
                placeAppWidget(pending.first, pending.second)
            } else {
                viewModel.widgetHost.delete(pending.first)
            }
        }
    }

    fun pickAppWidget(entry: WidgetProviderEntry) {
        val host = viewModel.widgetHost
        val id = host.allocateId()
        if (id < 0) {
            showMessage("Não foi possível adicionar widgets de outros apps neste aparelho.")
            return
        }
        if (host.bindIfAllowed(id, entry.info)) {
            placeAppWidget(id, entry)
        } else {
            pendingWidget = id to entry
            runCatching { bindWidget.launch(host.bindIntent(id, entry.info)) }.onFailure {
                pendingWidget = null
                host.delete(id)
                showMessage("Não foi possível pedir a permissão do widget.")
            }
        }
    }

    val keysOnHome = remember(layout) {
        (layout.pages.flatten() + layout.dock).flatMap { item ->
            when (item) {
                is HomeItem.App -> listOf(item.app.key)
                is HomeItem.Folder -> item.children.map { it.app.key }
                is HomeItem.Widget -> emptyList()
            }
        }.toSet()
    }

    val widgetActions = WidgetActions(
        onClockClick = { openExternalIntent(Intent(AlarmClock.ACTION_SHOW_ALARMS)) },
        onWeatherClick = {
            if (widgetData.weather.status == WeatherStatus.NO_PLACE) {
                openCristalSettings(SettingsActivity.PAGE_WEATHER)
            } else {
                viewModel.refreshWeather()
                showMessage("Atualizando o clima…")
            }
        },
        onBatteryClick = { openExternalIntent(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) },
        onAgendaClick = {
            val uri = ContentUris.appendId(CalendarContract.CONTENT_URI.buildUpon().appendPath("time"), System.currentTimeMillis())
            openExternalIntent(Intent(Intent.ACTION_VIEW, uri.build()))
        },
        onRequestCalendar = { runCatching { calendarPermission.launch(Manifest.permission.READ_CALENDAR) } },
        onRequestNotificationAccess = { openExternalIntent(Intent(SystemSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
        onMediaOpen = { viewModel.mediaOpen(displayId) },
        onMediaPlayPause = viewModel::mediaPlayPause,
        onMediaNext = viewModel::mediaNext,
        onMediaPrevious = viewModel::mediaPrevious,
        onTorch = viewModel::toggleTorch,
        onCamera = { openExternalIntent(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) },
        onSearch = { scope.launch { drawer.open() } },
        onSettings = { openCristalSettings() },
    )

    // --- Voltar -----------------------------------------------------------------------------------
    BackHandler {
        when {
            appPicker -> appPicker = false
            blockedHelp != null -> blockedHelp = null
            appOptions != null -> appOptions = null
            compat != null -> compat = null
            widgetPicker -> widgetPicker = false
            homeMenu -> homeMenu = false
            menu != null -> menu = null
            openFolderId != null -> openFolderId = null
            panel.isOpenOrOpening -> scope.launch { panel.close() }
            drawer.isOpenOrOpening -> scope.launch { drawer.close() }
            pagerState.currentPage != 1 -> scope.launch { pagerState.animateScrollToPage(1) }
            // Como app comum, "voltar" sai; como launcher padrão, não há para onde voltar.
            !permissions.defaultHome -> (context as? Activity)?.moveTaskToBack(true)
        }
    }

    // --- Gestos verticais da tela inicial ---------------------------------------------------------
    val gesturesEnabled = !overlayOpen && !drag.moving
    val blurMaxPx = with(density) { 22.dp.toPx() }
    val discretePx = with(density) { 56.dp.toPx() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // 1. Cena
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val amount = max(max(drawer.progress.value, panel.progress.value), max(folderAmount, dialogAmount * 0.7f))
                        .coerceIn(0f, 1f)
                    val scale = (1f - 0.035f * amount) * (1f - 0.14f * foldAmount) * (0.9f + 0.1f * arrival.value)
                    scaleX = scale
                    scaleY = scale
                    alpha = ((1f - 0.9f * foldAmount) * (0.2f + 0.8f * arrival.value)).coerceIn(0f, 1f)
                    renderEffect = if (quality != GlassQuality.LIGHT && amount > 0.01f) {
                        BlurEffect(blurMaxPx * amount, blurMaxPx * amount, TileMode.Clamp)
                    } else {
                        null
                    }
                }
                .glassBackdropSource(overlayBackdrop)
                .pointerInput(gesturesEnabled) {
                    if (!gesturesEnabled) return@pointerInput
                    val tracker = VelocityTracker()
                    var total = 0f
                    // 0 = indefinido, 1 = arrastando a gaveta, 2 = arrastando o painel, 3 = gesto simples
                    var mode = 0
                    var upward = false
                    detectVerticalDragGestures(
                        onDragStart = {
                            tracker.resetTracking()
                            total = 0f
                            mode = 0
                        },
                        onDragEnd = {
                            val velocity = tracker.calculateVelocity().y
                            val height = size.height.toFloat().coerceAtLeast(1f)
                            when (mode) {
                                1 -> scope.launch { drawer.settle(-velocity / height) }
                                2 -> scope.launch { panel.settle(velocity / height) }
                                3 -> if (kotlin.math.abs(total) > discretePx) {
                                    val s = settingsState.value
                                    runGesture(if (upward) s.gestureSwipeUp else s.gestureSwipeDown)
                                }
                            }
                        },
                        onDragCancel = {
                            when (mode) {
                                1 -> scope.launch { drawer.settle(0f) }
                                2 -> scope.launch { panel.settle(0f) }
                            }
                        },
                        onVerticalDrag = { change, amount ->
                            tracker.addPosition(change.uptimeMillis, change.position)
                            total += amount
                            val height = size.height.toFloat().coerceAtLeast(1f)
                            if (mode == 0 && total != 0f) {
                                upward = total < 0f
                                val s = settingsState.value
                                val action = if (upward) s.gestureSwipeUp else s.gestureSwipeDown
                                mode = when {
                                    upward && action == GestureAction.APP_DRAWER -> 1
                                    !upward && action == GestureAction.QUICK_PANEL -> 2
                                    else -> 3
                                }
                            }
                            when (mode) {
                                1 -> scope.launch { drawer.dragBy(-amount / height) }
                                2 -> scope.launch { panel.dragBy(amount / height) }
                            }
                            change.consume()
                        },
                    )
                },
        ) {
            WallpaperLayer(
                image = if (settings.wallpaperKind == WallpaperKind.IMAGE) wallpaper else null,
                presetIndex = settings.wallpaperPreset,
                dim = settings.wallpaperDim,
                blur = settings.wallpaperBlur,
                zoom = settings.wallpaperZoom,
                offsetX = settings.wallpaperOffsetX,
                offsetY = settings.wallpaperOffsetY,
                saturation = settings.wallpaperSaturation,
                modifier = Modifier.glassBackdropSource(sceneBackdrop),
                tilt = { homeTilt.value },
                spatial = settings.wallpaperSpatial,
                parallax = {
                    if (!settingsState.value.wallpaperParallax) {
                        0f
                    } else {
                        val last = (pagerState.pageCount - 1).coerceAtLeast(1)
                        (pagerState.currentPage + pagerState.currentPageOffsetFraction) / last * 2f - 1f
                    }
                },
            )
            CompositionLocalProvider(LocalGlassBackdrop provides sceneBackdrop) {
              Box(
                  Modifier.fillMaxSize().graphicsLayer {
                      alpha = 1f - 0.92f * max(drawer.progress.value, panel.progress.value).coerceIn(0f, 1f)
                  },
              ) {
                HomeContent(
                    settings = settings,
                    layout = layout,
                    pagerState = pagerState,
                    safe = safe,
                    drag = drag,
                    hover = hover,
                    widgetData = widgetData,
                    widgetActions = widgetActions,
                    callbacks = callbacks,
                    todayPanel = { TodayPanel(widgetData, widgetActions) },
                )
              }
            }
        }

        // 2. Véu
        Box(
            Modifier.fillMaxSize().drawBehind {
                val amount = max(max(drawer.progress.value, panel.progress.value), max(folderAmount, dialogAmount * 0.7f))
                val strength = if (quality == GlassQuality.LIGHT) 0.62f else 0.30f
                if (amount > 0.001f) drawRect(Color.Black.copy(alpha = (strength * amount).coerceIn(0f, 1f)))
            },
        )

        // 3. Sobreposições
        CompositionLocalProvider(LocalGlassBackdrop provides overlayBackdrop) {
            if (openFolder != null) {
                FolderOverlay(
                    folder = openFolder,
                    iconScale = settings.iconScale,
                    onAppClick = ::launchApp,
                    onChildHold = { child, at ->
                        strongHaptic()
                        menu = MenuState(MenuTarget.FolderChild(child, openFolder.id), at)
                    },
                    onRename = { viewModel.renameFolder(openFolder.id, it) },
                    onDismiss = { openFolderId = null },
                )
            }

            AppDrawer(
                state = drawer,
                apps = home.apps,
                settings = settings,
                safe = safe,
                onAppClick = ::launchApp,
                onAppHold = { app, at ->
                    strongHaptic()
                    menu = MenuState(MenuTarget.DrawerApp(app), at)
                },
            )

            ControlCenter(
                state = panel,
                safe = safe,
                quick = quick,
                layout = controlLayout,
                widgetData = widgetData,
                widgetActions = widgetActions,
                notifications = notifications,
                notificationAccess = permissions.notificationAccess,
                onCover = onCover,
                actions = ControlCenterActions(
                    onControl = { kind ->
                        when (kind) {
                            ControlKind.SCREENSHOT -> scope.launch {
                                // Fecha a central antes, para ela não sair na captura.
                                panel.close()
                                viewModel.takeScreenshot()
                            }
                            ControlKind.SEARCH -> {
                                scope.launch { panel.close() }
                                scope.launch { drawer.open() }
                            }
                            ControlKind.WALLPAPER -> openCristalSettings(SettingsActivity.PAGE_WALLPAPER)
                            ControlKind.SETTINGS -> openCristalSettings()
                            ControlKind.MEDIA -> viewModel.mediaOpen(displayId)
                            else -> viewModel.runControl(kind)
                        }
                    },
                    onControlSettings = viewModel::controlSettings,
                    onBrightness = viewModel::setBrightness,
                    onBrightnessEnd = viewModel::endBrightnessDrag,
                    onAutoBrightness = viewModel::toggleAutoBrightness,
                    onVolume = viewModel::setMediaVolume,
                    onSaveLayout = viewModel::saveControlLayout,
                    onPreset = viewModel::applyControlPreset,
                    onSystemPanel = {
                        if (!viewModel.openSystemQuickSettings()) {
                            showMessage("Ative o serviço de acessibilidade do Cristal para abrir o painel do sistema.")
                        }
                    },
                    onOpenNotification = { item ->
                        viewModel.openNotification(item, displayId)
                        scope.launch { panel.close() }
                    },
                    onDismissNotification = viewModel::dismissNotification,
                    onClearAll = viewModel::dismissAllNotifications,
                    onRequestNotificationAccess = {
                        openExternalIntent(Intent(SystemSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                ),
            )

            menu?.let { state ->
                val (title, actions) = menuFor(
                    target = state.target,
                    onClose = { menu = null },
                    viewModel = viewModel,
                    displayId = displayId,
                    currentPage = (pagerState.currentPage - 1).coerceAtLeast(0),
                    onOpenFolder = { openFolderId = it },
                    onAppOptions = { appOptions = it },
                )
                ContextMenu(state = state, title = title, actions = actions, onDismiss = { menu = null })
            }

            if (homeMenu) {
                val actions = buildList {
                    add(MenuAction(Icons.Rounded.Apps, "Apps") {
                        homeMenu = false
                        appPicker = true
                    })
                    add(MenuAction(Icons.Rounded.Widgets, "Widgets") {
                        homeMenu = false
                        widgetPicker = true
                    })
                    add(MenuAction(Icons.Rounded.Wallpaper, "Papel de parede") {
                        homeMenu = false
                        openCristalSettings(SettingsActivity.PAGE_WALLPAPER)
                    })
                    add(MenuAction(Icons.Rounded.Settings, "Configurações") {
                        homeMenu = false
                        openCristalSettings()
                    })
                    if (!onCover && cover.cover != null && cover.cover?.isDefault == false) {
                        add(MenuAction(Icons.Rounded.Smartphone, "Tela externa") {
                            homeMenu = false
                            viewModel.openOnCover()
                        })
                    }
                    if (settings.coverTakeover && onCover) {
                        add(MenuAction(Icons.Rounded.Smartphone, "Tela Motorola") {
                            homeMenu = false
                            viewModel.showStockCoverHome()
                        })
                    }
                }
                HomeMenuBar(actions = actions, onDismiss = { homeMenu = false }, bottomPadding = safe.bottom)
            }

            if (widgetPicker) {
                val providers = remember { viewModel.widgetHost.providers(settings.gridColumns, settings.gridRows) }
                WidgetPicker(
                    onPick = { kind ->
                        widgetPicker = false
                        viewModel.addWidget(kind, (pagerState.currentPage - 1).coerceAtLeast(0))
                    },
                    onDismiss = { widgetPicker = false },
                    providers = providers,
                    onPickProvider = { entry ->
                        widgetPicker = false
                        pickAppWidget(entry)
                    },
                )
            }

            if (appPicker) {
                AppPickerDialog(
                    apps = home.apps,
                    onHome = keysOnHome,
                    onAdd = { app -> viewModel.addToHome(app, (pagerState.currentPage - 1).coerceAtLeast(0)) },
                    onDismiss = { appPicker = false },
                )
            }

            compat?.let { request ->
                CompatDialog(
                    app = request.app,
                    report = request.report,
                    shizukuReady = shizukuReady,
                    onOpen = { dontAsk ->
                        compat = null
                        viewModel.confirmLaunch(request.app, request.displayId, dontAsk)
                    },
                    onOptions = {
                        compat = null
                        appOptions = request.app
                    },
                    onDismiss = { compat = null },
                )
            }

            appOptions?.let { app ->
                AppOptionsDialog(
                    app = app,
                    pref = home.prefs[app.key],
                    shizukuReady = shizukuReady,
                    onScale = { viewModel.setAppScale(app, it) },
                    onForceResizable = { viewModel.setForceResizable(app, it) },
                    onSkipWarning = { viewModel.setSkipWarning(app, it) },
                    onHidden = { viewModel.setHidden(app, it) },
                    onDismiss = { appOptions = null },
                )
            }

            blockedHelp?.let { app ->
                BlockedHelpDialog(
                    app = app,
                    shizukuReady = shizukuReady,
                    onOpenSystemSettings = {
                        blockedHelp = null
                        openExternalIntent(Intent(SystemSettings.ACTION_SETTINGS))
                    },
                    onOpenCristalSettings = {
                        blockedHelp = null
                        openCristalSettings(SettingsActivity.PAGE_COVER)
                    },
                    onDismiss = { blockedHelp = null },
                )
            }

            SnackbarHost(controller = snackbar, bottomPadding = safe.bottom)
        }

        // 4. Item arrastado
        DragGhost(drag)
    }
}

/** Monta título e ações do menu de contexto conforme o alvo. */
private fun menuFor(
    target: MenuTarget,
    onClose: () -> Unit,
    viewModel: LauncherViewModel,
    displayId: Int,
    currentPage: Int,
    onOpenFolder: (Long) -> Unit,
    onAppOptions: (AppInfo) -> Unit,
): Pair<String, List<MenuAction>> {
    fun action(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, destructive: Boolean = false, run: () -> Unit) =
        MenuAction(icon, label, destructive) {
            onClose()
            run()
        }

    fun appActions(app: AppInfo): List<MenuAction> = listOf(
        action(Icons.Rounded.Tune, "Opções na tela externa") { onAppOptions(app) },
        action(Icons.Rounded.Info, "Informações do app") { viewModel.openAppInfo(app, displayId) },
        action(Icons.Rounded.Delete, "Desinstalar", destructive = true) { viewModel.uninstall(app, displayId) },
    )

    return when (target) {
        is MenuTarget.Home -> when (val item = target.item) {
            is HomeItem.App -> item.label to buildList {
                if (target.inDock) {
                    add(action(Icons.Rounded.Home, "Mover para a tela inicial") { viewModel.moveToDesktop(item.id, currentPage) })
                    add(action(Icons.Rounded.RemoveCircleOutline, "Remover do dock") { viewModel.removeItem(item.id) })
                } else {
                    add(action(Icons.Rounded.VerticalAlignBottom, "Mover para o dock") { viewModel.moveToDock(item.id) })
                    add(action(Icons.Rounded.RemoveCircleOutline, "Remover da tela inicial") { viewModel.removeItem(item.id) })
                }
                addAll(appActions(item.app))
            }

            is HomeItem.Folder -> item.title.ifBlank { "Pasta" } to buildList {
                add(action(Icons.Rounded.Folder, "Abrir e renomear") { onOpenFolder(item.id) })
                if (target.inDock) {
                    add(action(Icons.Rounded.Home, "Mover para a tela inicial") { viewModel.moveToDesktop(item.id, currentPage) })
                } else {
                    add(action(Icons.Rounded.VerticalAlignBottom, "Mover para o dock") { viewModel.moveToDock(item.id) })
                }
                add(action(Icons.Rounded.Delete, "Remover pasta e atalhos", destructive = true) { viewModel.removeItem(item.id) })
            }

            is HomeItem.Widget -> "Widget" to listOf(
                action(Icons.Rounded.RemoveCircleOutline, "Remover widget", destructive = true) { viewModel.removeItem(item.id) },
            )
        }

        is MenuTarget.FolderChild -> target.item.label to buildList {
            add(action(Icons.AutoMirrored.Rounded.DriveFileMove, "Tirar da pasta") { viewModel.moveToDesktop(target.item.id, currentPage) })
            add(action(Icons.Rounded.RemoveCircleOutline, "Remover atalho") { viewModel.removeItem(target.item.id) })
            addAll(appActions(target.item.app))
        }

        is MenuTarget.DrawerApp -> target.app.label to buildList {
            add(action(Icons.Rounded.Add, "Adicionar à tela inicial") { viewModel.addToHome(target.app, currentPage) })
            add(action(Icons.Rounded.VerticalAlignBottom, "Adicionar ao dock") { viewModel.addToDock(target.app) })
            add(action(Icons.Rounded.VisibilityOff, "Ocultar da gaveta") { viewModel.setHidden(target.app, true) })
            addAll(appActions(target.app))
        }
    }
}
