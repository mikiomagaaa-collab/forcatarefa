package app.cristal.cover.data.shizuku

import android.content.Context
import androidx.annotation.Keep
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Serviço de usuário do Shizuku. É instanciado pelo Shizuku em um processo separado, que roda com
 * a identidade "shell" (a mesma do ADB). Por isso não pode usar nada do processo principal do app.
 */
@Keep
class ShellService : IShellService.Stub {

    @Keep
    constructor() : super()

    /** Construtor usado pelo Shizuku 13+. */
    @Keep
    @Suppress("UNUSED_PARAMETER")
    constructor(context: Context) : super()

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(command: String?): String {
        if (command.isNullOrBlank()) return "-1\ncomando vazio"
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = StringBuilder()
            val reader = Thread {
                runCatching {
                    process.inputStream.bufferedReader().use { r ->
                        val buf = CharArray(4096)
                        while (true) {
                            val n = r.read(buf)
                            if (n < 0) break
                            if (output.length < MAX_OUTPUT) output.append(buf, 0, n)
                        }
                    }
                }
            }
            reader.start()
            val finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return "-2\ntempo esgotado"
            }
            reader.join(1_000)
            "${process.exitValue()}\n$output"
        } catch (t: Throwable) {
            "-1\n${t.javaClass.simpleName}: ${t.message}"
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 15L
        const val MAX_OUTPUT = 64 * 1024
    }
}
