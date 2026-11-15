package app.lily.keyboard.engine

import android.content.Context
import android.util.Log
import app.lily.keyboard.core.KeyboardLanguage
import app.lily.keyboard.emoji.EmojiData
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Dicionários, dicionário pessoal e emojis, carregados uma vez por processo e em segundo plano
 * (o teclado nunca espera por eles: sem dicionário, só não há sugestões).
 */
class LanguageModels private constructor(private val context: Context) {
    private val dictionaries = ConcurrentHashMap<KeyboardLanguage, Dictionary>()
    private val loading = ConcurrentHashMap.newKeySet<KeyboardLanguage>()
    private val loader = Executors.newSingleThreadExecutor { r -> Thread(r, "lily-dicionarios").apply { priority = Thread.NORM_PRIORITY - 1 } }

    val user = UserDictionary(File(context.noBackupFilesDir, "palavras_pessoais.tsv"))

    @Volatile var emojis: EmojiData? = null
        private set

    private val _ready = MutableStateFlow(0)

    /** Muda cada vez que algo termina de carregar (a UI pode recalcular sugestões). */
    val ready: StateFlow<Int> = _ready.asStateFlow()

    val corrector = Corrector(::dictionary, user) { word -> emojis?.forWord(word) }

    fun dictionary(language: KeyboardLanguage): Dictionary? {
        val d = dictionaries[language]
        if (d == null) preload(language)
        return d
    }

    fun preload(language: KeyboardLanguage) {
        if (dictionaries.containsKey(language) || !loading.add(language)) return
        loader.execute {
            try {
                val start = System.currentTimeMillis()
                val dict = context.assets.open(language.asset).use { Dictionary.parse(it) }
                dictionaries[language] = dict
                Log.i(TAG, "Dicionário ${language.name}: ${dict.size} palavras em ${System.currentTimeMillis() - start} ms")
            } catch (t: Throwable) {
                Log.e(TAG, "Falha ao carregar o dicionário ${language.name}", t)
            } finally {
                loading.remove(language)
                _ready.value++
            }
        }
        if (emojis == null) {
            loader.execute {
                if (emojis == null) {
                    emojis = runCatching { EmojiData.load(context) }.onFailure { Log.e(TAG, "Falha ao carregar emojis", it) }.getOrNull()
                    _ready.value++
                }
            }
        }
    }

    companion object {
        private const val TAG = "LilyKeyboard"

        @Volatile private var instance: LanguageModels? = null

        fun get(context: Context): LanguageModels =
            instance ?: synchronized(this) { instance ?: LanguageModels(context.applicationContext).also { instance = it } }
    }
}
