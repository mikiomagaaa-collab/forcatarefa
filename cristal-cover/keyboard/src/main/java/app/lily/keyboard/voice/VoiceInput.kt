package app.lily.keyboard.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Immutable
data class VoiceState(
    val listening: Boolean = false,
    val partial: String = "",
    val final: String? = null,
    val error: String? = null,
    /** Volume da voz, de 0 a 1, para a animação. */
    val level: Float = 0f,
    val offline: Boolean = true,
)

/**
 * Ditado por voz com o reconhecimento de fala do próprio Android.
 *
 * Por padrão só usa o reconhecimento NO APARELHO (sem mandar áudio para servidores). Se o aparelho
 * não tiver, o usuário pode permitir o reconhecimento online do sistema nas configurações. O
 * microfone só liga quando o usuário toca no botão e desliga ao terminar ou cancelar.
 */
class VoiceInput(private val context: Context) {
    private val _state = MutableStateFlow(VoiceState())
    val state: StateFlow<VoiceState> = _state.asStateFlow()
    private var recognizer: SpeechRecognizer? = null

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun offlineAvailable(): Boolean = runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)

    fun onlineAvailable(): Boolean = runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)

    /** Começa a ouvir. Devolve uma mensagem de erro se não for possível. */
    fun start(languageTag: String, offlineOnly: Boolean): String? {
        stop()
        if (!hasPermission()) return "PERMISSAO"
        val offline = offlineAvailable()
        val r = when {
            offline -> runCatching { SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }.getOrNull()
            !offlineOnly && onlineAvailable() -> runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
            else -> null
        } ?: return if (offlineOnly) {
            "Este aparelho não tem reconhecimento de voz offline. Você pode permitir o reconhecimento do sistema (online) no L.I.L.Y Keyboard > Voz."
        } else {
            "Nenhum serviço de reconhecimento de voz encontrado."
        }
        recognizer = r
        _state.value = VoiceState(listening = true, offline = offline)
        r.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            // Pontuação automática (Android 13+).
            putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY)
        }
        return runCatching { r.startListening(intent) }.exceptionOrNull()?.let {
            stop()
            "Não foi possível ligar o microfone."
        }
    }

    /** Para de ouvir e espera o resultado final. */
    fun finish() {
        runCatching { recognizer?.stopListening() }
    }

    fun stop() {
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        _state.update { it.copy(listening = false, level = 0f) }
    }

    fun reset() {
        stop()
        _state.value = VoiceState()
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) {
            _state.update { it.copy(level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)) }
        }
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() {
            _state.update { it.copy(level = 0f) }
        }

        override fun onError(error: Int) {
            val message = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Não entendi. Toque no microfone e fale de novo."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permita o microfone para ditar."
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                    "O reconhecimento precisa de internet e ela não respondeu."
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                    "Este idioma ainda não foi baixado para o reconhecimento offline (Configurações do Android > Sistema > Idiomas > Reconhecimento de voz)."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "O reconhecimento de voz está ocupado. Tente de novo."
                else -> "O ditado parou (erro $error)."
            }
            _state.update { it.copy(listening = false, error = message, level = 0f) }
            runCatching { recognizer?.destroy() }
            recognizer = null
        }

        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            _state.update { it.copy(listening = false, final = text, partial = text, level = 0f) }
            runCatching { recognizer?.destroy() }
            recognizer = null
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
            _state.update { it.copy(partial = text) }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}
