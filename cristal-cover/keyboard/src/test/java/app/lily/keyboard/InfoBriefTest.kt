package app.lily.keyboard

import android.content.Context
import android.graphics.Bitmap
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.lily.keyboard.brief.BriefBridge
import app.lily.shared.brief.BriefCandidate
import app.lily.shared.brief.BriefDetector
import app.lily.shared.brief.BriefKind
import app.lily.keyboard.brief.BriefPhase
import app.lily.keyboard.brief.BriefUi
import app.lily.shared.bridge.CristalBrief
import app.lily.keyboard.brief.InfoBrief
import app.lily.keyboard.core.KeyboardSettings
import app.lily.keyboard.core.Themes
import app.lily.keyboard.engine.EditorConfig
import app.lily.keyboard.ui.BriefActions
import app.lily.keyboard.ui.BriefOverlay
import java.io.File
import java.time.LocalDateTime
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h1004dp-420dpi")
class InfoBriefTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val now = LocalDateTime.of(2026, 11, 16, 10, 0)

    /** Ponte de mentira: finge o Cristal e guarda o que foi mandado. */
    private class FakeBridge(context: Context, val result: Int, val present: Boolean = true) : BriefBridge(context) {
        val sent = ArrayList<BriefCandidate>()
        var origin: Offset? = null
        override fun installed() = present
        override fun available() = present
        override fun suggest(c: BriefCandidate, id: Long, origin: Offset?, displayId: Int, onResult: (Int) -> Unit) {
            sent += c
            this.origin = origin
            onResult(result)
        }
    }

    private fun chat(): EditorInfo = EditorInfo().apply {
        inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES or EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE
        packageName = "com.whatsapp"
    }

    private fun brief(bridge: BriefBridge, settings: KeyboardSettings = KeyboardSettings()): InfoBrief =
        InfoBrief(context, { settings }, bridge, clock = { now }).also {
            val info = chat()
            it.startInput(info, EditorConfig.from(info))
        }

    private fun idle(ms: Long) = ShadowLooper.idleMainLooper(ms, java.util.concurrent.TimeUnit.MILLISECONDS)

    @Test
    fun `campos permitidos`() {
        val own = context.packageName
        fun allowed(change: EditorInfo.() -> Unit): Boolean {
            val info = chat().apply(change)
            return InfoBrief.isAllowed(info, EditorConfig.from(info), own)
        }
        assertTrue(allowed {})
        assertFalse("senha", allowed { inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_PASSWORD })
        assertFalse("número", allowed { inputType = EditorInfo.TYPE_CLASS_NUMBER })
        assertFalse("e-mail", allowed { inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS })
        assertFalse("anônimo", allowed { imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING })
        assertFalse("código", allowed { hintText = "Código de verificação" })
        assertFalse("o próprio Cristal", allowed { packageName = CristalBrief.PACKAGE })
    }

    @Test
    fun `pausa de 1,5 s mostra a sugestao e a esfera vai para a ilha`() {
        val bridge = FakeBridge(context, CristalBrief.RESULT_SHOWN)
        val b = brief(bridge)
        b.onTextChanged("Vou ir ao show às 7 horas da noite.")
        idle(1_400)
        assertNull("ainda não deu 1,5 s", b.ui.value)
        idle(200)
        val ui = assertNotNull(b.ui.value).let { b.ui.value!! }
        assertEquals(BriefPhase.GLOW, ui.phase)
        assertEquals("Show", ui.candidate.title)
        assertTrue(ui.fly)
        b.launched(ui.id, Offset(120f, 1500f))
        assertEquals(1, bridge.sent.size)
        assertEquals(Offset(120f, 1500f), bridge.origin)
        assertNull("a ilha mostrou: o teclado some com a sugestão", b.ui.value)
        // A mesma coisa de novo (por exemplo ao enviar): nada repetido.
        b.onTextChanged("Vou ir ao show às 7 horas da noite.")
        b.onTextChanged("")
        idle(2_000)
        assertNull(b.ui.value)
        assertEquals(1, bridge.sent.size)
    }

    @Test
    fun `ao enviar tambem analisa`() {
        val b = brief(FakeBridge(context, CristalBrief.RESULT_SHOWN))
        b.onTextChanged("Não posso esquecer de entregar o trabalho")
        // O app limpou o campo (mensagem enviada) antes da pausa.
        b.onTextChanged("")
        assertEquals("Entregar o trabalho", b.ui.value?.candidate?.title)
    }

    @Test
    fun `sem o cristal vira chip com a acao`() {
        val b = brief(FakeBridge(context, CristalBrief.RESULT_NONE, present = false))
        b.onSendAction("Amanhã às 19h jantar com a Ana")
        val ui = b.ui.value!!
        assertFalse(ui.fly)
        b.launched(ui.id, null)
        assertEquals(BriefPhase.CHIP, b.ui.value?.phase)
        assertFalse(b.ui.value!!.viaCristal)
        idle(9_100)
        assertNull("o chip some sozinho", b.ui.value)
    }

    @Test
    fun `desligado nao analisa`() {
        val b = brief(FakeBridge(context, CristalBrief.RESULT_SHOWN), KeyboardSettings(infoBrief = false))
        b.onSendAction("Vou ao show às 7 horas da noite")
        assertNull(b.ui.value)
    }

    @Test
    fun `barra do teclado`() {
        val c = BriefDetector.detect("Vou ir ao show às 7 horas da noite.", LocalDateTime.now().withHour(10))!!
        val theme = Themes.liquid
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Column(Modifier.fillMaxSize().background(Color(0xFF3A3F55)).padding(top = 40.dp)) {
                Box(Modifier.fillMaxWidth().height(44.dp).background(Color(0xFF14121E))) {
                    BriefOverlay(BriefUi(1, c, BriefPhase.GLOW, fly = true), theme, BriefActions(), Modifier.fillMaxSize())
                }
                Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(44.dp).background(Color(0xFF14121E))) {
                    BriefOverlay(BriefUi(2, c, BriefPhase.CHIP, fly = false), theme, BriefActions(), Modifier.fillMaxSize())
                }
                Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(44.dp).background(Color(0xFF14121E))) {
                    BriefOverlay(BriefUi(3, c.copy(kind = BriefKind.TASK, title = "Entregar o trabalho", at = null), BriefPhase.CHIP, fly = false), theme, BriefActions(), Modifier.fillMaxSize())
                }
            }
        }
        // Meio do brilho: o chip com a expressão.
        rule.mainClock.advanceTimeBy(560)
        rule.onNodeWithContentDescription("Info Brief: às 7 horas da noite").assertExists()
        rule.onNodeWithText("Criar lembrete").assertExists()
        rule.onNodeWithText("Adicionar").assertExists()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/outputs/telas").apply { mkdirs() }
        File(dir, "k10-info-brief.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Duration.ZERO
    }
}
