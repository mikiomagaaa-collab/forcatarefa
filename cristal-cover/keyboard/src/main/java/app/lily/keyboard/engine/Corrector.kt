package app.lily.keyboard.engine

import androidx.compose.runtime.Immutable
import app.lily.keyboard.core.CorrectionStyle
import app.lily.keyboard.core.KeyboardLanguage
import java.util.Locale

@Immutable
data class Suggestion(val text: String, val kind: Kind) {
    enum class Kind { TYPED, CORRECTION, COMPLETION, EXPANSION }
}

/**
 * Resultado do corretor para a palavra sendo digitada.
 *
 * @param suggestions em ordem de prioridade: a primeira vai no meio da barra.
 * @param autoCorrection o que o espaço vai colocar no lugar da palavra (ou `null` para manter).
 * @param repeated a palavra é igual à anterior ("de de").
 */
@Immutable
data class CorrectionResult(
    val word: String,
    val suggestions: List<Suggestion>,
    val autoCorrection: String? = null,
    val emoji: String? = null,
    val repeated: Boolean = false,
) {
    companion object {
        val EMPTY = CorrectionResult("", emptyList())
    }
}

/**
 * L.I.L.Y Correct: corretor que roda todo no aparelho.
 *
 * Usa o dicionário de frequências, a distância entre as teclas do QWERTY (erros de dedo vizinho
 * custam menos), acentos esquecidos, o dicionário pessoal e um pouco de contexto (a palavra
 * anterior). Só troca sozinho quando a correção é clara; na dúvida, apenas sugere.
 */
