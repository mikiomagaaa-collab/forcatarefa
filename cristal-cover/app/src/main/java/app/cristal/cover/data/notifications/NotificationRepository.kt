package app.cristal.cover.data.notifications

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.view.Display
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import app.cristal.cover.core.Logx
import app.cristal.cover.core.safely
import dagger.hilt.android.qualifiers.ApplicationContext
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
data class NotificationItem(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val color: Int,
    val isClearable: Boolean,
    val autoCancel: Boolean,
    /** Visibilidade efetiva na tela de bloqueio (constantes de [Notification]). */
    val lockVisibility: Int,
    val contentIntent: PendingIntent?,
    val appIcon: ImageBitmap?,
    val largeIcon: ImageBitmap?,
    /** Categoria do Android (ex.: "call", "alarm"), para a Liquid Island. */
    val category: String? = null,
    /** Notificação contínua (chamada em andamento, timer, gravação). */
    val ongoing: Boolean = false,
    /** Início (ou fim, se [countDown]) do cronômetro mostrado pela notificação, em ms de relógio. */
    val chronometerBase: Long? = null,
    val countDown: Boolean = false,
    /** Canal da notificação: abre o ajuste certo (como o pop-up) para este tipo de aviso. */
    val channelId: String? = null,
    /** Importância efetiva do canal (constantes de NotificationManager). Alta = pop-up do sistema. */
    val importance: Int = android.app.NotificationManager.IMPORTANCE_DEFAULT,
    /** Progresso publicado pelo app (-1 quando não há barra). */
    val progress: Int = -1,
    val progressMax: Int = 0,
    val progressIndeterminate: Boolean = false,
    /** Etapas do Live Update do Android 16 (posição de cada ponto, na escala de [progressMax]). */
    val progressPoints: List<Int> = emptyList(),
    /** Texto curto do Live Update (o que o Android mostra no chip da barra de status). */
    val shortCriticalText: String? = null,
    /** O app pediu destaque como Live Update, ou o sistema já promoveu a notificação. */
    val promoted: Boolean = false,
) {
    /** Tem barra de progresso publicada pelo app. */
    val hasProgress: Boolean get() = progressIndeterminate || (progressMax > 0 && progress >= 0)

    val isCall: Boolean get() = category == Notification.CATEGORY_CALL && ongoing
    val isSecret: Boolean get() = lockVisibility == Notification.VISIBILITY_SECRET
    val isPublic: Boolean get() = lockVisibility == Notification.VISIBILITY_PUBLIC
}

/**
 * Estado das notificações, alimentado pelo [NotificationListenerService] do app. A UI nunca fala
 * com o serviço diretamente: tudo passa por aqui, e funciona (vazio) mesmo com o acesso negado.
 */
@Singleton
class NotificationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _items = MutableStateFlow<List<NotificationItem>>(emptyList())
    val items: StateFlow<List<NotificationItem>> = _items.asStateFlow()

    private val _connected = MutableStateFlow(false)

    /** `true` enquanto o sistema mantém o listener conectado. */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private var service: WeakReference<NotificationListenerService>? = null

    fun onConnected(listener: NotificationListenerService) {
        service = WeakReference(listener)
        _connected.value = true
    }

    fun onDisconnected() {
        service = null
        _connected.value = false
        _items.value = emptyList()
    }

    fun publish(items: List<NotificationItem>) {
        _items.value = items
    }

    fun dismiss(item: NotificationItem) {
        if (!item.isClearable) return
        safely("dispensar notificação") { service?.get()?.cancelNotification(item.key) }
        // Resposta imediata na UI; o serviço confirma em seguida.
        _items.value = _items.value.filterNot { it.key == item.key }
    }

    fun dismissAll() {
        safely("limpar notificações") { service?.get()?.cancelAllNotifications() }
        _items.value = _items.value.filterNot { it.isClearable }
    }

    /** Abre a notificação na tela pedida. Devolve `false` se ela não tiver ação ou a ação falhar. */
    fun open(item: NotificationItem, displayId: Int): Boolean {
        val intent = item.contentIntent ?: return false
        return try {
            val options = ActivityOptions.makeBasic()
            if (displayId != Display.INVALID_DISPLAY) options.launchDisplayId = displayId
            if (Build.VERSION.SDK_INT >= 36) {
                options.pendingIntentBackgroundActivityStartMode =
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
            } else if (Build.VERSION.SDK_INT >= 34) {
                @Suppress("DEPRECATION")
                options.pendingIntentBackgroundActivityStartMode =
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            }
            intent.send(context, 0, null, null, null, null, options.toBundle())
            if (item.autoCancel) dismiss(item)
            true
        } catch (e: PendingIntent.CanceledException) {
            Logx.w("A ação da notificação foi cancelada pelo app", e)
            false
        } catch (e: Exception) {
            Logx.w("Falha ao abrir notificação", e)
            false
        }
    }
}
