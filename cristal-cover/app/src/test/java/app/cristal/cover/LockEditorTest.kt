package app.cristal.cover

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import app.cristal.cover.data.battery.BatteryState
import app.cristal.cover.data.prefs.ClockBounds
import app.cristal.cover.data.prefs.ClockFont
import app.cristal.cover.data.prefs.Settings
import app.cristal.cover.ui.lock.ClockDraft
import app.cristal.cover.ui.lock.ClockSnap
import app.cristal.cover.ui.lock.EditorDraft
import app.cristal.cover.ui.lock.LockEditorActions
import app.cristal.cover.ui.lock.LockEditorScreen
import app.cristal.cover.ui.lock.LockUiState
import app.cristal.cover.ui.theme.CristalTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h1004dp-420dpi")
class LockEditorTest {

    @get:Rule
    val rule = createComposeRule()

    private var saved: EditorDraft? = null

    private fun shot(name: String) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/outputs/telas").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun show(settings: Settings = Settings(), cover: Boolean = false) {
        val state = LockUiState(settings = settings, battery = BatteryState(percent = 76), notifications = emptyList())
        rule.setContent {
            CristalTheme(settings = settings) {
                LockEditorScreen(
                    state = state,
                    initial = settings,
                    startOnCover = cover,
                    actions = LockEditorActions(onCancel = {}, onDone = { saved = it }, onOpenCollections = {}, onOpenWallpaper = {}),
                )
            }
        }
    }

    @Test
    fun `arrastar a previa move o relogio e concluido salva`() {
        show()
        shot("43-editor-bloqueio-interno")
        rule.onNodeWithContentDescription("Prévia: arraste para mover o relógio").performTouchInput {
            swipe(start = center, end = center + Offset(0f, height * 0.2f), durationMillis = 300)
        }
        rule.onNodeWithText("Concluído").performClick()
        val draft = assertNotNull(saved).let { saved!! }
        assertTrue("o relógio desceu", draft.inner.y > 0.1f)
        assertEquals("a tela externa não mudou", 0f, draft.cover.y)
    }

    @Test
    fun `setas movem 1 dp`() {
        show()
        repeat(3) { rule.onNodeWithContentDescription("Mover para a direita").performClick() }
        rule.onNodeWithText("Concluído").performClick()
        assertEquals(3f / 411f, saved!!.inner.x, 0.0005f)
    }

    @Test
    fun `aba da tela externa edita outro relogio`() {
        show(Settings().copy(coverClockFont = ClockFont.IMPACT))
        rule.onNodeWithText("Externa").performClick()
        repeat(2) { rule.onNodeWithContentDescription("Mover para baixo").performClick() }
        shot("44-editor-bloqueio-externo")
        rule.onNodeWithText("Concluído").performClick()
        assertEquals(2f / 426f, saved!!.cover.y, 0.0005f)
        assertEquals(0f, saved!!.inner.y)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w422dp-h426dp-400dpi")
    fun `editor cabe na tela externa`() {
        show(cover = true)
        shot("45-editor-na-tela-externa")
        rule.onNodeWithText("Concluído").assertExists()
    }

    @Test
    fun `limites e ima`() {
        val d = ClockDraft(0f, 0f, 0.25f, 1f, ClockFont.LIQUID, 0, true)
        assertEquals(ClockBounds.X, d.moved(5f, 0f).x)
        assertEquals(ClockBounds.Y_MIN, d.moved(0f, -5f).y)
        assertEquals(0f, ClockSnap.snap(0.01f, ClockSnap.X))
        assertEquals(0.05f, ClockSnap.snap(0.05f, ClockSnap.X))
        // O rascunho aplica nas configurações certas.
        val s = EditorDraft(d.copy(x = 0.1f, z = 0.9f), d).applyTo(Settings())
        assertEquals(0.1f, s.lockClockX)
        assertEquals(0.9f, s.lockClockZ)
        assertEquals(0f, s.coverClockX)
    }
}
