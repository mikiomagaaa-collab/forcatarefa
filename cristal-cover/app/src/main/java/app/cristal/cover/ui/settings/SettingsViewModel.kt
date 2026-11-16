package app.cristal.cover.ui.settings

import app.cristal.cover.data.island.IslandActivity
import app.cristal.cover.data.island.IslandRepository
import app.cristal.cover.data.notifications.NotificationItem
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cristal.cover.core.DefaultDispatcher
import app.cristal.cover.data.apps.AppInfo
import app.cristal.cover.data.apps.AppRepository
import app.cristal.cover.data.apps.IconPackInfo
import app.cristal.cover.data.apps.IconPackManager
import app.cristal.cover.data.backup.BackupManager
import app.cristal.cover.data.backup.BackupResult
import app.cristal.cover.data.collections.CollectionRepository
import app.cristal.cover.data.collections.CollectionPreview
import app.cristal.cover.data.collections.WallpaperCollection
import app.cristal.cover.data.db.AppPrefDao
import app.cristal.cover.data.display.CoverDisplayRepository
import app.cristal.cover.data.display.CoverState
import app.cristal.cover.data.launch.AppLauncher
import app.cristal.cover.data.notifications.NotificationRepository
import app.cristal.cover.data.prefs.PrefKeys
import app.cristal.cover.data.prefs.Settings
import app.cristal.cover.data.prefs.SettingsRepository
import app.cristal.cover.data.prefs.WallpaperConfig
import app.cristal.cover.data.prefs.WallpaperImages
import app.cristal.cover.data.prefs.WallpaperSlot
import app.cristal.cover.data.shizuku.ShizukuManager
import app.cristal.cover.data.system.PermissionCenter
import app.cristal.cover.data.system.PermissionState
import app.cristal.cover.data.wallpaper.SubjectStatus
import app.cristal.cover.data.wallpaper.WallpaperRepository
import app.cristal.cover.data.weather.CityResult
import app.cristal.cover.data.weather.WeatherRepository
import app.cristal.cover.data.weather.WeatherState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val permissionCenter: PermissionCenter,
    private val shizukuManager: ShizukuManager,
    private val iconPackManager: IconPackManager,
    private val wallpaperRepository: WallpaperRepository,
    private val weatherRepository: WeatherRepository,
    private val backupManager: BackupManager,
    private val appPrefDao: AppPrefDao,
    private val appLauncher: AppLauncher,
    private val islandRepository: IslandRepository,
    notificationRepository: NotificationRepository,
    private val collectionRepository: CollectionRepository,
    appRepository: AppRepository,
    coverDisplayRepository: CoverDisplayRepository,
    @DefaultDispatcher private val default: CoroutineDispatcher,
) : ViewModel() {

    val settings: StateFlow<Settings> = settingsRepository.settings

    /**
     * Apps cujas notificações viram pop-up do sistema (importância alta) e também aparecem na ilha.
     * Cada um abre direto no ajuste do canal, onde fica a opção de pop-up.
     */
    val popupApps: StateFlow<List<NotificationItem>> = notificationRepository.items
        .map { items ->
            items.filter { !it.ongoing && it.importance >= android.app.NotificationManager.IMPORTANCE_HIGH }
                .distinctBy { it.packageName to it.channelId }
                .take(8)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // --- Wallpaper Library ---------------------------------------------------------------------

    val collections: StateFlow<List<WallpaperCollection>> = collectionRepository.collections
    val activeCollection: StateFlow<String?> = collectionRepository.active

    private val _collectionPreviews = MutableStateFlow<Map<String, CollectionPreview>>(emptyMap())

    /** Prévia de cada coleção (configurações e imagens reduzidas), por id. */
    val collectionPreviews: StateFlow<Map<String, CollectionPreview>> = _collectionPreviews.asStateFlow()
    private val previewStamps = HashMap<String, Long>()

    fun loadCollections() {
        viewModelScope.launch {
            collectionRepository.refresh()
            refreshPreviews()
        }
    }

    private suspend fun refreshPreviews() {
        val list = collectionRepository.collections.value
        val next = HashMap(_collectionPreviews.value)
        next.keys.retainAll(list.map { it.id }.toSet())
        for (c in list) {
            if (previewStamps[c.id] == c.updated && next.containsKey(c.id)) continue
            next[c.id] = collectionRepository.preview(c)
            previewStamps[c.id] = c.updated
        }
        _collectionPreviews.value = next
    }

    private fun collectionJob(done: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            refreshPreviews()
            if (done != null) message(done)
        }
    }

    fun newCollection(name: String) = collectionJob("Coleção \"$name\" guardada.") { collectionRepository.saveCurrent(name) }

    fun renameCollection(id: String, name: String) = collectionJob { collectionRepository.rename(id, name) }

    fun duplicateCollection(id: String) = collectionJob("Coleção duplicada.") { collectionRepository.duplicate(id) }

    fun overwriteCollection(id: String) = collectionJob("Coleção atualizada com a aparência atual.") { collectionRepository.overwrite(id) }

    fun deleteCollection(id: String) = collectionJob("Coleção excluída.") { collectionRepository.delete(id) }

    /** Aplica uma coleção; [then] roda depois (por exemplo, abrir o editor do bloqueio). */
    fun applyCollection(id: String, then: (() -> Unit)?) {
        viewModelScope.launch {
            val ok = if (collectionRepository.active.value == id) true else collectionRepository.apply(id)
            if (!ok) {
                message("Não consegui aplicar esta coleção.")
                return@launch
            }
            if (then != null) then() else message("Coleção aplicada.")
        }
    }

    private val _systemPopups = MutableStateFlow<Boolean?>(null)

    /** Pop-ups do sistema (heads-up): ligados, desligados ou `null` quando não dá para saber sem o Shizuku. */
    val systemPopups: StateFlow<Boolean?> = _systemPopups.asStateFlow()

    fun refreshSystemPopups() {
        viewModelScope.launch {
            val result = shizukuManager.exec("settings get global $HEADS_UP_KEY")
            _systemPopups.value = if (result.ok) result.output.trim() != "0" else null
        }
    }

    /** Liga ou desliga os pop-ups do sistema para todos os apps (reversível). Só com o Shizuku. */
    fun setSystemPopups(enabled: Boolean) {
        viewModelScope.launch {
            val result = shizukuManager.exec("settings put global $HEADS_UP_KEY ${if (enabled) 1 else 0}")
            if (result.ok) {
                _systemPopups.value = enabled
                message(if (enabled) "Pop-ups do sistema ligados." else "Pop-ups do sistema desligados: agora só a ilha avisa.")
            } else {
                message("O Shizuku não respondeu. Use o caminho manual mostrado abaixo.")
            }
        }
    }

    /** Mostra a Liquid Island funcionando: carregando, depois uma notificação de exemplo. */
    fun islandDemo() {
        viewModelScope.launch {
            islandRepository.flash(IslandActivity.Charging(76), 2_600)
            kotlinx.coroutines.delay(3_000)
            islandRepository.flash(
                IslandActivity.Alert(
                    NotificationItem(
                        key = "demo", packageName = "app.cristal.cover", appLabel = "Cristal", title = "Liquid Island",
                        text = "É assim que as notificações chegam. Toque para abrir, deslize para cima para fechar.",
                        postTime = System.currentTimeMillis(), color = 0, isClearable = false, autoCancel = false,
                        lockVisibility = android.app.Notification.VISIBILITY_PUBLIC, contentIntent = null, appIcon = null, largeIcon = null,
                    ),
                ),
                4_200,
            )
            kotlinx.coroutines.delay(4_600)
            islandRepository.flash(IslandActivity.Ringer(true), 2_000)
        }
    }
    val permissions: StateFlow<PermissionState> = permissionCenter.state
    val cover: StateFlow<CoverState> = coverDisplayRepository.state
    val wallpapers: StateFlow<WallpaperImages> = wallpaperRepository.images

    /** Imagem da tela inicial interna, usada como fundo das próprias telas de configuração. */
    val wallpaper: StateFlow<ImageBitmap?> = wallpaperRepository.images
        .map { it[WallpaperSlot.HOME_INNER] }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val weather: StateFlow<WeatherState> = weatherRepository.state

    private val _iconPacks = MutableStateFlow<List<IconPackInfo>>(emptyList())
    val iconPacks: StateFlow<List<IconPackInfo>> = _iconPacks.asStateFlow()

    private val _cities = MutableStateFlow<List<CityResult>>(emptyList())
    val cities: StateFlow<List<CityResult>> = _cities.asStateFlow()

    private val _busy = MutableStateFlow(false)

    /** `true` enquanto uma operação demorada (backup, concessão via shell) está em andamento. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val hiddenApps: StateFlow<List<AppInfo>> = combine(appRepository.apps, appPrefDao.observeAll()) { apps, prefs ->
        val hidden = prefs.filter { it.hidden }.map { it.componentKey }.toSet()
        apps.filter { it.key in hidden }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    private var citySearch: Job? = null

    init {
        refreshPermissions()
    }

    fun message(text: String) {
        _messages.trySend(text)
    }

    fun <T> set(key: Preferences.Key<T>, value: T) {
        viewModelScope.launch { settingsRepository.set(key, value) }
    }

    fun <E : Enum<E>> setEnum(key: Preferences.Key<String>, value: E) {
        viewModelScope.launch { settingsRepository.setEnum(key, value) }
    }

    // --- Permissões e Shizuku ----------------------------------------------------------------------

    fun refreshPermissions() {
        permissionCenter.refresh()
    }

    val center: PermissionCenter get() = permissionCenter

    fun requestShizukuPermission() {
        if (!shizukuManager.requestPermission()) message("O Shizuku não está em execução. Abra o app Shizuku e inicie o serviço.")
        refreshPermissions()
    }

    /** Concede acesso às notificações e acessibilidade pela identidade "shell" do Shizuku. */
    fun grantViaShizuku() {
        viewModelScope.launch {
            _busy.value = true
            val ok = permissionCenter.grantSpecialAccessViaShell()
            // O sistema leva um instante para conectar os serviços recém-ativados.
            delay(900)
            val state = permissionCenter.refresh()
            _busy.value = false
            message(
                when {
                    !ok -> "O Shizuku não está pronto. Inicie o serviço e autorize o Cristal."
                    state.notificationAccess && state.accessibility -> "Tudo certo: acessos concedidos pelo Shizuku."
                    else -> "Comandos enviados, mas o sistema ainda não confirmou. Verifique os itens acima."
                },
            )
        }
    }

    fun setIgnoreOrientation(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.set(PrefKeys.IGNORE_ORIENTATION_ON_COVER, enabled)
            if (!appLauncher.applyIgnoreOrientation(enabled)) {
                message("Não foi possível aplicar agora. É preciso o Shizuku ativo e uma tela externa detectada.")
            }
        }
    }

    // --- Aparência ---------------------------------------------------------------------------------

    fun loadIconPacks() {
        viewModelScope.launch { _iconPacks.value = withContext(default) { iconPackManager.installedPacks() } }
    }

    fun setWallpaperImage(slot: WallpaperSlot, uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            val ok = wallpaperRepository.setFromUri(slot, uri)
            _busy.value = false
            message(if (ok) "Papel de parede aplicado." else "Não foi possível ler essa imagem.")
        }
    }

    fun useWallpaperPreset(slot: WallpaperSlot, index: Int) {
        viewModelScope.launch { wallpaperRepository.usePreset(slot, index) }
    }

    val subjectStatus: StateFlow<Map<WallpaperSlot, SubjectStatus>> = wallpaperRepository.subjectStatus
    val subjectError: StateFlow<String?> = wallpaperRepository.lastSubjectError

    fun detectSubject(slot: WallpaperSlot) = wallpaperRepository.detectSubject(slot)

    fun updateWallpaper(slot: WallpaperSlot, change: (WallpaperConfig) -> WallpaperConfig) {
        viewModelScope.launch { wallpaperRepository.update(slot, change) }
    }

    // --- Clima -------------------------------------------------------------------------------------

    fun searchCity(query: String) {
        citySearch?.cancel()
        if (query.trim().length < 2) {
            _cities.value = emptyList()
            return
        }
        citySearch = viewModelScope.launch {
            delay(350)
            _cities.value = weatherRepository.searchCity(query)
        }
    }

    fun pickCity(city: CityResult) {
        viewModelScope.launch {
            settingsRepository.edit {
                it[PrefKeys.WEATHER_CITY] = city.display
                it[PrefKeys.WEATHER_LAT] = city.lat
                it[PrefKeys.WEATHER_LON] = city.lon
                it[PrefKeys.WEATHER_USE_LOCATION] = false
            }
            _cities.value = emptyList()
            weatherRepository.refresh(force = true)
        }
    }

    fun setUseLocation(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.set(PrefKeys.WEATHER_USE_LOCATION, enabled)
            weatherRepository.refresh(force = true)
        }
    }

    fun refreshWeather() = weatherRepository.refresh(force = true)

    // --- Backup e apps ocultos ---------------------------------------------------------------------

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            val result = backupManager.export(uri)
            _busy.value = false
            message(if (result == BackupResult.OK) "Backup salvo." else "Não foi possível salvar o backup.")
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            val result = backupManager.import(uri)
            _busy.value = false
            message(
                when (result) {
                    BackupResult.OK -> "Configuração restaurada."
                    BackupResult.INVALID_FILE -> "Esse arquivo não é um backup do Cristal."
                    BackupResult.NEWER_FORMAT -> "Esse backup foi feito por uma versão mais nova do app."
                    BackupResult.IO_ERROR -> "Não foi possível ler o arquivo."
                },
            )
        }
    }

    fun unhide(app: AppInfo) {
        viewModelScope.launch {
            appPrefDao.get(app.key)?.let { appPrefDao.upsert(it.copy(hidden = false)) }
        }
    }

    /** Manda o launcher para a tela externa, sem depender do painel de apps da Motorola. */
    fun openOnCover() {
        viewModelScope.launch {
            val ok = appLauncher.openSelfOnCover()
            message(
                if (ok) "Cristal enviado para a tela externa. Feche o aparelho para ver."
                else "O sistema não deixou abrir na tela externa. Tente o bloco dos Ajustes rápidos ou o Shizuku.",
            )
        }
    }

    fun redoOnboarding() {
        viewModelScope.launch { settingsRepository.set(PrefKeys.ONBOARDING_DONE, false) }
    }
}

/** Chave global do Android que liga e desliga os pop-ups (heads-up) de notificação. */
private const val HEADS_UP_KEY = "heads_up_notifications_enabled"
