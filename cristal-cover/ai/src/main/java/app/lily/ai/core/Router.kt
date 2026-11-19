package app.lily.ai.core

import android.os.PowerManager
import app.lily.ai.models.Catalog
import app.lily.ai.models.Category
import app.lily.ai.models.Compat
import app.lily.ai.models.Compatibility
import app.lily.ai.models.Modality
import app.lily.ai.models.ModelSpec
import app.lily.ai.models.Runtime
import app.lily.ai.remote.RemoteModel

/** Os três perfis do roteador. */
enum class Profile(val label: String, val description: String) {
    ECO("Eco", "Prioriza bateria, memória e temperatura: modelos menores na CPU."),
    BALANCED("Equilibrado", "Equilíbrio entre velocidade, qualidade e consumo."),
    QUALITY("Qualidade", "Os modelos mais capazes que o aparelho comporta, na GPU quando possível."),
}

/** Onde o modelo de linguagem roda. */
enum class Accel(val label: String) { CPU("CPU"), GPU("GPU") }

/** Quando a L.I.L.Y pode usar modelos online (pelo L.I.L.Y Gateway). */
@kotlinx.serialization.Serializable
enum class OnlineMode(val label: String, val description: String) {
    OFF("Só no aparelho", "Nada sai do celular. Sem modelo instalado, a L.I.L.Y avisa."),
    FALLBACK("Online quando faltar", "Usa um modelo online só quando não há modelo no aparelho para a tarefa."),
    PREFER("Online primeiro", "Usa os modelos online sempre que houver internet (mais capazes, com custo)."),
}

/** O mecanismo escolhido para uma tarefa. */
sealed interface EngineChoice {
    data class Llm(val model: ModelSpec, val accel: Accel, val threads: Int) : EngineChoice
    data class SuperResolution(val model: ModelSpec) : EngineChoice
    data class ImageGen(val model: ModelSpec) : EngineChoice
    data object Ocr : EngineChoice
    data object Labels : EngineChoice
    data object Subject : EngineChoice
    data object Classic : EngineChoice
    data object SpeechInput : EngineChoice
    data object Voice : EngineChoice
    data object LanguageId : EngineChoice
    data object Translator : EngineChoice

    /** Um modelo online, pelo L.I.L.Y Gateway: o texto sai do aparelho (processamento externo). */
    data class Remote(val model: RemoteModel) : EngineChoice

    /** Não há mecanismo instalado/compatível: [reason] diz o que fazer. */
    data class Unavailable(val reason: String, val install: Category? = null) : EngineChoice
}

/** A decisão do roteador, com os motivos (mostrados ao usuário). */
data class RoutePlan(val task: TaskKind, val engine: EngineChoice, val reasons: List<String>) {
    /** false quando o pedido vai para um modelo online (processamento externo, mostrado na tela). */
    val local: Boolean get() = engine !is EngineChoice.Remote
    val remote: RemoteModel? get() = (engine as? EngineChoice.Remote)?.model
    val model: ModelSpec?
        get() = when (val e = engine) {
            is EngineChoice.Llm -> e.model
            is EngineChoice.SuperResolution -> e.model
            is EngineChoice.ImageGen -> e.model
            else -> null
        }
}

/** O que o roteador precisa saber além do aparelho. */
data class RouterInput(
    val profile: Profile,
    val installed: Set<String>,
    /** Modelo padrão por categoria (escolhido no Model Hub). */
    val defaults: Map<Category, String> = emptyMap(),
    /** Escolha manual para esta conversa ou pedido (vence tudo, se servir). */
    val manual: String? = null,
    /** O catálogo online conhecido (vazio sem o gateway). */
    val remote: List<RemoteModel> = emptyList(),
    val online: OnlineMode = OnlineMode.OFF,
    /** Gateway configurado, permitido pela pessoa e com internet agora. */
    val connected: Boolean = false,
)

/**
 * O AI Router: escolhe o mecanismo de cada tarefa com informação real (o que está instalado,
 * compatibilidade, memória livre, temperatura, bateria e o perfil), nunca só pelo nome do modelo.
 */
