package app.lily.ai.assist

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import app.lily.ai.AiGraph
import app.lily.ai.R
import app.lily.ai.data.MsgStatus
import app.lily.ai.data.Role
import app.lily.ai.ui.AssistActivity
import app.lily.ai.ui.MainActivity
import app.lily.ai.ui.Route
import app.lily.ai.ui.screens.ChatSession
import app.lily.ai.ui.screens.Markdown
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * A L.I.L.Y por voz (o painel em lente que a ilha, o widget e o assistente do Android abrem).
 * Guarda a conversa de voz por alguns minutos (perguntas de acompanhamento) e, se o painel for
 * fechado no meio de uma resposta, ela continua: um Live Update mostra "respondendo" (a Liquid
 * Island exibe) e, ao terminar, uma notificação traz a resposta.
 */
class AssistController(private val graph: AiGraph, private val app: Application) {
    var session: ChatSession? = null
        private set
    private var lastUsed = 0L
    private var attached = false
    private var watcher: Job? = null

    /** A conversa de voz atual (a mesma por 5 minutos depois do último uso), ou uma nova. */
    fun session(): ChatSession {
        val now = System.currentTimeMillis()
        val current = session
        if (current != null && (current.busy || now - lastUsed < KEEP_MS)) {
            lastUsed = now
            return current
        }
        current?.dispose()
        lastUsed = now
        return ChatSession(graph, app, Route.Chat()).also { session = it }
    }

    /** O painel apareceu: some com os avisos de fundo. */
    fun attach() {
        attached = true
        lastUsed = System.currentTimeMillis()
        watcher?.cancel()
        manager().cancel(WORKING_ID)
        manager().cancel(DONE_ID)
    }

    /** O painel saiu da tela: se ainda está respondendo, avisa pela ilha e, no fim, pela notificação. */
    fun detach() {
        attached = false
        lastUsed = System.currentTimeMillis()
        val s = session ?: return
        if (!s.working.value) return
        watcher?.cancel()
        watcher = graph.scope.launch {
            post(WORKING_ID, working(s))
            s.working.first { !it }
            manager().cancel(WORKING_ID)
            val answer = s.messages.lastOrNull { it.role == Role.ASSISTANT }
            if (!attached && answer != null && answer.status != MsgStatus.STOPPED) post(DONE_ID, done(s, Markdown.plain(answer.text)))
        }
    }

    private fun post(id: Int, n: Notification) {
        runCatching { manager().notify(id, n) }
    }

    private fun manager(): NotificationManager = app.getSystemService(NotificationManager::class.java).also { nm ->
        nm.createNotificationChannel(NotificationChannel(CHANNEL_WORKING, "L.I.L.Y respondendo", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_DONE, "Respostas da L.I.L.Y", NotificationManager.IMPORTANCE_DEFAULT))
    }

    /** Live Update do Android 16: a Liquid Island mostra a L.I.L.Y trabalhando. */
    private fun working(s: ChatSession): Notification {
        val question = s.messages.lastOrNull { it.role == Role.USER }?.text.orEmpty()
        // Tocar no aviso (ou na ilha) reabre o painel para ver a resposta, sem começar a ouvir.
        val open = PendingIntent.getActivity(
            app,
            21,
            Intent(app, AssistActivity::class.java).putExtra(AssistActivity.EXTRA_SILENT, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = Notification.Builder(app, CHANNEL_WORKING)
            .setSmallIcon(R.drawable.ic_stat_lily)
            .setContentTitle("L.I.L.Y respondendo")
            .setContentText(question.take(80))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(Notification.Builder(app, CHANNEL_WORKING).setSmallIcon(R.drawable.ic_stat_lily).setContentTitle("L.I.L.Y respondendo").build())
            .setContentIntent(open)
        if (Build.VERSION.SDK_INT >= 36) {
            b.setStyle(Notification.ProgressStyle().setProgressIndeterminate(true))
            b.setShortCriticalText("L.I.L.Y")
            if (Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) b.setRequestPromotedOngoing(true)
        } else {
            b.setProgress(0, 0, true)
        }
        return b.build()
    }

    private fun done(s: ChatSession, answer: String): Notification {
        val open = PendingIntent.getActivity(
            app,
            22,
            Intent(app, MainActivity::class.java).putExtra(MainActivity.EXTRA_CONVERSATION, s.conversationId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(app, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_lily)
            .setContentTitle("L.I.L.Y respondeu")
            .setContentText(answer.take(140))
            .setStyle(Notification.BigTextStyle().bigText(answer.take(1_000)))
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(Notification.Builder(app, CHANNEL_DONE).setSmallIcon(R.drawable.ic_stat_lily).setContentTitle("L.I.L.Y respondeu").build())
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
    }

    companion object {
        const val KEEP_MS = 5 * 60_000L
        private const val CHANNEL_WORKING = "voz_respondendo"
        private const val CHANNEL_DONE = "voz_respostas"
        private const val WORKING_ID = 7201
        private const val DONE_ID = 7202
    }
}
