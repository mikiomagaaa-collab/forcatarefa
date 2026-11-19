package app.lily.ai

import android.graphics.Bitmap
import app.lily.ai.engine.Enhance
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Os filtros clássicos (sem modelo) mexem nos pixels de verdade. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class EnhanceTest {
    private fun noisy(): Bitmap {
        val r = Random(7)
        val b = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        for (y in 0 until 64) for (x in 0 until 64) {
            val v = (128 + r.nextInt(-40, 40)).coerceIn(0, 255)
            b.setPixel(x, y, (0xFF shl 24) or (v shl 16) or (v shl 8) or v)
        }
        return b
    }

    private fun spread(b: Bitmap): Double {
        val px = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
        val g = px.map { (it shr 8 and 255).toDouble() }
        val mean = g.average()
        return Math.sqrt(g.sumOf { (it - mean) * (it - mean) } / g.size)
    }

    @Test
    fun `remover ruido reduz a variacao e mantem o tamanho`() {
        val src = noisy()
        var progress = 0f
        val out = Enhance.denoise(src, 0.6f) { progress = it }
        assertEquals(src.width, out.width)
        assertTrue("ruído caiu: ${spread(src)} → ${spread(out)}", spread(out) < spread(src) * 0.8)
        assertTrue(progress > 0.9f)
    }

    @Test
    fun `ampliacao classica dobra a resolucao`() {
        val out = Enhance.upscaleClassic(noisy())
        assertEquals(128, out.width)
        assertEquals(128, out.height)
    }

    @Test
    fun `tom automatico estica o contraste de uma foto apagada`() {
        val flat = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        for (y in 0 until 32) for (x in 0 until 32) {
            val v = 110 + (x + y) % 30
            flat.setPixel(x, y, (0xFF shl 24) or (v shl 16) or (v shl 8) or v)
        }
        assertTrue(spread(Enhance.autoTone(flat)) > spread(flat))
    }
}

/** Documentos de texto anexados à conversa. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DocumentsTest {
    @Test
    fun `texto puro e lido e limitado`() = kotlinx.coroutines.runBlocking {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        val f = java.io.File(ctx.cacheDir, "notas.txt").apply { writeText("Reunião sexta 15h\nLevar o relatório") }
        val doc = app.lily.ai.engine.Documents.extract(ctx, android.net.Uri.fromFile(f))
        assertEquals("Reunião sexta 15h\nLevar o relatório", doc.text)
        val big = java.io.File(ctx.cacheDir, "grande.txt").apply { writeText("a".repeat(app.lily.ai.engine.Documents.MAX_CHARS + 500)) }
        assertEquals(app.lily.ai.engine.Documents.MAX_CHARS, app.lily.ai.engine.Documents.extract(ctx, android.net.Uri.fromFile(big)).text.length)
    }
}
