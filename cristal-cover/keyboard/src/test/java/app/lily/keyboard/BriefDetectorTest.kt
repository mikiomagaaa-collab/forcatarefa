package app.lily.keyboard

import app.lily.keyboard.brief.BriefDetector
import app.lily.keyboard.brief.BriefKind
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exemplos do pedido do Info Brief, mais os casos que NÃO podem virar sugestão. */
class BriefDetectorTest {
    /** Segunda-feira, 16 de novembro de 2026, 10:00. */
    private val now = LocalDateTime.of(2026, 11, 16, 10, 0)
    private val today = now.toLocalDate()

    private fun detect(text: String) = BriefDetector.detect(text, now)

    @Test
    fun `show as 7 horas da noite`() {
        val text = "Vou ir ao show às 7 horas da noite."
        val c = assertNotNull(detect(text)).let { detect(text)!! }
        assertEquals(BriefKind.EVENT, c.kind)
        assertEquals("Show", c.title)
        assertEquals(today.atTime(19, 0), c.at)
        assertTrue("o horário foi dito", c.timeConfirmed)
        assertFalse("a data precisa ser confirmada", c.dateConfirmed)
        assertEquals("às 7 horas da noite", c.expression)
        assertEquals("às 7 horas da noite", text.substring(c.start, c.end))
    }

    @Test
    fun `lugar com data`() {
        val c = detect("Preciso passar no shopping amanhã.")!!
        assertEquals(BriefKind.PLACE, c.kind)
        assertEquals("Passar no shopping", c.title)
        assertEquals("shopping", c.place)
        assertEquals(today.plusDays(1), c.at!!.toLocalDate())
        assertTrue(c.dateConfirmed)
        assertFalse(c.timeConfirmed)
    }

    @Test
    fun `data sem horario`() {
        val c = detect("Minha apresentação será na sexta-feira.")!!
        assertEquals(BriefKind.EVENT, c.kind)
        assertEquals("Apresentação", c.title)
        assertEquals(LocalDate.of(2026, 11, 20), c.at!!.toLocalDate())
        assertTrue(c.dateConfirmed)
        assertFalse(c.timeConfirmed)
    }

    @Test
    fun `tarefa sem data`() {
        val c = detect("Não posso esquecer de entregar o trabalho.")!!
        assertEquals(BriefKind.TASK, c.kind)
        assertEquals("Entregar o trabalho", c.title)
        assertNull(c.at)
    }

    @Test
    fun `viagem no proximo mes`() {
        val c = detect("Vou viajar no próximo mês.")!!
        assertEquals(BriefKind.TRIP, c.kind)
        assertEquals("Viagem", c.title)
        assertEquals(LocalDate.of(2026, 12, 1), c.at!!.toLocalDate())
        assertFalse(c.dateConfirmed)
    }

    @Test
    fun `aula as 8h`() {
        val c = detect("Minha aula começa às 8h.")!!
        assertEquals("Aula", c.title)
        // Já passou das 8 hoje: amanhã, com a data a confirmar.
        assertEquals(today.plusDays(1).atTime(8, 0), c.at)
        assertTrue("8h é formato de 24 horas", c.timeConfirmed)
        assertFalse(c.dateConfirmed)
    }

    @Test
    fun `rotina toda segunda`() {
        val c = detect("Tenho aula toda segunda-feira.")!!
        assertEquals(BriefKind.RECURRING, c.kind)
        assertEquals("Aula", c.title)
        assertTrue(c.weekly)
        // Hoje é segunda, mas a manhã já passou: começa na próxima.
        assertEquals(today.plusWeeks(1), c.at!!.toLocalDate())
    }

    @Test
    fun `data e hora juntas com companhia`() {
        val text = "Amanhã às 19h jantar com a Ana"
        val c = detect(text)!!
        assertEquals("Jantar com Ana", c.title)
        assertEquals(today.plusDays(1).atTime(19, 0), c.at)
        assertTrue(c.dateConfirmed && c.timeConfirmed)
        assertEquals("Amanhã às 19h", text.substring(c.start, c.end))
    }

    @Test
    fun `horario sem periodo usa o contexto`() {
        val c = detect("O jantar é às 8")!!
        assertEquals(today.atTime(20, 0), c.at)
        assertFalse("sem 'da noite' é preciso confirmar", c.timeConfirmed)
        assertEquals(today.atTime(20, 0), detect("Hoje às 8 tem show")!!.at)
    }

    @Test
    fun `ingles`() {
        val c = detect("Meeting tomorrow at 3pm")!!
        assertEquals("Meeting", c.title)
        assertEquals(today.plusDays(1).atTime(15, 0), c.at)
        assertEquals(BriefKind.TASK, detect("Don't forget to call the bank.")!!.kind)
    }

    @Test
    fun `lugar com intencao`() {
        val c = detect("Vou passar na farmácia amanhã")!!
        assertEquals(BriefKind.PLACE, c.kind)
        assertEquals("farmácia", c.place)
    }

    @Test
    fun `nao sugere o que nao e compromisso`() {
        val quiet = listOf(
            "Se eu for ao show às 7, te aviso.",
            "Talvez a gente vá ao cinema amanhã.",
            "Vamos ao cinema às 8?",
            "Fui ao show ontem às 7 da noite.",
            "Não vou ao show amanhã.",
            "Comprei as 3 camisas.",
            "Vou levar as 3 caixas.",
            "Preciso dormir.",
            "Somos 2 pessoas.",
            "O show foi incrível!",
            "kkkkk",
            "",
        )
        for (text in quiet) assertNull("não deveria sugerir: \"$text\"", detect(text))
    }

    @Test
    fun `reagendado continua valendo`() {
        val c = detect("A reunião foi remarcada para sexta às 14h.")!!
        assertEquals("Reunião", c.title)
        assertEquals(LocalDate.of(2026, 11, 20).atTime(14, 0), c.at)
    }

    @Test
    fun `so a ultima frase e analisada`() {
        val text = "Ontem foi corrido. Vou ao show às 7 da noite"
        val c = detect(text)!!
        assertEquals("Show", c.title)
        assertEquals("às 7 da noite", text.substring(c.start, c.end))
    }

    @Test
    fun `tipos desligados nao aparecem`() {
        assertNull(BriefDetector.detect("Não posso esquecer de entregar o trabalho.", now, setOf(BriefKind.EVENT)))
        assertNull(BriefDetector.detect("Vou ao show às 7 da noite", now, emptySet()))
    }

    @Test
    fun `frase pela metade espera`() {
        assertFalse(BriefDetector.looksFinished("Vou ao show às 7 horas da"))
        assertFalse(BriefDetector.looksFinished("Vou ao show às"))
        assertTrue(BriefDetector.looksFinished("Vou ao show às 7 horas da noite"))
    }
}
