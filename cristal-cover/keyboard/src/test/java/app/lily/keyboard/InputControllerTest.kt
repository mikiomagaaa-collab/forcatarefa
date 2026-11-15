package app.lily.keyboard

import android.view.inputmethod.EditorInfo
import app.lily.keyboard.core.CorrectionStyle
import app.lily.keyboard.core.KeyboardLanguage
import app.lily.keyboard.core.KeyboardSettings
import app.lily.keyboard.core.LayoutId
import app.lily.keyboard.engine.Corrector
import app.lily.keyboard.engine.EditorConfig
import app.lily.keyboard.engine.EnterKind
import app.lily.keyboard.engine.InputController
import app.lily.keyboard.engine.ShiftState
import app.lily.keyboard.engine.Suggestion
import app.lily.keyboard.engine.SuggestionRunner
import app.lily.keyboard.engine.UserDictionary
import app.lily.keyboard.studio.PreviewSink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Testa o controlador com um campo de texto de mentira (o mesmo da prévia do Studio). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InputControllerTest {
    private var now = 10_000L
    private val sink = PreviewSink()
    private val user = UserDictionary(null)
    private val corrector = Corrector({ if (it == KeyboardLanguage.PT_BR) EngineTest.pt else EngineTest.en }, user)
    private val controller = InputController(sink, corrector, user, SuggestionRunner.Immediate) { now }.apply {
        settings = KeyboardSettings()
        startInput(EditorConfig())
    }
    private val text: String get() = sink.committed + sink.composing

    private fun type(s: String) {
        for (c in s) {
            now += 120
            if (c == ' ') controller.onSpace() else controller.onChar(c.toString())
        }
    }

    @Test
    fun `corrige no espaco e desfaz com apagar`() {
        // O campo começa vazio: a primeira letra sai maiúscula sozinha.
        type("nao ")
        assertEquals("Não ", text)
        controller.onDelete()
        assertEquals("Nao", text)
        // Depois de desfeita, a mesma palavra não é corrigida de novo.
        type(" ")
        assertEquals("Nao ", text)
        type("voce")
        controller.onChar(".")
        assertEquals("Nao você.", text)
    }

    @Test
    fun `maiuscula no comeco e depois do ponto`() {
        // Campo novo: a primeira letra sai maiúscula.
        assertEquals(ShiftState.ONCE, controller.state.value.shift)
        type("oi tudo bem")
        assertTrue(text, text.startsWith("Oi tudo"))
        controller.onSpace()
        now += 200
        controller.onSpace()
        assertEquals("Oi tudo bem. ", text)
        assertEquals(ShiftState.ONCE, controller.state.value.shift)
    }

    @Test
    fun `sugestao escolhida entra com espaco e pontuacao cola`() {
        type("obriga")
        val pick = controller.state.value.result.suggestions.first { it.text.equals("Obrigado", ignoreCase = true) }
        controller.onSuggestion(pick)
        assertEquals("Obrigado ", text)
        controller.onChar("!")
        assertEquals("Obrigado! ", text)
    }

    @Test
    fun `manter o que foi digitado ensina a palavra`() {
        type("zuleica")
        val typed = controller.state.value.result.suggestions.first { it.kind == Suggestion.Kind.TYPED }
        controller.onSuggestion(typed)
        assertTrue(user.isKnown("zuleica"))
    }

    @Test
    fun `deslize insere palavras com espaco entre elas`() {
        controller.onGesture(listOf("bom", "bem"))
        controller.onGesture(listOf("dia", "diz"))
        controller.onChar(".")
        assertEquals("Bom dia. ", text)
    }

    @Test
    fun `apagar palavra e apagar letra`() {
        type("vamos sair ")
        controller.onDeleteWord()
        assertEquals("Vamos ", text)
        controller.onDelete()
        // Voltou para o fim de "Vamos": a palavra volta a ter sugestões.
        assertEquals("Vamos", text)
        assertEquals("Vamos", controller.state.value.composing)
    }

    @Test
    fun `senha nao tem composicao nem aprendizado`() {
        val info = EditorInfo().apply { inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_PASSWORD }
        controller.startInput(EditorConfig.from(info))
        type("nao123")
        assertEquals("nao123", sink.committed)
        assertEquals("", sink.composing)
        assertTrue(controller.state.value.result.suggestions.isEmpty())
        assertFalse(user.isKnown("nao123"))
    }

    @Test
    fun `configuracao do campo`() {
        val email = EditorConfig.from(EditorInfo().apply {
            inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            imeOptions = EditorInfo.IME_ACTION_NEXT
        })
        assertFalse(email.autoCorrect)
        assertEquals(EnterKind.NEXT, email.enter)
        val phone = EditorConfig.from(EditorInfo().apply { inputType = EditorInfo.TYPE_CLASS_PHONE })
        assertEquals(LayoutId.PHONE, phone.layout)
        val chat = EditorConfig.from(EditorInfo().apply {
            inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES or EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION
        })
        assertEquals(EnterKind.NEWLINE, chat.enter)
        assertTrue(chat.autoCaps)
        val incognito = EditorConfig.from(EditorInfo().apply {
            inputType = EditorInfo.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        })
        assertFalse(incognito.learning)
    }

    @Test
    fun `estilo informal mantem abreviacoes`() {
        controller.settings = KeyboardSettings(correctionStyle = CorrectionStyle.INFORMAL)
        controller.startInput(EditorConfig())
        type("vc tbm vem ")
        assertEquals("Vc tbm vem ", text)
        controller.settings = KeyboardSettings(correctionStyle = CorrectionStyle.FORMAL)
        type("vc ")
        assertTrue(text, text.endsWith("você "))
    }
}