class Corrector(
    private val dictionaryFor: (KeyboardLanguage) -> Dictionary?,
    private val user: UserDictionary,
    private val emojiFor: (String) -> String? = { null },
) {
    /** A palavra está no dicionário do idioma (ou já foi aprendida). */
    fun isKnown(word: String, language: KeyboardLanguage): Boolean {
        val lower = word.lowercase()
        return dictionaryFor(language)?.contains(lower) == true || user.isKnown(lower)
    }

    private class Candidate(val word: String, val kind: Suggestion.Kind, var edit: Float, var score: Float)

    fun suggest(
        typed: String,
        previous: String?,
        language: KeyboardLanguage,
        style: CorrectionStyle,
        allowAutoCorrect: Boolean,
        withEmoji: Boolean = true,
    ): CorrectionResult {
        if (typed.isEmpty()) return CorrectionResult.EMPTY
        val locale = if (language == KeyboardLanguage.PT_BR) PT_BR else Locale.US
        val lower = typed.lowercase(locale)
        val f = TextTools.fold(lower)
        val prev = previous?.lowercase(locale)
        val repeated = prev != null && prev == lower && lower !in ALLOWED_REPEATS
        val emoji = if (withEmoji) emojiFor(lower) else null
        val dict = dictionaryFor(language) ?: return CorrectionResult(typed, emptyList(), null, emoji, repeated)

        val isPt = language == KeyboardLanguage.PT_BR
        val protected = isProtected(lower, isPt, style)
        val userKnown = user.isKnown(lower)
        val typedRank = dict.rank(lower) ?: if (userKnown) USER_RANK else null
        val candidates = LinkedHashMap<String, Candidate>()

        fun add(word: String, kind: Suggestion.Kind, rank: Int, edit: Float, extra: Float = 0f) {
            if (word == lower && kind != Suggestion.Kind.TYPED) return
            val score = rank / 100f + edit * EDIT_WEIGHT + extra
            val old = candidates[word]
            if (old == null || score < old.score) candidates[word] = Candidate(word, kind, edit, score)
        }

        if (typedRank != null) add(lower, Suggestion.Kind.TYPED, typedRank, 0f, -TYPED_BONUS)

        // Acentos esquecidos: "nao" → "não", "esta" → "está".
        for (idx in dict.variants(f)) add(dict.words[idx], Suggestion.Kind.CORRECTION, dict.ranks[idx], ACCENT_EDIT)

        // Completar a palavra.
        for (idx in dict.completions(f, 4, exclude = lower)) {
            val w = dict.words[idx]
            add(w, Suggestion.Kind.COMPLETION, dict.ranks[idx], 0f, COMPLETION_PENALTY + (w.length - f.length) * 0.08f)
        }
        for (w in user.completions(f, 2)) add(w, Suggestion.Kind.COMPLETION, USER_RANK, 0f, COMPLETION_PENALTY)

        // Erros de digitação (só vale a pena procurar se a palavra é rara ou desconhecida).
        if (f.length >= 2 && (typedRank == null || typedRank > RARE_RANK)) {
            val maxDist = maxDistance(f.length)
            val firsts = LinkedHashSet<Char>().apply {
                add(f[0])
                addAll(QwertyProximity.neighbors(f[0]))
            }
            for (first in firsts) {
                for (len in (f.length - 2).coerceAtLeast(1)..(f.length + 2)) {
                    for (idx in dict.bucket(first, len)) {
                        val d = weightedDistance(f, dict.folded[idx], maxDist)
                        if (d <= maxDist) add(dict.words[idx], Suggestion.Kind.CORRECTION, dict.ranks[idx], d)
                    }
                }
            }
        }

        // Abreviações do português: vc → você.
        if (isPt) {
            EXPANSIONS[lower]?.let { exp ->
                if (style != CorrectionStyle.INFORMAL) add(exp, Suggestion.Kind.EXPANSION, dict.rank(exp) ?: 300, 0f, EXPANSION_PENALTY)
            }
            applyContext(prev, f, candidates)
        }

        val ranked = candidates.values.sortedBy { it.score }
        val auto = if (allowAutoCorrect) decideAuto(lower, f, typedRank, userKnown, protected, ranked, prev, style, dict) else null
        val ordered = ArrayList<Suggestion>(3)
        fun push(word: String, kind: Suggestion.Kind) {
            val cased = TextTools.matchCase(typed, word, locale)
            if (ordered.none { it.text == cased }) ordered.add(Suggestion(cased, kind))
        }
        if (auto != null) {
            push(auto.word, auto.kind)
            ordered.add(Suggestion(typed, Suggestion.Kind.TYPED))
        } else {
            ranked.firstOrNull()?.let { push(it.word, it.kind) }
            // Palavra desconhecida: deixa o que foi digitado à mão para o usuário manter.
            if (typedRank == null) ordered.add(1.coerceAtMost(ordered.size), Suggestion(typed, Suggestion.Kind.TYPED))
        }
        for (c in ranked) {
            if (ordered.size >= 3) break
            push(c.word, c.kind)
        }
        return CorrectionResult(
            word = typed,
            suggestions = ordered.take(3),
            autoCorrection = auto?.let { TextTools.matchCase(typed, it.word, locale) },
            emoji = emoji,
            repeated = repeated,
        )
    }

    private fun decideAuto(
        lower: String,
        f: String,
        typedRank: Int?,
        userKnown: Boolean,
        protected: Boolean,
        ranked: List<Candidate>,
        prev: String?,
        style: CorrectionStyle,
        dict: Dictionary,
    ): Candidate? {
        if (lower.length < 2 || userKnown) return null
        // Abreviação no modo formal: troca pela forma completa quando não há ambiguidade.
        if (style == CorrectionStyle.FORMAL) {
            FORMAL_EXPANSIONS[lower]?.let { exp -> return ranked.firstOrNull { it.word == exp } ?: Candidate(exp, Suggestion.Kind.EXPANSION, 0f, 0f) }
        }
        if (protected) return null
        val corrections = ranked.filter { it.kind == Suggestion.Kind.CORRECTION || it.kind == Suggestion.Kind.EXPANSION }
        val best = corrections.firstOrNull() ?: return null

        if (typedRank != null) {
            // A palavra existe; só troca se for um acento esquecido e a forma acentuada for MUITO mais comum,
            // ou se o contexto deixar claro ("não esta" → "não está").
            if (style == CorrectionStyle.INFORMAL) return null
            val bestRank = dict.rank(best.word) ?: return null
            if (best.edit == ACCENT_EDIT) {
                val contextual = prev != null && f == "esta" && best.word == "está" && prev in STRONG_VERB_CONTEXT
                return if (bestRank + ACCENT_RATIO_CB < typedRank || contextual) best else null
            }
            // Erro de digitação tão comum que entrou no dicionário ("teh"): troca se a palavra digitada
            // é rara, a correção é uma troca de letras vizinhas e é MUITO mais comum (30×).
            val typo = typedRank > TYPO_RANK && lower.length >= 3 && best.edit <= TYPO_EDIT && bestRank + TYPO_RATIO_CB < typedRank
            return if (typo) best else null
        }

        // Palavra desconhecida: troca só com uma correção clara e bem à frente da segunda.
        val second = corrections.getOrNull(1)
        val margin = if (second == null) Float.MAX_VALUE else second.score - best.score
        val limitEdit = if (style == CorrectionStyle.INFORMAL) 1.0f else maxDistance(f.length)
        val minMargin = if (style == CorrectionStyle.INFORMAL) 0.9f else 0.45f
        if (best.edit > limitEdit || margin < minMargin || best.score > MAX_AUTO_SCORE) return null
        return best
    }

    private fun applyContext(prev: String?, f: String, candidates: MutableMap<String, Candidate>) {
        if (prev == null) return
        when {
            f == "esta" && prev in VERB_CONTEXT -> candidates["está"]?.let { it.score -= 1.2f }
            f == "e" && prev in VERB_CONTEXT -> {
                val c = candidates["é"] ?: Candidate("é", Suggestion.Kind.CORRECTION, ACCENT_EDIT, 2.4f).also { candidates["é"] = it }
                c.score = (c.score - 0.6f).coerceAtLeast(1.9f)
            }
        }
    }

    private fun isProtected(lower: String, isPt: Boolean, style: CorrectionStyle): Boolean {
        if (lower.any { it.isDigit() } || '@' in lower || "://" in lower || lower.contains('.')) return true
        if (LAUGH.matches(lower)) return true
        if (isPt && style != CorrectionStyle.FORMAL && (lower in INFORMAL_PT || lower in EXPANSIONS)) return true
        if (!isPt && lower in INFORMAL_EN) return true
        return false
    }

    private fun maxDistance(length: Int): Float = when {
        length <= 3 -> 1.0f
        length <= 6 -> 1.6f
        else -> 2.2f
    }

    companion object {
        private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")
        private const val USER_RANK = 330
        private const val RARE_RANK = 430
        private const val TYPO_RANK = 430
        private const val TYPO_EDIT = 0.6f
        private const val TYPO_RATIO_CB = 150
        private const val TYPED_BONUS = 0.9f
        private const val ACCENT_EDIT = 0.15f
        private const val EDIT_WEIGHT = 2.2f
        private const val COMPLETION_PENALTY = 0.7f
        private const val EXPANSION_PENALTY = 0.2f
        /** A forma acentuada precisa ser ~8× mais comum (90 cB) para trocar sozinha. */
        private const val ACCENT_RATIO_CB = 90
        private const val MAX_AUTO_SCORE = 8.4f

        private val LAUGH = Regex("^(k{2,}|(ha){2,}h?|(he){2,}h?|(hi){2,}|(rs)+|(ja){2,}|(ka){2,}|(ah)+|(eh)+|a+h+|o+h+|u+i+)$")

        private val ALLOWED_REPEATS = setOf("que", "não", "nao", "bem", "muito", "mais", "tchau", "sim", "já", "ja", "oi", "beijo")

        /** Palavras depois das quais "esta"/"e" provavelmente são o verbo ("está"/"é"). */
        private val VERB_CONTEXT = setOf(
            "eu", "ele", "ela", "você", "voce", "vc", "isso", "isto", "aquilo", "tudo", "onde", "como", "quem", "que",
            "não", "nao", "já", "ja", "agora", "ainda", "aqui", "ali", "lá", "la", "ninguém", "alguém", "sempre", "nunca",
            "também", "tambem", "tb", "tbm", "ela", "o", "a", "isso",
        )
        private val STRONG_VERB_CONTEXT = setOf("ele", "ela", "você", "voce", "vc", "isso", "tudo", "não", "nao", "já", "ja", "ainda", "onde", "como")

        /** Abreviações comuns e a forma completa (sugerida no modo natural, trocada no formal). */
        val EXPANSIONS: Map<String, String> = mapOf(
            "vc" to "você", "vcs" to "vocês", "tb" to "também", "tbm" to "também", "td" to "tudo", "tds" to "todos",
            "hj" to "hoje", "msm" to "mesmo", "mt" to "muito", "mto" to "muito", "mta" to "muita", "q" to "que",
            "cmg" to "comigo", "ctg" to "contigo", "qnd" to "quando", "qdo" to "quando", "agr" to "agora",
            "ngm" to "ninguém", "nd" to "nada", "dps" to "depois", "blz" to "beleza", "vlw" to "valeu",
            "pfv" to "por favor", "pf" to "por favor", "nn" to "não", "obg" to "obrigado", "sdd" to "saudade",
            "sdds" to "saudades", "aq" to "aqui", "aki" to "aqui", "pq" to "porque", "fds" to "fim de semana",
            "oq" to "o que", "cm" to "como", "kd" to "cadê", "tlgd" to "tá ligado",
        )

        /** No formal só trocamos as que não têm outro sentido comum. */
        private val FORMAL_EXPANSIONS: Map<String, String> = EXPANSIONS.filterKeys {
            it !in setOf("pq", "cm", "fds", "obg", "q", "tlgd", "oq", "kd")
        }

        private val INFORMAL_PT = setOf(
            "kk", "rs", "mds", "aff", "sla", "slk", "tlgd", "mn", "mna", "mano", "vey", "véi", "top", "ok", "oi", "oie",
            "eae", "eai", "flw", "tmj", "bjs", "bj", "cv", "krl", "pprt", "nmrl", "slc", "plmds", "tô", "tá", "pra",
            "pro", "pros", "pras", "num", "numa", "cê", "cês", "né", "ne", "hmm", "hm", "ué", "uai", "opa", "eita", "vish",
            "xau", "tchau", "bom", "dia", "pô", "po", "tipo", "ahn", "ein", "hein", "bora", "partiu", "fml",
        )
        private val INFORMAL_EN = setOf("lol", "omg", "btw", "idk", "u", "ur", "pls", "plz", "thx", "ty", "imo", "tbh", "brb", "gonna", "wanna")

        /** Distância de edição ponderada (Damerau-Levenshtein com pesos de teclado), com corte em [max]. */
        fun weightedDistance(a: String, b: String, max: Float): Float {
            val n = a.length
            val m = b.length
            if (kotlin.math.abs(n - m) * 0.4f > max) return Float.MAX_VALUE
            var prev2 = FloatArray(m + 1)
            var prev = FloatArray(m + 1) { j -> j * INS_COST }
            var cur = FloatArray(m + 1)
            for (i in 1..n) {
                cur[0] = i * INS_COST
                var rowMin = cur[0]
                val ca = a[i - 1]
                for (j in 1..m) {
                    val cb = b[j - 1]
                    val sub = when {
                        ca == cb -> 0f
                        QwertyProximity.near(ca, cb) -> NEAR_COST
                        else -> SUB_COST
                    }
                    // Letra dobrada a mais ou a menos ("caro"/"carro") custa pouco.
                    val del = if (i >= 2 && a[i - 2] == ca) DOUBLE_COST else INS_COST
                    val ins = if (j >= 2 && b[j - 2] == cb) DOUBLE_COST else INS_COST
                    var v = minOf(prev[j] + del, cur[j - 1] + ins, prev[j - 1] + sub)
                    if (i > 1 && j > 1 && ca == b[j - 2] && a[i - 2] == cb) v = minOf(v, prev2[j - 2] + SWAP_COST)
                    cur[j] = v
                    if (v < rowMin) rowMin = v
                }
                if (rowMin > max) return Float.MAX_VALUE
                val t = prev2
                prev2 = prev
                prev = cur
                cur = t
            }
            return prev[m]
        }

        private const val INS_COST = 0.85f
        private const val DOUBLE_COST = 0.35f
        private const val NEAR_COST = 0.55f
        private const val SUB_COST = 1.0f
        private const val SWAP_COST = 0.6f
    }
}
