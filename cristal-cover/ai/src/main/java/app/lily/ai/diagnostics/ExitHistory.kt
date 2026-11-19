package app.lily.ai.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Como os processos da L.I.L.Y AI terminaram, segundo o próprio Android (ApplicationExitInfo):
 * erro, falha nativa, "não está respondendo", falta de memória, encerramento pelo sistema ou pela
 * pessoa. Funciona mesmo quando o app nem chegou a mostrar a tela, porque o registro é do sistema.
 */
object ExitHistory {

    data class Exit(
        val time: Long,
        val pid: Int,
        val process: String,
        val reason: Int,
        val status: Int,
        val description: String?,
        val importance: Int,
        val pssKb: Long,
        val rssKb: Long,
        val step: String?,
        val trace: String?,
    )

    fun read(context: Context, max: Int = 12): List<Exit> = runCatching {
        val am = context.getSystemService(ActivityManager::class.java) ?: return emptyList()
        am.getHistoricalProcessExitReasons(context.packageName, 0, max).map { it.toExit() }
    }.getOrDefault(emptyList())

    private fun ApplicationExitInfo.toExit(): Exit {
        val trace = when (reason) {
            ApplicationExitInfo.REASON_CRASH_NATIVE -> readTrace(this)?.let { Tombstone.summary(it) }
            ApplicationExitInfo.REASON_ANR -> readTrace(this)?.let { anr(String(it)) }
            else -> null
        }
        return Exit(
            time = timestamp,
            pid = pid,
            process = processName,
            reason = reason,
            status = status,
            description = description,
            importance = importance,
            pssKb = pss,
            rssKb = rss,
            step = processStateSummary?.let { String(it) },
            trace = trace,
        )
    }

    private fun readTrace(info: ApplicationExitInfo): ByteArray? = runCatching {
        info.traceInputStream?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (out.size() < MAX_TRACE) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        }
    }.getOrNull()

    /** Do relatório de "não está respondendo", só a pilha da thread principal (a que travou). */
    fun anr(text: String): String {
        val main = Regex("(?s)\"main\".*?(?=\\n\\n\"|\\z)").find(text)?.value
        return (main ?: text).lines().take(40).joinToString("\n")
    }

    fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_EXIT_SELF -> "fechou sozinho (normal)"
        ApplicationExitInfo.REASON_SIGNALED -> "encerrado por sinal do sistema"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "encerrado por falta de memória"
        ApplicationExitInfo.REASON_CRASH -> "erro (exceção Java/Kotlin)"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "falha nativa (biblioteca em C/C++)"
        ApplicationExitInfo.REASON_ANR -> "parou de responder (ANR)"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "falha ao iniciar o processo"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "permissão alterada"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "uso excessivo de recursos"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "fechado pela pessoa (recentes ou Forçar parada)"
        ApplicationExitInfo.REASON_USER_STOPPED -> "usuário do aparelho parado"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "um app de que ele dependia caiu"
        ApplicationExitInfo.REASON_OTHER -> "outro motivo do sistema"
        ApplicationExitInfo.REASON_FREEZER -> "congelado pelo sistema"
        ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> "estado do pacote mudou"
        ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "app atualizado"
        else -> "desconhecido"
    }

    fun format(exits: List<Exit>): String = if (exits.isEmpty()) {
        "Nenhum registro do Android sobre como o app terminou."
    } else {
        exits.joinToString("\n\n") { e ->
            buildString {
                append(time(e.time)).append(" · ").append(e.process).append(" (pid ").append(e.pid).append(")\n")
                append("  Motivo: ").append(reasonName(e.reason))
                if (e.reason == ApplicationExitInfo.REASON_CRASH_NATIVE || e.reason == ApplicationExitInfo.REASON_SIGNALED) append(" · sinal ").append(e.status)
                append('\n')
                e.description?.takeIf { it.isNotBlank() }?.let { append("  Detalhe: ").append(it.take(300)).append('\n') }
                e.step?.takeIf { it.isNotBlank() }?.let { append("  Última etapa registrada: ").append(it.removePrefix("etapa: ")).append('\n') }
                append("  Memória: ").append(e.pssKb / 1024).append(" MB (PSS) · ").append(e.rssKb / 1024).append(" MB (RSS)")
                e.trace?.let { append("\n  Rastro:\n").append(it.prependIndent("    ")) }
            }
        }
    }

    private fun time(millis: Long): String = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.forLanguageTag("pt-BR"))
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    private const val MAX_TRACE = 512 * 1024
}

/**
 * Leitura mínima da "lápide" (tombstone) que o Android grava numa falha nativa, no formato protobuf
 * do debuggerd: sinal, mensagem de abort, causa, a pilha da thread que caiu e as últimas linhas do
 * Logcat do processo. O suficiente para saber qual biblioteca falhou.
 */
object Tombstone {
    private class Reader(val b: ByteArray, var pos: Int = 0, val end: Int = b.size) {
        fun more() = pos < end
        fun varint(): Long {
            var shift = 0
            var result = 0L
            while (pos < end) {
                val byte = b[pos++].toInt() and 0xFF
                result = result or ((byte and 0x7F).toLong() shl shift)
                if (byte and 0x80 == 0) return result
                shift += 7
                if (shift > 63) break
            }
            return result
        }

