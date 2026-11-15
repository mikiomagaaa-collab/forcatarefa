package app.cristal.cover.ui.launcher

import android.content.Intent
import app.cristal.cover.BuildConfig
import kotlinx.coroutines.flow.flatMapLatest
import android.graphics.Rect
import android.view.Display
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cristal.cover.core.DefaultDispatcher
import app.cristal.cover.data.apps.AppInfo
import app.cristal.cover.data.apps.AppRepository
import app.cristal.cover.data.battery.BatteryRepository
import app.cristal.cover.data.calendar.CalendarRepository
import app.cristal.cover.data.db.AppPrefDao
import app.cristal.cover.data.db.AppPrefEntity
import app.cristal.cover.data.db.Container
import app.cristal.cover.data.db.HomeItemEntity
import app.cristal.cover.data.db.ItemType
import app.cristal.cover.data.display.CoverDisplayRepository
import app.cristal.cover.data.display.CoverState
import app.cristal.cover.data.home.DropResult
import app.cristal.cover.data.home.HomeItem
import app.cristal.cover.data.home.HomeLayout
import app.cristal.cover.data.home.HomeProfile
import app.cristal.cover.data.home.HomeRepository
import app.cristal.cover.data.home.WidgetKind
import app.cristal.cover.data.launch.AppLauncher
import app.cristal.cover.data.launch.CompatReport
import app.cristal.cover.data.launch.LaunchFailure
import app.cristal.cover.data.launch.LaunchOutcome
import app.cristal.cover.data.media.MediaRepository
import app.cristal.cover.data.notifications.NotificationItem
import app.cristal.cover.data.notifications.NotificationRepository
import app.cristal.cover.data.prefs.DrawerSort
import app.cristal.cover.data.prefs.GestureAction
import app.cristal.cover.data.prefs.PrefKeys
import app.cristal.cover.data.prefs.Settings
import app.cristal.cover.data.prefs.SettingsRepository
import app.cristal.cover.data.prefs.WallpaperImages
import app.cristal.cover.data.shizuku.ShizukuManager
import app.cristal.cover.data.system.PermissionCenter
import app.cristal.cover.data.system.PermissionState
import app.cristal.cover.data.control.ControlCenterRepository
import app.cristal.cover.data.control.ControlKind
import app.cristal.cover.data.control.ControlLayout
import app.cristal.cover.data.control.ControlPreset
import app.cristal.cover.data.system.QuickSettingsRepository
import app.cristal.cover.data.system.QuickState
import app.cristal.cover.data.system.SystemBridge
import app.cristal.cover.data.system.ToggleResult
import app.cristal.cover.data.wallpaper.WallpaperRepository
import app.cristal.cover.data.weather.WeatherRepository
import app.cristal.cover.data.widgets.WidgetHostManager
import app.cristal.cover.ui.widgets.WidgetData
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.max
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Apps e tela inicial prontos para a UI. */
@Immutable
data class HomeUi(
    val loaded: Boolean = false,
    val layout: HomeLayout = HomeLayout(),
    /** Apps visíveis na gaveta, já na ordem escolhida. */
    val apps: List<AppInfo> = emptyList(),
    val prefs: Map<String, AppPrefEntity> = emptyMap(),
)

/** Eventos pontuais que a tela precisa mostrar. */
sealed interface LauncherEvent {
    data class Message(val text: String) : LauncherEvent
    data class ConfirmCompat(val app: AppInfo, val report: CompatReport, val displayId: Int) : LauncherEvent

    /** O app foi pedido, mas pode não ter aparecido na tela externa (bloqueio do fabricante). */
    data class CheckLaunched(val app: AppInfo) : LauncherEvent
    data class OpenSystemPanel(val action: String) : LauncherEvent
    data class OpenIntent(val intent: android.content.Intent) : LauncherEvent
    data object GoHome : LauncherEvent
}

/** O que a tela deve fazer depois de um gesto. */
enum class GestureEffect { NONE, OPEN_DRAWER, OPEN_QUICK_PANEL, OPEN_SETTINGS }

