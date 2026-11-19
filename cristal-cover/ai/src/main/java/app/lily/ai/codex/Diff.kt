package app.lily.ai.codex

import androidx.compose.runtime.Immutable

/** Uma linha do diff: ' ' igual, '-' saiu, '+' entrou. Números das linhas no antes e no depois (0 = não tem). */
@Immutable
data class DiffLine(val kind: Char, val text: String, val oldLine: Int, val newLine: Int)

@Immutable
data class Hunk(val oldStart: Int, val oldCount: Int, val newStart: Int, val newCount: Int, val lines: List<DiffLine>) {
    val header: String get() = "@@ -$oldStart,$oldCount +$newStart,$newCount @@"
}

@Immutable
data class FileDiff(val path: String, val hunks: List<Hunk>, val added: Int, val removed: Int, val created: Boolean, val deleted: Boolean) {
    /** O diff no formato unificado (o mesmo do git). */
    fun unified(): String = buildString {
        append("--- ").append(if (created) "/dev/null" else "a/$path").append('\n')
        append("+++ ").append(if (deleted) "/dev/null" else "b/$path").append('\n')
        for (h in hunks) {
            append(h.header).append('\n')
            for (l in h.lines) append(l.kind).append(l.text).append('\n')
        }
    }
}

/**
 * Diff por linhas: tira o começo e o fim iguais e compara o meio pela maior subsequência comum
 * (as edições do Codex costumam ser pequenas). Meios gigantes viram "trocou tudo" em vez de travar.
 */
object Diff {
    private const val MAX_CELLS = 4_000_000L

    fun lines(text: String?): List<String> = when {
        text == null -> emptyList()
        text.isEmpty() -> emptyList()
        else -> text.removeSuffix("\n").split('\n')
    }

    fun of(path: String, before: String?, after: String?, context: Int = 3): FileDiff {
        val a = lines(before)
        val b = lines(after)
        val script = script(a, b)
        val added = script.count { it.kind == '+' }
        val removed = script.count { it.kind == '-' }
        return FileDiff(path, hunks(script, context), added, removed, before == null, after == null)
    }

    /** O roteiro completo (iguais, saídas e entradas) com os números das linhas. */
    fun script(a: List<String>, b: List<String>): List<DiffLine> {
        var prefix = 0
        while (prefix < a.size && prefix < b.size && a[prefix] == b[prefix]) prefix++
        var suffix = 0
        while (suffix < a.size - prefix && suffix < b.size - prefix && a[a.size - 1 - suffix] == b[b.size - 1 - suffix]) suffix++
        val am = a.subList(prefix, a.size - suffix)
        val bm = b.subList(prefix, b.size - suffix)
        val out = ArrayList<DiffLine>(a.size + b.size)
        for (i in 0 until prefix) out += DiffLine(' ', a[i], i + 1, i + 1)
        if (am.size.toLong() * bm.size > MAX_CELLS) {
            am.forEachIndexed { i, s -> out += DiffLine('-', s, prefix + i + 1, 0) }
            bm.forEachIndexed { j, s -> out += DiffLine('+', s, 0, prefix + j + 1) }
        } else {
            // Tabela da maior subsequência comum (de trás para frente) e o caminho de volta.
            val n = am.size
            val m = bm.size
            val lcs = Array(n + 1) { IntArray(m + 1) }
            for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) {
                lcs[i][j] = if (am[i] == bm[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
            }
            var i = 0
            var j = 0
            while (i < n || j < m) {
                when {
                    i < n && j < m && am[i] == bm[j] -> { out += DiffLine(' ', am[i], prefix + i + 1, prefix + j + 1); i++; j++ }
                    // Empate: primeiro o que saiu, depois o que entrou (como o git mostra).
                    i < n && (j == m || lcs[i + 1][j] >= lcs[i][j + 1]) -> { out += DiffLine('-', am[i], prefix + i + 1, 0); i++ }
                    else -> { out += DiffLine('+', bm[j], 0, prefix + j + 1); j++ }
                }
            }
        }
        val baseA = a.size - suffix
        val baseB = b.size - suffix
        for (k in 0 until suffix) out += DiffLine(' ', a[baseA + k], baseA + k + 1, baseB + k + 1)
        return out
    }

    /** Agrupa as mudanças com [context] linhas de contexto em volta. */
    fun hunks(script: List<DiffLine>, context: Int = 3): List<Hunk> {
        val changed = script.indices.filter { script[it].kind != ' ' }
        if (changed.isEmpty()) return emptyList()
        val groups = ArrayList<IntRange>()
        var start = (changed.first() - context).coerceAtLeast(0)
        var end = (changed.first() + context).coerceAtMost(script.lastIndex)
        for (idx in changed.drop(1)) {
            if (idx - context <= end + 1) {
                end = (idx + context).coerceAtMost(script.lastIndex)
            } else {
                groups += start..end
                start = (idx - context).coerceAtLeast(0)
                end = (idx + context).coerceAtMost(script.lastIndex)
            }
        }
        groups += start..end
        return groups.map { range ->
            val lines = script.subList(range.first, range.last + 1)
            val oldStart = lines.firstOrNull { it.oldLine > 0 }?.oldLine ?: (script.take(range.first).lastOrNull { it.oldLine > 0 }?.oldLine ?: 0)
            val newStart = lines.firstOrNull { it.newLine > 0 }?.newLine ?: (script.take(range.first).lastOrNull { it.newLine > 0 }?.newLine ?: 0)
            Hunk(oldStart, lines.count { it.kind != '+' }, newStart, lines.count { it.kind != '-' }, lines)
        }
    }
}
