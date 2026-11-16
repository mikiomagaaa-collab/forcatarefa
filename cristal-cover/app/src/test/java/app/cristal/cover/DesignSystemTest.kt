package app.cristal.cover

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.mutablePreferencesOf
import app.cristal.cover.data.prefs.GlassQuality
import app.cristal.cover.data.prefs.LiquidThemeId
import app.cristal.cover.data.prefs.PrefKeys
import app.cristal.cover.data.prefs.Settings
import app.cristal.cover.data.prefs.toSettings
import app.cristal.cover.ui.design.LiquidMaterial
import app.cristal.cover.ui.design.LiquidMaterials
import app.cristal.cover.ui.design.LiquidMotion
import app.cristal.cover.ui.design.LiquidRadius
import app.cristal.cover.ui.design.LiquidThemes
import app.cristal.cover.ui.design.MaterialTuning
import app.cristal.cover.ui.design.interTracking
import app.cristal.cover.ui.design.liquidThemeState
import app.cristal.cover.ui.glass.GlassSpec
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignSystemTest {

    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }

    @Test
    fun `espacamento da Inter aperta nos tamanhos grandes`() {
        assertEquals(-0.0128f, interTracking(17f), 0.001f)
        assertTrue(interTracking(11f) > 0f)
        assertTrue(interTracking(34f) < interTracking(22f))
        assertTrue(interTracking(22f) < interTracking(13f))
    }

    @Test
    fun `molas convertem tempo de resposta em rigidez`() {
        assertEquals(438.6f, LiquidMotion.Snappy.stiffness, 0.5f)
        assertEquals(157.9f, LiquidMotion.Bouncy.stiffness, 0.5f)
        // Quanto mais lenta a resposta, menor a rigidez.
        val ordered = listOf(LiquidMotion.Snappy, LiquidMotion.Smooth, LiquidMotion.Bouncy, LiquidMotion.Gentle).map { it.stiffness }
        assertEquals(ordered.sortedDescending(), ordered)
    }

    @Test
    fun `cantos concentricos`() {
        assertEquals(32.dp, LiquidRadius.concentric(LiquidRadius.screen, 4.dp))
        assertEquals(0.dp, LiquidRadius.concentric(10.dp, 14.dp))
        assertEquals(13.8f, LiquidRadius.icon(60.dp).value, 0.01f)
    }

    @Test
    fun `os sete temas sao legiveis`() {
        for (id in LiquidThemeId.entries) {
            val c = LiquidThemes.resolve(id)
            assertTrue("$id: texto sobre o fundo", contrast(c.content, c.background) >= 7f)
            assertTrue("$id: texto secundário sobre a superfície", contrast(c.contentSecondary, c.surface) >= 4.5f)
            assertTrue("$id: texto sobre o destaque", contrast(c.onAccent, c.accent) >= 3f)
            assertEquals("$id: claro/escuro coerente com o fundo", c.dark, c.background.luminance() < 0.2f)
        }
    }

    @Test
    fun `tema personalizado parte de outro tema e troca o destaque`() {
        val green = Color(0xFF30D158)
        val c = LiquidThemes.resolve(LiquidThemeId.CUSTOM, LiquidThemeId.LAVENDER, green)
        assertEquals(LiquidThemes.Lavender.background, c.background)
        assertEquals(green, c.accent)
        for (accent in LiquidThemes.customAccents) {
            assertTrue("destaque $accent", contrast(LiquidThemes.onColorFor(accent), accent) >= 3f)
        }
        // Sem cor escolhida, fica igual ao tema de partida.
        assertEquals(LiquidThemes.OneUi, LiquidThemes.resolve(LiquidThemeId.CUSTOM, LiquidThemeId.ONE_UI, null))
    }

    @Test
    fun `materiais por funcao`() {
        val base = GlassSpec(quality = GlassQuality.LIQUID, intensity = 0.45f, dark = true)
        val colors = LiquidThemes.LiquidDark
        val tuning = MaterialTuning()
        val solid = LiquidMaterials.resolve(LiquidMaterial.SOLID, base, colors, tuning)
        assertNull(solid.glass)
        assertEquals(colors.surface, solid.solid)
        assertEquals(GlassQuality.LIQUID, LiquidMaterials.resolve(LiquidMaterial.LIQUID, base, colors, tuning).glass?.quality)
        // Painéis não usam refração: só desfoque.
        assertEquals(GlassQuality.BLUR, LiquidMaterials.resolve(LiquidMaterial.FROSTED, base, colors, tuning).glass?.quality)
        assertEquals(GlassQuality.BLUR, LiquidMaterials.resolve(LiquidMaterial.DARK, base, colors, tuning).glass?.quality)
        assertEquals(Color.White, LiquidMaterials.resolve(LiquidMaterial.DARK, base, colors, tuning).content)
        // O ajuste deixa a película mais forte.
        val weak = LiquidMaterials.resolve(LiquidMaterial.FROSTED, base, colors, tuning.copy(frosted = 0f)).film.alpha
        val strong = LiquidMaterials.resolve(LiquidMaterial.FROSTED, base, colors, tuning.copy(frosted = 1f)).film.alpha
        assertTrue(strong > weak)
        // Com o vidro no modo leve, nenhum material pede mais do que isso.
        val light = base.copy(quality = GlassQuality.LIGHT)
        for (m in LiquidMaterial.entries) {
            val q = LiquidMaterials.resolve(m, light, colors, tuning).glass?.quality ?: continue
            assertEquals(GlassQuality.LIGHT, q)
        }
    }

    @Test
    fun `configuracoes do design system sao lidas e limitadas`() {
        val prefs = mutablePreferencesOf(
            PrefKeys.LIQUID_THEME to "LAVENDER",
            PrefKeys.MATERIAL_DARK to 2f,
            PrefKeys.LIQUID_CUSTOM_ACCENT to 0xFF30D158.toInt(),
        )
        val s = prefs.toSettings()
        assertEquals(LiquidThemeId.LAVENDER, s.liquidTheme)
        assertEquals(1f, s.materialDark)
        assertEquals(Settings().materialFrosted, s.materialFrosted)
        assertEquals(LiquidThemes.Lavender, s.liquidThemeState().colors)
        // Padrão aprovado: Liquid Dark.
        assertEquals(LiquidThemeId.LIQUID_DARK, Settings().liquidTheme)
        // Tema desconhecido (de uma versão futura) volta ao padrão.
        assertEquals(LiquidThemeId.LIQUID_DARK, mutablePreferencesOf(PrefKeys.LIQUID_THEME to "NEON").toSettings().liquidTheme)
    }
}
