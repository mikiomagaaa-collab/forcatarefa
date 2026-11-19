package app.cristal.cover.data.system

import android.Manifest
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import app.cristal.cover.core.safely
import app.cristal.cover.data.shizuku.ShizukuManager
import app.cristal.cover.data.shizuku.ShizukuStatus
import app.cristal.cover.service.CristalAccessibilityService
import app.cristal.cover.service.CristalNotificationListener
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
data class PermissionState(
    val notificationAccess: Boolean = false,
    val accessibility: Boolean = false,
    val calendar: Boolean = false,
    val location: Boolean = false,
    val defaultHome: Boolean = false,
    /** O app recebeu WRITE_SECURE_SETTINGS (via Shizuku/ADB) e consegue religar os próprios serviços. */
    val secureSettings: Boolean = false,
    val shizuku: ShizukuStatus = ShizukuStatus.NOT_INSTALLED,
)

/**
 * Lê o estado de todas as permissões e sabe abrir a tela certa do sistema para cada uma.
 * Com Shizuku, também concede os acessos especiais em um toque.
 */
@Singleton
class PermissionCenter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizuku: ShizukuManager,
) {
    private val _state = MutableStateFlow(PermissionState())
    val state: StateFlow<PermissionState> = _state.asStateFlow()

    private val listenerComponent = ComponentName(context, CristalNotificationListener::class.java)
    private val accessibilityComponent = ComponentName(context, CristalAccessibilityService::class.java)

    fun refresh(): PermissionState {
        shizuku.refresh()
        val fresh = PermissionState(
            notificationAccess = isNotificationAccessGranted(),
            accessibility = isAccessibilityEnabled(),
            calendar = granted(Manifest.permission.READ_CALENDAR),
            location = granted(Manifest.permission.ACCESS_COARSE_LOCATION),
            defaultHome = isDefaultHome(),
            secureSettings = granted(Manifest.permission.WRITE_SECURE_SETTINGS),
            shizuku = shizuku.status.value,
        )
        _state.value = fresh
        return fresh
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun isNotificationAccessGranted(): Boolean = safely("verificar acesso às notificações") {
        context.getSystemService(NotificationManager::class.java)
            ?.isNotificationListenerAccessGranted(listenerComponent)
    } ?: false

    fun isAccessibilityEnabled(): Boolean = safely("verificar acessibilidade") {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        enabled.split(':').any { ComponentName.unflattenFromString(it) == accessibilityComponent }
    } ?: false

    fun isDefaultHome(): Boolean = safely("verificar launcher padrão") {
        context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME)
    } ?: false

    // --- Intents para as telas do sistema ---------------------------------------------------------

    fun notificationAccessIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listenerComponent.flattenToString())

    fun notificationAccessFallbackIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    fun accessibilityIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun appDetailsIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

    fun homeSettingsIntent(): Intent = Intent(Settings.ACTION_HOME_SETTINGS)

    fun developerSettingsIntent(): Intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)

    /** Tela "Tela externa" da Motorola, quando existir; senão, as configurações gerais. */
    fun externalDisplaySettingsIntents(): List<Intent> = buildList {
        for (pkg in MOTO_COVER_SETTINGS_PACKAGES) {
            safely("localizar configurações da tela externa") {
                context.packageManager.getLaunchIntentForPackage(pkg)
            }?.let(::add)
        }
        add(Intent(Settings.ACTION_DISPLAY_SETTINGS))
        add(Intent(Settings.ACTION_SETTINGS))
    }

    fun shizukuAppIntent(): Intent? = safely("localizar app Shizuku") {
        context.packageManager.getLaunchIntentForPackage(ShizukuManager.SHIZUKU_PACKAGE)
    }

    fun shizukuDownloadIntent(): Intent =
        Intent(Intent.ACTION_VIEW, "https://shizuku.rikka.app/download/".toUri())

    // --- Concessão automática via Shizuku -----------------------------------------------------------

    /**
     * Concede acesso às notificações, liga o serviço de acessibilidade e pede WRITE_SECURE_SETTINGS,
     * tudo pela identidade "shell". Devolve `false` se o Shizuku não estiver pronto.
     */
    suspend fun grantSpecialAccessViaShell(): Boolean {
        if (!shizuku.isReady) return false
        val pkg = context.packageName
        val listener = listenerComponent.flattenToString()
        val a11y = accessibilityComponent.flattenToString()
        shizuku.exec("appops set $pkg ACCESS_RESTRICTED_SETTINGS allow")
        shizuku.exec("pm grant $pkg android.permission.WRITE_SECURE_SETTINGS")
        shizuku.exec("cmd notification allow_listener '$listener'")
        val current = shizuku.exec("settings get secure enabled_accessibility_services").output.trim()
        val list = current.split(':').filter { it.isNotBlank() && it != "null" }.toMutableList()
        if (list.none { ComponentName.unflattenFromString(it) == accessibilityComponent }) list += a11y
        // Só caracteres de nomes de componentes chegam aqui; ainda assim, vai entre aspas simples.
        val joined = list.joinToString(":").replace("'", "")
        shizuku.exec("settings put secure enabled_accessibility_services '$joined'")
        shizuku.exec("settings put secure accessibility_enabled 1")
        refresh()
        return true
    }

    /**
     * Religa o serviço de acessibilidade sozinho, se o app já tiver WRITE_SECURE_SETTINGS.
     * Útil depois de atualizar o APK, quando o Android desliga serviços de apps de fora da loja.
     */
    fun selfEnableAccessibility(): Boolean {
        if (!granted(Manifest.permission.WRITE_SECURE_SETTINGS)) return false
        if (isAccessibilityEnabled()) return true
        return safely("religar acessibilidade") {
            val resolver = context.contentResolver
            val current = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            val list = current.split(':').filter { it.isNotBlank() }.toMutableList()
            list += accessibilityComponent.flattenToString()
            Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, list.joinToString(":"))
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            true
        } ?: false
    }

    private companion object {
        val MOTO_COVER_SETTINGS_PACKAGES = listOf("com.motorola.cli.settings", "com.motorola.personalize")
    }
}
