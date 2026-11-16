package app.lily.ai.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Immutable
import app.lily.ai.AiGraph
import app.lily.ai.BuildConfig
import app.lily.ai.core.Accel
import app.lily.ai.core.DeviceSnapshot
import app.lily.ai.core.EngineChoice
import app.lily.ai.engine.EngineStatus
import app.lily.ai.engine.SpeechInput
import app.lily.ai.engine.SuperRes
import app.lily.ai.engine.Vision
import app.lily.ai.models.Catalog
import app.lily.ai.models.ModelSpec
import app.lily.ai.models.Runtime
import app.lily.ai.models.formatBytes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Uma rodada de um modelo de linguagem, medida no aparelho. */
@Immutable
data class LlmRun(
    val model: String,
    val requested: Accel,
    val used: Accel?,
    val loadMillis: Long?,
    val firstPieceMillis: Long?,
    val totalMillis: Long,
    val answer: String,
    val error: String?,
    val availBefore: Long,
    val availAfter: Long,
    val thermalBefore: Int,
    val thermalAfter: Int,
) {
    /** Velocidade da geração (do primeiro ao último pedaço), em caracteres por segundo. */
    val charsPerSecond: Double?
        get() {
            val first = firstPieceMillis ?: return null
            val span = totalMillis - first
            return if (answer.length >= 10 && span > 200) answer.length * 1000.0 / span else null
        }
}

/** Uma verificação simples (OCR, ampliação, voz…). */
@Immutable
data class Check(val name: String, val ok: Boolean?, val detail: String, val millis: Long? = null)

@Immutable
data class Report(val header: List<String>, val runs: List<LlmRun>, val checks: List<Check>, val millis: Long) {
    fun text(): String = buildString {
        header.forEach { appendLine(it) }
        appendLine("Duração do teste: ${secs(millis)}")
        appendLine()
        appendLine("Modelos de linguagem (pergunta curta, temperatura 0,2):")
        if (runs.isEmpty()) appendLine("• Nenhum modelo de conversa instalado.")
        runs.forEach { r ->
            append("• ").append(r.model).append(" · ").append(r.requested.label).append(" pedida")
            r.used?.let { append(", ").append(it.label).append(" usada") }
            if (r.error != null) {
                appendLine(" · FALHOU: ${r.error}")
            } else {
                r.loadMillis?.let { append(" · carregou em ${secs(it)}") }
                r.firstPieceMillis?.let { append(" · 1º pedaço em ${secs(it)}") }
                append(" · ${r.answer.length} caracteres em ${secs(r.totalMillis)}")
                r.charsPerSecond?.let { append(String.format(PT, " (%.0f car/s)", it)) }
                appendLine()
            }
            appendLine("  Memória livre ${gb(r.availBefore)} → ${gb(r.availAfter)} · temperatura ${thermal(r.thermalBefore)} → ${thermal(r.thermalAfter)}")
            if (r.answer.isNotBlank()) appendLine("  Resposta: “${r.answer.replace('\n', ' ').take(160)}”")
        }
        appendLine()
        appendLine("Outros recursos:")
        checks.forEach { c ->
            val mark = when (c.ok) {
                true -> "ok"
                false -> "FALHOU"
                null -> "—"
            }
            append("• ").append(c.name).append(": ").append(mark)
            c.millis?.let { append(" em ${secs(it)}") }
            appendLine(" · ${c.detail}")
        }
    }.trimEnd()

    companion object {
        private val PT = Locale.forLanguageTag("pt-BR")
        fun secs(ms: Long): String = String.format(PT, "%.1f s", ms / 1000.0)
        fun gb(bytes: Long): String = String.format(PT, "%.1f GB", bytes / DeviceSnapshot.GIB)
        fun thermal(status: Int): String = when (status) {
            0 -> "normal"
            1 -> "morno"
            2 -> "quente"
            3 -> "muito quente"
            else -> "crítico"
        }
    }
}

@Immutable
sealed interface DiagState {
    data object Idle : DiagState
    data class Running(val step: String, val fraction: Float) : DiagState
    data class Done(val report: Report) : DiagState
}

