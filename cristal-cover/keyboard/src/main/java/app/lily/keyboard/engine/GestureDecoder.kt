package app.lily.keyboard.engine

import kotlin.math.hypot

/** Um ponto do traço do dedo, em pixels da área das teclas. */
data class TracePoint(val x: Float, val y: Float)

/**
 * Digitação por deslize: transforma o traço do dedo sobre as letras nas palavras mais prováveis.
 *
 * Para cada palavra candidata (filtrada pela primeira e pela última letra perto do início e do fim
 * do traço), monta o "traço ideal" ligando os centros das teclas, verifica se o dedo passou perto
 * de cada letra na ordem e compara a forma dos dois traços. A frequência da palavra desempata.
 *
 * @param keyCenters centro de cada letra (sem acento) no teclado atual.
 * @param keyWidth largura de uma tecla, em pixels: todas as distâncias são medidas nessa unidade.
 */
class GestureDecoder(private val keyCenters: Map<Char, TracePoint>, private val keyWidth: Float) {

    fun decode(trace: List<TracePoint>, dict: Dictionary, user: UserDictionary? = null, limit: Int = 5): List<String> {
        if (trace.size < 2 || keyWidth <= 0f) return emptyList()
        val path = resample(trace, SAMPLES)
        val userLength = length(trace) / keyWidth
        val starts = nearKeys(trace.first(), START_RADIUS)
        val ends = nearKeys(trace.last(), START_RADIUS)
        if (starts.isEmpty() || ends.isEmpty()) return emptyList()

        val scored = ArrayList<Pair<String, Float>>()
        for (s in starts) {
            for (e in ends) {
                for (idx in dict.withEnds(s, e)) {
                    val word = dict.folded[idx]
                    val score = score(word, path, trace, userLength) ?: continue
                    var total = score + dict.ranks[idx] / 100f * FREQ_WEIGHT
                    if (user != null && user.isKnown(dict.words[idx])) total -= 0.4f
                    scored.add(dict.words[idx] to total)
                }
            }
        }
        // A mesma forma sem acentos pode aparecer com e sem acento; fica a de melhor pontuação.
        return scored.sortedBy { it.second }.map { it.first }.distinct().take(limit)
    }

    /** Pontuação de uma palavra (menor = melhor), ou `null` se ela claramente não combina. */
    internal fun score(word: String, path: List<TracePoint>, trace: List<TracePoint>, userLength: Float): Float? {
        val keys = ArrayList<TracePoint>(word.length)
        var last = ' '
        for (c in word) {
            if (c == last) continue
            keys.add(keyCenters[c] ?: return null)
            last = c
        }
        if (keys.size < 2) return null
        val ideal = length(keys) / keyWidth
        if (userLength < ideal * 0.45f - 0.6f || userLength > ideal * 2.4f + 1.8f) return null

        // O dedo precisa passar perto de cada letra, na ordem.
        var from = 0
        var letterDist = 0f
        for ((i, k) in keys.withIndex()) {
            val limit = if (i == 0 || i == keys.lastIndex) START_RADIUS else PASS_RADIUS
            var best = Float.MAX_VALUE
            var bestAt = from
            // A primeira passagem perto da tecla (e não a mais perto do traço todo): assim letras
            // repetidas ("gente", "trabalho") casam com a passagem certa.
            for (p in from.coerceAtMost(trace.size - 1) until trace.size) {
                val d = dist(trace[p], k)
                if (d < best) {
                    best = d
                    bestAt = p
                } else if (best / keyWidth <= limit && d - best > keyWidth * 0.5f) {
                    break
                }
            }
            if (best / keyWidth > limit) return null
            letterDist += best / keyWidth
            from = if (i == keys.lastIndex) bestAt else bestAt + 1
        }
        letterDist /= keys.size

        val idealPath = resample(keys, SAMPLES)
        var shape = 0f
        for (i in 0 until SAMPLES) shape += dist(path[i], idealPath[i])
        shape /= SAMPLES * keyWidth
        // Traço muito mais longo (ou curto) que o da palavra: o dedo passou por letras que ela não tem.
        val ratio = kotlin.math.ln(((userLength + 0.5f) / (ideal + 0.5f)).toDouble()).toFloat()
        return shape * SHAPE_WEIGHT + letterDist * LETTER_WEIGHT + kotlin.math.abs(ratio) * LENGTH_WEIGHT
    }

    private fun nearKeys(p: TracePoint, radius: Float): List<Char> =
        keyCenters.entries.filter { dist(it.value, p) / keyWidth <= radius }.sortedBy { dist(it.value, p) }.take(4).map { it.key }

    companion object {
        private const val SAMPLES = 32
        private const val START_RADIUS = 1.05f
        private const val PASS_RADIUS = 0.95f
        private const val SHAPE_WEIGHT = 1.0f
        private const val LETTER_WEIGHT = 1.4f
        private const val FREQ_WEIGHT = 0.45f
        private const val LENGTH_WEIGHT = 2.2f

        private fun dist(a: TracePoint, b: TracePoint): Float = hypot(a.x - b.x, a.y - b.y)

        fun length(points: List<TracePoint>): Float {
            var total = 0f
            for (i in 1 until points.size) total += dist(points[i - 1], points[i])
            return total
        }

        /** Reamostra o traço em [n] pontos igualmente espaçados ao longo do caminho. */
        fun resample(points: List<TracePoint>, n: Int): List<TracePoint> {
            if (points.isEmpty()) return emptyList()
            val total = length(points)
            if (total <= 0f) return List(n) { points.first() }
            val step = total / (n - 1)
            val out = ArrayList<TracePoint>(n)
            out.add(points.first())
            var acc = 0f
            var i = 1
            var prev = points.first()
            while (i < points.size && out.size < n) {
                val cur = points[i]
                val d = dist(prev, cur)
                if (acc + d >= step && d > 0f) {
                    val t = (step - acc) / d
                    val q = TracePoint(prev.x + t * (cur.x - prev.x), prev.y + t * (cur.y - prev.y))
                    out.add(q)
                    prev = q
                    acc = 0f
                } else {
                    acc += d
                    prev = cur
                    i++
                }
            }
            while (out.size < n) out.add(points.last())
            return out
        }
    }
}
