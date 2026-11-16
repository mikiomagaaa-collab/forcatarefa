package app.lily.ai.tools

import android.graphics.Bitmap
import app.lily.ai.AiGraph
import app.lily.ai.core.EngineChoice
import app.lily.ai.core.RoutePlan
import app.lily.ai.core.Router
import app.lily.ai.core.TaskKind
import app.lily.ai.engine.Attachments
import app.lily.ai.engine.EngineRefused
import app.lily.ai.engine.Enhance
import app.lily.ai.engine.Label
import app.lily.ai.engine.Prompts
import app.lily.ai.engine.SuperRes
import app.lily.ai.engine.Tone
import app.lily.ai.engine.Translator
import app.lily.ai.engine.Turn
import app.lily.ai.engine.Vision
import app.lily.ai.engine.WritingStyle
import app.lily.ai.models.Catalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/** O que uma ferramenta de imagem devolveu. */
sealed interface ImageResult {
    data class Picture(val bitmap: Bitmap, val engine: String) : ImageResult
    data class Text(val text: String, val engine: String) : ImageResult
    data class Labels(val labels: List<Label>, val engine: String) : ImageResult
}

/**
 * Executa as tarefas pelo mecanismo que o roteador escolher. É o ponto único usado pelo chat,
 * pelas telas de ferramentas e pela ponte com o teclado e a Gallery.
 */
class AiTools(private val graph: AiGraph) {

    fun plan(task: TaskKind, manual: String? = null, needsImage: Boolean = false, needsAudio: Boolean = false): RoutePlan =
        Router.plan(task, graph.routerInput(manual), graph.device.snapshot(), needsImage, needsAudio)

    /** Conversa ou ferramenta de texto: a resposta chega em pedaços (texto acumulado). */
    fun text(
        plan: RoutePlan,
        prompt: String,
        history: List<Turn> = emptyList(),
        attachments: Attachments = Attachments(),
        targetLanguage: String? = null,
        temperature: Double = 0.7,
    ): Flow<String> = when (val e = plan.engine) {
        is EngineChoice.Llm -> graph.engines.generate(e, Prompts.system(graph.activeMemories()), history, prompt, attachments, temperature)
        is EngineChoice.Translator -> flow {
            emit(Translator.translate(prompt, targetLanguage ?: "en", graph.settings.value.value.wifiOnly))
        }
        is EngineChoice.Unavailable -> flow { throw EngineRefused(e.reason) }
        else -> flow { throw EngineRefused("Esta tarefa não é de texto.") }
    }

    /** Ferramenta de texto pronta: [task] com o texto, o estilo e o tom. */
    fun textTool(
        task: TaskKind,
        text: String,
        style: WritingStyle = WritingStyle.NATURAL,
        tone: Tone = Tone.NEUTRAL,
        language: String = "inglês",
        languageCode: String = "en",
        instruction: String? = null,
        manual: String? = null,
    ): Pair<RoutePlan, Flow<String>> {
        val plan = plan(task, manual)
        val temperature = if (task == TaskKind.CORRECT || task == TaskKind.TRANSLATE) 0.2 else 0.7
        val prompt = if (plan.engine is EngineChoice.Translator) text else Prompts.tool(task, text, style, tone, language, instruction)
        return plan to text(plan, prompt, targetLanguage = languageCode, temperature = temperature)
    }

    suspend fun image(task: TaskKind, bitmap: Bitmap, progress: (Float) -> Unit = {}, cancelled: () -> Boolean = { false }): ImageResult = withContext(Dispatchers.Default) {
        val plan = plan(task)
        when (val e = plan.engine) {
            is EngineChoice.Ocr -> ImageResult.Text(Vision.ocr(bitmap).ifBlank { "Não encontrei texto nesta imagem." }, "ML Kit")
            is EngineChoice.Labels -> ImageResult.Labels(Vision.labels(bitmap), "ML Kit")
            is EngineChoice.Subject -> {
                val cut = Vision.removeBackground(bitmap) ?: throw EngineRefused("Não encontrei um objeto principal para recortar.")
                ImageResult.Picture(cut, "ML Kit")
            }
            is EngineChoice.SuperResolution -> {
                val sr = SuperRes(graph.store.file(e.model, e.model.files.first()))
                val input = sr.prepare(bitmap)
                ImageResult.Picture(sr.upscale(input, progress, cancelled), e.model.name)
            }
            is EngineChoice.Classic -> {
                val src = Enhance.fit(bitmap)
                val out = when (task) {
                    TaskKind.DENOISE -> Enhance.denoise(src, 0.55f, progress)
                    TaskKind.UPSCALE -> Enhance.upscaleClassic(src).also { progress(1f) }
                    else -> Enhance.sharpen(Enhance.denoise(Enhance.autoTone(src), 0.35f, progress), 0.45f)
                }
                ImageResult.Picture(out, "Clássico (no aparelho)")
            }
            is EngineChoice.Unavailable -> throw EngineRefused(e.reason)
            else -> throw EngineRefused("Esta tarefa não é de imagem.")
        }
    }

    /** "Melhorar foto": tons e ruído clássicos e, com o Real-ESRGAN instalado, mais resolução. */
    suspend fun enhancePhoto(bitmap: Bitmap, progress: (Float) -> Unit, cancelled: () -> Boolean): ImageResult.Picture = withContext(Dispatchers.Default) {
        val base = Enhance.sharpen(Enhance.denoise(Enhance.autoTone(Enhance.fit(bitmap, 1024)), 0.35f) { progress(it * 0.3f) }, 0.35f)
        val sr = plan(TaskKind.UPSCALE).engine
        if (sr is EngineChoice.SuperResolution) {
            val engine = SuperRes(graph.store.file(sr.model, sr.model.files.first()))
            ImageResult.Picture(engine.upscale(engine.prepare(base), { progress(0.3f + 0.7f * it) }, cancelled), "Clássico + ${Catalog.esrgan.name}")
        } else {
            progress(1f)
            ImageResult.Picture(base, "Clássico (no aparelho)")
        }
    }
}
