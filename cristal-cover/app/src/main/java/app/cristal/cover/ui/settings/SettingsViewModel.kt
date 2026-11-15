package app.cristal.cover.ui.settings

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
import app.cristal.cover.data.db.AppPrefDao
import app.cristal.cover.data.display.CoverDisplayRepository
import app.cristal.cover.data.display.CoverState
import app.cristal.cover.data.launch.AppLauncher
import app.cristal.cover.data.prefs.PrefKeys
import app.cristal.cover.data.prefs.Settings
import app.cristal.cover.data.prefs.SettingsRepository
import app.cristal.cover.data.shizuku.ShizukuManager
import app.cristal.cover.data.system.PermissionCenter
import app.cristal.cover.data.system.PermissionState
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
    appRepository: AppRepository,
    coverDisplayRepository: CoverDisplayRepository,
    @DefaultDispatcher private val default: CoroutineDispatcher,
) : ViewModel() {

    val settings: StateFlow<Settings> = settingsRepository.settings
    val permissions: StateFlow<PermissionState> = permissionCenter.state
    val cover: StateFlow<CoverState> = coverDisplayRepository.state
    val wallpaper: StateFlow<ImageBitmap?> = wallpaperRepository.image
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

    fun setWallpaperImage(uri: Uri) {
        viewModelScope.launch {
            _busy.value = true
            val ok = wallpaperRepository.setFromUri(uri)
            _busy.value = false
            message(if (ok) "Papel de parede aplicado." else "Não foi possível ler essa imagem.")
        }
    }

    fun useWallpaperPreset(index: Int) {
        viewModelScope.launch { wallpaperRepository.usePreset(index) }
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

    fun redoOnboarding() {
        viewModelScope.launch { settingsRepository.set(PrefKeys.ONBOARDING_DONE, false) }
    }
}
