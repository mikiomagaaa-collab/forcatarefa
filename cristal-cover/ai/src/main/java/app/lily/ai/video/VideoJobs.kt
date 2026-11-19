package app.lily.ai.video

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import androidx.compose.runtime.Immutable
import app.lily.ai.remote.GatewayClient
import app.lily.ai.remote.GatewayException
import app.lily.ai.remote.VideoModel
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Como o vídeo nasce. */
enum class VideoMode(val label: String) {
    TEXT("A partir de uma descrição"),
    IMAGE("Animar uma imagem"),
    CONTINUE("Continuar um vídeo"),
    TRANSFORM("Transformar uma cena"),
}

/** Quanto movimento pedir (vira instruções na descrição; os modelos não têm um controle único). */
enum class Motion(val label: String, val hint: String) {
    GENTLE("Suave", "slow, gentle camera movement, subtle motion"),
    NATURAL("Natural", ""),
    DYNAMIC("Dinâmico", "dynamic camera movement, energetic motion"),
}

/** Um pedido de vídeo como a tela monta. */
data class VideoRequest(
    val model: VideoModel,
    val prompt: String,
    val mode: VideoMode,
    val duration: Int,
    val resolution: String,
    val aspect: String,
    val audio: Boolean,
    val motion: Motion,
    /** Imagem de referência (ou o quadro do vídeo anterior). */
    val reference: Bitmap? = null,
    /** O vídeo de onde veio a referência (continuar/transformar). */
    val parent: String? = null,
)

/** Um vídeo no histórico (gerando, pronto ou com falha). */
@Immutable
@Serializable
data class VideoJob(
    val id: String,
    val jobId: String,
    val model: String,
    val modelName: String,
    val mode: String,
    val prompt: String,
    val duration: Int,
    val resolution: String,
    val aspect: String,
    val audio: Boolean = false,
    val estimatedUsd: Double = 0.0,
    /** queued, running, downloading, done, failed, cancelled. */
    val status: String = "queued",
    val position: Int? = null,
    val startedAt: Long,
    val typicalSeconds: Int = 90,
    val elapsed: Int = 0,
    val remoteUrl: String? = null,
    val file: String? = null,
    val galleryUri: String? = null,
    val reference: String? = null,
    val parent: String? = null,
    val error: String? = null,
    val finishedAt: Long? = null,
    val note: String? = null,
) {
    val active: Boolean get() = status == "queued" || status == "running" || status == "downloading"

    /** Progresso ESTIMADO (o serviço não informa porcentagem): tempo passado sobre o tempo típico. */
    val progress: Float
        get() = when (status) {
            "queued" -> 0.04f
            "running" -> (0.08f + 0.85f * elapsed / max(10, typicalSeconds).toFloat()).coerceAtMost(0.93f)
            "downloading" -> 0.96f
            "done" -> 1f
            else -> 0f
        }
}

/**
 * Os vídeos da L.I.L.Y: manda o pedido ao gateway (o modelo roda lá fora, nunca no celular), guarda o
 * histórico, acompanha a fila pelo [VideoService], baixa o resultado e salva na galeria
 * (Filmes/L.I.L.Y, que a L.I.L.Y Gallery mostra).
 */
