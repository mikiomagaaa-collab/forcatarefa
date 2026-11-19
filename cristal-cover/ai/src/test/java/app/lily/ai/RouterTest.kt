package app.lily.ai

import app.lily.ai.core.Accel
import app.lily.ai.core.DeviceSnapshot
import app.lily.ai.core.EngineChoice
import app.lily.ai.core.Profile
import app.lily.ai.core.Router
import app.lily.ai.core.RouterInput
import app.lily.ai.core.TaskKind
import app.lily.ai.models.Catalog
import app.lily.ai.models.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val GB = 1024L * 1024 * 1024

/** Um aparelho como o Razr 40 Ultra (8 GB, Snapdragon 8+ Gen 1), com ajustes por teste. */
fun razr(
    availableGb: Double = 3.0,
    gpu: Boolean = true,
    thermal: Int = 0,
    battery: Int = 80,
    charging: Boolean = false,
    powerSave: Boolean = false,
    arm64: Boolean = true,
    totalGb: Double = 7.4,
    freeGb: Double = 40.0,
) = DeviceSnapshot(
    totalRamBytes = (totalGb * GB).toLong(),
    availableRamBytes = (availableGb * GB).toLong(),
    lowMemory = false,
    freeStorageBytes = (freeGb * GB).toLong(),
    soc = "Snapdragon 8+ Gen 1",
    cpuCores = 8,
    arm64 = arm64,
    gpuCompute = gpu,
    thermalStatus = thermal,
    batteryPercent = battery,
    charging = charging,
    powerSave = powerSave,
)

class RouterTest {
    private val small = Catalog.qwen3Small.id
    private val mid = Catalog.qwen3.id
    private val gemma = Catalog.gemma4.id

    private fun input(profile: Profile, vararg installed: String, manual: String? = null) =
        RouterInput(profile, installed.toSet() + Catalog.all.filter { it.builtIn }.map { it.id }, emptyMap(), manual)

    @Test
    fun `sem modelo de conversa pede o model hub`() {
        val plan = Router.plan(TaskKind.CHAT, input(Profile.BALANCED), razr())
        val e = plan.engine as EngineChoice.Unavailable
        assertEquals(Category.CHAT, e.install)
    }

    @Test
    fun `ocr e objetos usam o ml kit mesmo sem modelos`() {
        assertEquals(EngineChoice.Ocr, Router.plan(TaskKind.OCR, input(Profile.ECO), razr()).engine)
        assertEquals(EngineChoice.Labels, Router.plan(TaskKind.LABELS, input(Profile.ECO), razr()).engine)
    }

    @Test
    fun `perfis escolhem pelo tamanho real dos instalados`() {
        val eco = Router.plan(TaskKind.CHAT, input(Profile.ECO, small, mid, gemma), razr()).engine as EngineChoice.Llm
        assertEquals(small, eco.model.id)
        assertEquals(Accel.CPU, eco.accel)
        val quality = Router.plan(TaskKind.CHAT, input(Profile.QUALITY, small, mid, gemma), razr()).engine as EngineChoice.Llm
        assertEquals(gemma, quality.model.id)
        assertEquals(Accel.GPU, quality.accel)
        val balanced = Router.plan(TaskKind.CHAT, input(Profile.BALANCED, small, mid, gemma), razr()).engine as EngineChoice.Llm
        assertEquals(mid, balanced.model.id)
    }

    @Test
    fun `tarefa curta usa o modelo compacto`() {
        val e = Router.plan(TaskKind.CORRECT, input(Profile.BALANCED, small, mid), razr()).engine as EngineChoice.Llm
        assertEquals(small, e.model.id)
    }

    @Test
    fun `bateria baixa troca para o eco e explica`() {
        val plan = Router.plan(TaskKind.CHAT, input(Profile.QUALITY, small, gemma), razr(battery = 10))
        val e = plan.engine as EngineChoice.Llm
        assertEquals(small, e.model.id)
        assertTrue(plan.reasons.any { it.contains("Bateria") })
    }

    @Test
    fun `aparelho morno usa a cpu`() {
        val e = Router.plan(TaskKind.CHAT, input(Profile.QUALITY, gemma), razr(thermal = 2)).engine as EngineChoice.Llm
        assertEquals(Accel.CPU, e.accel)
    }

