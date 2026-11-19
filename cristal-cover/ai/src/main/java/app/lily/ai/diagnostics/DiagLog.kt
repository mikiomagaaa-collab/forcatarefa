package app.lily.ai.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * O registro de diagnóstico da L.I.L.Y AI, que não depende da interface: cada etapa da partida, os
 * módulos que não iniciaram e as falhas de carregamento de modelos. Vai para um arquivo
 * (files/diagnostico/registro.log, que sobrevive ao processo cair) e para o Logcat (tag [TAG]).
 * A etapa em andamento também fica no "resumo do processo" que o Android guarda quando ele morre
 * (ApplicationExitInfo), o que cobre até falhas nativas, travamentos e encerramento pelo sistema.
 *
 * Também conta as partidas que não terminaram: depois de [SAFE_AFTER] seguidas, a próxima abre em
 * modo seguro (sem efeitos e módulos opcionais).
 */
object DiagLog {
    const val TAG = "LilyDiag"
    private const val MAX_BYTES = 120_000L
    const val SAFE_AFTER = 2

    private val lock = Any()

    @Volatile
    private var dir: File? = null

    /** Partidas seguidas que não chegaram até a tela (zera quando a tela abre direito). */
    @Volatile
    var failedStarts = 0
        private set

    /** Modo seguro nesta partida: por falhas seguidas ou escolhido no diagnóstico. */
    @Volatile
    var safeMode = false
        private set

    fun dir(context: Context): File = File(context.filesDir, "diagnostico")

    private fun file(name: String): File? = dir?.let { File(it, name) }

    /**
     * Começo do processo principal (antes dos providers e do resto do app). Lê se a partida anterior
     * terminou; se não, pergunta ao Android como ela acabou para não contar, por exemplo, quem
     * fechou o app pela lista de recentes.
     */
    fun begin(context: Context) {
        appContext = context
        val d = dir(context).apply { mkdirs() }
        dir = d
        val marker = File(d, MARKER)
        val count = File(d, COUNT)
        var failures = count.readNumber()
        val previous = marker.takeIf { it.isFile }?.readText()?.trim()
        if (previous != null) {
            val pid = previous.substringBefore('|').toIntOrNull()
            val phase = previous.substringAfter('|', "")
            val reason = pid?.let { exitReason(context, it) }
            if (reason == null || reason in FAILURES) {
                failures++
                line("A partida anterior (pid $pid, fase $phase) não terminou: ${reason?.let(ExitHistory::reasonName) ?: "motivo desconhecido"}", Log.WARN)
            }
        }
        failedStarts = failures
        count.writeText(failures.toString())
        marker.writeText("${Process.myPid()}|app")
        safeMode = failures >= SAFE_AFTER || File(d, FORCED_SAFE).exists()
        line("Processo iniciado: L.I.L.Y AI ${versionOf(context)} · pid ${Process.myPid()}" + if (safeMode) " · MODO SEGURO ($failures partidas seguidas falharam)" else "")
    }

    /** O núcleo terminou de iniciar. A tela ainda pode estar por vir (marcada em [uiStarting]). */
    fun appReady() {
        val marker = file(MARKER) ?: return
        if (marker.readTextOrNull()?.startsWith("${Process.myPid()}|app") == true) marker.delete()
        line("Núcleo pronto")
    }

    /** A tela começou a ser criada: se o processo cair agora, conta como partida que falhou. */
    fun uiStarting(screen: String) {
        runCatching { file(MARKER)?.writeText("${Process.myPid()}|tela $screen") }
        line("Abrindo a tela: $screen")
    }

    /** A tela abriu e ficou de pé: zera as falhas seguidas. */
    fun uiReady(screen: String) {
        runCatching {
            file(MARKER)?.delete()
            if (failedStarts != 0) file(COUNT)?.writeText("0")
            file(LAST_OK)?.writeText(System.currentTimeMillis().toString())
        }
        failedStarts = 0
        line("Tela aberta: $screen")
    }

    /** Quando a interface abriu bem pela última vez (null se nunca abriu). */
    fun lastOk(context: Context): Long? = runCatching { File(dir(context), LAST_OK).readText().trim().toLong() }.getOrNull()

    /** Sair do modo seguro (botão do diagnóstico): zera a contagem e o modo escolhido. */
    fun leaveSafeMode(context: Context) {
        val d = dir(context)
        File(d, COUNT).delete()
        File(d, FORCED_SAFE).delete()
    }

