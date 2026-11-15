package app.cristal.cover.data.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.Immutable
import app.cristal.cover.core.safely
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

/** Um widget oferecido por outro app, pronto para aparecer na lista de escolha. */
@Immutable
class WidgetProviderEntry(
    val info: AppWidgetProviderInfo,
    val label: String,
    val appLabel: String,
    val spanX: Int,
    val spanY: Int,
)

/**
 * Hospeda widgets de outros apps (AppWidgetHost). Tudo é opcional e tolerante a falhas: se o
 * aparelho não suportar widgets ou o usuário negar a permissão, as funções devolvem vazio ou falso.
 */
@Singleton
class WidgetHostManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager: AppWidgetManager? = safely("obter AppWidgetManager") { AppWidgetManager.getInstance(context) }
    private val host: AppWidgetHost? = safely("criar AppWidgetHost") { AppWidgetHost(context, HOST_ID) }
    private var listening = false

    val isSupported: Boolean
        get() = manager != null && host != null &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS)

    /** Os widgets só se atualizam enquanto o host está "ouvindo"; chamado no onStart/onStop da tela. */
    fun startListening() {
        if (listening || !isSupported) return
        listening = safely("iniciar host de widgets") {
            host?.startListening()
            true
        } ?: false
    }

    fun stopListening() {
        if (!listening) return
        listening = false
        safely("parar host de widgets") { host?.stopListening() }
    }

    /** Lista os widgets instalados, com o tamanho já convertido para células da grade. */
    fun providers(cols: Int, rows: Int): List<WidgetProviderEntry> {
        if (!isSupported) return emptyList()
        val pm = context.packageManager
        val density = context.resources.displayMetrics.density
        val list = safely("listar widgets instalados") { manager?.installedProviders }.orEmpty()
        return list.mapNotNull { info ->
            safely("descrever widget") {
                val appLabel = pm.getApplicationLabel(
                    pm.getApplicationInfo(info.provider.packageName, PackageManager.ApplicationInfoFlags.of(0)),
                ).toString()
                val spanX = if (info.targetCellWidth > 0) info.targetCellWidth else ceil((info.minWidth / density + 16f) / CELL_DP).toInt()
                val spanY = if (info.targetCellHeight > 0) info.targetCellHeight else ceil((info.minHeight / density + 16f) / CELL_DP).toInt()
                WidgetProviderEntry(
                    info = info,
                    label = info.loadLabel(pm).orEmpty().ifBlank { appLabel },
                    appLabel = appLabel,
                    spanX = spanX.coerceIn(1, cols),
                    spanY = spanY.coerceIn(1, rows),
                )
            }
        }.sortedWith(compareBy({ it.appLabel.lowercase() }, { it.label.lowercase() }))
    }

    /** Reserva um id para um widget novo. Devolve -1 se não for possível. */
    fun allocateId(): Int = safely("reservar id de widget") { host?.allocateAppWidgetId() } ?: -1

    /** Tenta vincular sem perguntar; `false` significa que é preciso pedir ao usuário com [bindIntent]. */
    fun bindIfAllowed(id: Int, info: AppWidgetProviderInfo): Boolean = safely("vincular widget") {
        manager?.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)
    } ?: false

    fun bindIntent(id: Int, info: AppWidgetProviderInfo): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)

    /** Abre a tela de configuração do widget, se ele tiver uma. */
    fun configureIfNeeded(activity: Activity, id: Int, info: AppWidgetProviderInfo) {
        if (info.configure == null) return
        safely("abrir configuração do widget") {
            host?.startAppWidgetConfigureActivityForResult(activity, id, 0, REQUEST_CONFIGURE, null)
        }
    }

    fun delete(id: Int) {
        if (id < 0) return
        safely("remover widget do host") { host?.deleteAppWidgetId(id) }
    }

    fun infoOf(id: Int): AppWidgetProviderInfo? =
        if (id < 0) null else safely("ler widget $id") { manager?.getAppWidgetInfo(id) }

    fun createView(viewContext: Context, id: Int): AppWidgetHostView? {
        val info = infoOf(id) ?: return null
        return safely("criar view do widget") { host?.createView(viewContext, id, info) }
    }

    companion object {
        private const val HOST_ID = 0x4352
        const val REQUEST_CONFIGURE = 7301
        private const val CELL_DP = 74f
    }
}