    @Test
    fun `sem gpu usa a cpu`() {
        val e = Router.plan(TaskKind.CHAT, input(Profile.QUALITY, gemma), razr(gpu = false)).engine as EngineChoice.Llm
        assertEquals(Accel.CPU, e.accel)
    }

    @Test
    fun `pouca memoria livre usa o menor`() {
        val plan = Router.plan(TaskKind.CHAT, input(Profile.QUALITY, small, gemma), razr(availableGb = 0.5))
        assertEquals(small, (plan.engine as EngineChoice.Llm).model.id)
        assertTrue(plan.reasons.any { it.contains("Pouca memória") })
    }

    @Test
    fun `escolha manual vence`() {
        val e = Router.plan(TaskKind.CHAT, input(Profile.ECO, small, gemma, manual = gemma), razr()).engine as EngineChoice.Llm
        assertEquals(gemma, e.model.id)
    }

    @Test
    fun `escolha manual de modelo nao instalado e ignorada`() {
        val e = Router.plan(TaskKind.CHAT, input(Profile.ECO, small, manual = gemma), razr()).engine as EngineChoice.Llm
        assertEquals(small, e.model.id)
    }

    @Test
    fun `imagem exige modelo de visao`() {
        val plan = Router.plan(TaskKind.CHAT, input(Profile.BALANCED, small, mid), razr(), needsImage = true)
        assertEquals(Category.VISION, (plan.engine as EngineChoice.Unavailable).install)
        val ok = Router.plan(TaskKind.DESCRIBE_IMAGE, input(Profile.BALANCED, small, gemma), razr()).engine as EngineChoice.Llm
        assertEquals(gemma, ok.model.id)
    }

    @Test
    fun `audio so com modelo que ouve`() {
        val none = Router.plan(TaskKind.TRANSCRIBE_AUDIO, input(Profile.BALANCED, small, mid), razr()).engine
        assertTrue(none is EngineChoice.Unavailable)
        val ok = Router.plan(TaskKind.TRANSCRIBE_AUDIO, input(Profile.BALANCED, gemma), razr()).engine as EngineChoice.Llm
        assertEquals(gemma, ok.model.id)
    }

    @Test
    fun `ampliar usa o esrgan so quando instalado`() {
        assertEquals(EngineChoice.Classic, Router.plan(TaskKind.UPSCALE, input(Profile.BALANCED), razr()).engine)
        val sr = Router.plan(TaskKind.UPSCALE, input(Profile.BALANCED, Catalog.esrgan.id), razr()).engine
        assertTrue(sr is EngineChoice.SuperResolution)
    }

    @Test
    fun `geracao de imagem exige o iris e a gpu`() {
        val missing = Router.plan(TaskKind.GENERATE_IMAGE, input(Profile.BALANCED), razr()).engine as EngineChoice.Unavailable
        assertEquals(Category.IMAGE_GEN, missing.install)
        assertTrue(Router.plan(TaskKind.GENERATE_IMAGE, input(Profile.BALANCED, Catalog.iris.id), razr(gpu = false)).engine is EngineChoice.Unavailable)
        assertTrue(Router.plan(TaskKind.GENERATE_IMAGE, input(Profile.BALANCED, Catalog.iris.id), razr()).engine is EngineChoice.ImageGen)
        assertTrue(Router.plan(TaskKind.GENERATE_IMAGE, input(Profile.BALANCED, Catalog.iris.id), razr(thermal = 3)).engine is EngineChoice.Unavailable)
    }

    @Test
    fun `traducao rapida no ml kit e no qualidade pelo modelo`() {
        assertEquals(EngineChoice.Translator, Router.plan(TaskKind.TRANSLATE, input(Profile.BALANCED, mid), razr()).engine)
        assertTrue(Router.plan(TaskKind.TRANSLATE, input(Profile.QUALITY, mid), razr()).engine is EngineChoice.Llm)
    }

    @Test
    fun `nunca escolhe um modelo que nao esta instalado`() {
        for (task in TaskKind.entries) for (p in Profile.entries) {
            val plan = Router.plan(task, input(p, small), razr())
            plan.model?.let { assertTrue("$task/$p escolheu ${it.id}", it.id == small || it.builtIn) }
        }
    }

    @Test
    fun `tudo roda no aparelho`() {
        assertTrue(Router.plan(TaskKind.CHAT, input(Profile.BALANCED, small), razr()).local)
    }
}
