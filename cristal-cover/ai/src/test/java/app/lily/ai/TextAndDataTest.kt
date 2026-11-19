package app.lily.ai

import app.lily.ai.core.TaskKind
import app.lily.ai.data.ChatMessage
import app.lily.ai.data.ConversationStore
import app.lily.ai.data.MemoryStore
import app.lily.ai.data.PerfStore
import app.lily.ai.data.Role
import app.lily.ai.data.Sensitive
import app.lily.ai.engine.AudioFiles
import app.lily.ai.engine.Prompts
import app.lily.ai.engine.StreamText
import app.lily.ai.engine.WritingStyle
import app.lily.ai.ui.screens.Markdown
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextAndDataTest {
    @Test
    fun `resposta em pedacos ou acumulada da o mesmo texto e esconde o raciocinio`() {
        val deltas = StreamText()
        listOf("<think>pensando", " muito</think>", "Olá", ", tudo", " bem?").forEach { deltas.push(it) }
        assertEquals("Olá, tudo bem?", deltas.push(""))
        val acc = StreamText()
        listOf("Olá", "Olá, tudo", "Olá, tudo bem?").forEach { acc.push(it) }
        assertEquals("Olá, tudo bem?", acc.push(""))
        assertEquals("", StreamText.visible("<think>ainda pensando"))
    }

    @Test
    fun `modo informal nao formaliza abreviacoes`() {
        val informal = Prompts.tool(TaskKind.CORRECT, "vc viu q a prova mudou?", WritingStyle.INFORMAL)
        assertTrue(informal.contains("vc, pq, tbm, nn"))
        assertTrue(informal.contains("NÃO troque"))
        val formal = Prompts.tool(TaskKind.CORRECT, "vc viu?", WritingStyle.FORMAL)
        assertFalse(formal.contains("NÃO troque"))
        assertTrue(Prompts.system(listOf("Prefiro respostas curtas")).contains("Prefiro respostas curtas"))
        assertTrue(Prompts.system(emptyList()).contains("diga isso com honestidade"))
    }

    @Test
    fun `titulo curto a partir da primeira mensagem`() {
        assertEquals("Monta um plano de estudos pra", Prompts.titleFrom("monta um plano de estudos pra prova de cálculo"))
        assertEquals("Nova conversa", Prompts.titleFrom("   "))
    }

    @Test
    fun `memoria recusa dados sensiveis e pode ser desligada`() {
        val store = MemoryStore(File(Files.createTempDirectory("mem").toFile(), "m.json"))
        assertNull(store.add("minha senha do banco é 1234"))
        assertNull(store.add("meu CPF 123.456.789-09"))
        assertNull(store.add("cartão 4111 1111 1111 1111"))
        val m = store.add("Prefiro respostas curtas", "preferencia")!!
        store.add("Moro em Recife")
        assertEquals(2, store.active().size)
        store.toggle(m.id, false)
        assertEquals(listOf("Moro em Recife"), store.active())
        store.edit(m.id, "Prefiro respostas longas")
        assertEquals("Prefiro respostas longas", store.items.value.first { it.id == m.id }.text)
        store.clear()
        assertTrue(store.items.value.isEmpty())
        assertTrue(Sensitive.looksSensitive("o código de verificação é 482913"))
        assertFalse(Sensitive.looksSensitive("estudo engenharia elétrica"))
    }

    @Test
    fun `historico busca sem acento e apaga anexos junto`() = runBlocking {
        val dir = Files.createTempDirectory("conversas").toFile()
        val store = ConversationStore(dir)
        val meta = store.create("Ação de graças")
        val photo = File(store.attachments, "img-1.jpg").apply { writeText("x") }
        store.save(meta.id, listOf(ChatMessage(role = Role.USER, text = "Receita de pão", attachments = listOf(app.lily.ai.data.Attachment("image", photo.path)))))
        store.create("Outra conversa").also { store.save(it.id, listOf(ChatMessage(role = Role.USER, text = "nada a ver"))) }
        assertEquals(listOf(meta.id), store.search("acao").map { it.id })
        assertEquals(listOf(meta.id), store.search("PAO").map { it.id })
        store.pin(meta.id, true)
        store.rename(meta.id, "Receitas")
        val p = store.addProject("Casa", 0xFF00FF00)
        store.move(meta.id, p.id)
        val reopened = ConversationStore(dir)
        val m = reopened.meta(meta.id)!!
        assertTrue(m.pinned)
        assertEquals("Receitas", m.title)
        assertEquals(p.id, m.projectId)
        reopened.delete(meta.id)
        assertFalse(photo.exists())
        assertNull(reopened.meta(meta.id))
        reopened.deleteAll()
        assertTrue(reopened.conversations.value.isEmpty())
    }

    @Test
    fun `medicoes so contam respostas reais`() {
        val perf = PerfStore(File(Files.createTempDirectory("perf").toFile(), "p.json"))
        perf.recordAnswer("m", chars = 10, millis = 5_000)
        assertNull(perf.items.value["m"])
        perf.recordAnswer("m", chars = 400, millis = 10_000)
        assertEquals(40.0, perf.items.value["m"]!!.charsPerSecond, 0.01)
        perf.recordLoad("m", "GPU", 2_300)
        assertEquals(1, perf.items.value["m"]!!.loads)
    }

    @Test
    fun `audio reamostrado e wav com cabecalho certo`() {
        val input = ShortArray(48_000) { (it % 100).toShort() }
        assertEquals(16_000, AudioFiles.resample(input, 48_000, 16_000).size)
        val f = File(Files.createTempDirectory("wav").toFile(), "a.wav")
        AudioFiles.writeWav(f, ByteArray(3200))
        val bytes = f.readBytes()
        assertEquals("RIFF", String(bytes, 0, 4))
        assertEquals("WAVE", String(bytes, 8, 4))
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(16_000, header.getInt(24))
        assertEquals(3200, header.getInt(40))
        assertEquals(44 + 3200, bytes.size)
    }

    @Test
    fun `markdown das respostas`() {
        val blocks = Markdown.parse("# Título\n\nTexto com **negrito**.\n\n- um\n  - dois\n1. três\n\n> citação\n\n```kotlin\nval x = 1\n```\n---")
        assertTrue(blocks[0] is Markdown.Block.Heading)
        assertTrue(blocks[1] is Markdown.Block.Paragraph)
        val items = blocks.filterIsInstance<Markdown.Block.Item>()
        assertEquals(listOf(0, 1, 0), items.map { it.depth })
        assertEquals("1.", items[2].marker)
        assertTrue(blocks.any { it is Markdown.Block.Quote })
        assertEquals("val x = 1", blocks.filterIsInstance<Markdown.Block.Code>().single().code)
        assertTrue(blocks.last() is Markdown.Block.Rule)
        // Bloco de código ainda chegando (sem fechar) já aparece como código.
        assertEquals("linha", (Markdown.parse("```\nlinha").single() as Markdown.Block.Code).code)
        assertEquals("Texto com negrito.", Markdown.plain("Texto com **negrito**."))
    }
}

