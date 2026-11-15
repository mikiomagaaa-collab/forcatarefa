package app.lily.keyboard.core

import androidx.compose.runtime.Immutable

enum class KeyCode { CHAR, SHIFT, DELETE, ENTER, SPACE, TO_SYMBOLS, TO_LETTERS, TO_MORE_SYMBOLS, EMOJI }

/**
 * Uma tecla. [width] é em "teclas" (a fileira de cima tem 10 teclas de largura 1).
 * [alternates] aparecem no toque longo; [hint] é o caractere pequeno no canto (ex.: número).
 */
@Immutable
data class Key(
    val code: KeyCode,
    val label: String = "",
    val output: String = label,
    val width: Float = 1f,
    val alternates: List<String> = emptyList(),
    val hint: String? = null,
)

@Immutable
data class KeyRow(val keys: List<Key>, val startPad: Float = 0f, val endPad: Float = 0f)

enum class LayoutId { LETTERS, SYMBOLS, MORE_SYMBOLS, NUMBERS, PHONE }

/** Tipo de campo, para trocar teclas de apoio (arroba no e-mail, barra no endereço). */
enum class FieldKind { TEXT, EMAIL, URL }

@Immutable
data class KeyboardLayout(val id: LayoutId, val rows: List<KeyRow>)

@Immutable
data class LayoutOptions(
    val language: KeyboardLanguage = KeyboardLanguage.PT_BR,
    val numberRow: Boolean = false,
    val cedilla: Boolean = false,
    val punctuationWidth: Float = 1f,
    val field: FieldKind = FieldKind.TEXT,
)

object KeyLayouts {
    private val accentsPt = mapOf(
        'a' to listOf("á", "ã", "â", "à", "ä", "ª"),
        'e' to listOf("é", "ê", "è", "ë", "€"),
        'i' to listOf("í", "î", "ì", "ï"),
        'o' to listOf("ó", "õ", "ô", "ò", "ö", "º"),
        'u' to listOf("ú", "ü", "ù", "û"),
        'c' to listOf("ç", "©"),
        'n' to listOf("ñ"),
        's' to listOf("ß", "§", "$"),
        'y' to listOf("ý", "ÿ"),
        'l' to listOf("ł"),
        'z' to listOf("ž"),
    )
    private val accentsEn = mapOf(
        'a' to listOf("á", "à", "â", "ä", "ã"),
        'e' to listOf("é", "è", "ê", "ë"),
        'i' to listOf("í", "ì", "î", "ï"),
        'o' to listOf("ó", "ò", "ô", "ö", "õ"),
        'u' to listOf("ú", "ù", "û", "ü"),
        'c' to listOf("ç"),
        'n' to listOf("ñ"),
        's' to listOf("ß", "$"),
    )
    private val digits = "1234567890"

    fun build(id: LayoutId, options: LayoutOptions): KeyboardLayout = when (id) {
        LayoutId.LETTERS -> letters(options)
        LayoutId.SYMBOLS -> symbols(options)
        LayoutId.MORE_SYMBOLS -> moreSymbols(options)
        LayoutId.NUMBERS -> numbers(phone = false)
        LayoutId.PHONE -> numbers(phone = true)
    }

    private fun letter(c: Char, options: LayoutOptions, hint: String? = null): Key {
        val accents = (if (options.language == KeyboardLanguage.PT_BR) accentsPt else accentsEn)[c].orEmpty()
        val alternates = if (hint != null) listOf(hint) + accents else accents
        return Key(KeyCode.CHAR, c.toString(), c.toString(), alternates = alternates, hint = hint)
    }

    private fun letters(o: LayoutOptions): KeyboardLayout {
        val rows = ArrayList<KeyRow>()
        if (o.numberRow) rows.add(numberRow())
        rows.add(KeyRow("qwertyuiop".mapIndexed { i, c -> letter(c, o, if (o.numberRow) null else digits[i].toString()) }))
        rows.add(
            if (o.cedilla) {
                KeyRow("asdfghjklç".map { letter(it, o) })
            } else {
                KeyRow("asdfghjkl".map { letter(it, o) }, startPad = 0.5f, endPad = 0.5f)
            },
        )
        rows.add(
            KeyRow(
                listOf(Key(KeyCode.SHIFT, width = 1.5f)) + "zxcvbnm".map { letter(it, o) } + Key(KeyCode.DELETE, width = 1.5f),
            ),
        )
        rows.add(bottomRow(o, Key(KeyCode.TO_SYMBOLS, "?123", width = 1.5f)))
        return KeyboardLayout(LayoutId.LETTERS, rows)
    }

    private fun numberRow() = KeyRow(digits.map { Key(KeyCode.CHAR, it.toString(), alternates = fractions[it].orEmpty()) })

    private val fractions = mapOf(
        '1' to listOf("¹", "½", "⅓", "¼"),
        '2' to listOf("²", "⅔"),
        '3' to listOf("³", "¾"),
        '0' to listOf("°", "∅"),
    )