class VideoJobs(private val context: Context, private val gateway: () -> GatewayClient) {
    private val dir = File(context.filesDir, "video").apply { mkdirs() }
    private val historyFile = File(dir, "historico.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _jobs = MutableStateFlow(load())
    val jobs: StateFlow<List<VideoJob>> = _jobs.asStateFlow()

    private fun load(): List<VideoJob> = runCatching {
        json.decodeFromString(ListSerializer(VideoJob.serializer()), historyFile.readText())
    }.getOrDefault(emptyList())

    @Synchronized
    private fun save(list: List<VideoJob>) {
        _jobs.value = list
        runCatching { historyFile.writeText(json.encodeToString(ListSerializer(VideoJob.serializer()), list)) }
    }

    @Synchronized
    internal fun update(id: String, change: (VideoJob) -> VideoJob) {
        save(_jobs.value.map { if (it.id == id) change(it) else it })
    }

    fun job(id: String): VideoJob? = _jobs.value.firstOrNull { it.id == id }

    /** A descrição final: o texto da pessoa mais o movimento escolhido (e, ao continuar, a continuidade). */
    fun promptFor(r: VideoRequest): String = buildString {
        append(r.prompt.trim())
        if (r.mode == VideoMode.CONTINUE) append(". Continue seamlessly from this exact frame, same characters, lighting and style")
        if (r.mode == VideoMode.TRANSFORM) append(". Keep the composition of this frame")
        if (r.motion.hint.isNotEmpty()) append(". ").append(r.motion.hint)
    }

    fun requestJson(r: VideoRequest, imageDataUrl: String?): JsonObject = buildJsonObject {
        put("model", r.model.id)
        put("prompt", promptFor(r))
        put("duration", r.duration)
        put("resolution", r.resolution)
        put("aspect", r.aspect)
        put("audio", r.audio)
        if (imageDataUrl != null) put("image", imageDataUrl)
    }

    suspend fun quote(r: VideoRequest): Double = gateway().videoQuote(requestJson(r, if (r.reference != null) "data:image/jpeg;base64,AAAA" else null)).estimatedUsd

    /** Envia o pedido; o serviço acompanha a partir daqui. */
    suspend fun submit(r: VideoRequest): VideoJob {
        val id = UUID.randomUUID().toString()
        val refFile = r.reference?.let { bmp -> withContext(Dispatchers.IO) { saveReference(id, bmp) } }
        val dataUrl = r.reference?.let { bmp -> withContext(Dispatchers.Default) { dataUrl(bmp) } }
        val sub = gateway().videoSubmit(requestJson(r, dataUrl))
        val job = VideoJob(
            id = id,
            jobId = sub.jobId,
            model = sub.model,
            modelName = r.model.name,
            mode = r.mode.name,
            prompt = r.prompt.trim(),
            duration = sub.duration.takeIf { it > 0 } ?: r.duration,
            resolution = sub.resolution.ifBlank { r.resolution },
            aspect = sub.aspect.ifBlank { r.aspect },
            audio = r.audio,
            estimatedUsd = sub.estimatedUsd,
            startedAt = System.currentTimeMillis(),
            typicalSeconds = sub.typicalSeconds,
            reference = refFile?.path,
            parent = r.parent,
            note = if (sub.simplified) "O modelo recusou alguns ajustes; foi gerado só com a descrição e a imagem." else null,
        )
        save(listOf(job) + _jobs.value)
        VideoService.start(context)
        return job
    }

    /** Uma rodada de acompanhamento (o serviço chama a cada poucos segundos). Devolve se ainda há trabalho. */
    suspend fun pollOnce(autoSave: Boolean): Boolean {
        for (job in _jobs.value.filter { it.active }) {
            try {
                if (job.status == "downloading") {
                    finish(job, autoSave)
                    continue
                }
                val s = gateway().videoStatus(job.jobId)
                when (s.status) {
                    "queued" -> update(job.id) { it.copy(status = "queued", position = s.position, elapsed = s.elapsed) }
                    "running" -> update(job.id) { it.copy(status = "running", position = null, elapsed = s.elapsed) }
                    "done" -> {
                        update(job.id) { it.copy(status = "downloading", remoteUrl = s.video?.url, elapsed = s.elapsed) }
                        job(job.id)?.let { finish(it, autoSave) }
                    }
                    "failed" -> update(job.id) { it.copy(status = "failed", error = s.error ?: "O serviço não gerou o vídeo.", finishedAt = System.currentTimeMillis()) }
                }
            } catch (e: GatewayException) {
                // Sem internet ou gateway fora: tenta de novo na próxima rodada (até 30 min).
                if (System.currentTimeMillis() - job.startedAt > 30 * 60_000L) {
                    update(job.id) { it.copy(status = "failed", error = e.message, finishedAt = System.currentTimeMillis()) }
                }
            }
        }
        return _jobs.value.any { it.active }
    }

    private suspend fun finish(job: VideoJob, autoSave: Boolean) {
        val url = job.remoteUrl ?: return update(job.id) { it.copy(status = "failed", error = "Sem endereço do vídeo.") }
        val target = File(dir, "${job.id}.mp4")
        try {
            withContext(Dispatchers.IO) { download(url, target) }
            update(job.id) { it.copy(status = "done", file = target.path, finishedAt = System.currentTimeMillis()) }
            if (autoSave) saveToGallery(job.id)
        } catch (e: Exception) {
            if (System.currentTimeMillis() - job.startedAt > 45 * 60_000L) {
                update(job.id) { it.copy(status = "failed", error = "Não consegui baixar o vídeo: ${e.message}") }
            }
        }
    }

    private fun download(url: String, target: File) {
        // Só https (http apenas para o próprio aparelho, usado nos testes).
        require(url.startsWith("https://") || url.startsWith("http://127.0.0.1:")) { "endereço inseguro" }
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 60_000
        try {
            if (conn.responseCode !in 200..299) throw java.io.IOException("servidor respondeu ${conn.responseCode}")
            val tmp = File(target.path + ".parte")
            conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (tmp.length() < 1024) throw java.io.IOException("arquivo vazio")
            tmp.renameTo(target)
        } finally {
            conn.disconnect()
        }
    }

    /** Salva na galeria do aparelho (Filmes/L.I.L.Y): a L.I.L.Y Gallery mostra. */
    suspend fun saveToGallery(id: String): Uri? = withContext(Dispatchers.IO) {
        val job = job(id) ?: return@withContext null
        job.galleryUri?.let { return@withContext Uri.parse(it) }
        val file = job.file?.let(::File)?.takeIf { it.isFile } ?: return@withContext null
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "lily-video-${job.startedAt}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, FOLDER)
            put(MediaStore.Video.Media.DESCRIPTION, job.prompt.take(250))
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: return@withContext null
        resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
        update(id) { it.copy(galleryUri = uri.toString()) }
        uri
    }

    suspend fun cancel(id: String) {
        val job = job(id) ?: return
        runCatching { gateway().videoCancel(job.jobId) }
        update(id) { it.copy(status = "cancelled", finishedAt = System.currentTimeMillis()) }
    }

    fun delete(id: String) {
        job(id)?.let { j ->
            j.file?.let { File(it).delete() }
            j.reference?.let { File(it).delete() }
        }
        save(_jobs.value.filterNot { it.id == id })
    }

    /** O último quadro (para continuar) ou um quadro no meio (para transformar). */
    suspend fun frame(id: String, last: Boolean): Bitmap? = withContext(Dispatchers.IO) {
        val path = job(id)?.file ?: return@withContext null
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(path)
            val durationUs = (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000L
            val at = if (last) (durationUs - 50_000L).coerceAtLeast(0L) else durationUs / 2
            r.getFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }

    fun shareIntent(id: String): Intent? {
        val job = job(id) ?: return null
        val uri = job.galleryUri?.let(Uri::parse) ?: return null
        return Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun saveReference(id: String, bmp: Bitmap): File {
        val f = File(dir, "$id-ref.jpg")
        f.outputStream().use { scaled(bmp, 640).compress(Bitmap.CompressFormat.JPEG, 85, it) }
        return f
    }

    companion object {
        const val FOLDER = "Movies/L.I.L.Y"
        const val PRESENCE = "video"

        private fun scaled(bmp: Bitmap, maxSide: Int): Bitmap {
            val side = max(bmp.width, bmp.height)
            if (side <= maxSide) return bmp
            val s = maxSide.toFloat() / side
            return Bitmap.createScaledBitmap(bmp, (bmp.width * s).roundToInt(), (bmp.height * s).roundToInt(), true)
        }

        /** A referência vai como JPEG em data URL (até 1280 px: basta para os modelos e fica leve). */
        fun dataUrl(bmp: Bitmap): String {
            val out = ByteArrayOutputStream()
            scaled(bmp, 1280).compress(Bitmap.CompressFormat.JPEG, 88, out)
            return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }
    }
}