object Router {
    fun plan(task: TaskKind, input: RouterInput, device: DeviceSnapshot, needsImage: Boolean = false, needsAudio: Boolean = false): RoutePlan {
        val reasons = ArrayList<String>()
        val profile = effectiveProfile(input.profile, device, reasons)
        val engine = when (task) {
            TaskKind.OCR -> EngineChoice.Ocr.also { reasons += "Extração de texto: ML Kit, no aparelho." }
            TaskKind.LABELS -> EngineChoice.Labels.also { reasons += "Identificação de objetos: ML Kit, no aparelho." }
            TaskKind.REMOVE_BACKGROUND -> EngineChoice.Subject.also { reasons += "Recorte: segmentação do ML Kit." }
            TaskKind.DENOISE -> EngineChoice.Classic.also { reasons += "Ruído: filtro bilateral que preserva bordas, no aparelho." }
            TaskKind.SPEAK -> EngineChoice.Voice.also { reasons += "Voz do Android, com as vozes instaladas." }
            TaskKind.TRANSCRIBE_MIC -> EngineChoice.SpeechInput.also { reasons += "Reconhecimento de fala do Android (no aparelho quando o idioma está instalado)." }
            TaskKind.LANGUAGE_ID -> EngineChoice.LanguageId.also { reasons += "Identificação de idioma do ML Kit." }
            TaskKind.UPSCALE, TaskKind.ENHANCE -> superResolution(input, device, reasons)
            TaskKind.GENERATE_IMAGE -> imageGeneration(input, device, reasons)
            TaskKind.TRANSLATE -> translation(input, device, profile, reasons)
            TaskKind.DESCRIBE_IMAGE -> llm(task, input, device, profile, reasons, needImage = true, needAudio = false)
            TaskKind.TRANSCRIBE_AUDIO -> llm(task, input, device, profile, reasons, needImage = false, needAudio = true)
            else -> llm(task, input, device, profile, reasons, needsImage, needsAudio)
        }
        return RoutePlan(task, engine, reasons)
    }

    /** Bateria baixa, economia de energia ou aparelho quente puxam para o Eco. */
    fun effectiveProfile(chosen: Profile, device: DeviceSnapshot, reasons: MutableList<String>): Profile {
        if (chosen != Profile.ECO && device.shouldSave) {
            reasons += when {
                device.thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE -> "Aparelho ${device.thermalLabel.lowercase()}: usando o perfil Eco até esfriar."
                device.powerSave -> "Economia de energia ligada: usando o perfil Eco."
                else -> "Bateria em ${device.batteryPercent}%: usando o perfil Eco."
            }
            return Profile.ECO
        }
        return chosen
    }

    private fun usable(spec: ModelSpec, input: RouterInput, device: DeviceSnapshot): Boolean =
        spec.id in input.installed && Compatibility.evaluate(spec, device).level != Compat.INCOMPATIBLE

    /** Memória livre agora comporta o arquivo do modelo (mapeado) com folga para o contexto. */
    private fun fitsNow(spec: ModelSpec, device: DeviceSnapshot): Boolean =
        device.availableRamBytes > spec.totalBytes * 0.6 + 400L * 1024 * 1024

    private fun llm(
        task: TaskKind,
        input: RouterInput,
        device: DeviceSnapshot,
        profile: Profile,
        reasons: MutableList<String>,
        needImage: Boolean,
        needAudio: Boolean,
    ): EngineChoice {
        val candidates = Catalog.all.filter {
            it.runtime == Runtime.LITERT_LM && usable(it, input, device) &&
                (!needImage || it.has(Modality.IMAGE)) && (!needAudio || it.has(Modality.AUDIO))
        }
        val what = when {
            needImage -> "de visão"
            needAudio -> "que entenda áudio"
            else -> "de conversa"
        }
        // Modelos online: só texto, só com o gateway conectado e permitido pela pessoa.
        val remoteAllowed = input.online != OnlineMode.OFF && input.connected && !needImage && !needAudio
        val manualRemote = input.manual?.let { id -> input.remote.firstOrNull { it.id == id } }
        if (manualRemote != null) {
            if (remoteAllowed) {
                reasons += "Escolhido por você: ${manualRemote.label} (online, processamento externo)."
                return EngineChoice.Remote(manualRemote)
            }
            reasons += "${manualRemote.label} é online e não está disponível agora."
        }
        val remotePick = if (remoteAllowed) pickRemote(task, input.remote, profile, ROLE_CHAT) else null
        if (remotePick != null && input.online == OnlineMode.PREFER) {
            reasons += "Online primeiro: ${remotePick.label} (${remotePick.vendor}), processamento externo."
            return EngineChoice.Remote(remotePick)
        }
        if (candidates.isEmpty()) {
            val category = if (needImage) Category.VISION else if (needAudio) Category.SPEECH else Category.CHAT
            reasons += "Nenhum modelo $what instalado."
            if (remotePick != null) {
                reasons += "Usando ${remotePick.label} (${remotePick.vendor}) online: o texto vai para o gateway e o provedor."
                return EngineChoice.Remote(remotePick)
            }
            return EngineChoice.Unavailable("Instale um modelo $what no Model Hub.", category)
        }
        val manual = input.manual?.let { id -> candidates.firstOrNull { it.id == id } }
        val category = if (needImage) Category.VISION else if (needAudio) Category.SPEECH else Category.CHAT
        val preferred = input.defaults[category]?.let { id -> candidates.firstOrNull { it.id == id } }
        val fitting = candidates.filter { fitsNow(it, device) }.ifEmpty {
            reasons += "Pouca memória livre agora (${"%.1f".format(device.availableRamGb)} GB): usando o menor modelo."
            listOf(candidates.minBy { it.totalBytes })
        }
        val bySize = fitting.sortedBy { it.totalBytes }
        // Tarefas curtas não precisam do maior modelo, a não ser no perfil Qualidade.
        val light = task in setOf(TaskKind.CORRECT, TaskKind.SUMMARIZE, TaskKind.LANGUAGE_ID)
        val model = when {
            manual != null -> manual.also { reasons += "Escolhido por você: ${it.name}." }
            preferred != null && preferred in fitting -> preferred.also { reasons += "Modelo padrão de ${category.label.lowercase()}: ${it.name}." }
            profile == Profile.ECO -> bySize.first().also { reasons += "Perfil Eco: o menor modelo instalado (${it.name})." }
            profile == Profile.QUALITY -> bySize.last().also { reasons += "Perfil Qualidade: o mais capaz que cabe na memória (${it.name})." }
            light -> bySize.first().also { reasons += "${task.label}: um modelo compacto basta (${it.name})." }
            else -> bySize[bySize.size / 2].also { reasons += "Perfil Equilibrado: ${it.name}." }
        }
        val accel = when {
            !device.gpuCompute -> Accel.CPU
            profile == Profile.ECO -> Accel.CPU
            device.thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE -> Accel.CPU
            else -> Accel.GPU
        }
        val threads = if (profile == Profile.ECO) 2 else (device.cpuCores / 2).coerceIn(2, 4)
        reasons += if (accel == Accel.GPU) "Na GPU (mais rápido para textos longos)." else "Na CPU com $threads núcleos."
        return EngineChoice.Llm(model, accel, threads)
    }

