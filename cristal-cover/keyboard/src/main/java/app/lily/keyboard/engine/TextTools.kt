package app.lily.keyboard.engine

import java.util.Locale

/** Utilidades de texto usadas pelo corretor, pela digitação por deslize e pelo controlador. */
object TextTools {

    /** Minúsculas e sem acentos: "Você" → "voce", "ação" → "acao". */
    fun fold(s: String): String {
        val out = CharArray(s.length)
        for (i in s.indices) out[i] = foldChar(s[i].lowercaseChar())
        return String(out)
    }

    fun foldChar(c: Char): Char = when (c) {
        'á', 'à', 'â', 'ã', 'ä', 'å' -> 'a'
        'é', 'ê', 'è', 'ë' -> 'e'
        'í', 'ì', 'î', 'ï' -> 'i'
        'ó', 'ô', 'õ', 'ò', 'ö' -> 'o'
        'ú', 'ù', 'û', 'ü' -> 'u'
        'ç' -> 'c'
        'ñ' -> 'n'
        'ý', 'ÿ' -> 'y'
        else -> c
    }

    /** Letras (com acento) fazem parte da palavra; hífen e apóstrofo também, quando no meio dela. */
    fun isWordChar(c: Char): Boolean = c.isLetter()

    fun isJoiner(c: Char): Boolean = c == '-' || c == '\''

    /** Aplica à [word] a capitalização usada em [typed] (Primeira maiúscula ou TUDO MAIÚSCULO). */
    fun matchCase(typed: String, word: String, locale: Locale): String = when {
        typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } && typed.any { it.isLetter() } -> word.uppercase(locale)
        typed.firstOrNull()?.isUpperCase() == true -> word.replaceFirstChar { it.titlecase(locale) }
        else -> word
    }

    /** Última palavra antes do cursor (ou `null`), ignorando espaços e pontuação no fim. */
    fun lastWord(textBefore: CharSequence?): String? {
        if (textBefore.isNullOrEmpty()) return null
        var end = textBefore.length
        while (end > 0 && !isWordChar(textBefore[end - 1])) {
            // Pontuação de fim de frase quebra o contexto.
            if (textBefore[end - 1] in ".!?\n") return null
            end--
        }
        var start = end
        while (start > 0 && (isWordChar(textBefore[start - 1]) || isJoiner(textBefore[start - 1]))) start--
        return if (start < end) textBefore.substring(start, end) else null
    }

    /** Quantos caracteres a última "letra visível" (grafema) ocupa no fim de [text]. */
    fun lastGraphemeLength(text: CharSequence): Int {
        if (text.isEmpty()) return 0
        val it = java.text.BreakIterator.getCharacterInstance()
        it.setText(text.toString())
        val end = it.last()
        val start = it.previous()
        return if (start == java.text.BreakIterator.DONE) 1 else (end - start).coerceAtLeast(1)
    }
}

/** Distância entre teclas no QWERTY, para o corretor saber quais erros de dedo são prováveis. */
object QwertyProximity {
    private val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    private val offsets = floatArrayOf(0f, 0.5f, 1.5f)
    private val pos = HashMap<Char, Pair<Float, Float>>().apply {
        rows.forEachIndexed { r, row -> row.forEachIndexed { i, c -> put(c, (i + offsets[r]) to r.toFloat()) } }
    }

    fun near(a: Char, b: Char): Boolean {
        val pa = pos[a] ?: return false
        val pb = pos[b] ?: return false
        val dx = pa.first - pb.first
        val dy = pa.second - pb.second
        return dx * dx + dy * dy <= 1.3f
    }

    fun neighbors(c: Char): List<Char> = pos.keys.filter { it != c && near(it, c) }
}
