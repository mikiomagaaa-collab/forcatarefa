package app.lily.ai.data

import android.content.Context
import androidx.compose.runtime.Immutable
import app.lily.ai.core.Profile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class LilyTheme(val label: String) {
    LIQUID("Liquid Glass"),
    AMOLED("AMOLED escuro"),
    LIGHT("Claro"),
    PURPLE("Roxo"),
    CUSTOM("Personalizado"),
}

@Immutable
@Serializable
data class AiSettings(
    val profile: Profile = Profile.BALANCED,
    val theme: LilyTheme = LilyTheme.LIQUID,
    /** Cor de destaque do tema Personalizado (ARGB). */
    val accent: Long = 0xFFB79CFF,
    val memoryEnabled: Boolean = true,
    /** Minutos sem uso até descarregar o modelo de conversa. */
    val unloadMinutes: Int = 2,
    val voiceRate: Float = 1f,
    /** Ler as respostas em voz alta automaticamente quando a pergunta foi por voz. */
    val speakVoiceAnswers: Boolean = true,
    /** Baixar pacotes de tradução e modelos só no Wi-Fi. */
    val wifiOnly: Boolean = true,
    val onboarded: Boolean = false,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("lily_ai", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val _value = MutableStateFlow(load())
    val value: StateFlow<AiSettings> = _value.asStateFlow()

    @Synchronized
    fun update(change: (AiSettings) -> AiSettings) {
        val next = change(_value.value)
        if (next == _value.value) return
        _value.value = next
        prefs.edit().putString(KEY, json.encodeToString(AiSettings.serializer(), next)).apply()
    }

    private fun load(): AiSettings = runCatching {
        prefs.getString(KEY, null)?.let { json.decodeFromString(AiSettings.serializer(), it) }
    }.getOrNull() ?: AiSettings()

    private companion object {
        const val KEY = "settings"
    }
}
