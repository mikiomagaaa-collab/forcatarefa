package app.cristal.cover

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.cristal.cover.data.island.IslandActivity
import app.cristal.cover.data.island.IslandLogic
import app.cristal.cover.data.island.IslandState
import app.cristal.cover.data.media.MediaState
import app.cristal.cover.data.notifications.NotificationItem
import app.cristal.cover.data.prefs.Settings
import app.cristal.cover.ui.island.IslandActions
import app.cristal.cover.ui.island.IslandGeometry
import app.cristal.cover.ui.island.LiquidIsland
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
class IslandTest {
    @get:Rule
    val rule = createComposeRule()

    private val now = System.currentTimeMillis()

    private fun item(key: String, title: String, text: String, category: String? = null, ongoing: Boolean = false, chrono: Long? = null) =
        NotificationItem(
            key, "com.exemplo.$key", "WhatsApp", title, text, now - 1_000, 0, !ongoing, false,
            Notification.VISIBILITY_PUBLIC, null, null, null,
            category = category, ongoing = ongoing, chronometerBase = chrono,
        )

    private val art = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).also {
        Canvas(it).drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 64f, 64f, 0xFFFF6A3D.toInt(), 0xFF7C4DFF.toInt(), Shader.TileMode.CLAMP) })
    }.asImageBitmap()

    private val media = MediaState(
        "com.spotify.music", "Cartier", "Xavi y Gabito Ballesteros", art, isPlaying = true,
        canSkipNext = true, canSkipPrevious = true, position = 95_000, duration = 187_000, tint = 0xFFC77B45.toInt(),
    )
    private val call = item("call", "Mãe", "Chamada em andamento", Notification.CATEGORY_CALL, ongoing = true, chrono = now - 68_000)

    @Test
    fun `prioridade aviso chamada timer musica`() {
        val s = Settings()
        val withCall = IslandLogic.compute(s, listOf(call), media, null)
        assertTrue(withCall.primary is IslandActivity.Call)
        assertTrue(withCall.secondary is IslandActivity.Media)
        val alert = IslandActivity.Alert(item("msg", "Ana", "Oi!"))
        val withAlert = IslandLogic.compute(s, listOf(call), media, alert)
        assertEquals(alert, withAlert.primary)
        // Desligado nas configurações, o aviso não aparece.
        assertTrue(IslandLogic.compute(s.copy(islandAlerts = false), emptyList(), null, alert).isEmpty)
        // Música pausada não ocupa a ilha.
        assertNull(IslandLogic.compute(s, emptyList(), media.copy(isPlaying = false), null).primary)
        assertTrue(IslandLogic.isAlertWorthy(item("x", "Oi", ""), now))
        assertTrue(!IslandLogic.isAlertWorthy(call, now))
    }

    @Test
    fun `todos os estados da ilha`() {
        val geometry = IslandGeometry()
        val states = listOf(
            IslandState() to false,
            IslandState(IslandActivity.Media(media)) to false,
            IslandState(IslandActivity.Charging(76)) to false,
            IslandState(IslandActivity.Call(call), IslandActivity.Media(media)) to false,
            IslandState(IslandActivity.Ringer(true)) to false,
            IslandState(IslandActivity.Alert(item("msg", "Beatriz", "Hallo! Freuen uns schon auf Samstag! Denkt dran, die Parkerlaubnis…"))) to false,
            IslandState(IslandActivity.Media(media)) to true,
        )
        rule.setContent {
            Column(
                Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFE9C6F2), Color(0xFF9EC9FF), Color(0xFF3E5AA8)))),
            ) {
                for ((state, expanded) in states) {
                    Box(Modifier.fillMaxWidth().height(if (expanded) 200.dp else if (state.primary is IslandActivity.Alert) 100.dp else 58.dp)) {
                        LiquidIsland(
                            state = state,
                            geometry = geometry,
                            alwaysVisible = true,
                            concealAlerts = false,
                            expanded = expanded,
                            onExpandedChange = {},
                            actions = IslandActions(),
                        )
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_500)
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/outputs/telas").apply { mkdirs() }
        File(dir, "30-liquid-island.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(rule.onAllNodesWithContentDescription("Liquid Island").fetchSemanticsNodes().size >= 7)
    }
}
