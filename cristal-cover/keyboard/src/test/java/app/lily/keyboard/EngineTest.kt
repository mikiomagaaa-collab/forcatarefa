package app.lily.keyboard

import app.lily.keyboard.clipboard.ClipKind
import app.lily.keyboard.clipboard.ClipboardHistory
import app.lily.keyboard.core.CorrectionStyle
import app.lily.keyboard.core.KeyLayouts
import app.lily.keyboard.core.KeyboardLanguage
import app.lily.keyboard.core.LayoutId
import app.lily.keyboard.core.LayoutOptions
import app.lily.keyboard.engine.Corrector
import app.lily.keyboard.engine.Dictionary
import app.lily.keyboard.engine.GestureDecoder
import app.lily.keyboard.engine.TracePoint
import app.lily.keyboard.engine.UserDictionary
import app.lily.keyboard.ui.KeyGeometry
import java.io.File
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Testes do motor com os dicionários reais que vão no APK. */
class EngineTest {
    companion object {
        val pt: Dictionary by lazy { File("src/main/assets/pt_br.dict").inputStream().use { Dictionary.parse(it) } }
        val en: Dictionary by lazy { File("src/main/assets/en.dict").inputStream().use { Dictionary.parse(it) } }
    }

    private val user = UserDictionary(null)
    private val corrector = Corrector({ if (it == KeyboardLanguage.PT_BR) pt else en }, user)

    private fun auto(word: String, prev: String? = null, style: CorrectionStyle = CorrectionStyle.NATURAL) =
        corrector.suggest(word, prev, KeyboardLanguage.PT_BR, style, allowAutoCorrect = true).autoCorrection

    @Test
    fun `dicionarios carregam inteiros`() {
        assertTrue(pt.size > 80_000)
        assertTrue(en.size > 50_000)
        assertTrue(pt.contains("você"))
        assertTrue(pt.rank("não")!! < pt.rank("nao")!!)
    }

    @Test
    fun `acentos esquecidos sao corrigidos`() {
        assertEquals("não", auto("nao"))
        assertEquals("você", auto("voce"))
        assertEquals("também", auto("tambem"))
        assertEquals("Você", auto("Voce"))
        assertEquals("NÃO", auto("NAO"))
    }

    @Test
    fun `erros de digitacao com teclas vizinhas`() {
        assertEquals("obrigado", auto("obrigadp"))
        assertEquals("amanhã", auto("amanha"))
        assertEquals("quando", auto("qiando"))
        assertEquals("porque", auto("porqur"))
    }

    @Test
    fun `na duvida nao troca sozinho`() {
        // "esta" e "está" existem e são comuns: só sugere.
        assertNull(auto("esta"))
        val r = corrector.suggest("esta", null, KeyboardLanguage.PT_BR, CorrectionStyle.NATURAL, true)
        assertTrue(r.suggestions.any { it.text == "está" })
        // Palavras certas ficam como estão.
        assertNull(auto("casa"))
        assertNull(auto("e"))
    }

    @Test
    fun `contexto decide esta e e`() {
        assertEquals("está", auto("esta", prev = "não"))
        assertEquals("está", auto("esta", prev = "ele"))
        val r = corrector.suggest("e", "ele", KeyboardLanguage.PT_BR, CorrectionStyle.NATURAL, true)
        assertTrue(r.suggestions.any { it.text == "é" })
    }

    @Test
    fun `estilos respeitam abreviacoes`() {
        assertNull(auto("vc", style = CorrectionStyle.INFORMAL))
        assertNull(auto("tbm", style = CorrectionStyle.NATURAL))
        assertNull(auto("kkkkk", style = CorrectionStyle.NATURAL))
        assertEquals("você", auto("vc", style = CorrectionStyle.FORMAL))
        assertEquals("também", auto("tbm", style = CorrectionStyle.FORMAL))
        // "pq" é ambíguo (porque / por que): nem o formal troca.
        assertNull(auto("pq", style = CorrectionStyle.FORMAL))
        // O natural sugere a forma completa sem trocar.
        val r = corrector.suggest("vc", null, KeyboardLanguage.PT_BR, CorrectionStyle.NATURAL, true)
        assertTrue(r.suggestions.any { it.text == "você" })
        // No informal, acento não é trocado sozinho.
        assertNull(auto("nao", style = CorrectionStyle.INFORMAL))
    }

    @Test
    fun `palavra aprendida nao e corrigida`() {
        user.learn("lilyzinha")
        assertFalse(user.isKnown("lilyzinha"))
        user.learn("lilyzinha")
        assertTrue(user.isKnown("lilyzinha"))
        assertNull(auto("lilyzinha"))
    }

