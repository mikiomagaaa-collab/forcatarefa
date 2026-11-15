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
import app.cristal.cover.data.prefs.WallpaperSlot
import app.cristal.cover.data.system.QuickSettingsRepository
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
) {
    /** Este estado "visto" da tela de bloqueio interna ou externa, com o papel de parede dela. */
    fun forDisplay(onCover: Boolean): LockUiState {
        val view = settings.forSlot(WallpaperSlot.lock(onCover))
        return copy(settings = view, wallpaper = images[view.wallpaperSlot])
    }
}

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
    @AppScope scope: CoroutineScope,
) {
    val state: StateFlow<LockUiState> = combine(
        settingsRepository.settings,
        batteryRepository.state,
        mediaRepository.state,
        notificationRepository.items,
        combine(quickSettingsRepository.state, wallpaperRepository.images) { quick, images -> quick to images },
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
            torchAvailable = extra.first.torchAvailable,
            torchOn = extra.first.torchOn,
            images = extra.second,
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
