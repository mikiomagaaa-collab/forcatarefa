package app.cristal.cover

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.cristal.cover.data.collections.AppearanceKeys
import app.cristal.cover.data.home.WidgetKind
import app.cristal.cover.data.media.MediaState
import app.cristal.cover.data.prefs.ClockFill
import app.cristal.cover.data.prefs.ClockFont
import app.cristal.cover.data.prefs.ClockLayout
import app.cristal.cover.data.prefs.Settings
import app.cristal.cover.data.prefs.WidgetDensity
import app.cristal.cover.data.prefs.WidgetLook
import app.cristal.cover.data.weather.WeatherState
import app.cristal.cover.ui.components.AdaptiveClock
import app.cristal.cover.ui.components.ClockFillSpec
import app.cristal.cover.ui.components.WallpaperLayer
import app.cristal.cover.ui.glass.LocalGlassBackdrop
import app.cristal.cover.ui.glass.glassBackdropSource
import app.cristal.cover.ui.glass.rememberGlassBackdrop
import app.cristal.cover.ui.launcher.WidgetPicker
import app.cristal.cover.ui.lock.ClockColors
import app.cristal.cover.ui.lock.ClockDraft
import app.cristal.cover.ui.lock.EditorDraft
import app.cristal.cover.ui.lock.clockLook
import app.cristal.cover.ui.theme.CristalTheme
import app.cristal.cover.ui.widgets.LocalWidgetScreen
import app.cristal.cover.ui.widgets.LocalWidgetStyle
import app.cristal.cover.ui.widgets.WidgetActions
import app.cristal.cover.ui.widgets.WidgetCard
import app.cristal.cover.ui.widgets.WidgetColors
import app.cristal.cover.ui.widgets.WidgetData
import app.cristal.cover.ui.widgets.WidgetSamples
import app.cristal.cover.ui.widgets.WidgetScreen
import app.cristal.cover.ui.widgets.WidgetSize
import app.cristal.cover.ui.widgets.WidgetStyle
import app.cristal.cover.ui.widgets.lilySuggestions
import app.cristal.cover.ui.widgets.widgetStyle
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * O redesign dos widgets (um sistema visual para todos, tamanhos que reorganizam o conteúdo, cinco
 * aparências, tela externa, galeria com prévias reais) e o relógio colorido do bloqueio.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h1004dp-420dpi")
class WidgetsRedesignTest {
    @get:Rule
    val rule = createComposeRule()

    private val data = WidgetSamples.data().copy(
        media = MediaState("app.musica", "Noite Clara", "Banda Aurora", null, isPlaying = true, canSkipNext = true, canSkipPrevious = true, position = 70_000, duration = 200_000, tint = 0xFF6C4BD6.toInt()),
    )

    // --- Lógica ---------------------------------------------------------------------------------

    @Test
    fun `cada tamanho vem do espaco real`() {
        assertEquals(WidgetSize.STRIP, WidgetSize.of(360.dp, 84.dp))
        assertEquals(WidgetSize.SMALL, WidgetSize.of(176.dp, 176.dp))
        assertEquals(WidgetSize.MEDIUM, WidgetSize.of(360.dp, 176.dp))
        assertEquals(WidgetSize.LARGE, WidgetSize.of(360.dp, 360.dp))
        assertEquals(WidgetSize.LARGE, WidgetSize.of(176.dp, 360.dp))
        assertEquals(WidgetSize.EXTRA_LARGE, WidgetSize.of(360.dp, 460.dp))
    }

    @Test
    fun `paletas com contraste e da mesma familia`() {
        val accent = Color(0xFF9F86FF)
        val light = WidgetColors.palette(WidgetStyle(look = WidgetLook.LIGHT), WidgetKind.CLOCK, false, accent)
        assertTrue("texto escuro no claro", light.primary.luminance() < 0.1f)
        val dark = WidgetColors.palette(WidgetStyle(look = WidgetLook.DARK), WidgetKind.CLOCK, true, accent)
        assertTrue("texto claro no escuro", dark.primary.luminance() > 0.9f)
        val colorful = WidgetKind.entries.filter { it.builtIn }.map { WidgetColors.hue(it, accent) }.toSet()
        assertTrue("cada tipo com a sua cor no colorido", colorful.size >= 8)
        val mono = WidgetColors.palette(WidgetStyle(look = WidgetLook.MONO), WidgetKind.WEATHER, true, accent)
        assertEquals("monocromático sem cor de destaque", mono.primary, mono.accent)
        // Transparência: mais transparente = película mais leve.
        val opaque = WidgetColors.palette(WidgetStyle(look = WidgetLook.DARK, transparency = 0f), WidgetKind.CLOCK, true, accent)
        val clear = WidgetColors.palette(WidgetStyle(look = WidgetLook.DARK, transparency = 1f), WidgetKind.CLOCK, true, accent)
        assertNotEquals(opaque.fill, clear.fill)
    }