@HiltViewModel
class LauncherViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val appRepository: AppRepository,
    private val homeRepository: HomeRepository,
    private val appPrefDao: AppPrefDao,
    private val appLauncher: AppLauncher,
    private val notificationRepository: NotificationRepository,
    private val mediaRepository: MediaRepository,
    private val weatherRepository: WeatherRepository,
    private val calendarRepository: CalendarRepository,
    private val quickSettingsRepository: QuickSettingsRepository,
    private val controlCenterRepository: ControlCenterRepository,
    private val systemBridge: SystemBridge,
    private val permissionCenter: PermissionCenter,
    private val shizukuManager: ShizukuManager,
    val widgetHost: WidgetHostManager,
    batteryRepository: BatteryRepository,
    coverDisplayRepository: CoverDisplayRepository,
    wallpaperRepository: WallpaperRepository,
    @DefaultDispatcher default: CoroutineDispatcher,
) : ViewModel() {

    val settings: StateFlow<Settings> = settingsRepository.settings

    private val _settingsLoaded = MutableStateFlow(false)

    /** `true` quando as preferências já vieram do disco (evita piscar o onboarding). */
    val settingsLoaded: StateFlow<Boolean> = _settingsLoaded.asStateFlow()

    private val profile = MutableStateFlow(HomeProfile.INNER)

    /** A tela informa em qual tela física está; cada uma tem a sua tela inicial. */
    fun setProfile(value: HomeProfile) {
        profile.value = value
    }

    private val gridFlow: Flow<Triple<Int, DrawerSort, HomeProfile>> = combine(
        settings.map { it.dockCount to it.drawerSort }.distinctUntilChanged(),
        profile,
    ) { pair, p -> Triple(pair.first, pair.second, p) }

    val home: StateFlow<HomeUi> = combine(
        homeRepository.items,
        appRepository.apps,
        appPrefDao.observeAll(),
        gridFlow,
        appRepository.loaded,
    ) { entities, apps, prefs, grid, loaded ->
        buildHome(entities, apps, prefs, dockCount = grid.first, sort = grid.second, loaded = loaded, profile = grid.third)
    }.flowOn(default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())

    val widgetData: StateFlow<WidgetData> = combine(
        batteryRepository.state,
        weatherRepository.state,
        mediaRepository.state,
        calendarRepository.state,
        combine(
            settings.map { it.tempUnit }.distinctUntilChanged(),
            notificationRepository.connected,
            quickSettingsRepository.state.map { it.torchOn }.distinctUntilChanged(),
        ) { unit, connected, torch -> Triple(unit, connected, torch) },
    ) { battery, weather, media, agenda, extra ->
        WidgetData(
            battery = battery,
            weather = weather,
            media = media,
            agenda = agenda,
            tempUnit = extra.first,
            notificationAccess = extra.second,
            torchOn = extra.third,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WidgetData())

    val notifications: StateFlow<List<NotificationItem>> = notificationRepository.items
    val quick: StateFlow<QuickState> = quickSettingsRepository.state

    /** Layout da Liquid Control Center da tela atual (interna ou externa). */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val controlLayout: StateFlow<ControlLayout> = profile
        .flatMapLatest { p -> controlCenterRepository.layout(p == HomeProfile.COVER) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), controlCenterRepository.inner.value)
    val permissions: StateFlow<PermissionState> = permissionCenter.state
    val cover: StateFlow<CoverState> = coverDisplayRepository.state
    val wallpapers: StateFlow<WallpaperImages> = wallpaperRepository.images
    val accessibilityConnected: StateFlow<Boolean> = systemBridge.accessibilityConnected

    private val _events = Channel<LauncherEvent>(Channel.BUFFERED)
    val events: Flow<LauncherEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            // Espera o StateFlow alcançar o que está no disco, para a UI não piscar com os padrões.
            val disk = settingsRepository.current()
            settings.first { it == disk }
            _settingsLoaded.value = true
        }
    }

    // --- Ciclo de vida ----------------------------------------------------------------------------

    fun onResume() {
        permissionCenter.refresh()
        quickSettingsRepository.refresh()
        calendarRepository.refresh()
        weatherRepository.refresh()
        appLauncher.restoreDisplayScale()
        // Com Shizuku ativo, garante que a Motorola deixe o próprio Cristal aparecer na tela externa.
        viewModelScope.launch { appLauncher.allowSelfOnCover() }
    }

    /** Volta ao estado inicial (tecla Home, tela apagada). */
    fun requestHome() {
        _events.trySend(LauncherEvent.GoHome)
    }

    fun finishOnboarding() {
        viewModelScope.launch { settingsRepository.set(PrefKeys.ONBOARDING_DONE, true) }
    }

    // --- Abrir apps -------------------------------------------------------------------------------

    fun launchApp(app: AppInfo, displayId: Int, onCover: Boolean, bounds: Rect? = null, force: Boolean = false) {
        viewModelScope.launch {
            when (val outcome = appLauncher.launch(app, displayId, onCover, bounds, force)) {
                LaunchOutcome.Started -> if (onCover) _events.trySend(LauncherEvent.CheckLaunched(app))
                is LaunchOutcome.NeedsConfirmation ->
                    _events.trySend(LauncherEvent.ConfirmCompat(outcome.app, outcome.report, displayId))

                is LaunchOutcome.Failed -> _events.trySend(
                    LauncherEvent.Message(
                        when (outcome.reason) {
                            LaunchFailure.NOT_FOUND -> "${app.label} não está mais instalado."
                            LaunchFailure.NOT_ALLOWED -> "O sistema não permitiu abrir ${app.label} nesta tela."
                            LaunchFailure.UNKNOWN -> "Não foi possível abrir ${app.label}."
                        },
                    ),
                )
            }
        }
    }

    /** O usuário confirmou abrir um app com aviso de compatibilidade. */
    fun confirmLaunch(app: AppInfo, displayId: Int, dontAskAgain: Boolean) {
        viewModelScope.launch {
            if (dontAskAgain) updatePref(app.key) { it.copy(skipWarning = true) }
            launchApp(app, displayId, onCover = true, force = true)
        }
    }

    fun openAppInfo(app: AppInfo, displayId: Int) {
        if (!appLauncher.openAppInfo(app, displayId)) message("Não foi possível abrir os detalhes do app.")
    }

    fun uninstall(app: AppInfo, displayId: Int) {
        if (app.isSystem) {
            message("${app.label} é um app do sistema e não pode ser desinstalado.")
            return
        }
        if (!appLauncher.uninstall(app, displayId)) message("Não foi possível iniciar a desinstalação.")
    }

    // --- Edição da tela inicial -------------------------------------------------------------------

    fun addToHome(app: AppInfo, page: Int) {
        viewModelScope.launch {
            homeRepository.addAppToDesktop(app.key, page, profile.value)
            message("${app.label} adicionado à tela inicial.")
        }
    }

    fun addToDock(app: AppInfo) {
        viewModelScope.launch {
            val ok = homeRepository.addAppToDock(app.key, profile.value)
            message(if (ok) "${app.label} adicionado ao dock." else "O dock está cheio.")
        }
    }

    fun addWidget(kind: WidgetKind, page: Int) {
        viewModelScope.launch { homeRepository.addWidget(kind, page, profile.value) }
    }

    fun removeItem(id: Long) {
        // Widgets de outros apps também precisam ser liberados no host.
        home.value.layout.pages.asSequence().flatten()
            .filterIsInstance<HomeItem.Widget>()
            .firstOrNull { it.id == id && it.appWidgetId >= 0 }
            ?.let { widgetHost.delete(it.appWidgetId) }
        viewModelScope.launch { homeRepository.remove(id) }
    }

    fun addAppWidget(appWidgetId: Int, spanX: Int, spanY: Int, page: Int) {
        viewModelScope.launch { homeRepository.addWidget(WidgetKind.APP_WIDGET, page, profile.value, appWidgetId, spanX, spanY) }
    }

    fun moveToDesktop(id: Long, page: Int) {
        viewModelScope.launch { homeRepository.moveToDesktop(id, page, profile.value) }
    }

    fun moveToDock(id: Long) {
        viewModelScope.launch {
            val result = homeRepository.dropOnDock(id, Int.MAX_VALUE, profile.value)
            if (result == DropResult.REJECTED) message("O dock está cheio.")
        }
    }

    fun dropOnCell(id: Long, page: Int, x: Int, y: Int) {
        viewModelScope.launch {
            if (homeRepository.dropOnCell(id, page, x, y, profile.value) == DropResult.REJECTED) message("Não há espaço nessa posição.")
        }
    }

    fun dropOnDock(id: Long, index: Int) {
        viewModelScope.launch {
            if (homeRepository.dropOnDock(id, index, profile.value) == DropResult.REJECTED) message("O dock está cheio.")
        }
    }

    fun renameFolder(id: Long, title: String) {
        viewModelScope.launch { homeRepository.renameFolder(id, title) }
    }

    fun setHidden(app: AppInfo, hidden: Boolean) {
        viewModelScope.launch {
            updatePref(app.key) { it.copy(hidden = hidden) }
            if (hidden) message("${app.label} foi ocultado. Reexiba em Configurações > Apps ocultos.")
        }
    }

    fun setAppScale(app: AppInfo, percent: Int) {
        viewModelScope.launch { updatePref(app.key) { it.copy(scalePercent = percent.coerceIn(50, 100)) } }
    }

    fun setForceResizable(app: AppInfo, enabled: Boolean) {
        viewModelScope.launch {
            updatePref(app.key) { it.copy(forceResizable = enabled) }
            if (!enabled) appLauncher.clearCompat(app.packageName)
        }
    }

    fun setSkipWarning(app: AppInfo, skip: Boolean) {
        viewModelScope.launch { updatePref(app.key) { it.copy(skipWarning = skip) } }
    }

    private suspend fun updatePref(key: String, change: (AppPrefEntity) -> AppPrefEntity) {
        val current = appPrefDao.get(key) ?: AppPrefEntity(componentKey = key)
        appPrefDao.upsert(change(current))
    }

    // --- Notificações e mídia ---------------------------------------------------------------------

    fun dismissNotification(item: NotificationItem) = notificationRepository.dismiss(item)

    fun dismissAllNotifications() = notificationRepository.dismissAll()

    fun openNotification(item: NotificationItem, displayId: Int) {
        if (!notificationRepository.open(item, displayId)) message("Esta notificação não tem uma ação para abrir.")
    }

    fun mediaPlayPause() = mediaRepository.playPause()
    fun mediaNext() = mediaRepository.next()
    fun mediaPrevious() = mediaRepository.previous()

    fun mediaOpen(displayId: Int) {
        if (!mediaRepository.openPlayer(displayId)) message("Nenhum player ativo para abrir.")
    }

    fun refreshWeather() = weatherRepository.refresh(force = true)

    // --- Ajustes rápidos --------------------------------------------------------------------------

    fun toggleTorch() {
        if (!quickSettingsRepository.toggleTorch()) message("A lanterna não está disponível agora.")
    }

    fun toggleRinger() {
        if (!quickSettingsRepository.toggleRinger()) message("Não foi possível mudar o modo de toque (Não Perturbe ativo?).")
    }

    fun setMediaVolume(fraction: Float) = quickSettingsRepository.setMediaVolume(fraction)

    fun toggleWifi() = shellToggle(android.provider.Settings.Panel.ACTION_WIFI) { quickSettingsRepository.toggleWifi() }

    fun toggleBluetooth() =
        shellToggle(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS) { quickSettingsRepository.toggleBluetooth() }

    fun toggleMobileData() =
        shellToggle(android.provider.Settings.Panel.ACTION_INTERNET_CONNECTIVITY) { quickSettingsRepository.toggleMobileData() }

    private fun shellToggle(panelAction: String, toggle: suspend () -> ToggleResult) {
        viewModelScope.launch { handleToggle(toggle(), Intent(panelAction)) }
    }

    private fun handleToggle(result: ToggleResult, panel: Intent?, permission: Intent? = null, permissionHint: String? = null) {
        when (result) {
            ToggleResult.DONE -> Unit
            ToggleResult.NEEDS_SYSTEM_PANEL, ToggleResult.FAILED -> panel?.let { _events.trySend(LauncherEvent.OpenIntent(it)) }
            ToggleResult.NEEDS_PERMISSION -> {
                permissionHint?.let(::message)
                (permission ?: panel)?.let { _events.trySend(LauncherEvent.OpenIntent(it)) }
            }
        }
    }

    // --- Liquid Control Center --------------------------------------------------------------------

    fun saveControlLayout(layout: ControlLayout) {
        val onCover = profile.value == HomeProfile.COVER
        viewModelScope.launch { controlCenterRepository.save(onCover, layout) }
    }

    fun applyControlPreset(preset: ControlPreset) {
        val onCover = profile.value == HomeProfile.COVER
        viewModelScope.launch { controlCenterRepository.applyPreset(onCover, preset) }
    }

    private val writeSettingsIntent: Intent
        get() = Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.parse("package:${BuildConfig.APPLICATION_ID}"))

    /** Executa o toque num módulo da central. Os módulos que mexem na própria tela são tratados pela UI. */
    fun runControl(kind: ControlKind) {
        when (kind) {
            ControlKind.TORCH -> toggleTorch()
            ControlKind.RINGER -> toggleRinger()
            ControlKind.WIFI -> toggleWifi()
            ControlKind.BLUETOOTH -> toggleBluetooth()
            ControlKind.MOBILE_DATA -> toggleMobileData()
            ControlKind.AIRPLANE -> viewModelScope.launch {
                handleToggle(quickSettingsRepository.toggleAirplane(), Intent(android.provider.Settings.ACTION_AIRPLANE_MODE_SETTINGS))
            }
            ControlKind.LOCATION -> viewModelScope.launch {
                handleToggle(quickSettingsRepository.toggleLocation(), Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            ControlKind.NFC -> viewModelScope.launch {
                handleToggle(quickSettingsRepository.toggleNfc(), Intent(android.provider.Settings.ACTION_NFC_SETTINGS))
            }
            ControlKind.BATTERY_SAVER -> viewModelScope.launch {
                handleToggle(quickSettingsRepository.toggleBatterySaver(), Intent(android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS))
            }
            ControlKind.DARK_MODE -> viewModelScope.launch {
                handleToggle(quickSettingsRepository.toggleDarkMode(), Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS))
            }
            ControlKind.DND -> viewModelScope.launch {
                handleToggle(
                    quickSettingsRepository.toggleDnd(),
                    panel = null,
                    permission = Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
                    permissionHint = "Libere o Cristal em \"Acesso ao Não perturbe\" para usar este botão.",
                )
            }
            ControlKind.ROTATION -> viewModelScope.launch {
                handleToggle(
                    quickSettingsRepository.toggleRotation(),
                    panel = null,
                    permission = writeSettingsIntent,
                    permissionHint = "Permita que o Cristal altere as configurações do sistema para mudar a rotação.",
                )
            }
            ControlKind.HOTSPOT -> _events.trySend(LauncherEvent.OpenIntent(Intent(TETHER_SETTINGS)))
            ControlKind.LOCK -> lockScreen()
            ControlKind.RECENTS -> openRecents()
            ControlKind.SYSTEM_PANEL -> if (!openSystemQuickSettings()) {
                message("Ative o serviço de acessibilidade do Cristal para abrir o painel do sistema.")
            }
            ControlKind.TIMER -> _events.trySend(LauncherEvent.OpenIntent(Intent(android.provider.AlarmClock.ACTION_SHOW_TIMERS)))
            ControlKind.ALARM -> _events.trySend(LauncherEvent.OpenIntent(Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)))
            ControlKind.CALCULATOR -> _events.trySend(
                LauncherEvent.OpenIntent(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR)),
            )
            ControlKind.CAMERA -> _events.trySend(
                LauncherEvent.OpenIntent(Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
            )
            ControlKind.MEDIA, ControlKind.BATTERY, ControlKind.BRIGHTNESS, ControlKind.VOLUME, ControlKind.CONNECTIVITY,
            ControlKind.SCREENSHOT, ControlKind.SEARCH, ControlKind.WALLPAPER, ControlKind.SETTINGS -> Unit
        }
    }

    /** Toque longo num módulo: abre a tela do sistema ligada a ele. */
    fun controlSettings(kind: ControlKind) {
        val action = when (kind) {
            ControlKind.WIFI, ControlKind.CONNECTIVITY -> android.provider.Settings.ACTION_WIFI_SETTINGS
            ControlKind.BLUETOOTH -> android.provider.Settings.ACTION_BLUETOOTH_SETTINGS
            ControlKind.MOBILE_DATA -> android.provider.Settings.ACTION_DATA_ROAMING_SETTINGS
            ControlKind.AIRPLANE -> android.provider.Settings.ACTION_AIRPLANE_MODE_SETTINGS
            ControlKind.HOTSPOT -> TETHER_SETTINGS
            ControlKind.NFC -> android.provider.Settings.ACTION_NFC_SETTINGS
            ControlKind.LOCATION -> android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS
            ControlKind.RINGER, ControlKind.VOLUME -> android.provider.Settings.ACTION_SOUND_SETTINGS
            ControlKind.DND -> android.provider.Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS
            ControlKind.ROTATION, ControlKind.DARK_MODE, ControlKind.BRIGHTNESS -> android.provider.Settings.ACTION_DISPLAY_SETTINGS
            ControlKind.BATTERY_SAVER, ControlKind.BATTERY -> android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS
            else -> null
        } ?: return
        _events.trySend(LauncherEvent.OpenIntent(Intent(action)))
    }

    fun setBrightness(fraction: Float) {
        if (quickSettingsRepository.setBrightness(fraction) == ToggleResult.NEEDS_PERMISSION) {
            message("Permita que o Cristal altere as configurações do sistema para mudar o brilho por aqui.")
            _events.trySend(LauncherEvent.OpenIntent(writeSettingsIntent))
        }
    }

    fun endBrightnessDrag() = quickSettingsRepository.endBrightnessDrag()

    fun toggleAutoBrightness() {
        viewModelScope.launch {
            handleToggle(
                quickSettingsRepository.toggleAutoBrightness(),
                panel = null,
                permission = writeSettingsIntent,
                permissionHint = "Permita que o Cristal altere as configurações do sistema para usar o brilho automático.",
            )
        }
    }

    // --- Gestos e ações do sistema ----------------------------------------------------------------

    /** Executa uma ação de gesto. Ações de UI são devolvidas para a tela tratar. */
    fun performGesture(action: GestureAction): GestureEffect {
        when (action) {
            GestureAction.NONE -> Unit
            GestureAction.APP_DRAWER -> return GestureEffect.OPEN_DRAWER
            GestureAction.QUICK_PANEL -> return GestureEffect.OPEN_QUICK_PANEL
            GestureAction.SETTINGS -> return GestureEffect.OPEN_SETTINGS
            GestureAction.SYSTEM_NOTIFICATIONS -> if (!systemBridge.openNotifications()) return needsAccessibility(GestureEffect.OPEN_QUICK_PANEL)
            GestureAction.SYSTEM_QUICK_SETTINGS -> if (!systemBridge.openQuickSettings()) return needsAccessibility(GestureEffect.OPEN_QUICK_PANEL)
            GestureAction.LOCK_SCREEN -> if (!systemBridge.lockScreen()) return needsAccessibility(GestureEffect.NONE)
            GestureAction.RECENTS -> if (!systemBridge.openRecents()) return needsAccessibility(GestureEffect.NONE)
            GestureAction.TORCH -> toggleTorch()
        }
        return GestureEffect.NONE
    }

    private fun needsAccessibility(fallback: GestureEffect): GestureEffect {
        message("Ative o serviço de acessibilidade do Cristal para usar este gesto.")
        return fallback
    }

    fun lockScreen() {
        if (!systemBridge.lockScreen()) message("Ative o serviço de acessibilidade do Cristal para bloquear a tela por aqui.")
    }

    fun openSystemQuickSettings(): Boolean = systemBridge.openQuickSettings()

    /** Deixa o usuário chegar à tela externa original por 20 segundos, sem o Cristal reassumir. */
    fun showStockCoverHome() {
        systemBridge.pauseTakeover(20_000)
        if (!systemBridge.goHome()) message("Use o gesto de início do sistema para ver a tela da Motorola.")
    }

    fun takeScreenshot() {
        if (!systemBridge.takeScreenshot()) message("Ative o serviço de acessibilidade do Cristal para capturar a tela por aqui.")
    }

    fun openRecents() {
        if (!systemBridge.openRecents()) message("Ative o serviço de acessibilidade do Cristal para abrir os apps recentes.")
    }

    /** Manda o launcher para a tela externa (útil para abrir o Cristal lá sem depender do painel da Motorola). */
    fun openOnCover() {
        viewModelScope.launch {
            val ok = appLauncher.openSelfOnCover()
            message(
                if (ok) "Cristal enviado para a tela externa. Feche o aparelho para ver."
                else "Não foi possível abrir na tela externa. Veja Configurações > Tela externa.",
            )
        }
    }

    val isShizukuReady: Boolean get() = shizukuManager.isReady

    fun message(text: String) {
        _events.trySend(LauncherEvent.Message(text))
    }

    // --- Montagem do layout -----------------------------------------------------------------------

    private fun buildHome(
        entities: List<HomeItemEntity>,
        apps: List<AppInfo>,
        prefs: List<AppPrefEntity>,
        dockCount: Int,
        sort: DrawerSort,
        loaded: Boolean,
        profile: HomeProfile,
    ): HomeUi {
        val appByKey = apps.associateBy { it.key }
        val prefByKey = prefs.associateBy { it.componentKey }
        val childrenByFolder = entities.filter { it.container > 0 }.groupBy { it.container }

        fun toApp(e: HomeItemEntity): HomeItem.App? {
            val app = appByKey[e.componentKey] ?: return null
            val label = prefByKey[app.key]?.customLabel?.takeIf { it.isNotBlank() } ?: app.label
            return HomeItem.App(e.id, e.page, e.cellX, e.cellY, app, label)
        }

        fun convert(e: HomeItemEntity): HomeItem? = when (e.type) {
            ItemType.APP -> toApp(e)
            ItemType.FOLDER -> {
                val children = childrenByFolder[e.id].orEmpty().sortedBy { it.cellX }.mapNotNull(::toApp)
                if (children.isEmpty()) null else HomeItem.Folder(e.id, e.page, e.cellX, e.cellY, e.title.orEmpty(), children)
            }

            ItemType.WIDGET -> WidgetKind.fromName(e.widgetKind)?.let {
                HomeItem.Widget(e.id, e.page, e.cellX, e.cellY, e.spanX, e.spanY, it, e.title?.toIntOrNull() ?: -1)
            }

            else -> null
        }

        val desktop = entities.filter { it.container == profile.desktop }.mapNotNull(::convert)
        val pageCount = max(1, (desktop.maxOfOrNull { it.page } ?: 0) + 1)
        val pages = List(pageCount) { page -> desktop.filter { it.page == page } }
        val dock = entities.filter { it.container == profile.dock }
            .sortedBy { it.cellX }
            .mapNotNull(::convert)
            .take(dockCount)

        val visible = apps.filter { prefByKey[it.key]?.hidden != true }
        val sorted = when (sort) {
            DrawerSort.NAME -> visible
            DrawerSort.USAGE -> visible.sortedWith(
                compareByDescending<AppInfo> { prefByKey[it.key]?.launchCount ?: 0 }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label },
            )
        }
        return HomeUi(loaded = loaded, layout = HomeLayout(pages, dock), apps = sorted, prefs = prefByKey)
    }

    companion object {
        /** Tela de ponto de acesso; não é uma constante pública, mas existe em todos os Android recentes. */
        const val TETHER_SETTINGS = "android.settings.TETHER_SETTINGS"

        const val NO_DISPLAY = Display.INVALID_DISPLAY
    }
}
