package app.lily.keyboard.ai

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.Immutable
import app.lily.keyboard.core.KeyboardSettings
import app.lily.shared.bridge.AiBridge
import app.lily.shared.bridge.AiClient
import app.lily.shared.bridge.AiListener
import app.lily.shared.bridge.AiRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** O texto que a ação vai usar: o selecionado ou o que está antes do cursor. */
@Immutable
sealed interface AiSource {
    data class Text(val text: String, val selection: Boolean) : AiSource

    /** Campo protegido (senha, código): nada vai para a IA. */
    data class Blocked(val reason: String) : AiSource
}

/** As ações da L.I.L.Y no teclado (os nomes são os da ponte). */
enum class AiAction(val wire: String, val label: String, val hint: String) {
    ASK("ASK", "Perguntar", "Responde à pergunta escrita antes do cursor."),
    CORRECT("CORRECT", "Corrigir", "Corrige ortografia, acentos e pontuação."),
    REWRITE("REWRITE", "Reescrever", "Reescreve no tom escolhido."),
    TRANSLATE("TRANSLATE", "Traduzir", "Traduz para o idioma escolhido."),
    SUMMARIZE("SUMMARIZE", "Resumir", "Resume os pontos principais."),
    REPLY("REPLY", "Responder", "Sugere uma resposta para a mensagem copiada."),
    WRITE("GENERATE_TEXT", "Escrever", "Escreve um texto a partir do que você pediu."),
}

@Immutable
data class AiUi(
    val action: AiAction? = null,
    val running: Boolean = false,
    val output: String = "",
    val model: String? = null,
    val millis: Long? = null,
    val error: String? = null,
    /** Falta um modelo na L.I.L.Y AI: o painel oferece o Model Hub. */
    val noModel: Boolean = false,
)

enum class AiAvailability { READY, NOT_INSTALLED, NOT_ALLOWED }

/**
 * A L.I.L.Y no teclado. Só manda o texto num toque explícito numa ação; o pedido vai para a
 * L.I.L.Y AI (que roda no aparelho) pela ponte de assinatura, e a resposta volta em pedaços.
 */
class KeyboardAi(private val context: Context, private val settings: () -> KeyboardSettings) {
    private var client: AiClient? = null
    private val _ui = MutableStateFlow(AiUi())
    val ui: StateFlow<AiUi> = _ui.asStateFlow()

    fun availability(): AiAvailability = when {
        !AiBridge.installed(context) -> AiAvailability.NOT_INSTALLED
        !AiBridge.permitted(context) -> AiAvailability.NOT_ALLOWED
        else -> AiAvailability.READY
    }

    fun run(action: AiAction, text: String, tone: String = "NEUTRAL", languageCode: String = "en", languageName: String = "inglês") {
        val c = client ?: AiClient(context).also { client = it }
        c.cancel()
        val started = SystemClock.elapsedRealtime()
        _ui.value = AiUi(action = action, running = true)
        val request = AiRequest(
            action = action.wire,
            text = text,
            style = styleFor(settings()),
            tone = tone,
            language = languageName,
            languageCode = languageCode,
        )
        val ok = c.request(request, object : AiListener {
            override fun onChunk(text: String, model: String?) {
                _ui.update { if (it.action == action && it.running) it.copy(output = text, model = model) else it }
            }

            override fun onDone(text: String, model: String?, millis: Long) {
                _ui.update { it.copy(running = false, output = text, model = model, millis = SystemClock.elapsedRealtime() - started) }
            }

            override fun onError(message: String, code: String) {
                _ui.update { it.copy(running = false, error = message, noModel = code == AiBridge.CODE_NO_MODEL) }
            }
        })
        if (!ok) _ui.value = AiUi(action = action, error = "Não foi possível falar com a L.I.L.Y AI. Abra o app uma vez e tente de novo.")
    }

    /** Para os testes de tela. */
    @androidx.annotation.VisibleForTesting
    fun show(ui: AiUi) {
        _ui.value = ui
    }

    fun stop() {
        client?.cancel()
        _ui.update { it.copy(running = false) }
    }

    fun reset() {
        client?.cancel()
        _ui.value = AiUi()
    }

    /** Solta a ligação com a IA (ela pode descarregar o modelo). */
    fun release() {
        reset()
        client?.release()
        client = null
    }

    companion object {
        /**
         * O texto que pode ir para a IA: nunca de campos de senha nem de números/códigos (como os de
         * verificação); com um trecho selecionado, só ele; senão, o que está antes do cursor.
         */
        fun sourceFor(config: app.lily.keyboard.engine.EditorConfig, selected: String?, before: String?): AiSource = when {
            config.password -> AiSource.Blocked("Campos de senha nunca vão para a IA.")
            config.layout == app.lily.keyboard.core.LayoutId.NUMBERS || config.layout == app.lily.keyboard.core.LayoutId.PHONE ->
                AiSource.Blocked("Campos de números e códigos (como os de verificação) não vão para a IA.")
            !selected.isNullOrBlank() -> AiSource.Text(selected.take(MAX_TEXT), true)
            else -> AiSource.Text(before.orEmpty().takeLast(MAX_TEXT), false)
        }

        const val MAX_TEXT = 4_000

        /** O jeito de escrever do teclado vira o estilo da IA: no informal, vc/pq/tbm/nn ficam. */
        fun styleFor(s: KeyboardSettings): String = s.correctionStyle.name

        val languages = listOf("en" to "Inglês", "es" to "Espanhol", "pt" to "Português", "fr" to "Francês", "it" to "Italiano", "de" to "Alemão")

        val tones = listOf("NEUTRAL" to "Neutro", "FORMAL" to "Formal", "CASUAL" to "Descontraído", "FRIENDLY" to "Amigável", "CONCISE" to "Direto")
    }
}