    @Test
    fun `sugestoes da lily vem do momento`() {
        val now = System.currentTimeMillis()
        val rainy = data.copy(weather = WeatherState(data.weather.status, data.weather.data!!.copy(code = 63)), battery = data.battery.copy(percent = 12, charging = false))
        val s = lilySuggestions(rainy, now)
        assertTrue(s.first().startsWith("Preparar para Reunião do projeto"))
        assertTrue(s.contains("Vai chover: o que levar?"))
        assertTrue(s.contains("Como economizar bateria?"))
        assertTrue(s.contains("Sobre esta música"))
        assertEquals(s.size, s.distinct().size)
    }

    @Test
    fun `aparencia dos widgets e relogio colorido vao nas colecoes`() {
        listOf("widget_style_look", "widget_style_transparency", "widget_style_blur", "widget_style_accent", "widget_style_corner", "widget_style_density",
            "lock_clock_fill", "lock_clock_color2", "lock_clock_fill_opacity", "lock_clock_split", "lock_clock_harmony", "cover_clock_fill").forEach {
            assertTrue(it, AppearanceKeys.belongs(it))
        }
        val s = Settings(widgetLook = WidgetLook.COLORFUL, widgetAccent = 0xFF30D158.toInt(), widgetDensity = WidgetDensity.COMPACT)
        val style = s.widgetStyle()
        assertEquals(WidgetLook.COLORFUL, style.look)
        assertEquals(Color(0xFF30D158), style.accent)
        assertEquals(WidgetDensity.COMPACT, style.density)
    }

    @Test
    fun `relogio colorido - modos, original preservado e rascunho do editor`() {
        var made: app.cristal.cover.ui.glass.GlassBackdrop? = null
        rule.setContent { made = rememberGlassBackdrop() }
        rule.waitForIdle()
        val backdrop = made!!
        val original = clockLook(ClockFill.ORIGINAL, glassOn = true, glassOpacity = 0.3f, color = 0, color2 = 0, opacity = 1f, split = false, harmony = true, backdrop = backdrop)
        assertNotNull("o original continua de vidro", original.glass)
        assertNull(original.fill)
        assertNull(original.accent)
        val solid = clockLook(ClockFill.SOLID, true, 0.3f, 0xFFFF2D55.toInt(), 0, 0.9f, false, true, backdrop)
        assertNull(solid.glass)
        assertEquals(ClockFill.SOLID, solid.fill!!.mode)
        assertEquals(Color(0xFFFF2D55), solid.accent)
        assertEquals("harmonia clareia a cor na data", ClockColors.harmony(Color(0xFFFF2D55)), solid.companion(Color.White))
        val liquid = clockLook(ClockFill.LIQUID, false, 0.3f, 0xFF0A84FF.toInt(), 0, 0.8f, false, false, backdrop)
        assertEquals(Color(0xFF0A84FF), liquid.glass!!.tint)
        assertEquals("sem harmonia, a data fica como estava", Color.White, liquid.companion(Color.White))
        val auto = ClockColors.second(0, Color(0xFFFF2D55))
        assertNotEquals("segunda cor automática combina, mas é outra", Color(0xFFFF2D55), auto)

        val s = Settings(lockClockFill = ClockFill.GRADIENT, lockClockColor = 0xFFFF2D55.toInt(), lockClockColor2 = 0xFF5E5CE6.toInt(), lockClockSplit = true, lockClockFillOpacity = 0.7f)
        val draft = EditorDraft.from(s)
        assertEquals(ClockFill.GRADIENT, draft.inner.fill)
        val back = draft.with(false, draft.inner.copy(harmony = false)).applyTo(Settings())
        assertEquals(ClockFill.GRADIENT, back.lockClockFill)
        assertEquals(0xFF5E5CE6.toInt(), back.lockClockColor2)
        assertEquals(0.7f, back.lockClockFillOpacity, 0.001f)
        assertTrue(back.lockClockSplit)
        assertTrue(!back.lockClockHarmony)
        assertEquals(ClockFill.ORIGINAL, ClockDraft(0f, 0f, 0f, 1f, ClockFont.LIQUID, 0, true).fill)
    }

