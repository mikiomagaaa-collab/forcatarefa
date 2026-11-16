package app.cristal.cover

import android.app.Application
import android.app.Notification
import android.content.pm.PackageInfo
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.cristal.cover.data.home.WidgetKind
import app.cristal.cover.data.island.IslandActivity
import app.cristal.cover.data.island.IslandState
import app.cristal.cover.data.island.LiveIntegration
import app.cristal.cover.data.island.LiveSource
import app.cristal.cover.data.lily.LilyLink
import app.cristal.cover.data.media.MediaState
import app.cristal.cover.data.notifications.NotificationItem
import app.cristal.cover.ui.island.IslandActions
import app.cristal.cover.ui.island.IslandGeometry
import app.cristal.cover.ui.island.IslandSizing
import app.cristal.cover.ui.island.LiquidIsland
import app.cristal.cover.ui.widgets.WidgetActions
import app.cristal.cover.ui.widgets.WidgetCard
import app.cristal.cover.ui.widgets.WidgetData
import app.lily.shared.bridge.AiBridge
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A L.I.L.Y no Cristal: ponte, ilha (segurar e Live Updates), widget. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h1004dp-420dpi")
class LilyIntegrationTest {
    @get:Rule
    val rule = createComposeRule()

    private val now = System.currentTimeMillis()

    private fun shot(name: String) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/outputs/telas").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun `nomes da ponte iguais aos do modulo compartilhado`() {
        assertEquals(AiBridge.PACKAGE, LilyLink.PACKAGE)
        assertEquals(AiBridge.PERMISSION, LilyLink.PERMISSION)
        assertEquals(AiBridge.ACTION_VOICE, LilyLink.ACTION_VOICE)
        assertEquals(AiBridge.ACTION_ASK, LilyLink.ACTION_ASK)
    }

    @Test
    fun `so abre a lily instalada e com a ponte liberada`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertFalse(LilyLink.available(app))
        assertFalse(LilyLink.voice(app))
        shadowOf(app.packageManager).installPackage(PackageInfo().apply { packageName = LilyLink.PACKAGE })
        assertFalse("sem a mesma assinatura, não", LilyLink.available(app))
        shadowOf(app).grantPermissions(LilyLink.PERMISSION)
        assertTrue(LilyLink.available(app))
        assertTrue(LilyLink.voice(app))
        val voice = shadowOf(app).nextStartedActivity
        assertEquals(LilyLink.ACTION_VOICE, voice.action)
        assertEquals(LilyLink.PACKAGE, voice.`package`)
        assertTrue(LilyLink.ask(app, "  quanto é 2 + 2?  "))
        val ask = shadowOf(app).nextStartedActivity
        assertEquals(LilyLink.ACTION_ASK, ask.action)
        assertEquals("quanto é 2 + 2?", ask.getStringExtra(android.content.Intent.EXTRA_TEXT))
        assertFalse("pergunta vazia não abre nada", LilyLink.ask(app, "   "))
    }

    @Test
    fun `segurar a ilha parada chama a lily e segurar a musica expande`() {
        var assists = 0
        var expanded = false
        val geometry = IslandGeometry()
        val state = androidx.compose.runtime.mutableStateOf(IslandState())
        rule.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFF3E5AA8))) {
                LiquidIsland(
                    state = state.value,
                    geometry = geometry,
                    alwaysVisible = true,
                    concealAlerts = false,
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    actions = IslandActions(onAssist = { assists++ }),
                )
            }
        }
        rule.onAllNodesWithContentDescription("Liquid Island")[0].performTouchInput { longClick() }
        rule.waitForIdle()
        assertEquals(1, assists)
        state.value = IslandState(IslandActivity.Media(MediaState("com.spotify.music", "Música", "Artista", null, isPlaying = true, canSkipNext = true, canSkipPrevious = true)))
        rule.waitForIdle()
        rule.onAllNodesWithContentDescription("Liquid Island")[0].performTouchInput { longClick() }
        rule.waitForIdle()
        assertEquals("segurar a música expande, não chama a L.I.L.Y", 1, assists)
        assertTrue(expanded)
    }

    @Test
    fun `lily trabalhando aparece na ilha como live update`() {
        val working = NotificationItem(
            "lily", LilyLink.PACKAGE, "L.I.L.Y AI", "L.I.L.Y respondendo", "Qual a capital do Brasil?", now - 2_000, 0, false, false,
            Notification.VISIBILITY_PRIVATE, null, null, null,
            ongoing = true, importance = android.app.NotificationManager.IMPORTANCE_LOW, progressIndeterminate = true, shortCriticalText = "L.I.L.Y",
        )
        val download = working.copy(key = "dl", title = "Baixando Gemma 4 E2B", text = "1,1 GB de 2,4 GB · 8 MB/s", progress = 46, progressMax = 100, progressIndeterminate = false, shortCriticalText = "46%")
        val live = LiveIntegration.toLive(working)
        assertNotNull(live)
        assertEquals(LiveSource.LIVE_UPDATE, live!!.source)
        val geometry = IslandGeometry()
        rule.setContent {
            Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFE9C6F2), Color(0xFF9EC9FF), Color(0xFF3E5AA8))))) {
                for ((item, expanded) in listOf(working to false, download to false, download to true)) {
                    val state = IslandState(IslandActivity.Live(LiveIntegration.toLive(item)!!))
                    val target = IslandSizing.target(state, geometry, expanded, true)
                    Box(Modifier.fillMaxWidth().height(geometry.topPadding + target.totalHeight + 12.dp)) {
                        LiquidIsland(state, geometry, true, false, expanded, {}, IslandActions())
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_500)
        shot("40-lily-na-ilha")
    }

    @Test
    fun `widget da lily`() {
        rule.setContent {
            Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2B1F5C), Color(0xFF0E3A55)))).padding(16.dp)) {
                WidgetCard(WidgetKind.LILY, WidgetData(lily = true), WidgetActions(), Modifier.fillMaxWidth().height(76.dp))
                Spacer(Modifier.height(16.dp))
                WidgetCard(WidgetKind.LILY, WidgetData(lily = false), WidgetActions(), Modifier.fillMaxWidth().height(76.dp))
            }
        }
        shot("41-widget-lily")
    }
}
