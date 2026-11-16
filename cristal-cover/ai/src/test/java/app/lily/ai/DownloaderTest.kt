package app.lily.ai

import app.lily.ai.models.Category
import app.lily.ai.models.DownloadPaused
import app.lily.ai.models.Downloader
import app.lily.ai.models.HttpOpener
import app.lily.ai.models.HttpResponse
import app.lily.ai.models.IntegrityError
import app.lily.ai.models.Modality
import app.lily.ai.models.ModelFile
import app.lily.ai.models.ModelSpec
import app.lily.ai.models.ModelStore
import app.lily.ai.models.Runtime
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Downloads de verdade contra um servidor falso em memória: retomada por Range, integridade e pausa. */
class DownloaderTest {
    private val a = Random(1).nextBytes(3 * 1024 * 1024 + 123)
    private val b = Random(2).nextBytes(1024 * 1024 + 7)

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private val spec = ModelSpec(
        id = "teste", name = "Teste", developer = "x", architecture = "x", parameters = "x", quantization = "x",
        categories = setOf(Category.CHAT), modalities = setOf(Modality.TEXT), runtime = Runtime.LITERT_LM,
        description = "", purposes = emptyList(), license = "x", licenseUrl = null, minRamGb = 1.0,
        repo = "org/teste", revision = "0".repeat(40),
        files = listOf(ModelFile("a.bin", a.size.toLong(), sha(a)), ModelFile("pasta/b.bin", b.size.toLong(), sha(b))),
    )

    /** Servidor que respeita (ou não) o Range e pode corromper um byte. */
    private class Server(val files: Map<String, ByteArray>, var honorRange: Boolean = true, var corrupt: Boolean = false) : HttpOpener {
        val requests = mutableListOf<Pair<String, Long>>()
        override fun open(url: String, fromByte: Long): HttpResponse {
            requests += url.substringAfterLast("/resolve/") to fromByte
            val name = url.substringAfter("0000000000000000000000000000000000000000/")
            val data = files.getValue(name).copyOf()
            if (corrupt) data[data.size / 2] = (data[data.size / 2] + 1).toByte()
            return if (fromByte > 0 && honorRange) {
                HttpResponse(206, data.size - fromByte, ByteArrayInputStream(data, fromByte.toInt(), data.size - fromByte.toInt())) {}
            } else {
                HttpResponse(200, data.size.toLong(), ByteArrayInputStream(data)) {}
            }
        }
    }

    private fun store(): Pair<ModelStore, File> {
        val dir = Files.createTempDirectory("dl").toFile()
        return ModelStore(dir) to dir
    }

    @Test
    fun `baixa todos os arquivos e confere o sha`() = runBlocking {
        val (store, _) = store()
        val server = Server(mapOf("a.bin" to a, "pasta/b.bin" to b))
        var last = 0L
        Downloader(store, server).download(spec, paused = { false }) { last = it.done }
        assertArrayEquals(a, store.file(spec, spec.files[0]).readBytes())
        assertArrayEquals(b, store.file(spec, spec.files[1]).readBytes())
        assertEquals(spec.totalBytes, last)
        assertFalse(File(store.file(spec, spec.files[0]).path + ".part").exists())
    }

    @Test
    fun `pausa e retoma de onde parou`() = runBlocking {
        val (store, _) = store()
        val server = Server(mapOf("a.bin" to a, "pasta/b.bin" to b))
        var reads = 0
        try {
            // O buffer é de 1 MB: pausa depois de 2 leituras.
            Downloader(store, server).download(spec, paused = { reads++ >= 2 }) {}
            error("deveria ter pausado")
        } catch (_: DownloadPaused) {
        }
        val partial = store.bytesOnDisk(spec)
        assertTrue("guardou a parte", partial in 1 until a.size)
        Downloader(store, server).download(spec, paused = { false }) {}
        assertEquals(partial, server.requests[1].second)
        assertArrayEquals(a, store.file(spec, spec.files[0]).readBytes())
    }

    @Test
    fun `servidor que ignora o range recomeca o arquivo sem corromper`() = runBlocking {
        val (store, _) = store()
        val server = Server(mapOf("a.bin" to a, "pasta/b.bin" to b))
        var reads = 0
        runCatching { Downloader(store, server).download(spec, paused = { reads++ >= 1 }) {} }
        server.honorRange = false
        Downloader(store, server).download(spec, paused = { false }) {}
        assertArrayEquals(a, store.file(spec, spec.files[0]).readBytes())
    }

    @Test
    fun `arquivo diferente do publicado e recusado`() = runBlocking {
        val (store, _) = store()
        val server = Server(mapOf("a.bin" to a, "pasta/b.bin" to b), corrupt = true)
        try {
            Downloader(store, server).download(spec, paused = { false }) {}
            error("deveria recusar")
        } catch (_: IntegrityError) {
        }
        assertFalse(store.file(spec, spec.files[0]).exists())
        assertFalse(File(store.file(spec, spec.files[0]).path + ".part").exists())
    }
}
