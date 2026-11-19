package app.cristal.cover.service

import android.app.Notification
import android.os.Build
import android.app.NotificationManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import app.cristal.cover.core.Logx
import app.cristal.cover.core.safely
import app.cristal.cover.data.apps.AppRepository
import app.cristal.cover.data.media.MediaRepository
import app.cristal.cover.data.notifications.NotificationItem
import app.cristal.cover.data.notifications.NotificationRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Recebe as notificações do sistema e as publica no [NotificationRepository].
 * Também habilita o [MediaRepository], que precisa de um listener ativo para ver as sessões de mídia.
 *
 * O serviço não guarda nem envia nada para fora do aparelho.
 */
@AndroidEntryPoint
class CristalNotificationListener : NotificationListenerService() {

    @Inject lateinit var repository: NotificationRepository
    @Inject lateinit var mediaRepository: MediaRepository
    @Inject lateinit var appRepository: AppRepository

    private var scope: CoroutineScope? = null
    private var refreshJob: Job? = null
    private val labelCache = HashMap<String, String>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        repository.onConnected(this)
        mediaRepository.onListenerConnected(ComponentName(this, CristalNotificationListener::class.java))
        scheduleRefresh(immediate = true)
    }

    override fun onListenerDisconnected() {
        repository.onDisconnected()
        mediaRepository.onListenerDisconnected()
        scope?.cancel()
        scope = null
        super.onListenerDisconnected()
        // Pede ao sistema para reconectar (acontece, por exemplo, depois de atualizar o app).
        safely("pedir reconexão do listener") {
            requestRebind(ComponentName(this, CristalNotificationListener::class.java))
        }
    }

    override fun onDestroy() {
        repository.onDisconnected()
        mediaRepository.onListenerDisconnected()
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = scheduleRefresh()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = scheduleRefresh()

    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) = scheduleRefresh()

    /** Junta rajadas de eventos em uma única reconstrução da lista. */
    private fun scheduleRefresh(immediate: Boolean = false) {
        val s = scope ?: return
        refreshJob?.cancel()
        refreshJob = s.launch {
            if (!immediate) delay(DEBOUNCE_MS)
            val list = withContext(Dispatchers.Default) { buildList() }
            if (list != null) repository.publish(list)
        }
    }

    private fun buildList(): List<NotificationItem>? {
        val active: Array<StatusBarNotification> = try {
            activeNotifications ?: return emptyList()
        } catch (t: Throwable) {
            // SecurityException se o acesso foi revogado no meio do caminho.
            Logx.w("Não foi possível ler as notificações ativas", t)
            return null
        }
        val rankingMap = safely("ler ranking") { currentRanking }
        val order = rankingMap?.orderedKeys?.withIndex()?.associate { (i, k) -> k to i }.orEmpty()
        val groupsWithChildren = active
            .filter { it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
            .mapNotNull { it.groupKey }
            .toSet()
        val ranking = Ranking()
        val out = ArrayList<NotificationItem>(active.size)
        for (sbn in active) {
            val item = safely("converter notificação") { convert(sbn, rankingMap, ranking, groupsWithChildren) }
            if (item != null) out += item
        }
        return out.sortedWith(compareBy({ order[it.key] ?: Int.MAX_VALUE }, { -it.postTime }))
    }

    private fun convert(
        sbn: StatusBarNotification,
        rankingMap: RankingMap?,
        ranking: Ranking,
        groupsWithChildren: Set<String>,
    ): NotificationItem? {
        val n = sbn.notification ?: return null
        if (sbn.packageName == packageName) return null
        val isSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0
        if (isSummary && sbn.groupKey in groupsWithChildren) return null
        // Notificações de mídia viram o card de música.
        if (n.category == Notification.CATEGORY_TRANSPORT || n.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true) {
            return null
        }

        var lockVisibility = n.visibility
        var importance = NotificationManager.IMPORTANCE_DEFAULT
        if (rankingMap != null && rankingMap.getRanking(sbn.key, ranking)) {
            if (ranking.importance < NotificationManager.IMPORTANCE_LOW) return null
            importance = ranking.importance
            if (ranking.isSuspended) return null
            val override = ranking.lockscreenVisibilityOverride
            if (override != Ranking.VISIBILITY_NO_OVERRIDE) lockVisibility = override
        }

        val extras = n.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = (extras?.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras?.getCharSequence(Notification.EXTRA_SUB_TEXT))?.toString()?.trim().orEmpty()
        if (title.isEmpty() && text.isEmpty()) return null

        return NotificationItem(
            key = sbn.key,
            packageName = sbn.packageName,
            appLabel = labelOf(sbn.packageName),
            title = title,
            text = text,
            postTime = if (n.`when` > 0) n.`when` else sbn.postTime,
            color = n.color,
            isClearable = sbn.isClearable,
            autoCancel = n.flags and Notification.FLAG_AUTO_CANCEL != 0,
            lockVisibility = lockVisibility,
            contentIntent = n.contentIntent,
            appIcon = appRepository.apps.value.firstOrNull { it.packageName == sbn.packageName }?.icon,
            largeIcon = largeIconOf(n),
            category = n.category,
            ongoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0,
            chronometerBase = if (extras?.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER) == true && n.`when` > 0) n.`when` else null,
            countDown = extras?.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN) == true,
            channelId = n.channelId,
            importance = importance,
        ).withProgress(n)
    }

    /**
     * Lê a barra de progresso que o app publicou. No Android 16, quando o app usa o formato oficial
     * de Live Update (ProgressStyle), lê também as etapas, o texto curto e o pedido de destaque.
     */
    private fun NotificationItem.withProgress(n: Notification): NotificationItem {
        val extras = n.extras ?: return this
        var max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        var value = if (max > 0) extras.getInt(Notification.EXTRA_PROGRESS, -1) else -1
        var indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)
        var points = emptyList<Int>()
        var shortText: String? = null
        var promoted = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            shortText = safely("texto curto do Live Update") { n.shortCriticalText }?.takeIf { it.isNotBlank() }
            // isRequestPromotedOngoing só existe a partir do Android 16 QPR2 (36.1).
            val canAskPromoted = Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1
            promoted = n.flags and Notification.FLAG_PROMOTED_ONGOING != 0 ||
                (canAskPromoted && safely("pedido de destaque") { n.isRequestPromotedOngoing } == true)
            val style = safely("estilo de progresso") {
                if (extras.getString(Notification.EXTRA_TEMPLATE)?.endsWith("ProgressStyle") == true) {
                    Notification.Builder.recoverBuilder(this@CristalNotificationListener, n).style as? Notification.ProgressStyle
                } else {
                    null
                }
            }
            if (style != null) {
                max = style.progressMax
                value = style.progress
                indeterminate = style.isProgressIndeterminate
                points = style.progressPoints.map { it.position }.sorted()
            }
        }
        return copy(
            progress = value,
            progressMax = max,
            progressIndeterminate = indeterminate,
            progressPoints = points,
            shortCriticalText = shortText,
            promoted = promoted,
        )
    }

    private fun labelOf(pkg: String): String = labelCache.getOrPut(pkg) {
        safely("rótulo de $pkg") {
            val pm = packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))).toString()
        } ?: pkg
    }

    private fun largeIconOf(n: Notification): ImageBitmap? = safely("ícone grande da notificação") {
        val drawable = n.getLargeIcon()?.loadDrawable(this) ?: return@safely null
        val bmp: Bitmap = createBitmap(LARGE_ICON_PX, LARGE_ICON_PX)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, LARGE_ICON_PX, LARGE_ICON_PX)
        drawable.draw(canvas)
        bmp.asImageBitmap()
    }

    private companion object {
        const val DEBOUNCE_MS = 120L
        const val LARGE_ICON_PX = 112
    }
}