    /** Abrir a próxima vez em modo seguro (botão do diagnóstico). */
    fun forceSafeMode(context: Context) {
        dir(context).mkdirs()
        File(dir(context), FORCED_SAFE).writeText("1")
    }

    fun isForcedSafe(context: Context): Boolean = File(dir(context), FORCED_SAFE).exists()

    /** Partidas seguidas que falharam, lidas do arquivo (serve para outro processo, como o do diagnóstico). */
    fun savedFailures(context: Context): Int = File(dir(context), COUNT).readNumber()

    fun line(text: String, level: Int = Log.INFO) {
        runCatching { Log.println(level, TAG, text) }
        val f = file(LOG) ?: return
        runCatching {
            synchronized(lock) {
                if (f.length() > MAX_BYTES) {
                    val keep = f.readText().takeLast((MAX_BYTES / 2).toInt())
                    f.writeText(keep.substringAfter('\n'))
                }
                FileOutputStream(f, true).use { it.write("${stamp()} [${Process.myPid()}] $text\n".toByteArray()) }
            }
        }
    }

    /** Uma etapa da partida: registra começo, fim e tempo, ou a falha (que segue adiante). */
    fun <T> step(name: String, block: () -> T): T {
        summary(name)
        line("▶ $name")
        val start = SystemClock.elapsedRealtime()
        try {
            val result = block()
            line("✓ $name (${SystemClock.elapsedRealtime() - start} ms)")
            return result
        } catch (t: Throwable) {
            line("✗ $name: ${t.javaClass.name}: ${t.message}", Log.ERROR)
            throw t
        }
    }

    /** Um módulo opcional não iniciou e o app segue sem ele. */
    fun moduleFailed(module: String, error: Throwable) {
        line("✗ Módulo indisponível: $module · ${error.javaClass.simpleName}: ${error.message}", Log.ERROR)
        Log.e(TAG, "Módulo indisponível: $module", error)
    }

    /** Situação dos módulos nesta partida (vai no relatório). */
    fun modules(lines: List<String>) {
        runCatching { file(MODULES)?.writeText(lines.joinToString("\n")) }
    }

    fun read(context: Context): String = File(dir(context), LOG).readTextOrNull().orEmpty()

    fun modulesText(context: Context): String = File(dir(context), MODULES).readTextOrNull().orEmpty()

    /** Guarda a etapa atual no resumo do processo (aparece no ApplicationExitInfo se ele morrer). */
    private fun summary(step: String) {
        val am = appContext?.getSystemService(ActivityManager::class.java) ?: return
        runCatching { am.setProcessStateSummary("etapa: $step".toByteArray().take(120).toByteArray()) }
    }

    @Volatile
    private var appContext: Context? = null

    private fun exitReason(context: Context, pid: Int): Int? = runCatching {
        context.getSystemService(ActivityManager::class.java)
            ?.getHistoricalProcessExitReasons(context.packageName, pid, 1)
            ?.firstOrNull()?.reason
    }.getOrNull()

    private fun versionOf(context: Context): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "${info.versionName} (${info.longVersionCode})"
    }.getOrDefault("?")

    private fun stamp(): String = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss.SSS", Locale.forLanguageTag("pt-BR"))
        .format(Instant.now().atZone(ZoneId.systemDefault()))

    private fun File.readNumber(): Int = readTextOrNull()?.trim()?.toIntOrNull() ?: 0

    private fun File.readTextOrNull(): String? = runCatching { if (isFile) readText() else null }.getOrNull()

    /** Motivos de saída que contam como partida que falhou. */
    private val FAILURES = setOf(
        ApplicationExitInfo.REASON_CRASH,
        ApplicationExitInfo.REASON_CRASH_NATIVE,
        ApplicationExitInfo.REASON_ANR,
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
        ApplicationExitInfo.REASON_DEPENDENCY_DIED,
        ApplicationExitInfo.REASON_UNKNOWN,
    )

    private const val LOG = "registro.log"
    private const val MARKER = "partida-em-andamento"
    private const val COUNT = "partidas-com-falha"
    private const val FORCED_SAFE = "modo-seguro"
    private const val MODULES = "modulos.txt"
    private const val LAST_OK = "ultima-abertura-ok"
}
