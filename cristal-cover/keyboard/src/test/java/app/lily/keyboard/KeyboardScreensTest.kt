package app.lily.keyboard

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import app.lily.keyboard.core.KeyboardSettings
import app.lily.keyboard.core.Themes
import app.lily.keyboard.engine.LanguageModels
import app.lily.keyboard.studio.PreviewKeyboard
import app.lily.keyboard.ui.Panel
import java.io.File
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Monta o teclado de verdade (o mesmo código do serviço) e salva imagens em
 * keyboard/build/outputs/telas, na tela interna e na externa do Razr.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h1004dp-420dpi")
class KeyboardScreensTest {
    @get:Rule
    val rule = createComposeRule()

    @Before
    fun loadDictionaries() {
        val models = LanguageModels.get(ApplicationProvider.getApplicationContext())
        models.preload(KeyboardSettings().language)
        // Espera o carregamento em segundo plano (dicionário e emojis).
        val deadline = System.currentTimeMillis() + 20_000
        while ((models.dictionary(KeyboardSettings().language) == null || models.emojis == null) && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/outputs/telas").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun screen(settings: KeyboardSettings, panel: Panel = Panel.KEYS) {
        rule.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF3A3F55)), contentAlignment = Alignment.BottomCenter) {
                Column(Modifier.fillMaxWidth().wrapContentHeight()) {
                    PreviewKeyboard(settings, initialPanel = panel)
                }
            }
        }
    }

    @Test
    fun `liquid glass na tela interna com sugestoes`() {
        screen(KeyboardSettings())
        rule.onNodeWithContentDescription("Teclado").assertExists()
        shot("k01-liquid-inicial")
        // Digita "obrigadp" tocando nas teclas e confere a sugestão.
        val word = "obrigadp"
        val node = rule.onNodeWithContentDescription("Teclado")
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        for (c in word) {
            val r = rows.indexOfFirst { c in it }
            val i = rows[r].indexOf(c)
            val unit = bounds.width / 10f
            val offset = if (r == 0) 0f else if (r == 1) 0.5f else 1.5f
            val x = (offset + i + 0.5f) * unit
            val rowH = (bounds.height - 8f) / 4f
            val y = r * rowH + rowH / 2f
            node.performTouchInput {
                down(Offset(x, y))
                up()
            }
        }
        rule.waitForIdle()
        shot("k02-liquid-sugestoes")
        rule.onNodeWithText("Obrigado").assertExists()
    }

    @Test
    fun `deslize na tela interna`() {
        screen(KeyboardSettings())
        val node = rule.onNodeWithContentDescription("Teclado")
        val b = node.fetchSemanticsNode().boundsInRoot
        val unit = b.width / 10f
        val rowH = (b.height - 8f) / 4f
        fun key(row: Int, i: Int) = Offset(((if (row == 0) 0f else if (row == 1) 0.5f else 1.5f) + i + 0.5f) * unit, row * rowH + rowH / 2f)
        // c-a-s-a
        val path = listOf(key(2, 2), key(1, 0), key(1, 1), key(1, 0))
        node.performTouchInput {
            down(path[0])
            for (p in 1 until path.size) {
                val a = path[p - 1]
                val z = path[p]
                for (s in 1..8) moveTo(Offset(a.x + (z.x - a.x) * s / 8f, a.y + (z.y - a.y) * s / 8f))
            }
            up()
        }
        rule.waitForIdle()
        shot("k03-deslize")
        rule.onNodeWithText("Casa", substring = true).assertExists()
    }

    @Test
    fun `temas one ui e amoled`() {
        screen(KeyboardSettings(themeId = Themes.oneUi.id, numberRow = true))
        shot("k04-one-ui-numeros")
    }

    @Test
    fun `painel de emojis`() {
        screen(KeyboardSettings(themeId = Themes.lavanda.id), Panel.EMOJI)
        shot("k05-emojis")
    }

    @Test
    @Config(sdk = [36], qualifiers = "w422dp-h426dp-400dpi")
    fun `tela externa compacta`() {
        screen(KeyboardSettings(themeId = Themes.violeta.id, numberRow = true))
        shot("k06-tela-externa")
    }

    @Test
    fun `modo de uma mao`() {
        screen(KeyboardSettings(themeId = Themes.amoled.id, oneHanded = true))
        shot("k07-uma-mao")
    }
}
