package app.lily.ai.remote

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/** O que o L.I.L.Y Gateway tem configurado (cada recurso depende de uma chave no servidor). */
@Immutable
@Serializable
data class GatewayFeatures(
    val code: Boolean = false,
    /** Vídeo (o grátis está sempre disponível num gateway 1.1+). */
    val video: Boolean = false,
    /** Modelos de vídeo pagos (fal.ai) configurados. */
    val videoPaid: Boolean = false,
    /** Imagens grátis pelo Workers AI. */
    val image: Boolean = false,
    /** Conversa online grátis (beta) pelo Workers AI. */
    val chatFree: Boolean = false,
    val search: Boolean = false,
    val repos: Boolean = false,
)

@Immutable
@Serializable
data class GatewayBudget(
    val dailyUsd: Double = 0.0,
    /** null = o gateway não tem onde guardar o gasto (KV): o limite não é garantido. */
    val spentTodayUsd: Double? = null,
    val enforced: Boolean = false,
)

@Immutable
@Serializable
data class GatewayStatus(
    val ok: Boolean = false,
    val version: String = "",
    val features: GatewayFeatures = GatewayFeatures(),
    val codeModels: Int = 0,
    val codeError: String? = null,
    val searchProvider: String? = null,
    val repos: List<String> = emptyList(),
    val budget: GatewayBudget = GatewayBudget(),
    val free: GatewayFree = GatewayFree(),
)

/** O que sai de graça neste gateway (e quanto ainda resta hoje). */
@Immutable
@Serializable
data class GatewayFree(
    val image: FreeImages? = null,
    val video: FreeVideo? = null,
    /** Nome do modelo de conversa grátis (null = desligado). */
    val chat: String? = null,
    val neuronsToday: Double? = null,
    val neuronsPerDay: Double = 0.0,
)

@Immutable
@Serializable
data class FreeImages(val model: String = "", val perDay: Int = 0, val usedToday: Int? = null, val width: Int = 1024, val height: Int = 1024)

@Immutable
@Serializable
data class FreeVideo(val model: String = "", val account: String = "")

/** Uma imagem gerada online (data URL), grátis dentro da cota diária. */
@Serializable
data class OnlineImage(
    val image: String,
    val model: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val costUsd: Double = 0.0,
    val free: Boolean = true,
    val usedToday: Int? = null,
    val perDay: Int = 0,
)

/**
 * Um modelo online (lido ao vivo pelo gateway na lista do provedor). Preços em US$ por milhão de
 * tokens, informados pelo provedor.
 */
@Immutable
@Serializable
data class RemoteModel(
    val id: String,
    val name: String,
    val vendor: String,
    val family: String = "",
    /** frontier, strong ou fast. */
    val tier: String = "strong",
    /** fast, medium ou slow. */
    val speed: String = "medium",
    val roles: List<String> = emptyList(),
    val context: Int = 0,
    val priceIn: Double = 0.0,
    val priceOut: Double = 0.0,
    val created: Long = 0,
    /** Grátis (Workers AI do gateway, dentro da cota diária). */
    val free: Boolean = false,
) {
    val label: String get() = name.substringAfter(": ", name)

    /** Custo de uma resposta típica (2 mil tokens de entrada, 800 de saída), para comparar. */
    val typicalUsd: Double get() = (2_000 * priceIn + 800 * priceOut) / 1e6

    fun estimate(inputChars: Int, outputTokens: Int): Double = (inputChars / 3.5 * priceIn + outputTokens * priceOut) / 1e6
}

@Serializable
data class RemoteModelList(val provider: String? = null, val models: List<RemoteModel> = emptyList())

@Immutable
@Serializable
data class SearchResult(val title: String, val url: String, val snippet: String = "")

@Serializable
data class SearchResponse(val provider: String? = null, val query: String = "", val results: List<SearchResult> = emptyList())

/** Um modelo de vídeo hospedado (o celular só manda o pedido e acompanha). */
@Immutable
@Serializable
data class VideoModel(
    val id: String,
    val name: String,
    val family: String = "",
    val summary: String = "",
    val license: String = "",
    /** "text" e/ou "image". */
    val modes: List<String> = emptyList(),
    val durations: List<Int> = emptyList(),
    val resolutions: List<String> = emptyList(),
    val aspects: List<String> = emptyList(),
    val audio: Boolean = false,
    val quality: Int = 3,
    val speed: String = "medium",
    val typicalSeconds: Int = 90,
    val fromUsd: Double = 0.0,
    val priceNote: String = "",
    /** Grátis (cota diária do Hugging Face). */
    val free: Boolean = false,
    val provider: String = "fal",
) {
    val fromImage: Boolean get() = "image" in modes
    val fromText: Boolean get() = "text" in modes
}

@Serializable
data class VideoModelList(val provider: String? = null, val models: List<VideoModel> = emptyList())

@Serializable
data class VideoQuote(val model: String = "", val estimatedUsd: Double = 0.0, val duration: Int = 0, val resolution: String = "")

@Serializable
data class VideoSubmitted(
    val jobId: String,
    val model: String = "",
    val mode: String = "text",
    val estimatedUsd: Double = 0.0,
    val typicalSeconds: Int = 90,
    val simplified: Boolean = false,
    val duration: Int = 0,
    val resolution: String = "",
    val aspect: String = "",
    val free: Boolean = false,
    /** O acompanhamento é uma conexão longa (o vídeo grátis é cancelado se ela cair). */
    val stream: Boolean = false,
)

@Serializable
data class VideoFile(val url: String, val contentType: String = "video/mp4", val fileSize: Long? = null)

@Serializable
data class VideoStatus(
    /** queued, running, done ou failed. */
    val status: String,
    val position: Int? = null,
    val elapsed: Int = 0,
    val typicalSeconds: Int = 0,
    val logs: List<String> = emptyList(),
    val video: VideoFile? = null,
    val error: String? = null,
    val free: Boolean = false,
)

@Serializable
data class RepoInfo(val repo: String, val available: Boolean = false, val defaultBranch: String? = null, val private: Boolean = false, val error: String? = null)

@Serializable
data class RepoList(val repos: List<RepoInfo> = emptyList(), val workflow: String? = null)

@Serializable
data class RepoEntry(val path: String, val size: Long = 0)

@Serializable
data class RepoTree(val repo: String = "", val ref: String = "", val sha: String? = null, val truncated: Boolean = false, val files: List<RepoEntry> = emptyList())

@Serializable
data class RepoFile(val path: String, val exists: Boolean = false, val sha: String? = null, val content: String? = null)

@Serializable
data class RepoChange(val path: String, val content: String?)

@Serializable
data class RepoCommit(val repo: String = "", val branch: String = "", val commit: String = "", val parent: String = "", val created: Boolean = false, val url: String = "")

@Serializable
data class CheckStarted(val repo: String = "", val branch: String = "", val workflow: String = "", val runner: String = "", val startedAt: String = "")

@Serializable
data class CheckFailure(val job: String, val log: String)

@Serializable
data class CheckStatus(
    /** waiting, queued, running ou completed. */
    val status: String,
    val conclusion: String? = null,
    val url: String? = null,
    val runId: Long? = null,
    val failures: List<CheckFailure> = emptyList(),
)

/** O fim de uma resposta online: quanto custou (informado pelo provedor). */
@Immutable
data class RemoteUsage(val promptTokens: Int, val completionTokens: Int, val costUsd: Double?)