    @Test
    fun `completa palavras e repeticao`() {
        val r = corrector.suggest("obriga", null, KeyboardLanguage.PT_BR, CorrectionStyle.NATURAL, true)
        assertTrue(r.suggestions.map { it.text }.toString(), r.suggestions.any { it.text == "obrigado" })
        assertTrue(corrector.suggest("de", "de", KeyboardLanguage.PT_BR, CorrectionStyle.NATURAL, true).repeated)
        assertFalse(corrector.suggest("que", "que", KeyboardLanguage.PT_BR, CorrectionStyle.NATURAL, true).repeated)
    }

    @Test
    fun `ingles tambem corrige`() {
        val r = corrector.suggest("teh", null, KeyboardLanguage.EN, CorrectionStyle.NATURAL, true)
        assertEquals("the", r.autoCorrection)
        assertNull(corrector.suggest("lol", null, KeyboardLanguage.EN, CorrectionStyle.NATURAL, true).autoCorrection)
    }

    @Test
    fun `sugestoes sao rapidas`() {
        // Aquece e mede: o corretor roda a cada letra, precisa ser bem rápido.
        repeat(5) { auto("obrigadp") }
        val words = listOf("obrigadp", "amanha", "qiando", "voce", "esgta", "trabalhp", "conseguo", "pessial")
        val start = System.nanoTime()
        repeat(10) { for (w in words) auto(w) }
        val perWord = (System.nanoTime() - start) / 1_000_000.0 / (10 * words.size)
        assertTrue("cada palavra levou $perWord ms", perWord < 25.0)
    }

    // --- Deslize ----------------------------------------------------------------------------------

    private val geometry = KeyGeometry.build(
        layout = KeyLayouts.build(LayoutId.LETTERS, LayoutOptions()),
        width = 1080f,
        rowHeight = 140f,
        gapX = 12f,
        gapY = 20f,
        sidePadding = 8f,
    )

    /** Traço "humano": passa pelos centros das letras com um pouco de tremor e pontos no meio. */
    private fun trace(word: String, seed: Int = 1): List<TracePoint> {
        val centers = geometry.letterCenters()
        val rnd = Random(seed)
        val keys = word.map { centers[it]!! }
        val out = ArrayList<TracePoint>()
        for (i in keys.indices) {
            val k = keys[i]
            if (i > 0) {
                val p = keys[i - 1]
                for (s in 1..6) {
                    val t = s / 7f
                    out.add(TracePoint(p.x + (k.x - p.x) * t + rnd.nextFloat() * 14 - 7, p.y + (k.y - p.y) * t + rnd.nextFloat() * 14 - 7))
                }
            }
            out.add(TracePoint(k.x + rnd.nextFloat() * 30 - 15, k.y + rnd.nextFloat() * 30 - 15))
        }
        return out
    }

    @Test
    fun `deslize reconhece palavras comuns`() {
        val decoder = GestureDecoder(geometry.letterCenters(), geometry.keyWidth)
        val words = listOf("obrigado", "casa", "trabalho", "amanha", "voce", "quando", "hoje", "bom", "gente", "muito", "nao", "tudo")
        val misses = ArrayList<String>()
        for ((i, w) in words.withIndex()) {
            val result = decoder.decode(trace(w, i), pt)
            val folded = result.map { app.lily.keyboard.engine.TextTools.fold(it) }
            if (folded.firstOrNull() != w) misses.add("$w → $result")
        }
        // Pode errar uma ou outra palavra curta; a grande maioria tem que acertar de primeira.
        assertTrue("erros: $misses", misses.size <= 2)
    }

    @Test
    fun `deslize prefere a forma com acento`() {
        val decoder = GestureDecoder(geometry.letterCenters(), geometry.keyWidth)
        assertEquals("não", decoder.decode(trace("nao", 7), pt).first())
        assertEquals("você", decoder.decode(trace("voce", 3), pt).first())
    }

    // --- Área de transferência --------------------------------------------------------------------

    @Test
    fun `area de transferencia descarta o que e sensivel`() {
        var now = 1_000_000L
        val history = ClipboardHistory(null) { now }
        assertFalse(history.add("482913"))
        assertFalse(history.add("123 456"))
        assertFalse(history.add("S3nh@Forte!2024"))
        assertFalse(history.add("ghp_A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6Q7r8"))
        assertTrue(history.add("Reunião amanhã às 15h na sala 2"))
        assertTrue(history.add("https://exemplo.com.br/aula"))
        assertEquals(ClipKind.LINK, history.items.value.first().kind)
        assertEquals(ClipKind.EMAIL, ClipboardHistory.classify("aluno@escola.com"))
        assertEquals(ClipKind.PHONE, ClipboardHistory.classify("+55 (11) 98888-7777"))
        // Fixado fica; o resto vence em 1 hora.
        history.togglePin(history.items.value.last().id)
        now += ClipboardHistory.EXPIRY_MS + 1
        history.prune()
        assertEquals(listOf("Reunião amanhã às 15h na sala 2"), history.items.value.map { it.text })
    }
}
