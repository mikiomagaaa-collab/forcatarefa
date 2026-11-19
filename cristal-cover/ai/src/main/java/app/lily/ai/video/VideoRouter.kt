package app.lily.ai.video

import app.lily.ai.remote.VideoModel

/** O nível pedido pela pessoa (sem nomes de modelos na escolha automática). */
enum class VideoQuality(val label: String, val detail: String) {
    FAST("Rápido", "O mais barato e rápido para a tarefa"),
    BALANCED("Equilibrado", "Boa qualidade sem o preço máximo"),
    BEST("Máximo", "O melhor modelo disponível (mais caro)"),
}

/**
 * Escolhe o modelo de vídeo pela tarefa, sem gastar à toa: precisa animar uma imagem? precisa de som?
 * Um modelo grátis que faz o pedido vem sempre primeiro (nos níveis Rápido e Equilibrado); só o nível
 * Máximo ou um pedido que o grátis não faz (som) chega aos pagos. Nunca escolhe um modelo que não faz
 * o pedido.
 */
object VideoRouter {
    data class Pick(val model: VideoModel, val reason: String)

    private fun perSecond(m: VideoModel): Double = m.fromUsd / (m.durations.minOrNull() ?: 5).coerceAtLeast(1)

    fun pick(models: List<VideoModel>, needsImage: Boolean, wantsAudio: Boolean, quality: VideoQuality): Pick? {
        val able = models.filter { (if (needsImage) it.fromImage else it.fromText) && (!wantsAudio || it.audio) }
        if (able.isEmpty()) return null
        val free = able.filter { it.free }
        val paid = able.filterNot { it.free }
        if (free.isNotEmpty() && (quality != VideoQuality.BEST || paid.isEmpty())) {
            val chosen = free.maxBy { it.quality }
            val why = buildString {
                append(if (needsImage) "Anima a sua imagem" else "Cria a partir da descrição")
                append(" · grátis: ").append(chosen.name)
            }
            return Pick(chosen, why)
        }
        val chosen = when (quality) {
            VideoQuality.FAST -> able.minWith(compareBy<VideoModel>({ perSecond(it) }, { if (it.speed == "fast") 0 else 1 }))
            VideoQuality.BEST -> able.maxWith(compareBy<VideoModel>({ it.quality }, { -perSecond(it) }))
            VideoQuality.BALANCED -> able.maxBy { it.quality - 8.0 * perSecond(it) }
        }
        val why = buildString {
            append(if (needsImage) "Anima a sua imagem" else "Cria a partir da descrição")
            if (wantsAudio) append(", com som")
            append(" · ").append(quality.label.lowercase()).append(": ").append(chosen.name)
        }
        return Pick(chosen, why)
    }
}
