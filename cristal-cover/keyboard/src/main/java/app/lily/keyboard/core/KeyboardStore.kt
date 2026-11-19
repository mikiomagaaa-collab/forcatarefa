package app.lily.keyboard.core

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * Preferências do teclado. Ficam num SharedPreferences (leitura síncrona: o teclado abre sem
 * esperar disco) e são compartilhadas entre o serviço do teclado e o Keyboard Studio, que rodam no
 * mesmo processo.
 */
class KeyboardStore private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<KeyboardSettings> = _settings.asStateFlow()

    private fun load(): KeyboardSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return KeyboardSettings()
        return try {
            val s = json.decodeFromString(KeyboardSettings.serializer(), raw)
            // Quem atualizou ganha o atalho da L.I.L.Y na barra uma vez (pode tirar no Studio).
            if (!s.aiToolbar && ToolbarItem.AI !in s.toolbar) s.copy(toolbar = listOf(ToolbarItem.AI) + s.toolbar, aiToolbar = true) else s
        } catch (t: Throwable) {
            Log.w(TAG, "Preferências inválidas; usando os padrões", t)
            KeyboardSettings()
        }
    }

    @Synchronized
    fun update(change: (KeyboardSettings) -> KeyboardSettings) {
        val next = change(_settings.value)
        if (next == _settings.value) return
        _settings.value = next
        prefs.edit().putString(KEY_SETTINGS, json.encodeToString(KeyboardSettings.serializer(), next)).apply()
    }

    fun exportTheme(theme: KeyboardTheme): String =
        json.encodeToString(KeyboardTheme.serializer(), theme.copy(backgroundImage = null, builtIn = false))

    /** Lê um tema exportado. Devolve `null` se o texto não for um tema válido. */
    fun parseTheme(text: String): KeyboardTheme? = try {
        json.decodeFromString(KeyboardTheme.serializer(), text.trim()).copy(builtIn = false, backgroundImage = null)
    } catch (t: Throwable) {
        null
    }

    // Listas pequenas (emojis recentes e favoritos), guardadas como texto separado por quebras de linha.
    fun getList(key: String): List<String> = prefs.getString(key, null)?.split('\n')?.filter { it.isNotEmpty() } ?: emptyList()

    fun putList(key: String, values: List<String>) {
        prefs.edit().putString(key, values.joinToString("\n")).apply()
    }

    companion object {
        private const val TAG = "LilyKeyboard"
        private const val PREFS = "lily_keyboard"
        private const val KEY_SETTINGS = "settings"

        @Volatile private var instance: KeyboardStore? = null

        fun get(context: Context): KeyboardStore =
            instance ?: synchronized(this) { instance ?: KeyboardStore(context.applicationContext).also { instance = it } }
    }
}
