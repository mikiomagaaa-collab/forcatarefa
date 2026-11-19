package app.lily.ai.video

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import app.lily.ai.LilyAiApp
import app.lily.ai.R
import app.lily.ai.presence.LilyPresence
import app.lily.ai.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Acompanha os vídeos em geração (a fila roda no serviço online; aqui só se pergunta o andamento e se
 * baixa o resultado). A notificação é um Live Update: a Liquid Island mostra o andamento estimado, e a
 * avatar fica em "trabalhando". Para sozinho quando não há mais nada em andamento.
 */
class VideoService : Service() {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + kotlinx.coroutines.CoroutineExceptionHandler { _, t ->
            runCatching { app.lily.ai.diagnostics.CrashReport.save(this, "vídeo", t, fatal = false) }
        },
    )
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel(this)
        val graph = (application as LilyAiApp).graphOrNull
        startForeground(NOTIFICATION_ID, notification(this, "Vídeo da L.I.L.Y", "Conferindo o andamento", null), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (graph == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (loop?.isActive == true) return START_NOT_STICKY
        loop = scope.launch {
            var finishedBefore = graph.videos.jobs.value.filter { !it.active }.map { it.id }.toSet()
            // No vídeo grátis o acompanhamento fica numa conexão longa: a notificação segue o andamento
            // pelos dados da fila enquanto isso (no máximo a cada 3 s).
            val follow = launch {
                graph.videos.jobs.collect { jobs ->
                    val first = jobs.firstOrNull { it.active && it.free && it.status == "running" } ?: return@collect
                    LilyPresence.working(VideoJobs.PRESENCE, "Gerando vídeo (grátis)", first.progress)
                    runCatching {
                        getSystemService(NotificationManager::class.java).notify(
                            NOTIFICATION_ID,
                            notification(this@VideoService, "Gerando vídeo grátis", "Cerca de ${(first.progress * 100).toInt()}% (estimado)", first.progress),
                        )
                    }
                    delay(3_000)
                }
            }
            while (true) {
                val more = try {
                    graph.videos.pollOnce(graph.settings.value.value.videoAutoSave)
                } catch (c: kotlinx.coroutines.CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    runCatching { app.lily.ai.diagnostics.CrashReport.save(this@VideoService, "vídeo", t, fatal = false) }
                    false
                }
                val jobs = graph.videos.jobs.value
                val active = jobs.filter { it.active }
                // Avisos de término (sucesso ou falha) e a presença da avatar.
                jobs.filter { !it.active && it.id !in finishedBefore }.forEach { j ->
                    LilyPresence.finished(VideoJobs.PRESENCE, j.status == "done", if (j.status == "done") "Vídeo pronto" else "O vídeo não saiu")
                    notifyResult(j)
                }
                finishedBefore = jobs.filter { !it.active }.map { it.id }.toSet()
                if (!more) break
                val first = active.first()
                val label = when (first.status) {
                    "queued" -> first.position?.let { "Na fila (posição ${it + 1})" } ?: "Na fila"
                    "downloading" -> "Baixando o vídeo"
                    else -> "Gerando · cerca de ${(first.progress * 100).toInt()}% (estimado)"
                }
                LilyPresence.working(VideoJobs.PRESENCE, "Gerando vídeo", first.progress)
                runCatching {
                    getSystemService(NotificationManager::class.java).notify(
                        NOTIFICATION_ID,
                        notification(this@VideoService, if (active.size > 1) "${active.size} vídeos em geração" else "Gerando vídeo", label, first.progress),
                    )
                }
                delay(POLL_MS)
            }
            follow.cancel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun notifyResult(job: VideoJob) {
        val ok = job.status == "done"
        if (job.status == "cancelled") return
        val n = Notification.Builder(this, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_lily)
            .setContentTitle(if (ok) "Vídeo pronto" else "O vídeo não foi gerado")
            .setContentText(if (ok) job.prompt.take(100) else job.error ?: "Tente de novo.")
            .setContentIntent(openIntent(this))
            .setAutoCancel(true)
            .build()
        runCatching { getSystemService(NotificationManager::class.java).notify(job.id.hashCode(), n) }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "video_gerando"
        private const val CHANNEL_DONE = "video_pronto"
        private const val NOTIFICATION_ID = 7301
        const val POLL_MS = 5_000L

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, VideoService::class.java)) }
        }

        private fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Vídeos em geração", NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel(CHANNEL_DONE, "Vídeos prontos", NotificationManager.IMPORTANCE_DEFAULT))
        }

        private fun openIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            31,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "video").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** Live Update do Android 16 (a Liquid Island exibe); antes disso, uma barra de progresso comum. */
        fun notification(context: Context, title: String, text: String, progress: Float?): Notification {
            val b = Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_lily)
                .setContentTitle(title)
                .setContentText("$text · processamento online")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setContentIntent(openIntent(context))
            if (Build.VERSION.SDK_INT >= 36) {
                val style = Notification.ProgressStyle()
                if (progress == null) style.setProgressIndeterminate(true) else style.setProgress((progress * 100).toInt())
                b.setStyle(style)
                b.setShortCriticalText("Vídeo")
                if (Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) b.setRequestPromotedOngoing(true)
            } else {
                b.setProgress(100, ((progress ?: 0f) * 100).toInt(), progress == null)
            }
            return b.build()
        }
    }
}