/** A costura dos blocos do Real-ESRGAN, com um ampliador de teste (vizinho mais próximo 4×). */
class SuperResTilingTest {
    /** Faz o papel do modelo: NHWC 128×128×3 → NCHW 3×512×512, copiando cada pixel 4×4. */
    private val nearest: (FloatArray) -> FloatArray = { tile ->
        val out = FloatArray(3 * 512 * 512)
        val plane = 512 * 512
        for (y in 0 until 512) for (x in 0 until 512) {
            val i = ((y / 4) * 128 + (x / 4)) * 3
            out[y * 512 + x] = tile[i]
            out[plane + y * 512 + x] = tile[i + 1]
            out[2 * plane + y * 512 + x] = tile[i + 2]
        }
        out
    }

    private fun image(w: Int, h: Int) = IntArray(w * h) { i ->
        val x = i % w
        val y = i / w
        (0xFF shl 24) or ((x * 7 % 256) shl 16) or ((y * 5 % 256) shl 8) or ((x + y) % 256)
    }

    @Test
    fun `costura sem emendas em varios tamanhos`() {
        for ((w, h) in listOf(100 to 60, 128 to 128, 300 to 250, 513 to 207, 1024 to 768)) {
            val src = image(w, h)
            var last = 0f
            val out = app.lily.ai.engine.SuperRes.tiled(src, w, h, nearest, { last = it })
            assertEquals(w * 4 * h * 4, out.size)
            for (y in 0 until h * 4 step 3) for (x in 0 until w * 4 step 3) {
                assertEquals("${w}x$h em ($x,$y)", src[(y / 4) * w + x / 4], out[y * w * 4 + x])
            }
            assertEquals(1f, last, 0f)
        }
    }

    @Test
    fun `parar interrompe a ampliacao`() {
        var calls = 0
        try {
            app.lily.ai.engine.SuperRes.tiled(image(400, 400), 400, 400, { calls++; nearest(it) }, cancelled = { calls >= 2 })
            error("deveria parar")
        } catch (_: InterruptedException) {
        }
        assertEquals(2, calls)
    }
}
