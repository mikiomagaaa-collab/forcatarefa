package app.lily.ai.imagegen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Immutable
import app.lily.ai.LilyAiApp
import app.lily.ai.R
import app.lily.ai.data.MediaSaver
import app.lily.ai.imagegen.iris.HostOps
import app.lily.ai.imagegen.iris.IrisPipeline
import app.lily.ai.imagegen.iris.LiteRtExec
import app.lily.ai.imagegen.iris.PromptTokens
import app.lily.ai.imagegen.iris.RunFiles
import app.lily.ai.imagegen.iris.StageLog
import app.lily.ai.imagegen.iris.StopRequested
import app.lily.ai.models.Catalog
import app.lily.ai.ui.MainActivity
import com.google.ai.edge.litert.Environment
import java.io.File
import java.util.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Estilos: só acrescentam palavras à descrição (o modelo é o mesmo). */
enum class ImageStyle(val label: String, val suffix: String) {
    NONE("Livre", ""),
    PHOTO("Fotográfico", ", photorealistic photo, natural light, sharp focus, 50mm"),
    CINEMA("Cinematográfico", ", cinematic still, dramatic lighting, film grain, wide shot"),
    ILLUSTRATION("Ilustração", ", digital illustration, clean lines, vibrant colors"),
    ANIME("Anime", ", anime style, cel shading, detailed background"),
    WATERCOLOR("Aquarela", ", watercolor painting, soft edges, paper texture"),
    RENDER_3D("3D", ", 3d render, soft studio lighting, octane render"),
    GLASS("Liquid Glass", ", translucent liquid glass, iridescent reflections, soft gradients, minimal"),
}

@Immutable
sealed interface GenState {
    data object Idle : GenState
    data class Running(val prompt: String, val stage: String, val fraction: Float, val startedAt: Long) : GenState
    data class Done(val prompt: String, val uri: Uri, val file: File, val seconds: Long, val seed: Long) : GenState
    data class Failed(val message: String) : GenState
    data object Stopped : GenState
}

/** A geração de imagens: uma por vez, num serviço em primeiro plano (leva minutos). */
class ImageGenJobs(private val context: Context) {
    private val _state = MutableStateFlow<GenState>(GenState.Idle)
    val state: StateFlow<GenState> = _state.asStateFlow()

    @Volatile internal var stopRequested = false

    fun start(prompt: String, style: ImageStyle, seed: Long) {
        if (_state.value is GenState.Running) return
        stopRequested = false
        _state.value = GenState.Running(prompt, "Preparando", 0f, SystemClock.elapsedRealtime())
        context.startForegroundService(
            Intent(context, ImageGenService::class.java)
                .putExtra(ImageGenService.EXTRA_PROMPT, prompt + style.suffix)
                .putExtra(ImageGenService.EXTRA_SHOWN, prompt)
                .putExtra(ImageGenService.EXTRA_SEED, seed),
        )
    }

    fun stop() {
        stopRequested = true
    }

    fun clear() {
        if (_state.value !is GenState.Running) _state.value = GenState.Idle
    }

    internal fun set(state: GenState) {
        _state.value = state
    }
}

/**
 * Roda o iris-3b Turbo: libera a memória dos outros modelos, carrega um grafo por vez na GPU, para
 * se pedirem ou se a memória livre cair abaixo de ~200 MB, e salva a imagem na galeria (Pictures/L.I.L.Y).
 * A notificação é um Live Update: a Liquid Island mostra a etapa e o progresso.
 */
class ImageGenService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastNotify = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val graph = (application as LilyAiApp).graphOrNull ?: run {
            // O núcleo não iniciou: cumpre o pedido do Android (primeiro plano) e encerra.
            ensureChannel()
            startForeground(NOTIFICATION_ID, notification("Geração indisponível", "", 0f), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_STOP) {
            graph.imageGen.stop()
            return START_NOT_STICKY
        }
        val prompt = intent?.getStringExtra(EXTRA_PROMPT) ?: return START_NOT_STICKY.also { stopSelf() }
        val shown = intent.getStringExtra(EXTRA_SHOWN) ?: prompt
        val seed = intent.getLongExtra(EXTRA_SEED, System.currentTimeMillis())
        ensureChannel()
        startForeground(NOTIFICATION_ID, notification("Gerando imagem", "Preparando", 0f), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
        scope.launch { run(prompt, shown, seed) }
        return START_NOT_STICKY
    }

