package app.cristal.cover.data.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import app.cristal.cover.BuildConfig
import app.cristal.cover.core.IoDispatcher
import app.cristal.cover.core.Logx
import app.cristal.cover.core.safely
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

enum class ShizukuStatus {
    /** O app Shizuku não está instalado. */
    NOT_INSTALLED,

    /** Instalado, mas o serviço não foi iniciado (precisa da depuração sem fio ou de root). */
    NOT_RUNNING,

    /** Rodando, mas o Cristal ainda não foi autorizado. */
    NO_PERMISSION,

    READY,
}

data class ShellResult(val exitCode: Int, val output: String) {
    val ok: Boolean get() = exitCode == 0

    companion object {
        val unavailable = ShellResult(-100, "Shizuku indisponível")
    }
}

/**
 * Ponte com o Shizuku. Tudo aqui é opcional: se o Shizuku não existir, não estiver rodando ou a
 * permissão for negada, [exec] devolve [ShellResult.unavailable] em vez de lançar exceção.
 */
@Singleton
class ShizukuManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val _status = MutableStateFlow(ShizukuStatus.NOT_INSTALLED)
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    private val bindMutex = Mutex()
    @Volatile private var service: IShellService? = null
    private var pendingBind: CompletableDeferred<IShellService?>? = null

    private val args: Shizuku.UserServiceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(context.packageName, ShellService::class.java.name))
            .daemon(false)
            .processNameSuffix("shell")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = if (binder != null && binder.pingBinder()) IShellService.Stub.asInterface(binder) else null
            service = svc
            pendingBind?.complete(svc)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener {
        service = null
        refresh()
    }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    init {
        safely("registrar listeners do Shizuku") {
            Shizuku.addBinderReceivedListenerSticky(binderReceived)
            Shizuku.addBinderDeadListener(binderDead)
            Shizuku.addRequestPermissionResultListener(permissionResult)
        }
        refresh()
    }

    val isReady: Boolean get() = _status.value == ShizukuStatus.READY

    fun isInstalled(): Boolean = safely("verificar Shizuku instalado") {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } ?: false

    /** Reavalia o estado. Barato; pode ser chamado em todo onResume. */
    fun refresh() {
        _status.value = try {
            when {
                !Shizuku.pingBinder() -> if (isInstalled()) ShizukuStatus.NOT_RUNNING else ShizukuStatus.NOT_INSTALLED
                Shizuku.isPreV11() -> ShizukuStatus.NOT_RUNNING
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuStatus.READY
                else -> ShizukuStatus.NO_PERMISSION
            }
        } catch (t: Throwable) {
            Logx.w("Shizuku: estado indisponível", t)
            if (isInstalled()) ShizukuStatus.NOT_RUNNING else ShizukuStatus.NOT_INSTALLED
        }
    }

    /** Abre o diálogo de permissão do Shizuku. Devolve `false` se nem isso for possível. */
    fun requestPermission(): Boolean = safely("pedir permissão do Shizuku") {
        if (!Shizuku.pingBinder() || Shizuku.isPreV11()) return@safely false
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            refresh()
            return@safely true
        }
        Shizuku.requestPermission(REQUEST_CODE)
        true
    } ?: false

    /** Roda [command] como "shell". Nunca lança: falhas viram um [ShellResult] com código negativo. */
    suspend fun exec(command: String): ShellResult = withContext(io) {
        refresh()
        if (!isReady) return@withContext ShellResult.unavailable
        val svc = obtainService() ?: return@withContext ShellResult.unavailable
        try {
            parse(svc.exec(command))
        } catch (t: Throwable) {
            Logx.w("Shizuku: falha ao executar comando", t)
            service = null
            ShellResult(-1, t.message ?: t.javaClass.simpleName)
        }
    }

    /** Executa vários comandos em sequência e devolve o resultado do último. */
    suspend fun execAll(commands: List<String>): ShellResult {
        var last = ShellResult(0, "")
        for (c in commands) last = exec(c)
        return last
    }

    private suspend fun obtainService(): IShellService? = bindMutex.withLock {
        service?.let { existing ->
            if (safely("ping do serviço shell") { existing.asBinder().pingBinder() } == true) return@withLock existing
        }
        service = null
        val deferred = CompletableDeferred<IShellService?>()
        pendingBind = deferred
        val started = safely("vincular serviço de usuário do Shizuku") {
            Shizuku.bindUserService(args, connection)
            true
        } ?: false
        if (!started) {
            pendingBind = null
            return@withLock null
        }
        val result = withTimeoutOrNull(BIND_TIMEOUT_MS) { deferred.await() }
        pendingBind = null
        result
    }

    private fun parse(raw: String?): ShellResult {
        if (raw == null) return ShellResult(-1, "")
        val newline = raw.indexOf('\n')
        if (newline < 0) return ShellResult(raw.trim().toIntOrNull() ?: -1, "")
        val code = raw.substring(0, newline).trim().toIntOrNull() ?: -1
        return ShellResult(code, raw.substring(newline + 1).trim())
    }

    companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val REQUEST_CODE = 4107
        private const val BIND_TIMEOUT_MS = 6_000L
    }
}