/**
 * O teste do aparelho: roda de verdade cada modelo de conversa instalado (na GPU e na CPU), o OCR,
 * a ampliação e a voz, e mede tempo, velocidade, memória e temperatura. Nada é estimado; o que
 * falhar aparece como falha. O relatório pode ser copiado e enviado.
 */
class Diagnostics(private val graph: AiGraph, private val context: Context) {
    private val _state = MutableStateFlow<DiagState>(DiagState.Idle)
    val state: StateFlow<DiagState> = _state.asStateFlow()

    private fun avail(): Long {
        val am = context.getSystemService(ActivityManager::class.java)
        return ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }.availMem
    }

    suspend fun run(includeGpu: Boolean = true): Report {
        val started = SystemClock.elapsedRealtime()
        val device = graph.device.snapshot()
        val installed = graph.store.installedIds()
        val models = Catalog.all.filter { it.runtime == Runtime.LITERT_LM && it.id in installed }.sortedBy { it.totalBytes }
        val accels = buildList {
            if (includeGpu && device.gpuCompute) add(Accel.GPU)
            add(Accel.CPU)
        }
        val totalSteps = models.size * accels.size + 4
        var step = 0
        fun progress(label: String) {
            _state.value = DiagState.Running(label, step.toFloat() / totalSteps)
            step++
        }

        val runs = ArrayList<LlmRun>()
        for (spec in models) {
            for (accel in accels) {
                progress("${spec.name} na ${accel.label}")
                runs += runModel(spec, accel, device)
            }
        }
        graph.engines.unloadNow()

        val checks = ArrayList<Check>()
        progress("Reconhecimento de texto (ML Kit)")
        checks += ocrCheck()
        progress("Ampliação (Real-ESRGAN)")
        checks += superResCheck()
        progress("Voz")
        checks += Check("Reconhecimento de fala", SpeechInput.onDevice(context), if (SpeechInput.onDevice(context)) "no aparelho" else "sem o pacote no aparelho (o Android pode usar a rede)")
        checks += ttsCheck()
        progress("Armazenamento")
        val after = graph.device.snapshot()
        checks += Check("Armazenamento", null, "${formatBytes(after.freeStorageBytes)} livres; modelos instalados: ${formatBytes(Catalog.all.filter { it.id in installed && !it.builtIn }.sumOf { it.totalBytes })}")

        val time = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR")).format(Instant.now().atZone(ZoneId.systemDefault()))
        val header = listOf(
            "Teste do aparelho · L.I.L.Y AI ${BuildConfig.VERSION_NAME} · $time",
            "Aparelho: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${device.soc}",
            String.format(Locale.forLanguageTag("pt-BR"), "RAM: %.1f GB · GPU (OpenCL): %s · núcleos: %d", device.totalRamGb, if (device.gpuCompute) "sim" else "não", device.cpuCores),
            "Bateria ${battery(device.batteryPercent)} → ${battery(after.batteryPercent)} · temperatura ${Report.thermal(device.thermalStatus)} → ${Report.thermal(after.thermalStatus)}",
        )
        val report = Report(header, runs, checks, SystemClock.elapsedRealtime() - started)
        _state.value = DiagState.Done(report)
        return report
    }

    private suspend fun runModel(spec: ModelSpec, accel: Accel, device: DeviceSnapshot): LlmRun {
        // Descarrega antes para medir o carregamento de verdade.
        graph.engines.unloadNow()
        val availBefore = avail()
        val thermalBefore = graph.device.snapshot().thermalStatus
        val started = SystemClock.elapsedRealtime()
        var first: Long? = null
        var text = ""
        var error: String? = null
        try {
            val finished = withTimeoutOrNull(TIMEOUT_MS) {
                graph.engines.generate(
                    EngineChoice.Llm(spec, accel, (device.cpuCores / 2).coerceIn(2, 4)),
                    "Você é um assistente. Responda em português do Brasil, em uma ou duas frases.",
                    emptyList(),
                    PROMPT,
                    temperature = 0.2,
                ).collect { piece ->
                    if (first == null && piece.isNotEmpty()) first = SystemClock.elapsedRealtime() - started
                    text = piece
                }
                true
            }
            if (finished == null) error = "passou de ${TIMEOUT_MS / 1000} s"
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            error = t.message ?: t.javaClass.simpleName
        }
        val total = SystemClock.elapsedRealtime() - started
        val status = graph.engines.status.value
        val (used, load) = when (status) {
            is EngineStatus.Ready -> (if (status.model.id == spec.id) status.accel else null) to (if (status.model.id == spec.id) status.loadMillis else null)
            is EngineStatus.Busy -> (if (status.model.id == spec.id) status.accel else null) to null
            else -> null to null
        }
        val run = LlmRun(spec.name, accel, used, load, first, total, text.trim(), error, availBefore, avail(), thermalBefore, graph.device.snapshot().thermalStatus)
        run.charsPerSecond?.let { first?.let { f -> graph.perf.recordAnswer(spec.id, run.answer.length, total - f) } }
        return run
    }

    private suspend fun ocrCheck(): Check {
        val bitmap = Bitmap.createBitmap(900, 260, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.drawText("LILY 2026 RAZR", 40f, 170f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 110f })
        val started = SystemClock.elapsedRealtime()
        return try {
            val text = withTimeoutOrNull(20_000) { Vision.ocr(bitmap) } ?: return Check("Reconhecimento de texto (ML Kit)", false, "não respondeu em 20 s")
            val ok = text.uppercase().replace(" ", "").contains("2026")
            Check("Reconhecimento de texto (ML Kit)", ok, "leu “${text.replace('\n', ' ').take(40)}”", SystemClock.elapsedRealtime() - started)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Check("Reconhecimento de texto (ML Kit)", false, t.message ?: t.javaClass.simpleName)
        }
    }

    private suspend fun superResCheck(): Check {
        val spec = Catalog.esrgan
        if (!graph.store.isInstalled(spec)) return Check("Ampliação (Real-ESRGAN)", null, "não instalado (usa a ampliação clássica)")
        val src = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).also { b ->
            for (y in 0 until 128) for (x in 0 until 128) b.setPixel(x, y, Color.rgb(x * 2, y * 2, (x + y) % 256))
        }
        val started = SystemClock.elapsedRealtime()
        return try {
            val out = withContext(Dispatchers.Default) { SuperRes(graph.store.file(spec, spec.files.first())).upscale(src, {}, { false }) }
            Check("Ampliação (Real-ESRGAN)", out.width == 512 && out.height == 512, "128×128 → ${out.width}×${out.height}", SystemClock.elapsedRealtime() - started)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Check("Ampliação (Real-ESRGAN)", false, t.message ?: t.javaClass.simpleName)
        }
    }

    private suspend fun ttsCheck(): Check {
        val started = SystemClock.elapsedRealtime()
        var tts: TextToSpeech? = null
        return try {
            val ok = withTimeoutOrNull(8_000) {
                suspendCancellableCoroutine { cont ->
                    tts = TextToSpeech(context.applicationContext) { status -> if (cont.isActive) cont.resume(status == TextToSpeech.SUCCESS) }
                }
            } ?: false
            val pt = tts?.voices?.count { it.locale.language == "pt" } ?: 0
            Check("Leitura em voz alta", ok, if (ok) "motor ${tts?.defaultEngine}; $pt vozes em português" else "o motor de voz não respondeu", SystemClock.elapsedRealtime() - started)
        } catch (t: Throwable) {
            Check("Leitura em voz alta", false, t.message ?: t.javaClass.simpleName)
        } finally {
            runCatching { tts?.shutdown() }
        }
    }

    fun reset() {
        _state.value = DiagState.Idle
    }

    private fun battery(percent: Int) = if (percent >= 0) "$percent%" else "—"

    companion object {
        const val PROMPT = "Qual é a capital do Brasil e em que ano ela foi inaugurada?"
        const val TIMEOUT_MS = 120_000L
    }
}
