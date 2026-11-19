package app.cristal.cover.data.island

import android.app.NotificationManager
import androidx.compose.runtime.Immutable
import app.cristal.cover.data.notifications.NotificationItem

/** Tipo de atividade de outro app. */
enum class LiveCategory { DELIVERY, RIDE, NAVIGATION, PROGRESS, GENERIC }

/** De onde vieram os dados, para a ilha dizer a fonte com honestidade. */
enum class LiveSource(val label: String) {
    /** O app publicou um Live Update do Android 16 (progresso oficial). */
    LIVE_UPDATE("Live Update"),

    /** Reconhecido por um perfil do app (pacote e frases de status). */
    APP_PROFILE("Via notificação"),

    /** Qualquer notificação contínua com progresso. */
    GENERIC("Via notificação"),
}

/**
 * Perfil de um app conhecido. Adicionar um app novo é só acrescentar um perfil: o resto da
 * integração (ilha, Now Brief, bloqueio) não muda.
 *
 * @param stages etapas na ordem, cada uma com as frases que o app costuma usar. Servem só para
 * marcar a etapa quando o app não publica progresso; o texto mostrado continua sendo o do app.
 */
@Immutable
data class AppProfile(
    val packages: Set<String>,
    val category: LiveCategory,
    val stages: List<Pair<String, List<String>>> = emptyList(),
)

/** Uma atividade ao vivo de outro app, no formato único que a ilha, o Now Brief e o bloqueio usam. */
@Immutable
data class LiveActivityData(
    val item: NotificationItem,
    val category: LiveCategory,
    val source: LiveSource,
    /** Nomes das etapas do perfil (vazio se o app não tem perfil). */
    val stages: List<String> = emptyList(),
    /** Etapa atual, reconhecida no texto do app (null se não deu para saber). */
    val stage: Int? = null,
    /** Tempo que o próprio app escreveu ("12 min", "20:15"), nunca calculado por nós. */
    val eta: String? = null,
) {
    /** Progresso de 0 a 1, quando o app publicou. */
    val fraction: Float?
        get() = if (item.progressMax > 0 && item.progress >= 0) (item.progress.toFloat() / item.progressMax).coerceIn(0f, 1f) else null

    /** Pontos das etapas do Live Update, de 0 a 1. */
    val points: List<Float>
        get() = if (item.progressMax > 0) item.progressPoints.map { (it.toFloat() / item.progressMax).coerceIn(0f, 1f) } else emptyList()
}

object LiveIntegration {
    private val delivery = listOf(
        "Confirmado" to listOf("confirmado", "recebemos seu pedido", "pedido aceito", "aceitou seu pedido"),
        "Preparando" to listOf("preparando", "em preparo", "sendo preparado", "separando"),
        "A caminho" to listOf("a caminho", "saiu para entrega", "saiu para a entrega", "em rota", "chegando", "está chegando"),
        "Entregue" to listOf("entregue", "pedido chegou", "foi entregue"),
    )
    private val ride = listOf(
        "Procurando" to listOf("procurando", "buscando motorista", "encontrando"),
        "A caminho" to listOf("a caminho", "chega em", "chegando", "está indo"),
        "Chegou" to listOf("chegou", "está aguardando", "no local de partida", "esperando você"),
        "Em viagem" to listOf("em viagem", "a caminho do destino", "rumo ao destino"),
    )

    /** Perfis conhecidos. Pacotes são os publicados pelos próprios apps na Play Store. */
    val profiles: List<AppProfile> = listOf(
        AppProfile(
            setOf(
                "br.com.brainweb.ifood", // iFood
                "com.ubercab.eats", // Uber Eats
                "com.grability.rappi", // Rappi
                "com.mercadolibre", // Mercado Livre
                "com.shopee.br", // Shopee
                "com.amazon.mShop.android.shopping", // Amazon
            ),
            LiveCategory.DELIVERY,
            delivery,
        ),
        AppProfile(
            setOf(
                "com.ubercab", // Uber
                "com.taxis99", // 99
                "com.cabify.rider", // Cabify
                "sinet.startup.inDriver", // inDrive
            ),
            LiveCategory.RIDE,
            ride,
        ),
        AppProfile(setOf("com.google.android.apps.maps", "com.waze"), LiveCategory.NAVIGATION),
    )

    fun profileOf(packageName: String): AppProfile? = profiles.firstOrNull { packageName in it.packages }

    /**
     * Transforma uma notificação em atividade ao vivo, ou devolve null. Entram: Live Updates do
     * Android 16, notificações contínuas de apps com perfil e qualquer notificação contínua com barra
     * de progresso. Chamadas, timers e mídia têm apresentação própria e ficam de fora.
     */
    fun toLive(item: NotificationItem): LiveActivityData? {
        if (item.isCall) return null
        val profile = profileOf(item.packageName)
        val official = item.promoted || item.progressPoints.isNotEmpty() || item.shortCriticalText != null
        if (!item.ongoing && !item.promoted) return null
        // Cronômetro sem perfil nem progresso é timer (já tem apresentação própria).
        if (profile == null && !official && item.chronometerBase != null) return null
        if (profile == null && !official && !item.hasProgress) return null
        // Serviços em segundo plano silenciosos ("app em execução") não entram.
        if (profile == null && !official && item.importance < NotificationManager.IMPORTANCE_DEFAULT) return null
        val category = profile?.category ?: if (item.hasProgress) LiveCategory.PROGRESS else LiveCategory.GENERIC
        val source = when {
            official -> LiveSource.LIVE_UPDATE
            profile != null -> LiveSource.APP_PROFILE
            else -> LiveSource.GENERIC
        }
        val text = "${item.title} ${item.text}".lowercase()
        val stage = profile?.stages?.indexOfLast { (_, keys) -> keys.any { it in text } }?.takeIf { it >= 0 }
        val eta = item.shortCriticalText ?: extractEta("${item.title} ${item.text}")
        return LiveActivityData(item, category, source, profile?.stages?.map { it.first }.orEmpty(), stage, eta)
    }

    private val minutes = Regex("""(\d{1,3})\s*(?:min|mins|minuto|minutos)\b""", RegexOption.IGNORE_CASE)
    private val clock = Regex("""\b([01]?\d|2[0-3])[:h]([0-5]\d)\b""")

    /** Um tempo que o app escreveu ("12 min", "20:15"). Nunca calcula nem inventa. */
    fun extractEta(text: String): String? {
        minutes.find(text)?.let { return "${it.groupValues[1]} min" }
        clock.find(text)?.let { return "${it.groupValues[1]}:${it.groupValues[2]}" }
        return null
    }

    /** Ordem de prioridade: navegação na frente; entregas e corridas chegando antes das demais. */
    fun rank(live: LiveActivityData): Int = when (live.category) {
        LiveCategory.NAVIGATION -> 0
        LiveCategory.RIDE -> if ((live.stage ?: 0) >= 1) 1 else 3
        LiveCategory.DELIVERY -> if ((live.stage ?: 0) >= 2) 1 else 3
        LiveCategory.PROGRESS -> 4
        LiveCategory.GENERIC -> 5
    }
}
