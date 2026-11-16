package app.lily.ai.models

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import app.lily.ai.LilyAiApp
import app.lily.ai.R
import app.lily.ai.ui.MainActivity
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Baixa os modelos em primeiro plano. A notificação usa o estilo de progresso do Android 16 e pede
 * destaque (Live Update): a Liquid Island do Cristal mostra o download sem nenhuma ligação extra.
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var worker: Job? = null
    private var lastNotify = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val graph = (application as LilyAiApp).graph
        when (intent?.action) {
            ACTION_PAUSE -> intent.getStringExtra(EXTRA_ID)?.let(graph.downloads::pause)
            ACTION_CANCEL -> intent.getStringExtra(EXTRA_ID)?.let(Catalog::byId)?.let(graph.downloads::cancel)
        }
        ensureChannel()
        startForeground(NOTIFICATION_ID, build("Preparando downloads", null, null), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (worker?.isActive != true) worker = scope.launch { runQueue() }
        return START_NOT_STICKY
    }

    private suspend fun runQueue() {
        val graph = (application as LilyAiApp).graph
        val downloads = graph.downloads
        val store = graph.store
        val downloader = Downloader(store)
        while (true) {
            val spec = downloads.next() ?: break
            val update = store.updateAvailable(spec)
            try {
                downloads.set(spec.id, DlState.Running(DownloadProgress(store.bytesOnDisk(spec), spec.totalBytes, 0, "")))
                downloader.download(spec, paused = { downloads.isPaused(spec.id) }) { p ->
                    downloads.set(spec.id, DlState.Running(p))
                    notifyProgress(spec, p)
                }
                if (update) store.discardPartial(spec)
                store.markInstalled(spec, update)
                downloads.set(spec.id, null)
                notifyDone(spec)
            } catch (paused: DownloadPaused) {
                if (downloads.isCancelled(spec.id)) {
                    downloads.set(spec.id, null)
                } else {
                    downloads.set(spec.id, DlState.Paused(store.bytesOnDisk(spec), spec.totalBytes))
                }
            } catch (t: IOException) {
                Log.w(TAG, "Download de ${spec.id} falhou", t)
                store.recordFailure(spec, t.message.orEmpty())
                downloads.set(spec.id, DlState.Failed(t.message ?: "Falha de rede"))
            } catch (t: Throwable) {
                Log.w(TAG, "Download de ${spec.id} falhou", t)
                downloads.set(spec.id, DlState.Failed(t.message ?: t.javaClass.simpleName))
            }
        }
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun notifyProgress(spec: ModelSpec, p: DownloadProgress) {
        val now = System.currentTimeMillis()
        if (now - lastNotify < 1_000 && !p.verifying) return
        lastNotify = now
        val text = if (p.verifying) "Conferindo ${p.file}" else "${formatBytes(p.done)} de ${formatBytes(p.total)} · ${formatBytes(p.bytesPerSecond)}/s"
        manager().notify(NOTIFICATION_ID, build("Baixando ${spec.name}", text, p, spec.id))
    }

    private fun notifyDone(spec: ModelSpec) {
        manager().notify(
            spec.id.hashCode(),
            Notification.Builder(this, CHANNEL_DONE)
                .setSmallIcon(R.drawable.ic_stat_lily)
                .setContentTitle("${spec.name} instalado")
                .setContentText("Pronto para usar, sem internet.")
                .setContentIntent(openHub())
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun build(title: String, text: String?, p: DownloadProgress?, id: String? = null): Notification {
        val b = Notification.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_lily)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setContentIntent(openHub())
        if (text != null) b.setContentText(text)
        val percent = ((p?.fraction ?: 0f) * 100).toInt()
        if (p != null && Build.VERSION.SDK_INT >= 36) {
            // Live Update do Android 16: barra oficial e texto curto (a ilha mostra os dois).
            b.setStyle(Notification.ProgressStyle().setProgress(percent).setProgressIndeterminate(p.verifying))
            b.setShortCriticalText(if (p.verifying) "Conferindo" else "$percent%")
            if (Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) b.setRequestPromotedOngoing(true)
        } else if (p != null) {
            b.setProgress(100, percent, p.verifying)
        }
        if (id != null) {
            b.addAction(Notification.Action.Builder(null, "Pausar", service(ACTION_PAUSE, id)).build())
            b.addAction(Notification.Action.Builder(null, "Cancelar", service(ACTION_CANCEL, id)).build())
        }
        return b.build()
    }

    private fun service(action: String, id: String): PendingIntent = PendingIntent.getService(
        this,
        (action + id).hashCode(),
        Intent(this, DownloadService::class.java).setAction(action).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openHub(): PendingIntent = PendingIntent.getActivity(
        this,
        1,
        Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "hub").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun manager(): NotificationManager = getSystemService(NotificationManager::class.java)

    private fun ensureChannel() {
        manager().createNotificationChannel(NotificationChannel(CHANNEL_PROGRESS, "Downloads de modelos", NotificationManager.IMPORTANCE_LOW))
        manager().createNotificationChannel(NotificationChannel(CHANNEL_DONE, "Modelos instalados", NotificationManager.IMPORTANCE_DEFAULT))
    }

    /**
     * Android 15+: serviços de sincronização têm um limite de horas por dia. Ao chegar nele, pausa
     * (a parte baixada fica no disco) e encerra, como o sistema pede.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        (application as LilyAiApp).graph.downloads.pauseAll()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_RUN = "app.lily.ai.download.RUN"
        const val ACTION_PAUSE = "app.lily.ai.download.PAUSE"
        const val ACTION_CANCEL = "app.lily.ai.download.CANCEL"
        const val EXTRA_ID = "id"
        private const val CHANNEL_PROGRESS = "downloads"
        private const val CHANNEL_DONE = "instalados"
        private const val NOTIFICATION_ID = 7001
        private const val TAG = "LilyDownloads"
    }
}

/** "1,2 GB", "340 MB". */
fun formatBytes(bytes: Long): String {
    val locale = java.util.Locale.forLanguageTag("pt-BR")
    return when {
        bytes >= 1L shl 30 -> String.format(locale, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
        bytes >= 1L shl 20 -> String.format(locale, "%.0f MB", bytes / (1024.0 * 1024))
        bytes >= 1L shl 10 -> String.format(locale, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
