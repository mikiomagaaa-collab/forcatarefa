package app.cristal.cover.service

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.app.AlarmManager
import app.cristal.cover.core.AppScope
import app.cristal.cover.core.safely
import app.cristal.cover.data.brief.BriefRepository
import app.cristal.cover.data.brief.ReminderNotifier
import app.cristal.cover.data.brief.ReminderRepository
import app.cristal.cover.data.system.PermissionCenter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Depois de ligar o aparelho ou atualizar o app, pede a reconexão do listener de notificações e,
 * se o app tiver WRITE_SECURE_SETTINGS (concedida via Shizuku/ADB), religa a acessibilidade.
 * Também reagenda os lembretes (o Android apaga os alarmes ao desligar) e avisa os que passaram;
 * e, quando "Alarmes e lembretes" é liberado, troca os avisos aproximados pelos exatos.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var permissionCenter: PermissionCenter
    @Inject lateinit var reminders: ReminderRepository
    @Inject lateinit var brief: BriefRepository

    @Inject @AppScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                safely("religar acessibilidade no boot") { permissionCenter.selfEnableAccessibility() }
                reschedule(context)
                safely("reconectar listener no boot") {
                    if (permissionCenter.isNotificationAccessGranted()) {
                        NotificationListenerService.requestRebind(
                            ComponentName(context, CristalNotificationListener::class.java),
                        )
                    }
                }
            }
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> reschedule(context)
        }
    }

    private fun reschedule(context: Context) {
        val done = goAsync()
        scope.launch {
            try {
                val missed = reminders.rescheduleAll()
                missed.forEach { ReminderNotifier.post(context, it, onIsland = brief.islandReady.value, late = true) }
            } finally {
                done.finish()
            }
        }
    }
}
