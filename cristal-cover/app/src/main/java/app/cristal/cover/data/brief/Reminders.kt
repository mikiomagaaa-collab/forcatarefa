package app.cristal.cover.data.brief

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import app.cristal.cover.R
import app.cristal.cover.core.Logx
import app.cristal.cover.service.ReminderReceiver
import java.text.SimpleDateFormat
import java.util.Locale

/** Agenda e cancela o aviso de cada lembrete no AlarmManager. */
object ReminderScheduler {
    /** "Alarmes e lembretes" liberado: o aviso chega na hora exata. Sem isso, pode atrasar alguns minutos. */
    fun canExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    fun schedule(context: Context, reminder: Reminder) {
        val at = reminder.at ?: return
        if (reminder.done) return
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = fireIntent(context, reminder.id)
        try {
            when {
                canExact(context) && reminder.alarm -> am.setAlarmClock(AlarmManager.AlarmClockInfo(at, pi), pi)
                canExact(context) -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                else -> am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            // A permissão foi retirada entre a checagem e o pedido: fica o aviso aproximado.
            Logx.w("Alarme exato negado; usando aviso aproximado", e)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(context: Context, id: Long) {
        context.getSystemService(AlarmManager::class.java)?.cancel(fireIntent(context, id))
    }

    private fun fireIntent(context: Context, id: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        id.hashCode(),
        Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_FIRE).putExtra(ReminderReceiver.EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** Mostra o aviso do lembrete na hora marcada. */
object ReminderNotifier {
    /** Com a Liquid Island ativa: som, sem o aviso flutuante do sistema (a ilha já mostra). */
    const val CHANNEL_ISLAND = "lembretes_ilha"
    const val CHANNEL_REMINDERS = "lembretes"
    const val CHANNEL_ALARMS = "alarmes_info_brief"
    private const val TAG = "lembrete"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Lembretes", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Lembretes do Info Brief e do Now Brief."
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ISLAND, "Lembretes na Liquid Island", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Quando a Liquid Island está ligada, o lembrete aparece nela, sem repetir o aviso do sistema."
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARMS, "Alarmes do Info Brief", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Lembretes que tocam como alarme até você responder."
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                enableVibration(true)
                setBypassDnd(false)
            },
        )
    }

    fun enabled(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == true

    fun post(context: Context, reminder: Reminder, onIsland: Boolean, late: Boolean = false) {
        if (!enabled(context)) {
            Logx.w("Notificações desligadas: o lembrete \"${reminder.title}\" não pôde avisar")
            return
        }
        ensureChannels(context)
        val time = reminder.at?.let { SimpleDateFormat("HH:mm", Locale.forLanguageTag("pt-BR")).format(it) }
        val channel = when {
            reminder.alarm -> CHANNEL_ALARMS
            onIsland -> CHANNEL_ISLAND
            else -> CHANNEL_REMINDERS
        }
        val builder = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_tile_cristal)
            .setContentTitle(reminder.title)
            .setContentText(listOfNotNull(if (late) "Era às" else "Agora", time).joinToString(" · ") + (reminder.place?.let { " · $it" } ?: ""))
            .setCategory(if (reminder.alarm) Notification.CATEGORY_ALARM else Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setWhen(reminder.at ?: System.currentTimeMillis())
            .setShowWhen(true)
            .setContentIntent(action(context, ReminderReceiver.ACTION_DONE, reminder.id))
            .setDeleteIntent(action(context, ReminderReceiver.ACTION_DISMISSED, reminder.id))
            .addAction(Notification.Action.Builder(null, "Adiar 10 min", action(context, ReminderReceiver.ACTION_SNOOZE, reminder.id)).build())
            .addAction(Notification.Action.Builder(null, "Concluir", action(context, ReminderReceiver.ACTION_DONE, reminder.id)).build())
        val notification = builder.build()
        if (reminder.alarm) notification.flags = notification.flags or Notification.FLAG_INSISTENT
        try {
            context.getSystemService(NotificationManager::class.java)?.notify(TAG, reminder.id.hashCode(), notification)
        } catch (e: SecurityException) {
            Logx.w("Sem permissão para notificar", e)
        }
    }

    fun cancel(context: Context, id: Long) {
        context.getSystemService(NotificationManager::class.java)?.cancel(TAG, id.hashCode())
    }

    private fun action(context: Context, action: String, id: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        id.hashCode(),
        Intent(context, ReminderReceiver::class.java).setAction(action).putExtra(ReminderReceiver.EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
