package app.lily.keyboard.engine

import java.io.InputStream

/**
 * Dicionário de um idioma: palavras com a frequência de uso.
 *
 * A frequência vem como "centibels" (cB) do wordfreq: 0 seria uma palavra em cada texto; cada 100
 * a menos divide a frequência por 10. Quanto MENOR o número, mais comum a palavra.
 */
class Dictionary(val words: Array<String>, val ranks: IntArray) {
    val size: Int get() = words.size

    /** Forma sem acentos de cada palavra (mesma ordem de [words]). */
    val folded: Array<String> = Array(words.size) { TextTools.fold(words[it]) }

    private val exact = HashMap<String, Int>(words.size * 2).apply {
        for (i in words.indices) putIfAbsent(words[i], i)
    }

    /** Índices ordenados pela forma sem acentos, para buscar por prefixo. */
    private val sorted: IntArray = words.indices.sortedWith(compareBy<Int> { folded[it] }.thenBy { ranks[it] }).toIntArray()

    private val byFolded = HashMap<String, IntArray>(words.size * 2).apply {
        val tmp = HashMap<String, MutableList<Int>>(words.size * 2)
        for (i in words.indices) tmp.getOrPut(folded[i]) { ArrayList(1) }.add(i)
        for ((k, v) in tmp) put(k, v.toIntArray())
    }

    /** Palavras agrupadas por primeira letra e tamanho (sem acentos), para o corretor. */
    private val buckets: HashMap<Int, IntArray> = HashMap<Int, MutableList<Int>>().let { tmp ->
        for (i in words.indices) {
            val f = folded[i]
            if (f.isEmpty()) continue
            tmp.getOrPut(bucketKey(f[0], f.length)) { ArrayList() }.add(i)
        }
        HashMap<Int, IntArray>().apply { for ((k, v) in tmp) put(k, v.toIntArray()) }
    }

    /** Palavras agrupadas por primeira e última letra, para a digitação por deslize. */
    private val ends: HashMap<Int, IntArray> by lazy {
        val tmp = HashMap<Int, MutableList<Int>>()
        for (i in words.indices) {
            val f = folded[i]
            if (f.length < 2) continue
            tmp.getOrPut(endsKey(f.first(), f.last())) { ArrayList() }.add(i)
        }
        HashMap<Int, IntArray>().apply { for ((k, v) in tmp) put(k, v.toIntArray()) }
    }

    fun rank(word: String): Int? = exact[word]?.let { ranks[it] }

    fun contains(word: String): Boolean = exact.containsKey(word)

    /** Palavras com a mesma forma sem acentos ("esta" → "esta", "está"). */
    fun variants(foldedWord: String): IntArray = byFolded[foldedWord] ?: EMPTY

    fun bucket(first: Char, length: Int): IntArray = buckets[bucketKey(first, length)] ?: EMPTY

    fun withEnds(first: Char, last: Char): IntArray = ends[endsKey(first, last)] ?: EMPTY

    /** As [limit] palavras mais comuns que começam com [prefix] (sem acentos), exceto [exclude]. */
    fun completions(prefix: String, limit: Int, exclude: String? = null): List<Int> {
        if (prefix.isEmpty()) return emptyList()
        var lo = 0
        var hi = sorted.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (folded[sorted[mid]] < prefix) lo = mid + 1 else hi = mid
        }
        val best = IntArray(limit) { -1 }
        var scanned = 0
        var i = lo
        while (i < sorted.size && scanned < MAX_SCAN) {
            val idx = sorted[i]
            if (!folded[idx].startsWith(prefix)) break
            if (words[idx] != exclude && folded[idx].length > prefix.length) insertBest(best, idx)
            i++
            scanned++
        }
        return best.filter { it >= 0 }
    }

    private fun insertBest(best: IntArray, idx: Int) {
        val r = ranks[idx]
        for (k in best.indices) {
            val cur = best[k]
            if (cur < 0 || r < ranks[cur]) {
                for (m in best.size - 1 downTo k + 1) best[m] = best[m - 1]
                best[k] = idx
                return
            }
        }
    }

    companion object {
        private val EMPTY = IntArray(0)
        private const val MAX_SCAN = 25_000

        private fun bucketKey(first: Char, length: Int) = first.code * 64 + length.coerceAtMost(63)
        private fun endsKey(first: Char, last: Char) = first.code * 65_536 + last.code

        /** Lê o formato "palavra<TAB>cB" (uma por linha, da mais comum para a menos comum). */
        fun parse(input: InputStream): Dictionary {
            val words = ArrayList<String>(90_000)
            val ranks = ArrayList<Int>(90_000)
            input.bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    val tab = line.indexOf('\t')
                    if (tab <= 0) continue
                    words.add(line.substring(0, tab))
                    ranks.add(line.substring(tab + 1).trim().toIntOrNull() ?: 700)
                }
            }
            return Dictionary(words.toTypedArray(), ranks.toIntArray())
        }
    }
}
