package app.lily.keyboard.engine

import java.io.File

/**
 * Dicionário pessoal: palavras que você usa e que não estão no dicionário (nomes, gírias, termos).
 *
 * Uma palavra passa a ser "conhecida" depois de usada duas vezes, ou na hora, se você tocar nela na
 * barra de sugestões. Fica só no aparelho, num arquivo privado do app, fora de qualquer backup.
 */
class UserDictionary(private val file: File?) {
    private val counts = HashMap<String, Int>()
    @Volatile private var dirty = false

    init {
        load()
    }

    @Synchronized
    fun isKnown(word: String): Boolean = (counts[word.lowercase()] ?: 0) >= KNOWN_AT

    @Synchronized
    fun count(word: String): Int = counts[word.lowercase()] ?: 0

    @Synchronized
    fun learn(word: String, amount: Int = 1) {
        val key = word.lowercase()
        if (key.length < 2 || key.length > 40) return
        counts[key] = ((counts[key] ?: 0) + amount).coerceAtMost(9_999)
        if (counts.size > MAX_WORDS) {
            // Esquece as menos usadas.
            counts.entries.sortedBy { it.value }.take(counts.size - MAX_WORDS).forEach { counts.remove(it.key) }
        }
        dirty = true
    }

    @Synchronized
    fun forget(word: String) {
        if (counts.remove(word.lowercase()) != null) dirty = true
    }

    /** Palavras conhecidas que começam com [prefix] (sem acentos), das mais usadas para as menos. */
    @Synchronized
    fun completions(prefixFolded: String, limit: Int): List<String> = counts.entries
        .asSequence()
        .filter { it.value >= KNOWN_AT && it.key.length > prefixFolded.length && TextTools.fold(it.key).startsWith(prefixFolded) }
        .sortedByDescending { it.value }
        .take(limit)
        .map { it.key }
        .toList()

    @Synchronized
    fun all(): List<Pair<String, Int>> = counts.entries.sortedByDescending { it.value }.map { it.key to it.value }

    @Synchronized
    fun clear() {
        counts.clear()
        dirty = true
        save()
    }

    @Synchronized
    fun save() {
        val f = file ?: return
        if (!dirty) return
        runCatching {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(counts.entries.joinToString("\n") { "${it.key}\t${it.value}" })
            if (!tmp.renameTo(f)) {
                tmp.copyTo(f, overwrite = true)
                tmp.delete()
            }
            dirty = false
        }
    }

    @Synchronized
    private fun load() {
        val f = file ?: return
        if (!f.exists()) return
        runCatching {
            f.forEachLine { line ->
                val tab = line.indexOf('\t')
                if (tab > 0) counts[line.substring(0, tab)] = line.substring(tab + 1).toIntOrNull() ?: 1
            }
        }
    }

    companion object {
        const val KNOWN_AT = 2
        private const val MAX_WORDS = 5_000
    }
}
