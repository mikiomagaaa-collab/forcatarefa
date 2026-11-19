package app.cristal.cover.ui.lock

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import app.cristal.cover.core.AppScope
import app.cristal.cover.data.battery.BatteryRepository
import app.cristal.cover.data.battery.BatteryState
import app.cristal.cover.data.media.MediaRepository
import app.cristal.cover.data.media.MediaState
import app.cristal.cover.data.notifications.NotificationItem
import app.cristal.cover.data.notifications.NotificationRepository
import app.cristal.cover.data.prefs.Settings
import app.cristal.cover.data.prefs.SettingsRepository
import app.cristal.cover.data.prefs.WallpaperImages
import app.cristal.cover.data.prefs.WallpaperKind
import app.cristal.cover.data.prefs.WallpaperSlot
import app.cristal.cover.data.system.QuickSettingsRepository
import app.cristal.cover.data.system.QuickState
import app.cristal.cover.data.weather.WeatherRepository
import app.cristal.cover.data.weather.WeatherState
import app.cristal.cover.data.calendar.AgendaState
import app.cristal.cover.data.calendar.CalendarRepository
import app.cristal.cover.data.wallpaper.WallpaperRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@Immutable
data class LockUiState(
    val settings: Settings = Settings(),
    val battery: BatteryState = BatteryState(),
    val media: MediaState? = null,
    /** Notificações que podem aparecer na tela de bloqueio (as secretas já foram retiradas). */
    val notifications: List<NotificationItem> = emptyList(),
    val torchAvailable: Boolean = false,
    val torchOn: Boolean = false,
    val wallpaper: ImageBitmap? = null,
    val images: WallpaperImages = emptyMap(),
    /** Recorte do objeto principal (efeito de profundidade), quando houver. */
    val subject: ImageBitmap? = null,
    val subjects: WallpaperImages = emptyMap(),
    val weather: WeatherState = WeatherState(),
    val agenda: AgendaState = AgendaState(),
) {
    /** Este estado "visto" da tela de bloqueio interna ou externa, com o papel de parede dela. */
    fun forDisplay(onCover: Boolean): LockUiState {
        val view = settings.forSlot(WallpaperSlot.lock(onCover))
        val image = images[view.wallpaperSlot]
        // A profundidade só faz sentido com a foto nítida: com desfoque, o recorte destoaria.
        val depth = view.wallpaperKind == WallpaperKind.IMAGE && view.wallpaperDepth && view.wallpaperBlur < 0.05f
        return copy(settings = view, wallpaper = image, subject = if (depth && image != null) subjects[view.wallpaperSlot] else null)
    }
}

private class LockExtras(
    val quick: QuickState,
    val images: WallpaperImages,
    val subjects: WallpaperImages,
    val weather: WeatherState,
    val agenda: AgendaState,
)

/**
 * Estado e ações da tela de bloqueio, compartilhados entre a activity e o modo de sobreposição
 * (que roda dentro do serviço de acessibilidade, sem ViewModel).
 */
@Singleton
class LockPresenter @Inject constructor(
    settingsRepository: SettingsRepository,
    private val notificationRepository: NotificationRepository,
    private val mediaRepository: MediaRepository,
    private val quickSettingsRepository: QuickSettingsRepository,
    batteryRepository: BatteryRepository,
    wallpaperRepository: WallpaperRepository,
    weatherRepository: WeatherRepository,
    calendarRepository: CalendarRepository,
    @AppScope scope: CoroutineScope,
) {
    val state: StateFlow<LockUiState> = combine(
        settingsRepository.settings,
        batteryRepository.state,
        mediaRepository.state,
        notificationRepository.items,
        combine(
            quickSettingsRepository.state,
            wallpaperRepository.images,
            wallpaperRepository.subjects,
            weatherRepository.state,
            calendarRepository.state,
        ) { quick, images, subjects, weather, agenda -> LockExtras(quick, images, subjects, weather, agenda) },
    ) { settings, battery, media, notifications, extra ->
        LockUiState(
            settings = settings,
            battery = battery,
            media = if (settings.lockMusic) media else null,
            notifications = if (settings.lockShowNotifications) {
                notifications.filterNot { it.isSecret }.take(MAX_NOTIFICATIONS)
            } else {
                emptyList()
            },
            torchAvailable = extra.quick.torchAvailable,
            torchOn = extra.quick.torchOn,
            images = extra.images,
            subjects = extra.subjects,
            weather = extra.weather,
            agenda = extra.agenda,
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), LockUiState(settings = settingsRepository.settings.value))

    fun toggleTorch() {
        quickSettingsRepository.toggleTorch()
    }

    fun mediaPlayPause() = mediaRepository.playPause()
    fun mediaNext() = mediaRepository.next()
    fun mediaPrevious() = mediaRepository.previous()

    fun dismiss(item: NotificationItem) = notificationRepository.dismiss(item)

    fun open(item: NotificationItem, displayId: Int): Boolean = notificationRepository.open(item, displayId)

    private companion object {
        const val MAX_NOTIFICATIONS = 4
    }
}
