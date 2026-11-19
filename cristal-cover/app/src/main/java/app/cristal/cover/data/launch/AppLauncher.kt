package app.cristal.cover.data.launch

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.view.Display
import app.cristal.cover.core.AppScope
import app.cristal.cover.core.IoDispatcher
import app.cristal.cover.core.Logx
import app.cristal.cover.core.safely
import app.cristal.cover.data.apps.AppInfo
import app.cristal.cover.data.db.AppPrefDao
import app.cristal.cover.data.db.AppPrefEntity
import app.cristal.cover.data.display.CoverDisplayRepository
import app.cristal.cover.data.prefs.SettingsRepository
import app.cristal.cover.data.shizuku.ShizukuManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface LaunchOutcome {
    /** O pedido foi aceito pelo sistema. Não garante que o fabricante deixou o app aparecer. */
    data object Started : LaunchOutcome

    /** O app tem sinais de incompatibilidade; a UI deve perguntar antes de abrir. */
    data class NeedsConfirmation(val app: AppInfo, val report: CompatReport) : LaunchOutcome

    data class Failed(val reason: LaunchFailure) : LaunchOutcome
}

enum class LaunchFailure { NOT_FOUND, NOT_ALLOWED, UNKNOWN }

/**
 * Abre apps na tela pedida.
 *
 * Caminho normal: [LauncherApps.startMainActivity] com [ActivityOptions.setLaunchDisplayId], que o
 * Android permite a qualquer app em telas públicas. Quando o Shizuku está ativo, antes de abrir
 * pedimos ao serviço da Motorola para liberar o pacote na tela externa e aplicamos os ajustes de
 * compatibilidade escolhidos. Se o caminho normal falhar, tentamos `am start --display` via shell.
 */
