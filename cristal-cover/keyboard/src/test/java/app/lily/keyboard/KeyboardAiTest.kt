package app.lily.keyboard

import android.content.pm.PackageInfo
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import app.lily.keyboard.ai.AiAction
import app.lily.keyboard.ai.AiSource
import app.lily.keyboard.ai.AiUi
import app.lily.keyboard.ai.KeyboardAi
import app.lily.keyboard.core.CorrectionStyle
import app.lily.keyboard.core.KeyboardSettings
import app.lily.keyboard.core.LayoutId
import app.lily.keyboard.core.ToolbarItem
import app.lily.keyboard.engine.EditorConfig
import app.lily.keyboard.engine.InputController
import app.lily.keyboard.engine.LanguageModels
import app.lily.keyboard.engine.SuggestionRunner
import app.lily.keyboard.studio.PreviewSink
import app.lily.keyboard.ui.KeyboardHost
import app.lily.keyboard.ui.KeyboardRoot
import app.lily.keyboard.ui.Panel
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h1004dp-420dpi")
class KeyboardAiTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `senha e codigos nunca vao para a IA`() {
        val password = KeyboardAi.sourceFor(EditorConfig(password = true), "minhasenha", "minhasenha")
        assertTrue(password is AiSource.Blocked)
        val code = KeyboardAi.sourceFor(EditorConfig(layout = LayoutId.NUMBERS), null, "123456")
        assertTrue(code is AiSource.Blocked)
        val phone = KeyboardAi.sourceFor(EditorConfig(layout = LayoutId.PHONE), null, "81999990000")
        assertTrue(phone is AiSource.Blocked)
    }

    @Test
    fun `selecao tem prioridade e o texto e limitado`() {
        val sel = KeyboardAi.sourceFor(EditorConfig(), "trecho", "texto todo antes do cursor") as AiSource.Text
        assertEquals("trecho", sel.text)
        assertTrue(sel.selection)
        val long = "a".repeat(10_000)
        val before = KeyboardAi.sourceFor(EditorConfig(), null, long) as AiSource.Text
        assertEquals(KeyboardAi.MAX_TEXT, before.text.length)
    }

    @Test
    fun `estilo informal vai para a IA`() {
        assertEquals("INFORMAL", KeyboardAi.styleFor(KeyboardSettings(correctionStyle = CorrectionStyle.INFORMAL)))
    }

    @Test
    fun `atalho da lily esta na barra por padrao`() {
        assertEquals(ToolbarItem.AI, KeyboardSettings().toolbar.first())
    }

    @Test
    fun `painel da lily com a previa antes de substituir`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        // A L.I.L.Y AI "instalada" e a ponte liberada (mesma assinatura).
        shadowOf(app.packageManager).installPackage(PackageInfo().apply { packageName = "app.lily.ai" })
        shadowOf(app).grantPermissions("app.lily.ai.permission.USE_AI")
        val models = LanguageModels.get(app)
        val settings = KeyboardSettings(correctionStyle = CorrectionStyle.INFORMAL)
        val ai = KeyboardAi(app) { settings }
        ai.show(AiUi(action = AiAction.CORRECT, output = "vc viu que a prova mudou pra sexta? tbm nn sabia", model = "Qwen3 0.6B", millis = 1_800))
        val host = object : KeyboardHost {
            override fun aiSource(): AiSource = AiSource.Text("vc viu q a prova mudou pra sexta? tbm nn sabia", false)
        }
        rule.setContent {
            val controller = remember {
                InputController(PreviewSink(), models.corrector, models.user, SuggestionRunner.Immediate).apply {
                    this.settings = settings
                    startInput(EditorConfig())
                }
            }
            Box(Modifier.fillMaxSize().background(Color(0xFF3A3F55)), contentAlignment = Alignment.BottomCenter) {
                KeyboardRoot(controller = controller, settings = settings, host = host, models = models, initialPanel = Panel.AI, ai = ai)
            }
        }
        rule.onNodeWithText("Substituir").assertExists()
        rule.onNodeWithText("L.I.L.Y · no aparelho").assertExists()
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/outputs/telas").apply { mkdirs() }
        File(dir, "k20-lily-no-teclado.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