    /**
     * O modelo online para uma tarefa: o papel pedido, o nível que o perfil permite e o menor custo
     * entre os iguais. Eco = rápidos e baratos; Equilibrado = os fortes do dia a dia; Qualidade = os
     * de fronteira. Tarefas curtas sempre usam um rápido.
     */
    fun pickRemote(task: TaskKind, models: List<RemoteModel>, profile: Profile, role: String): RemoteModel? {
        val withRole = models.filter { role in it.roles }.ifEmpty { return null }
        val light = task in setOf(TaskKind.CORRECT, TaskKind.SUMMARIZE, TaskKind.LANGUAGE_ID, TaskKind.REPLY)
        val order = when {
            light || profile == Profile.ECO -> listOf("fast", "strong", "frontier")
            profile == Profile.QUALITY -> listOf("frontier", "strong", "fast")
            else -> listOf("strong", "fast", "frontier")
        }
        for (tier in order) {
            withRole.filter { it.tier == tier }.minByOrNull { it.typicalUsd }?.let { return it }
        }
        return withRole.minByOrNull { it.typicalUsd }
    }

    const val ROLE_CHAT = "chat"

    private fun superResolution(input: RouterInput, device: DeviceSnapshot, reasons: MutableList<String>): EngineChoice {
        val model = Catalog.esrgan
        return if (usable(model, input, device)) {
            reasons += "Ampliação com ${model.name} (4×), no aparelho."
            EngineChoice.SuperResolution(model)
        } else {
            reasons += "${model.name} não está instalado: ampliação clássica (interpolação e nitidez)."
            EngineChoice.Classic
        }
    }

    private fun imageGeneration(input: RouterInput, device: DeviceSnapshot, reasons: MutableList<String>): EngineChoice {
        val model = Catalog.iris
        if (model.id !in input.installed) {
            reasons += "Nenhum gerador de imagens instalado."
            return EngineChoice.Unavailable("Instale o ${model.name} no Model Hub (experimental, ${"%.1f".format(model.totalBytes / DeviceSnapshot.GIB)} GB).", Category.IMAGE_GEN)
        }
        val report = Compatibility.evaluate(model, device)
        if (!report.usable) {
            reasons += report.reasons
            return EngineChoice.Unavailable(report.reasons.first())
        }
        if (device.thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) {
            reasons += "Aparelho ${device.thermalLabel.lowercase()}: a geração espera esfriar."
            return EngineChoice.Unavailable("O aparelho está ${device.thermalLabel.lowercase()}. Espere esfriar para gerar imagens.")
        }
        if (device.availableRamBytes < 3_500L * 1024 * 1024) {
            reasons += "Memória livre ${"%.1f".format(device.availableRamGb)} GB: a geração precisa de cerca de 3,2 GB."
        }
        reasons += "Geração experimental com ${model.name} na GPU."
        return EngineChoice.ImageGen(model)
    }

    private fun translation(input: RouterInput, device: DeviceSnapshot, profile: Profile, reasons: MutableList<String>): EngineChoice {
        if (input.online == OnlineMode.PREFER && input.connected && profile == Profile.QUALITY) {
            return llm(TaskKind.TRANSLATE, input, device, profile, reasons, needImage = false, needAudio = false)
        }
        if (profile != Profile.QUALITY || Catalog.all.none { it.runtime == Runtime.LITERT_LM && usable(it, input, device) }) {
            reasons += "Tradução no aparelho com o ML Kit (rápida e leve)."
            return EngineChoice.Translator
        }
        return llm(TaskKind.TRANSLATE, input, device, profile, reasons, needImage = false, needAudio = false)
    }
}
