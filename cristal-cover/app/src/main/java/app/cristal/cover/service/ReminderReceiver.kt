package app.cristal.cover.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.cristal.cover.core.AppScope
import app.cristal.cover.data.brief.BriefRepository
import app.cristal.cover.data.brief.ReminderNotifier
import app.cristal.cover.data.brief.ReminderRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Hora do lembrete, e as ações da notificação (adiar, concluir). */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var reminders: ReminderRepository
    @Inject lateinit var brief: BriefRepository

    @Inject @AppScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id < 0) return
        val done = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_FIRE -> reminders.fire(id)?.let { ReminderNotifier.post(context, it, onIsland = brief.islandReady.value) }
                    ACTION_SNOOZE -> reminders.snooze(id, 10)
                    ACTION_DONE -> reminders.complete(id)
                    ACTION_DISMISSED -> Unit
                }
            } finally {
                done.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "app.cristal.cover.action.REMINDER_FIRE"
        const val ACTION_SNOOZE = "app.cristal.cover.action.REMINDER_SNOOZE"
        const val ACTION_DONE = "app.cristal.cover.action.REMINDER_DONE"
        const val ACTION_DISMISSED = "app.cristal.cover.action.REMINDER_DISMISSED"
        const val EXTRA_ID = "id"
    }
}
