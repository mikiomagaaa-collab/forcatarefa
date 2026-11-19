package app.cristal.cover.data.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.compose.ui.graphics.asImageBitmap
import app.cristal.cover.core.AppScope
import app.cristal.cover.core.DefaultDispatcher
import app.cristal.cover.core.Logx
import app.cristal.cover.core.safely
import app.cristal.cover.data.prefs.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lista de apps instalados (de todos os perfis, incluindo o de trabalho), com ícones já
 * renderizados. Recarrega sozinho quando um app é instalado, removido ou atualizado e quando o
 * pacote de ícones muda.
 */
@Singleton
class AppRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val iconPackManager: IconPackManager,
    @AppScope private val scope: CoroutineScope,
    @DefaultDispatcher private val default: CoroutineDispatcher,
) {
    private val launcherApps: LauncherApps? = context.getSystemService(LauncherApps::class.java)
    private val userManager: UserManager? = context.getSystemService(UserManager::class.java)

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val _loaded = MutableStateFlow(false)

    /** Fica `true` depois da primeira carga, mesmo que a lista esteja vazia. */
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val reloadRequests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val _removedPackages = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /** Pacotes desinstalados do perfil principal, para limpar atalhos órfãos. */
    val removedPackages: SharedFlow<String> = _removedPackages.asSharedFlow()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) {
            if (packageName != null && user == Process.myUserHandle()) _removedPackages.tryEmit(packageName)
            requestReload()
        }

        override fun onPackageAdded(packageName: String?, user: UserHandle?) = requestReload()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = requestReload()
        override fun onPackagesAvailable(names: Array<out String>?, user: UserHandle?, replacing: Boolean) =
            requestReload()

        override fun onPackagesUnavailable(names: Array<out String>?, user: UserHandle?, replacing: Boolean) =
            requestReload()
    }

    init {
        safely("registrar callback de apps") {
            launcherApps?.registerCallback(callback, Handler(Looper.getMainLooper()))
        }
        scope.launch {
            combine(
                settingsRepository.settings.map { it.iconPack }.distinctUntilChanged(),
                reloadRequests.onStart { emit(Unit) },
            ) { pack, _ -> pack }.collectLatest { pack -> load(pack) }
        }
    }

    fun requestReload() {
        reloadRequests.tryEmit(Unit)
    }

    fun find(key: String): AppInfo? = _apps.value.firstOrNull { it.key == key }

    private suspend fun load(iconPackPackage: String) {
        val result = withContext(default) {
            try {
                val la = launcherApps ?: return@withContext emptyList()
                val pack = iconPackManager.load(iconPackPackage)
                val iconSize = (context.resources.displayMetrics.density * ICON_DP).toInt().coerceIn(96, 224)
                val me = Process.myUserHandle()
                val profiles = safely("listar perfis") { userManager?.userProfiles } ?: listOf(me)
                val out = ArrayList<AppInfo>(128)
                for (user in profiles) {
                    val serial = safely("serial do perfil") { userManager?.getSerialNumberForUser(user) } ?: 0L
                    val activities = safely("listar activities do perfil") { la.getActivityList(null, user) }
                        ?: continue
                    for (info in activities) {
                        if (info.componentName.packageName == context.packageName) continue
                        buildApp(info, user, serial, user != me, pack, iconSize)?.let(out::add)
                    }
                }
                out.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                Logx.e("Falha ao carregar a lista de apps", t)
                _apps.value
            }
        }
        _apps.value = result
        _loaded.value = true
    }

    private fun buildApp(
        info: LauncherActivityInfo,
        user: UserHandle,
        serial: Long,
        isWork: Boolean,
        pack: IconPack?,
        iconSize: Int,
    ): AppInfo? = safely("montar app ${info.componentName}") {
        val component = info.componentName
        val packDrawable = pack?.drawableFor(component)
        val drawable = packDrawable ?: safely("ícone do app") { info.getIcon(0) }
        val rendered = if (drawable != null) {
            safely("renderizar ícone") { IconRenderer.render(drawable, iconSize, fromIconPack = packDrawable != null) }
        } else {
            null
        }
        val full = rendered?.full ?: IconRenderer.placeholder(iconSize)
        AppInfo(
            key = AppInfo.keyOf(component, serial),
            component = component,
            user = user,
            label = info.label?.toString()?.trim().orEmpty().ifEmpty { component.packageName },
            icon = full.asImageBitmap(),
            monoIcon = rendered?.mono?.asImageBitmap(),
            maskable = rendered?.maskable ?: true,
            isSystem = (info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            isWorkProfile = isWork,
            accent = rendered?.accent ?: 0xFFD9D6E3.toInt(),
        )
    }

    private companion object {
        const val ICON_DP = 64f
    }
}