    private suspend fun run(prompt: String, shown: String, seed: Long) {
        val graph = (application as LilyAiApp).graph
        val jobs = graph.imageGen
        val spec = Catalog.iris
        val started = SystemClock.elapsedRealtime()
        var env: Environment? = null
        var tokens: PromptTokens? = null
        try {
            if (!graph.store.isInstalled(spec)) throw IllegalStateException("O ${spec.name} não está instalado.")
            // A geração precisa de ~3,2 GB: tira o modelo de conversa da memória antes.
            graph.engines.unloadNow()
            val dir = File(graph.store.dir(spec), "model")
            File(dir, ROWS).writeText("""{"kind":"generic","config":"$CONFIG","inputs":[],"rows":[]}""")
            val files = RunFiles(dir, ROWS)
            files.materializeTokenizer()
            tokens = PromptTokens(File(dir, "tok"))
            env = Environment.create(this)
            val config = files.engineConfig()
            val steps = files.host["sigmas"].size
            val totalRuns = config.textEncoder.size + 1 + steps * (config.dual.size + config.single.size + 1)
            var runs = 0
            val log = StageLog(clock = System::nanoTime, memory = { emptyMap() }) { event ->
                if (event.stage == "run") {
                    runs++
                    val fraction = runs.toFloat() / totalRuns
                    val stage = when {
                        event.step < 0 && runs <= config.textEncoder.size -> "Entendendo a descrição"
                        event.step < 0 -> "Preparando a cena"
                        else -> "Desenhando · etapa ${event.step + 1} de $steps"
                    }
                    jobs.set(GenState.Running(shown, stage, fraction, started))
                    notifyProgress(stage, fraction)
                }
            }
            val pipeline = IrisPipeline(
                LiteRtExec(env, dir),
                config,
                files.host,
                tokens,
                availableBytes = ::memAvailableBytes,
                stop = { jobs.stopRequested || memAvailableBytes() < MIN_FREE_BYTES },
            )
            val random = Random(seed)
            val noise = FloatArray(HostOps.IMAGE_FLOATS) { random.nextGaussian().toFloat() }
            val result = pipeline.generate(prompt, noise, log)
            val bitmap = toBitmap(result.image)
            val saved = MediaSaver.savePng(this, bitmap, "lily-${System.currentTimeMillis()}", "L.I.L.Y AI · ${spec.name} · $shown")
            jobs.set(GenState.Done(shown, saved.uri, saved.file, (SystemClock.elapsedRealtime() - started) / 1000, seed))
            notifyDone(saved.uri)
        } catch (stop: StopRequested) {
            jobs.set(
                if (jobs.stopRequested) GenState.Stopped
                else GenState.Failed("A memória livre acabou durante a geração. Feche outros apps e tente de novo."),
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Geração falhou", t)
            jobs.set(GenState.Failed(t.message ?: t.javaClass.simpleName))
        } finally {
            runCatching { tokens?.close() }
            runCatching { env?.close() }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun toBitmap(image: FloatArray): Bitmap {
        val size = HostOps.IMAGE_SIZE
        val plane = size * size
        fun level(v: Float) = ((v + 1f) * 127.5f + 0.5f).toInt().coerceIn(0, 255)
        val pixels = IntArray(plane) { i ->
            (0xFF shl 24) or (level(image[i]) shl 16) or (level(image[plane + i]) shl 8) or level(image[2 * plane + i])
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    /** MemAvailable de /proc/meminfo (o mesmo que o app de exemplo do iris usa como guarda). */
    private fun memAvailableBytes(): Long = runCatching {
        File("/proc/meminfo").useLines { lines ->
            lines.first { it.startsWith("MemAvailable:") }.filter(Char::isDigit).toLong() * 1024
        }
    }.getOrDefault(Long.MAX_VALUE)

    private fun notifyProgress(stage: String, fraction: Float) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastNotify < 1_000) return
        lastNotify = now
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification("Gerando imagem", stage, fraction))
    }

    private fun notifyDone(uri: Uri) {
        val view = PendingIntent.getActivity(this, 3, Intent(Intent.ACTION_VIEW, uri).setType("image/png").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), PendingIntent.FLAG_IMMUTABLE)
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID + 1,
            Notification.Builder(this, CHANNEL_DONE)
                .setSmallIcon(R.drawable.ic_stat_lily)
                .setContentTitle("Imagem pronta")
                .setContentText("Salva na galeria, em Pictures/L.I.L.Y.")
                .setContentIntent(view)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun notification(title: String, text: String, fraction: Float): Notification {
        val percent = (fraction * 100).toInt()
        val b = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_lily)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setContentIntent(PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "imagegen"), PendingIntent.FLAG_IMMUTABLE))
            .addAction(Notification.Action.Builder(null, "Parar", PendingIntent.getService(this, 4, Intent(this, ImageGenService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)).build())
        if (Build.VERSION.SDK_INT >= 36) {
            b.setStyle(Notification.ProgressStyle().setProgress(percent))
            b.setShortCriticalText("$percent%")
            if (Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) b.setRequestPromotedOngoing(true)
        } else {
            b.setProgress(100, percent, false)
        }
        return b.build()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Geração de imagens", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_DONE, "Imagens prontas", NotificationManager.IMPORTANCE_DEFAULT))
    }

    /** Android 15+: o tempo do serviço de processamento acabou; para a geração (como o sistema pede). */
    override fun onTimeout(startId: Int, fgsType: Int) {
        (application as LilyAiApp).graph.imageGen.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PROMPT = "prompt"
        const val EXTRA_SHOWN = "shown"
        const val EXTRA_SEED = "seed"
        const val ACTION_STOP = "app.lily.ai.imagegen.STOP"
        private const val ROWS = "lily_rows.json"
        private const val CONFIG = "iris3b_config_dit_int8_fp16.json"
        private const val CHANNEL = "geracao"
        private const val CHANNEL_DONE = "geracao_pronta"
        private const val NOTIFICATION_ID = 7101
        private const val MIN_FREE_BYTES = 200_000L * 1024
        private const val TAG = "LilyImageGen"
    }
}
