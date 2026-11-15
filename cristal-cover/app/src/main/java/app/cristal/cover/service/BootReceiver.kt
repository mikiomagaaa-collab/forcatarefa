package app.cristal.cover.service

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import app.cristal.cover.core.safely
import app.cristal.cover.data.system.PermissionCenter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Depois de ligar o aparelho ou atualizar o app, pede a reconexão do listener de notificações e,
 * se o app tiver WRITE_SECURE_SETTINGS (concedida via Shizuku/ADB), religa a acessibilidade.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var permissionCenter: PermissionCenter

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                safely("religar acessibilidade no boot") { permissionCenter.selfEnableAccessibility() }
                safely("reconectar listener no boot") {
                    if (permissionCenter.isNotificationAccessGranted()) {
                        NotificationListenerService.requestRebind(
                            ComponentName(context, CristalNotificationListener::class.java),
                        )
                    }
                }
            }
        }
    }
}