    @Test
    fun `horas e minutos com cores proprias, conferido nos pixels`() {
        rule.setContent {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AdaptiveClock(
                    time = "12:45",
                    font = ClockFont.LIQUID,
                    layout = ClockLayout.LINE,
                    adaptive = true,
                    shadow = null,
                    fill = ClockFillSpec(ClockFill.SOLID, Color(0xFFFF0000), Color(0xFF0000FF), 1f, split = true),
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                )
            }
        }
        rule.waitForIdle()
        val b = rule.onRoot().captureToImage().asAndroidBitmap()
        var redLeft = 0
        var blueRight = 0
        var wrong = 0
        for (y in 0 until (b.height * 220f / 1004f).toInt() step 6) {
            for (x in 0 until b.width step 3) {
                val p = b.getPixel(x, y)
                val r = p shr 16 and 0xFF
                val bl = p and 0xFF
                if (r < 140 && bl < 140) continue
                if (x < b.width * 0.42f) if (r > bl) redLeft++ else wrong++
                if (x > b.width * 0.58f) if (bl > r) blueRight++ else wrong++
            }
        }
        assertTrue("horas em vermelho ($redLeft)", redLeft > 30)
        assertTrue("minutos em azul ($blueRight)", blueRight > 30)
        assertTrue("sem cor trocada ($wrong)", wrong < (redLeft + blueRight) / 20)
    }

    // --- Telas ----------------------------------------------------------------------------------

    @Composable
    private fun Scene(settings: Settings, screen: WidgetScreen = WidgetScreen.INNER, content: @Composable ColumnScope.() -> Unit) {
        CristalTheme(settings = settings) {
            val backdrop = rememberGlassBackdrop()
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                WallpaperLayer(image = null, presetIndex = 2, dim = 0.08f, modifier = Modifier.glassBackdropSource(backdrop))
                CompositionLocalProvider(
                    LocalGlassBackdrop provides backdrop,
                    LocalWidgetStyle provides settings.widgetStyle(),
                    LocalWidgetScreen provides screen,
                ) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
                }
            }
        }
    }

    @Composable
    private fun HomeSet() {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WidgetCard(WidgetKind.CLOCK, data, WidgetActions(), Modifier.weight(1f).height(172.dp))
            WidgetCard(WidgetKind.WEATHER, data, WidgetActions(), Modifier.weight(1f).height(172.dp))
        }
        WidgetCard(WidgetKind.AGENDA, data, WidgetActions(), Modifier.fillMaxWidth().height(172.dp))
        WidgetCard(WidgetKind.MUSIC, data, WidgetActions(), Modifier.fillMaxWidth().height(150.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WidgetCard(WidgetKind.BATTERY, data, WidgetActions(), Modifier.weight(1f).height(172.dp))
            WidgetCard(WidgetKind.BRIEF, data, WidgetActions(), Modifier.weight(1f).height(172.dp))
        }
        WidgetCard(WidgetKind.LILY, data, WidgetActions(), Modifier.fillMaxWidth().height(84.dp))
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/outputs/telas").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun `widgets liquid glass na tela interna`() {
        rule.setContent { Scene(Settings()) { HomeSet() } }
        shot("60-widgets-liquid-glass")
    }

    @Test
    fun `widgets claros`() {
        rule.setContent { Scene(Settings(widgetLook = WidgetLook.LIGHT, widgetTransparency = 0.3f)) { HomeSet() } }
        shot("61-widgets-claros")
    }

    @Test
    fun `widgets escuros`() {
        rule.setContent { Scene(Settings(widgetLook = WidgetLook.DARK, widgetAccent = 0xFFFF9F0A.toInt())) { HomeSet() } }
        shot("62-widgets-escuros")
    }

    @Test
    fun `widgets coloridos`() {
        rule.setContent { Scene(Settings(widgetLook = WidgetLook.COLORFUL, widgetCorner = 1.2f)) { HomeSet() } }
        shot("63-widgets-coloridos")
    }

    @Test
    fun `widgets monocromaticos e compactos`() {
        rule.setContent { Scene(Settings(widgetLook = WidgetLook.MONO, widgetDensity = WidgetDensity.COMPACT, widgetCorner = 0.7f)) { HomeSet() } }
        shot("64-widgets-monocromaticos")
    }

    @Test
    fun `tamanhos grandes reorganizam o conteudo`() {
        rule.setContent {
            Scene(Settings()) {
                WidgetCard(WidgetKind.WEATHER, data, WidgetActions(), Modifier.fillMaxWidth().height(372.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WidgetCard(WidgetKind.AGENDA, data, WidgetActions(), Modifier.weight(1f).height(372.dp))
                    WidgetCard(WidgetKind.MUSIC, data, WidgetActions(), Modifier.weight(1f).height(372.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WidgetCard(WidgetKind.ANALOG_CLOCK, data, WidgetActions(), Modifier.weight(1f).height(172.dp))
                    WidgetCard(WidgetKind.DATE, data, WidgetActions(), Modifier.weight(1f).height(172.dp))
                }
            }
        }
        shot("65-widgets-grandes")
    }

    @Test
    @Config(qualifiers = "w422dp-h426dp-420dpi")
    fun `tela externa com composicoes proprias`() {
        rule.setContent {
            Scene(Settings(), WidgetScreen.COVER) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WidgetCard(WidgetKind.CLOCK, data, WidgetActions(), Modifier.weight(1f).height(178.dp))
                    WidgetCard(WidgetKind.WEATHER, data, WidgetActions(), Modifier.weight(1f).height(178.dp))
                }
                WidgetCard(WidgetKind.SHORTCUTS, data, WidgetActions(), Modifier.fillMaxWidth().height(86.dp))
                WidgetCard(WidgetKind.LILY, data, WidgetActions(), Modifier.fillMaxWidth().height(86.dp))
            }
        }
        shot("66-widgets-tela-externa")
    }

    @Test
    fun `galeria com previas reais`() {
        rule.setContent {
            Scene(Settings()) {
                WidgetPicker(onPick = {}, onDismiss = {}, previewData = data)
            }
        }
        shot("67-galeria-de-widgets")
    }

    @Test
    fun `estados vazios e sem permissao`() {
        val empty = WidgetData()
        rule.setContent {
            Scene(Settings()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WidgetCard(WidgetKind.WEATHER, empty, WidgetActions(), Modifier.weight(1f).height(172.dp))
                    WidgetCard(WidgetKind.AGENDA, empty, WidgetActions(), Modifier.weight(1f).height(172.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WidgetCard(WidgetKind.MUSIC, empty, WidgetActions(), Modifier.weight(1f).height(172.dp))
                    WidgetCard(WidgetKind.BRIEF, empty, WidgetActions(), Modifier.weight(1f).height(172.dp))
                }
                WidgetCard(WidgetKind.LILY, empty, WidgetActions(), Modifier.fillMaxWidth().height(84.dp))
                WidgetCard(WidgetKind.SEARCH, empty, WidgetActions(), Modifier.fillMaxWidth().height(64.dp))
            }
        }
        shot("68-widgets-estados-vazios")
    }

    @Test
    fun `relogio colorido no bloqueio`() {
        val modes = listOf(
            ClockFillSpec(ClockFill.SOLID, Color(0xFFFF375F), Color(0xFFFF375F), 0.95f, false),
            ClockFillSpec(ClockFill.GRADIENT, Color(0xFFFF9F0A), Color(0xFFFF375F), 0.95f, false),
            ClockFillSpec(ClockFill.TRANSLUCENT, Color(0xFF64D2FF), Color(0xFF64D2FF), 0.8f, false),
            ClockFillSpec(ClockFill.SOLID, Color(0xFF30D158), Color(0xFF5E5CE6), 0.95f, true),
        )
        rule.setContent {
            Scene(Settings()) {
                modes.forEach { fill ->
                    AdaptiveClock(
                        time = "09:41",
                        font = ClockFont.LIQUID,
                        layout = ClockLayout.LINE,
                        adaptive = true,
                        fill = fill,
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                    )
                }
            }
        }
        shot("57-relogio-colorido")
    }
}
