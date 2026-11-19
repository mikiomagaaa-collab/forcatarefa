package app.lily.keyboard.emoji

import android.content.Context
import android.graphics.Paint
import androidx.compose.runtime.Immutable
import app.lily.keyboard.core.KeyboardStore
import app.lily.keyboard.engine.TextTools
import java.io.InputStream

@Immutable
data class Emoji(val value: String, val group: Int, val keywords: List<String>)

/** Categorias na ordem do arquivo (grupos do Unicode, sem o de "componentes"). */
enum class EmojiGroup(val id: Int, val label: String, val icon: String) {
    SMILEYS(0, "Carinhas", "😀"),
    PEOPLE(1, "Pessoas", "👋"),
    ANIMALS(3, "Natureza", "🐶"),
    FOOD(4, "Comida", "🍕"),
    TRAVEL(5, "Lugares", "🚗"),
    ACTIVITIES(6, "Atividades", "⚽"),
    OBJECTS(7, "Objetos", "💡"),
    SYMBOLS(8, "Símbolos", "❤️"),
    FLAGS(9, "Bandeiras", "🏳️"),
}

/**
 * Emojis com nomes e palavras-chave em português (dados do Emojibase), usados no painel, na busca
 * e nas sugestões. Emojis que o aparelho não sabe desenhar ficam de fora.
 */
class EmojiData(val all: List<Emoji>) {
    private val byKeyword: Map<String, String> = HashMap<String, String>().apply {
        for (e in all) {
            for (k in e.keywords) {
                // Só palavras soltas viram sugestão ("feliz" → 😀), e vale o primeiro emoji (o mais comum).
                if (' ' in k || k.length < 3) continue
                putIfAbsent(TextTools.fold(k), e.value)
            }
        }
    }

    fun group(group: EmojiGroup): List<Emoji> = all.filter { it.group == group.id }

    /** Emoji ligado a uma palavra digitada, para a barra de sugestões. */
    fun forWord(word: String): String? = if (word.length < 3) null else byKeyword[TextTools.fold(word)]

    fun search(query: String, limit: Int = 60): List<Emoji> {
        val q = TextTools.fold(query.trim())
        if (q.isEmpty()) return emptyList()
        val starts = ArrayList<Emoji>()
        val contains = ArrayList<Emoji>()
        for (e in all) {
            val keys = e.keywords.map { TextTools.fold(it) }
            when {
                keys.any { it.startsWith(q) || it.split(' ').any { w -> w.startsWith(q) } } -> starts.add(e)
                keys.any { q in it } -> contains.add(e)
            }
            if (starts.size >= limit) break
        }
        return (starts + contains).take(limit)
    }

    companion object {
        fun parse(input: InputStream, canDraw: (String) -> Boolean = { true }): EmojiData {
            val list = ArrayList<Emoji>(2_000)
            input.bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    val parts = line.split('\t')
                    if (parts.size < 3) continue
                    val value = parts[0]
                    if (!canDraw(value)) continue
                    list.add(Emoji(value, parts[1].toIntOrNull() ?: 0, parts[2].split(';').filter { it.isNotBlank() }))
                }
            }
            return EmojiData(list)
        }

        fun load(context: Context): EmojiData {
            val paint = Paint()
            return context.assets.open("emoji.tsv").use { parse(it) { e -> runCatching { paint.hasGlyph(e) }.getOrDefault(true) } }
        }
    }
}

/** Emojis recentes e favoritos (guardados nas preferências do teclado, só no aparelho). */
class EmojiHistory(private val store: KeyboardStore) {
    fun recent(): List<String> = store.getList(KEY_RECENT)

    fun favorites(): List<String> = store.getList(KEY_FAVORITES)

    fun used(emoji: String) {
        store.putList(KEY_RECENT, (listOf(emoji) + recent().filter { it != emoji }).take(MAX_RECENT))
    }

    fun toggleFavorite(emoji: String): Boolean {
        val current = favorites()
        val isFav = emoji in current
        store.putList(KEY_FAVORITES, if (isFav) current - emoji else (listOf(emoji) + current).take(MAX_FAVORITES))
        return !isFav
    }

    private companion object {
        const val KEY_RECENT = "emoji_recent"
        const val KEY_FAVORITES = "emoji_favorites"
        const val MAX_RECENT = 40
        const val MAX_FAVORITES = 60
    }
}
