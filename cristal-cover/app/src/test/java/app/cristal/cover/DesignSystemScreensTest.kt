package app.cristal.cover

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.cristal.cover.data.prefs.LiquidThemeId
import app.cristal.cover.data.prefs.Settings
import app.cristal.cover.ui.settings.DesignSystemActions
import app.cristal.cover.ui.settings.DesignSystemShowcase
import app.cristal.cover.ui.theme.CristalTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Monta a prévia do Design System em cada tema e guarda as imagens em build/outputs/telas. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h2600dp-420dpi")
class DesignSystemScreensTest {

    @get:Rule
    val rule = createComposeRule()

    private var chosenTheme: LiquidThemeId? = null

    private val actions = DesignSystemActions(
        setTheme = { chosenTheme = it },
        setCustomBase = {},
        setCustomAccent = {},
        setLiquid = {},
        setFrosted = {},
        setDark = {},
        setColored = {},
    )

    private fun shot(name: String) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/outputs/telas").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun show(settings: Settings) {
        rule.setContent {
            CristalTheme(settings = settings) {
                DesignSystemShowcase(settings = settings, wallpaper = null, onBack = {}, actions = actions)
            }
        }
    }

    @Test
    fun `previa no liquid dark`() {
        show(Settings())
        shot("40-design-system-liquid-dark")
        rule.onNodeWithText("Frosted Glass").assertExists()
        rule.onNodeWithText("Superfícies sólidas").assertExists()
        rule.onNodeWithText("AMOLED").performClick()
        assertEquals(LiquidThemeId.AMOLED, chosenTheme)
    }

    @Test
    fun `previa no liquid light`() {
        show(Settings().copy(liquidTheme = LiquidThemeId.LIQUID_LIGHT))
        shot("41-design-system-liquid-light")
        val toggle = rule.onAllNodes(isToggleable())[0]
        toggle.assertIsOn()
        toggle.performClick()
        toggle.assertIsOff()
    }

    @Test
    fun `tema personalizado mostra o tema de partida e as cores`() {
        show(Settings().copy(liquidTheme = LiquidThemeId.CUSTOM, liquidCustomBase = LiquidThemeId.LAVENDER, liquidCustomAccent = 0xFF30D158.toInt()))
        shot("42-design-system-personalizado")
        rule.onNodeWithText("Partir de").assertExists()
        rule.onNodeWithText("Cor de destaque").assertExists()
    }
}
