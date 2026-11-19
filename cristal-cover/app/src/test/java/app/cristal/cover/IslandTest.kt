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
import app.cristal.cover.data.island.LiveIntegration
import app.cristal.cover.data.island.LiveSource
import app.cristal.cover.data.island.LiveCategory
import app.cristal.cover.data.island.BriefSuggestion
import app.cristal.cover.data.prefs.IslandStyle
import app.cristal.cover.ui.island.IslandLevel
import app.cristal.cover.ui.island.IslandSizing
import app.cristal.cover.ui.island.IslandSpace
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import app.cristal.cover.ui.island.IslandLensGeometry
import app.cristal.cover.ui.island.LENS_DESCRIPTION
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
                    val target = IslandSizing.target(state, geometry, expanded, true)
                    Box(Modifier.fillMaxWidth().height(geometry.topPadding + target.totalHeight + 12.dp)) {
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

    private fun live(
        key: String,
        pkg: String,
        title: String,
        text: String,
        progress: Int = -1,
        max: Int = 0,
        points: List<Int> = emptyList(),
        promoted: Boolean = false,
        importance: Int = android.app.NotificationManager.IMPORTANCE_DEFAULT,
        app: String = "App",
    ) = NotificationItem(
        key, pkg, app, title, text, now - 60_000, 0xFFFF5A36.toInt(), false, false,
        Notification.VISIBILITY_PUBLIC, null, null, null,
        ongoing = true, progress = progress, progressMax = max, progressPoints = points, promoted = promoted, importance = importance,
    )

    @Test
    fun `integracao com apps so mostra o que o app publicou`() {
        // Live Update oficial com etapas.
        val official = LiveIntegration.toLive(live("a", "com.exemplo.entregas", "Saiu para entrega", "Chega entre 20:10 e 20:20", 62, 100, listOf(0, 33, 100), promoted = true))!!
        assertEquals(LiveSource.LIVE_UPDATE, official.source)
        assertEquals(0.62f, official.fraction!!, 0.001f)
        assertEquals(listOf(0f, 0.33f, 1f), official.points)
        assertEquals("20:10", official.eta)
        // Perfil do app (iFood): etapa e tempo vêm do texto que o app escreveu.
        val ifood = LiveIntegration.toLive(live("b", "br.com.brainweb.ifood", "Seu pedido saiu para entrega", "Chega em 12 min"))!!
        assertEquals(LiveCategory.DELIVERY, ifood.category)
        assertEquals("A caminho", ifood.stages[ifood.stage!!])
        assertEquals("12 min", ifood.eta)
        assertNull("sem progresso publicado, sem barra", ifood.fraction)
        // Corrida sem tempo escrito: nada de horário inventado.
        val ride = LiveIntegration.toLive(live("c", "com.ubercab", "Motorista a caminho", "Carro prata"))!!
        assertNull(ride.eta)
        // Serviço silencioso em segundo plano não entra.
        assertNull(LiveIntegration.toLive(live("d", "com.exemplo.vpn", "VPN ativa", "Protegido", importance = android.app.NotificationManager.IMPORTANCE_LOW)))
        // Notificação comum (não contínua) também não.
        assertNull(LiveIntegration.toLive(live("e", "br.com.brainweb.ifood", "Promoção", "50% off").copy(ongoing = false)))
        assertEquals("7 min", LiveIntegration.extractEta("chega em 7 minutos"))
        assertNull(LiveIntegration.extractEta("pedido confirmado"))
    }

    @Test
    fun `niveis pelo espaco livre`() {
        val g = IslandGeometry()
        val music = IslandState(IslandActivity.Media(media))
        assertEquals(IslandLevel.COMPACT, IslandSizing.target(music, g, false, false, IslandSpace(available = 300.dp)).level)
        assertEquals(IslandLevel.MINIMAL, IslandSizing.target(music, g, false, false, IslandSpace(available = 150.dp)).level)
        assertEquals(IslandLevel.RING, IslandSizing.target(music, g, false, false, IslandSpace(available = 60.dp)).level)
        assertEquals(IslandLevel.HIDDEN, IslandSizing.target(music, g, false, false, IslandSpace(fullscreen = true)).level)
        val calling = IslandState(IslandActivity.Call(call))
        assertEquals("chamada continua em tela cheia", IslandLevel.RING, IslandSizing.target(calling, g, false, false, IslandSpace(fullscreen = true)).level)
        // Expandida: a cápsula fica no espaço livre e a lente desce abaixo da barra de status,
        // em qualquer espaço (nunca cobre os ícones).
        for (free in listOf(40.dp, 150.dp, 300.dp)) {
            for (bar in listOf(24.dp, 32.dp, 44.dp)) {
                val expanded = IslandSizing.target(music, g, true, false, IslandSpace(available = free, statusBarHeight = bar))
                assertEquals(IslandLevel.EXPANDED, expanded.level)
                val lens = expanded.lens!!
                assertTrue("lente abaixo da barra ($free, $bar)", g.topPadding + lens.top >= bar + IslandSizing.LENS_GAP)
                assertTrue("cápsula cabe no espaço ($free)", expanded.width <= maxOf(free, g.pillHeight))
                assertTrue(lens.width <= g.wideWidth)
            }
        }
        val alert = IslandState(IslandActivity.Alert(item("m", "Ana", "Oi! Tudo bem?")))
        assertEquals("aviso sempre vira lente", IslandLevel.EXPANDED, IslandSizing.target(alert, g, false, false, IslandSpace(available = 60.dp)).level)
        // Com pouco espaço, a segunda atividade não vira bolha.
        val two = IslandState(IslandActivity.Call(call), IslandActivity.Media(media))
        assertTrue(!IslandSizing.target(two, g, false, false, IslandSpace(available = 240.dp)).withBubble)
        assertTrue(IslandSizing.target(two, g, false, false, IslandSpace(available = null)).withBubble)
    }

    @Test
    fun `ilha 2 gota e atividades`() {
        val geometry = IslandGeometry()
        val delivery = LiveIntegration.toLive(live("a", "com.exemplo.entregas", "Saiu para entrega", "Chega entre 20:10 e 20:20", 62, 100, listOf(0, 33, 100), promoted = true, app = "App de entrega"))!!
        val ride = LiveIntegration.toLive(live("r", "com.ubercab", "Motorista a caminho", "Chega em 3 min · carro prata", app = "Uber"))!!
        val brief = BriefSuggestion(1, "Show", now + 4 * 3_600_000L, dateConfirmed = false, kind = "event", sourceText = "às 7 horas da noite")
        val scenes = listOf(
            Triple(IslandState(IslandActivity.Media(media)), false, IslandSpace(available = 300.dp)) to IslandStyle.NATIVE,
            Triple(IslandState(IslandActivity.Live(ride)), false, IslandSpace(available = 300.dp)) to IslandStyle.NATIVE,
            Triple(IslandState(IslandActivity.Live(delivery)), false, IslandSpace(available = 140.dp)) to IslandStyle.NATIVE,
            Triple(IslandState(IslandActivity.Call(call)), false, IslandSpace(available = 50.dp)) to IslandStyle.NATIVE,
            Triple(IslandState(IslandActivity.Media(media)), true, IslandSpace(available = 180.dp)) to IslandStyle.NATIVE,
            Triple(IslandState(IslandActivity.Live(delivery)), true, IslandSpace(available = 200.dp)) to IslandStyle.GLASS,
            Triple(IslandState(IslandActivity.Brief(brief)), false, IslandSpace(available = 200.dp)) to IslandStyle.NATIVE,
        )
        rule.setContent {
            Column(
                Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFE9C6F2), Color(0xFF9EC9FF), Color(0xFF3E5AA8)))),
            ) {
                for ((scene, style) in scenes) {
                    val (state, expanded, space) = scene
                    val target = IslandSizing.target(state, geometry, expanded, true, space)
                    Box(Modifier.fillMaxWidth().height(geometry.topPadding + target.totalHeight + 8.dp)) {
                        LiquidIsland(
                            state = state,
                            geometry = geometry,
                            alwaysVisible = true,
                            concealAlerts = false,
                            expanded = expanded,
                            onExpandedChange = {},
                            actions = IslandActions(),
                            space = space,
                            style = style,
                        )
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_800)
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/outputs/telas").apply { mkdirs() }
        File(dir, "50-ilha-2.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        rule.onNodeWithText("Criar lembrete").assertExists()
        rule.onNodeWithText("Live Update").assertExists()
        assertNothingClipped()
    }

    /**
     * Nada fica cortado pela curva da lente: os cantos de cada texto, botão e barra dentro de uma
     * lente expandida precisam estar dentro da forma oval.
     */
    private fun assertNothingClipped() {
        val lenses = rule.onAllNodesWithContentDescription(LENS_DESCRIPTION).fetchSemanticsNodes().map { it.boundsInRoot }
        assertTrue("há lentes na cena", lenses.size >= 3)
        val inside = SemanticsMatcher("texto ou descrição") {
            it.config.contains(SemanticsProperties.Text) ||
                (it.config.contains(SemanticsProperties.ContentDescription) && it.config[SemanticsProperties.ContentDescription].none { d -> d == LENS_DESCRIPTION || d == "Liquid Island" })
        }
        val nodes = rule.onAllNodes(inside, useUnmergedTree = true).fetchSemanticsNodes()
        var checked = 0
        val margin = 1.5f
        for (node in nodes) {
            val b = node.boundsInRoot
            if (b.width <= 0f || b.height <= 0f) continue
            val lens = lenses.firstOrNull { it.contains(b.center) } ?: continue
            val size = Size(lens.width, lens.height)
            val corners = listOf(
                Offset(b.left + margin, b.top + margin),
                Offset(b.right - margin, b.top + margin),
                Offset(b.left + margin, b.bottom - margin),
                Offset(b.right - margin, b.bottom - margin),
            )
            for (c in corners) {
                val label = node.config.getOrNull(SemanticsProperties.Text)?.joinToString() ?: node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
                assertTrue("\"$label\" sai da lente em $c", IslandLensGeometry.contains(size, 1f, c.x - lens.left, c.y - lens.top))
            }
            checked++
        }
        assertTrue("conferiu o conteúdo das lentes ($checked)", checked >= 10)
    }
}
