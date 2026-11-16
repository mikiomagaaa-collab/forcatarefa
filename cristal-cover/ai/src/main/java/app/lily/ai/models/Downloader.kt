package app.lily.ai.models

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Progresso de um modelo inteiro (todos os arquivos). */
data class DownloadProgress(val done: Long, val total: Long, val bytesPerSecond: Long, val file: String, val verifying: Boolean = false) {
    val fraction: Float get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
}

class DownloadPaused : IOException("Pausado")

class IntegrityError(file: String) : IOException("O arquivo $file chegou diferente do publicado (SHA-256 não confere)")

/** Abre uma conexão HTTP a partir de um byte (para retomar). Trocável nos testes. */
fun interface HttpOpener {
    /** Devolve o código HTTP, o tamanho do corpo (-1 se desconhecido) e o corpo. */
    fun open(url: String, fromByte: Long): HttpResponse
}

class HttpResponse(val code: Int, val length: Long, val body: InputStream, val close: () -> Unit)

/** A conexão real: segue redirecionamentos (o Hugging Face manda para o CDN) e pede só o que falta. */
val DefaultOpener = HttpOpener { url, from ->
    var target = URL(url)
    var redirects = 0
    while (true) {
        val c = target.openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        c.connectTimeout = 20_000
        c.readTimeout = 60_000
        c.setRequestProperty("User-Agent", "LILY-AI/1.0 (Android)")
        if (from > 0) c.setRequestProperty("Range", "bytes=$from-")
        val code = c.responseCode
        if (code in 300..399 && redirects < 8) {
            val location = c.getHeaderField("Location") ?: throw IOException("Redirecionamento sem destino")
            target = URL(target, location)
            c.disconnect()
            redirects++
            continue
        }
        val stream = if (code >= 400) c.errorStream ?: c.inputStream else c.inputStream
        return@HttpOpener HttpResponse(code, c.contentLengthLong, stream) { c.disconnect() }
    }
    @Suppress("UNREACHABLE_CODE")
    throw IOException("inalcançável")
}

/**
 * Baixa os arquivos de um modelo para a pasta dele. Cada arquivo vai para `<nome>.part` e continua
 * de onde parou (cabeçalho Range); ao terminar, o SHA-256 é conferido com o publicado antes de virar
 * o arquivo final. Pausar ([paused]) deixa a parte no disco.
 */
class Downloader(private val store: ModelStore, private val opener: HttpOpener = DefaultOpener) {

    suspend fun download(spec: ModelSpec, paused: () -> Boolean, onProgress: (DownloadProgress) -> Unit) = withContext(Dispatchers.IO) {
        require(spec.repo != null && spec.revision != null) { "${spec.name} não é baixado pelo Model Hub" }
        val total = spec.totalBytes
        var done = store.bytesOnDisk(spec)
        var windowStart = System.nanoTime()
        var windowBytes = 0L
        var speed = 0L
        for (f in spec.files) {
            val target = store.file(spec, f)
            if (target.length() == f.bytes) continue
            target.parentFile?.mkdirs()
            val part = File(target.path + ".part")
            if (part.length() > f.bytes) part.delete()
            var have = part.length()
            if (have < f.bytes) {
                val response = opener.open(spec.url(f), have)
                try {
                    when {
                        response.code == 206 -> Unit
                        response.code == 200 -> {
                            // O servidor ignorou o Range: recomeça este arquivo.
                            done -= have
                            have = 0
                            part.delete()
                        }
                        response.code == 416 && have == f.bytes -> Unit
                        else -> throw IOException("O servidor respondeu ${response.code} para ${f.name}")
                    }
                    FileOutputStream(part, have > 0).use { out ->
                        val buffer = ByteArray(BUFFER)
                        while (have < f.bytes) {
                            currentCoroutineContext().ensureActive()
                            if (paused()) throw DownloadPaused()
                            val n = response.body.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            have += n
                            done += n
                            windowBytes += n
                            val now = System.nanoTime()
                            if (now - windowStart > 500_000_000L) {
                                speed = windowBytes * 1_000_000_000L / (now - windowStart)
                                windowStart = now
                                windowBytes = 0
                                onProgress(DownloadProgress(done, total, speed, f.name))
                            }
                        }
                    }
                } finally {
                    runCatching { response.body.close() }
                    response.close()
                }
            }
            if (part.length() != f.bytes) throw IOException("${f.name} ficou incompleto (${part.length()} de ${f.bytes} bytes)")
            if (f.sha256 != null) {
                onProgress(DownloadProgress(done, total, 0, f.name, verifying = true))
                val actual = sha256(part)
                if (!actual.equals(f.sha256, ignoreCase = true)) {
                    part.delete()
                    throw IntegrityError(f.name)
                }
            }
            if (!part.renameTo(target)) {
                target.delete()
                if (!part.renameTo(target)) throw IOException("Não foi possível finalizar ${f.name}")
            }
        }
        onProgress(DownloadProgress(total, total, 0, ""))
    }

    companion object {
        private const val BUFFER = 1 shl 20

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(BUFFER)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