@Singleton
class AppLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val displayRepository: CoverDisplayRepository,
    private val compatAdvisor: CompatAdvisor,
    private val appPrefDao: AppPrefDao,
    private val shizuku: ShizukuManager,
    @AppScope private val scope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val launcherApps: LauncherApps? = context.getSystemService(LauncherApps::class.java)

    /** Pacotes já liberados na tela externa desde que o processo iniciou (a liberação some no boot). */
    private val allowedOnCover: MutableSet<String> = Collections.synchronizedSet(HashSet())
    private val compatApplied: MutableSet<String> = Collections.synchronizedSet(HashSet())
    @Volatile private var allowAllApplied = false
    @Volatile private var selfAllowed = false

    /** Tela cuja densidade alteramos e a densidade original, para desfazer depois. */
    @Volatile private var scaledDisplayId: Int = Display.INVALID_DISPLAY

    /**
     * @param displayId tela de destino; [Display.INVALID_DISPLAY] deixa o sistema decidir.
     * @param onCover se o destino é a tela externa (liga avisos e ajustes de compatibilidade).
     * @param force ignora o aviso de compatibilidade (o usuário já confirmou).
     */
    suspend fun launch(
        app: AppInfo,
        displayId: Int,
        onCover: Boolean,
        sourceBounds: Rect? = null,
        force: Boolean = false,
    ): LaunchOutcome {
        val settings = settingsRepository.settings.value
        val pref = withContext(io) { safely("ler preferências do app") { appPrefDao.get(app.key) } }

        if (onCover && !force && settings.warnIncompatible && pref?.skipWarning != true) {
            val cover = displayRepository.currentCover(settings.coverDisplayOverride)
            val report = compatAdvisor.evaluate(app.component, cover)
            if (report.level != CompatLevel.OK) return LaunchOutcome.NeedsConfirmation(app, report)
        }

        if (onCover) prepareCover(app, pref, displayId)

        val outcome = startOnDisplay(app, displayId, sourceBounds)
        val finalOutcome = if (outcome is LaunchOutcome.Failed && shizuku.isReady) {
            startViaShell(app, displayId)
        } else {
            outcome
        }
        if (finalOutcome == LaunchOutcome.Started) recordLaunch(app, pref)
        return finalOutcome
    }

    private fun startOnDisplay(app: AppInfo, displayId: Int, sourceBounds: Rect?): LaunchOutcome {
        val la = launcherApps ?: return LaunchOutcome.Failed(LaunchFailure.UNKNOWN)
        return try {
            la.startMainActivity(app.component, app.user, sourceBounds, optionsFor(displayId))
            LaunchOutcome.Started
        } catch (e: ActivityNotFoundException) {
            Logx.w("App não encontrado: ${app.key}", e)
            LaunchOutcome.Failed(LaunchFailure.NOT_FOUND)
        } catch (e: SecurityException) {
            Logx.w("Sem permissão para abrir ${app.key} na tela $displayId", e)
            LaunchOutcome.Failed(LaunchFailure.NOT_ALLOWED)
        } catch (e: Exception) {
            // IllegalArgumentException quando a tela some entre a escolha e o lançamento, etc.
            Logx.w("Falha ao abrir ${app.key} na tela $displayId", e)
            if (displayId != Display.INVALID_DISPLAY) {
                // Última tentativa sem fixar a tela.
                try {
                    la.startMainActivity(app.component, app.user, sourceBounds, null)
                    LaunchOutcome.Started
                } catch (inner: Exception) {
                    LaunchOutcome.Failed(LaunchFailure.UNKNOWN)
                }
            } else {
                LaunchOutcome.Failed(LaunchFailure.UNKNOWN)
            }
        }
    }

    private suspend fun startViaShell(app: AppInfo, displayId: Int): LaunchOutcome {
        val component = app.component.flattenToShortString()
        if (!isSafeComponent(component)) return LaunchOutcome.Failed(LaunchFailure.UNKNOWN)
        val userArg = if (app.user == Process.myUserHandle()) "" else " --user ${app.user.hashCode()}"
        val displayArg = if (displayId != Display.INVALID_DISPLAY) " --display $displayId" else ""
        val result = shizuku.exec(
            "am start$displayArg$userArg -a android.intent.action.MAIN " +
                "-c android.intent.category.LAUNCHER -f 0x10200000 -n '$component'",
        )
        return if (result.ok && !result.output.contains("Error", ignoreCase = true)) {
            LaunchOutcome.Started
        } else {
            Logx.w("am start falhou: ${result.output}")
            LaunchOutcome.Failed(LaunchFailure.NOT_ALLOWED)
        }
    }

    /** Ajustes que só fazem sentido na tela externa e dependem do Shizuku. */
    /**
     * Libera um pacote na tela externa antes de abrir uma tela dele por lá sem passar por [launch]
     * (a L.I.L.Y pelo widget, pela busca ou pela Central): sem a liberação, a Motorola fecha o app
     * logo que ele abre. Só faz algo com o Shizuku ativo e "Liberar apps automaticamente" ligado.
     */
    suspend fun allowOnCover(packageName: String) {
        if (!shizuku.isReady || !isSafePackage(packageName)) return
        val commands = allowCommands(packageName)
        if (commands.isNotEmpty()) shizuku.execAll(commands)
    }

    /** Os comandos do climanager que ainda faltam para liberar [pkg] na tela externa. */
    private fun allowCommands(pkg: String): MutableList<String> {
        val commands = ArrayList<String>()
        if (settingsRepository.settings.value.autoAllowOnCover) {
            if (!allowAllApplied) {
                allowAllApplied = true
                commands += "cmd climanager set-allow-all-oncli true"
            }
            if (allowedOnCover.add(pkg)) commands += "cmd climanager set-pkg-allowed-oncli $pkg true"
        }
        return commands
    }

    private suspend fun prepareCover(app: AppInfo, pref: AppPrefEntity?, displayId: Int) {
        if (!shizuku.isReady) return
        val pkg = app.packageName
        if (!isSafePackage(pkg)) return
        val commands = allowCommands(pkg)
        if (pref?.forceResizable == true && compatApplied.add(pkg)) {
            commands += "am compat enable FORCE_RESIZE_APP $pkg"
        }
        val scale = pref?.scalePercent ?: 100
        if (scale in 50..99 && displayId != Display.INVALID_DISPLAY) {
            val base = baseDensity(displayId)
            if (base > 0) {
                commands += "wm density ${(base * scale / 100).coerceAtLeast(120)} -d $displayId"
                scaledDisplayId = displayId
            }
        }
        if (commands.isNotEmpty()) {
            // Os comandos do climanager não existem fora da Motorola; a falha é esperada e ignorada.
            shizuku.execAll(commands)
        }
    }

    /** Densidade física da tela, lida do próprio `wm`, para não confundir com um override ativo. */
    private suspend fun baseDensity(displayId: Int): Int {
        val out = shizuku.exec("wm density -d $displayId").output
        val physical = Regex("Physical density:\\s*(\\d+)").find(out)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return physical ?: displayRepository.snapshot().firstOrNull { it.id == displayId }?.densityDpi ?: 0
    }

    /** Desfaz a escala de densidade aplicada para o último app. Chamado quando o launcher volta. */
    fun restoreDisplayScale() {
        val id = scaledDisplayId
        if (id == Display.INVALID_DISPLAY) return
        scaledDisplayId = Display.INVALID_DISPLAY
        scope.launch { shizuku.exec("wm density reset -d $id") }
    }

    /** Liga ou desliga "ignorar orientação pedida pelos apps" na tela externa. */
    suspend fun applyIgnoreOrientation(enabled: Boolean): Boolean {
        val cover = displayRepository.currentCover(settingsRepository.settings.value.coverDisplayOverride)
            ?: return false
        return shizuku.exec("wm set-ignore-orientation-request -d ${cover.id} $enabled").ok
    }

    /** Remove o ajuste "forçar redimensionamento" de um app. */
    suspend fun clearCompat(packageName: String) {
        if (!isSafePackage(packageName)) return
        compatApplied.remove(packageName)
        shizuku.exec("am compat reset FORCE_RESIZE_APP $packageName")
    }

    private fun recordLaunch(app: AppInfo, pref: AppPrefEntity?) {
        scope.launch(io) {
            safely("registrar uso do app") {
                val base = pref ?: AppPrefEntity(componentKey = app.key)
                appPrefDao.upsert(base.copy(launchCount = base.launchCount + 1, lastLaunch = System.currentTimeMillis()))
            }
        }
    }

    /** Pede ao serviço da Motorola (via Shizuku) que deixe o próprio Cristal aparecer na tela externa. */
    suspend fun allowSelfOnCover() {
        if (selfAllowed || !shizuku.isReady) return
        selfAllowed = true
        allowAllApplied = true
        shizuku.execAll(
            listOf(
                "cmd climanager set-allow-all-oncli true",
                "cmd climanager set-pkg-allowed-oncli ${context.packageName} true",
            ),
        )
    }

    /**
     * Abre o launcher do Cristal na tela externa. Primeiro pelo caminho normal do Android; se o
     * sistema recusar e o Shizuku estiver ativo, pelo `am start --display`.
     */
    suspend fun openSelfOnCover(): Boolean {
        val cover = displayRepository.currentCover(settingsRepository.settings.value.coverDisplayOverride) ?: return false
        if (cover.isDefault) return false
        allowSelfOnCover()
        val intent = Intent().setClassName(context, LAUNCHER_CLASS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val started = try {
            context.startActivity(intent, optionsFor(cover.id))
            true
        } catch (e: Exception) {
            Logx.w("Caminho normal recusou abrir o Cristal na tela ${cover.id}", e)
            false
        }
        if (started || !shizuku.isReady) return started
        return shizuku.exec("am start --display ${cover.id} -n '${context.packageName}/$LAUNCHER_CLASS'").ok
    }

    fun optionsFor(displayId: Int): Bundle? {
        if (displayId == Display.INVALID_DISPLAY) return null
        return safely("montar ActivityOptions") {
            ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle()
        }
    }

    /** Abre uma Intent comum na tela pedida, com todos os erros tratados. */
    fun startIntent(intent: Intent, displayId: Int = Display.INVALID_DISPLAY, from: Context = context): Boolean {
        if (from === context) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            from.startActivity(intent, optionsFor(displayId))
            true
        } catch (e: Exception) {
            Logx.w("Falha ao abrir intent ${intent.action}", e)
            if (displayId == Display.INVALID_DISPLAY) return false
            try {
                from.startActivity(intent)
                true
            } catch (inner: Exception) {
                false
            }
        }
    }

    fun openAppInfo(app: AppInfo, displayId: Int): Boolean = safely("abrir detalhes do app") {
        launcherApps?.startAppDetailsActivity(app.component, app.user, null, optionsFor(displayId))
        true
    } ?: false

    fun uninstall(app: AppInfo, displayId: Int): Boolean {
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
            .putExtra(Intent.EXTRA_USER, app.user)
        return startIntent(intent, displayId)
    }

    companion object {
        const val LAUNCHER_CLASS = "app.cristal.cover.ui.launcher.LauncherActivity"
        private val SAFE_PACKAGE = Regex("^[A-Za-z0-9_.]+$")
        private val SAFE_COMPONENT = Regex("^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$")

        /** Só nomes de pacote "bem comportados" entram em comandos de shell (sem aspas nem espaços). */
        fun isSafePackage(value: String): Boolean = SAFE_PACKAGE.matches(value)

        /** Componentes vão sempre entre aspas simples; aqui garantimos que não há aspas no nome. */
        fun isSafeComponent(value: String): Boolean = SAFE_COMPONENT.matches(value)

        fun componentOf(flat: String): ComponentName? = ComponentName.unflattenFromString(flat)
    }
}