        /** Campo seguinte: número, tipo e, para os de tamanho variável, o trecho. */
        fun next(): Triple<Int, Int, Any>? {
            if (!more()) return null
            val key = varint()
            val field = (key ushr 3).toInt()
            return when (val type = (key and 7).toInt()) {
                0 -> Triple(field, type, varint())
                1 -> { pos += 8; Triple(field, type, 0L) }
                2 -> {
                    val len = varint().toInt()
                    if (len < 0 || pos + len > end) { pos = end; return null }
                    val r = Reader(b, pos, pos + len)
                    pos += len
                    Triple(field, type, r)
                }
                5 -> { pos += 4; Triple(field, type, 0L) }
                else -> { pos = end; null }
            }
        }

        fun string(): String = String(b, pos, end - pos)
    }

    private class Frame(var function: String = "", var offset: Long = 0, var file: String = "", var pc: Long = 0)

    fun summary(bytes: ByteArray): String = runCatching { parse(bytes) }.getOrNull()?.takeIf { it.isNotBlank() }
        ?: "(lápide sem dados reconhecíveis: ${bytes.size} bytes)"

    private fun parse(bytes: ByteArray): String {
        val r = Reader(bytes)
        var tid = 0L
        var signal = ""
        var abort = ""
        val causes = ArrayList<String>()
        val threads = HashMap<Long, Pair<String, List<Frame>>>()
        val logs = ArrayList<String>()
        while (true) {
            val (field, _, value) = r.next() ?: break
            when (field) {
                6 -> tid = value as Long
                10 -> signal = signal(value as Reader)
                14 -> abort = (value as Reader).string()
                15 -> (value as Reader).next()?.let { (f, _, v) -> if (f == 1) causes += (v as Reader).string() }
                16 -> thread(value as Reader)?.let { threads[it.first] = it.second }
                18 -> logs += logBuffer(value as Reader)
            }
        }
        return buildString {
            if (signal.isNotEmpty()) append("Sinal: ").append(signal).append('\n')
            if (abort.isNotEmpty()) append("Abort: ").append(abort.take(400)).append('\n')
            causes.forEach { append("Causa: ").append(it).append('\n') }
            val crashed = threads[tid] ?: threads.values.firstOrNull()
            crashed?.let { (name, frames) ->
                append("Thread que caiu: ").append(name).append('\n')
                frames.take(16).forEachIndexed { i, f ->
                    append("  #").append(i.toString().padStart(2, '0')).append(' ').append(f.file.ifEmpty { "?" })
                    if (f.function.isNotEmpty()) append(" (").append(f.function).append("+").append(f.offset).append(')')
                    append('\n')
                }
            }
            if (logs.isNotEmpty()) {
                append("Últimas linhas do Logcat do processo:\n")
                logs.takeLast(20).forEach { append("  ").append(it).append('\n') }
            }
        }.trimEnd()
    }

    private fun signal(r: Reader): String {
        var number = 0L
        var name = ""
        var code = ""
        var fault: Long? = null
        while (true) {
            val (f, _, v) = r.next() ?: break
            when (f) {
                1 -> number = v as Long
                2 -> name = (v as Reader).string()
                4 -> code = (v as Reader).string()
                9 -> fault = v as Long
            }
        }
        return buildString {
            append(name.ifEmpty { number.toString() })
            if (code.isNotEmpty()) append(" (").append(code).append(')')
            fault?.let { append(" em 0x").append(java.lang.Long.toHexString(it)) }
        }
    }

    /** Uma entrada do mapa threads: chave = tid, valor = Thread{nome, pilha}. */
    private fun thread(entry: Reader): Pair<Long, Pair<String, List<Frame>>>? {
        var key = 0L
        var name = ""
        val frames = ArrayList<Frame>()
        while (true) {
            val (f, _, v) = entry.next() ?: break
            when (f) {
                1 -> key = v as Long
                2 -> {
                    val t = v as Reader
                    while (true) {
                        val (tf, _, tv) = t.next() ?: break
                        when (tf) {
                            2 -> name = (tv as Reader).string()
                            4 -> frames += frame(tv as Reader)
                        }
                    }
                }
            }
        }
        return key to (name to frames)
    }

    private fun frame(r: Reader): Frame {
        val frame = Frame()
        while (true) {
            val (f, _, v) = r.next() ?: break
            when (f) {
                1 -> frame.pc = v as Long
                4 -> frame.function = (v as Reader).string()
                5 -> frame.offset = v as Long
                6 -> frame.file = (v as Reader).string()
            }
        }
        return frame
    }

    private fun logBuffer(r: Reader): List<String> {
        val lines = ArrayList<String>()
        while (true) {
            val (f, _, v) = r.next() ?: break
            if (f == 2) {
                val m = v as Reader
                var tag = ""
                var message = ""
                while (true) {
                    val (mf, _, mv) = m.next() ?: break
                    when (mf) {
                        5 -> tag = (mv as Reader).string()
                        6 -> message = (mv as Reader).string()
                    }
                }
                lines += "$tag: ${message.trim()}"
            }
        }
        return lines
    }
}
