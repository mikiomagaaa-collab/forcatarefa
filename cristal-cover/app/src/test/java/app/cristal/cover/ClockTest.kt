package app.cristal.cover

import app.cristal.cover.data.prefs.ClockFont
import app.cristal.cover.data.prefs.ClockLayout
import app.cristal.cover.data.prefs.CoverClockStyle
import app.cristal.cover.ui.components.ClockFitting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockTest {
    @Test
    fun `forma automatica prefere uma linha esticada`() {
        // Caixa alta, mas a fonte estica até preencher: continua em uma linha (relógio "escorrido").
        val tallFill = ClockFitting.fit(200f, 100f, 400f, 500f, maxStretch = 3.4f, stacked = false)
        assertFalse(ClockFitting.stacked(ClockLayout.AUTO, tallFill, 100f, 400f, 500f))
        // A fonte não estica o bastante: sobra muita altura, então empilha.
        val shortFill = ClockFitting.fit(200f, 100f, 400f, 500f, maxStretch = 1.3f, stacked = false)
        assertTrue(ClockFitting.stacked(ClockLayout.AUTO, shortFill, 100f, 400f, 500f))
        assertTrue(ClockFitting.stacked(ClockLayout.STACKED, shortFill, 100f, 400f, 100f))
        assertFalse(ClockFitting.stacked(ClockLayout.LINE, shortFill, 100f, 100f, 400f))
    }

    @Test
    fun `cresce ate a largura e estica com a altura que sobra`() {
        // Texto de 200×100 numa caixa de 400×300: a largura limita (×1,92) e sobra altura.
        val fit = ClockFitting.fit(200f, 100f, 400f, 300f, maxStretch = 1.6f, stacked = false)
        assertEquals(1.92f, fit.scale, 0.01f)
        assertEquals(1.56f, fit.stretch, 0.01f)
        // A esticada respeita o limite da fonte.
        assertEquals(1.2f, ClockFitting.fit(200f, 100f, 400f, 600f, 1.2f, false).stretch, 0.001f)
        // Caixa baixa: a altura limita e não estica.
        val low = ClockFitting.fit(200f, 100f, 400f, 80f, 1.6f, false)
        assertEquals(0.8f, low.scale, 0.001f)
        assertEquals(1f, low.stretch, 0.001f)
    }

    @Test
    fun `estilos antigos do cover clock viram fontes`() {
        assertEquals(ClockFont.LIQUID, ClockFont.fromCoverStyle(CoverClockStyle.LIQUID))
        assertEquals(ClockFont.THIN, ClockFont.fromCoverStyle(CoverClockStyle.MINIMAL))
        assertEquals(ClockFont.DIGITAL, ClockFont.fromCoverStyle(CoverClockStyle.DIGITAL))
        assertEquals(ClockFont.EDITORIAL, ClockFont.fromCoverStyle(CoverClockStyle.CLASSIC))
        assertTrue(ClockFont.entries.size >= 14)
        assertTrue(ClockFont.LIQUID.maxStretch >= 3f)
        assertTrue(ClockFont.TALL.maxStretch > ClockFont.SCRIPT.maxStretch)
    }
}