    /** Última fileira: troca de página, vírgula, espaço, ponto e enter. */
    private fun bottomRow(o: LayoutOptions, switch: Key): KeyRow {
        val p = o.punctuationWidth.coerceIn(0.8f, 1.4f)
        val space = 10f - 1.5f - 1.5f - 2 * p
        val left = when (o.field) {
            FieldKind.EMAIL -> Key(KeyCode.CHAR, "@", width = p, alternates = listOf(",", "_", "-"))
            FieldKind.URL -> Key(KeyCode.CHAR, "/", width = p, alternates = listOf(",", ":", "_", "-"))
            FieldKind.TEXT -> Key(KeyCode.CHAR, ",", width = p, alternates = listOf("😊", ";", ":", "'"))
        }
        val right = Key(
            KeyCode.CHAR, ".", width = p,
            alternates = if (o.field == FieldKind.TEXT) listOf("?", "!", ",", "'", "\"", ":", ";", "-", "…") else listOf(".com", ".br", ".com.br", "?", "-", "_"),
        )
        return KeyRow(listOf(switch, left, Key(KeyCode.SPACE, width = space), right, Key(KeyCode.ENTER, width = 1.5f)))
    }

    private fun chars(s: String, alternates: Map<Char, List<String>> = emptyMap()) =
        s.map { Key(KeyCode.CHAR, it.toString(), alternates = alternates[it].orEmpty()) }

    private fun symbols(o: LayoutOptions): KeyboardLayout {
        val alt = mapOf(
            '$' to listOf("R$", "€", "£", "¥", "¢"),
            '-' to listOf("–", "—", "_"),
            '(' to listOf("[", "{", "<"),
            ')' to listOf("]", "}", ">"),
            '"' to listOf("“", "”", "«", "»"),
            '\'' to listOf("‘", "’"),
            '!' to listOf("¡"),
            '?' to listOf("¿"),
            '%' to listOf("‰"),
            '*' to listOf("•", "★"),
        )
        return KeyboardLayout(
            LayoutId.SYMBOLS,
            listOf(
                KeyRow(chars("1234567890", fractions)),
                KeyRow(chars("@#$%&-+()/", alt)),
                KeyRow(listOf(Key(KeyCode.TO_MORE_SYMBOLS, "=\\<", width = 1.5f)) + chars("*\"':;!?", alt) + Key(KeyCode.DELETE, width = 1.5f)),
                bottomRow(o.copy(field = FieldKind.TEXT), Key(KeyCode.TO_LETTERS, "ABC", width = 1.5f)),
            ),
        )
    }

    private fun moreSymbols(o: LayoutOptions) = KeyboardLayout(
        LayoutId.MORE_SYMBOLS,
        listOf(
            KeyRow(chars("~`|•√π÷×¶∆")),
            KeyRow(chars("£¢€¥^°={}\\")),
            KeyRow(listOf(Key(KeyCode.TO_SYMBOLS, "?123", width = 1.5f)) + chars("_©®™✓[]") + Key(KeyCode.DELETE, width = 1.5f)),
            bottomRow(o.copy(field = FieldKind.TEXT), Key(KeyCode.TO_LETTERS, "ABC", width = 1.5f)),
        ),
    )

    private fun numbers(phone: Boolean): KeyboardLayout {
        fun k(s: String, w: Float = 1f, alt: List<String> = emptyList()) = Key(KeyCode.CHAR, s, width = w, alternates = alt)
        val rows = if (phone) {
            listOf(
                KeyRow(listOf(k("1", 2f), k("2", 2f), k("3", 2f), k("-", 2f, listOf("(", ")", "/")))),
                KeyRow(listOf(k("4", 2f), k("5", 2f), k("6", 2f), Key(KeyCode.SPACE, width = 2f))),
                KeyRow(listOf(k("7", 2f), k("8", 2f), k("9", 2f), Key(KeyCode.DELETE, width = 2f))),
                KeyRow(listOf(k("*", 1f), k("#", 1f), k("0", 2f, listOf("+")), k("+", 1f), k(",", 1f, listOf(";")), Key(KeyCode.ENTER, width = 2f))),
            )
        } else {
            listOf(
                KeyRow(listOf(k("1", 2f), k("2", 2f), k("3", 2f), k("-", 2f, listOf("+", "/")))),
                KeyRow(listOf(k("4", 2f), k("5", 2f), k("6", 2f), k(",", 2f, listOf(":")))),
                KeyRow(listOf(k("7", 2f), k("8", 2f), k("9", 2f), Key(KeyCode.DELETE, width = 2f))),
                KeyRow(listOf(Key(KeyCode.TO_SYMBOLS, "?123", width = 2f), k("0", 2f), k(".", 2f), Key(KeyCode.ENTER, width = 2f))),
            )
        }
        return KeyboardLayout(if (phone) LayoutId.PHONE else LayoutId.NUMBERS, rows)
    }
}
