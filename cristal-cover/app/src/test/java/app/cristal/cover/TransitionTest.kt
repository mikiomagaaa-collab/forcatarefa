package app.cristal.cover

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.cristal.cover.service.FoldContent
import app.cristal.cover.service.FoldTransitionOverlay
import app.cristal.cover.ui.fold.FoldFocus
import app.cristal.cover.ui.fold.FoldGeometry
import app.cristal.cover.ui.fold.FoldLayer
import app.cristal.cover.ui.fold.FoldMotionModel
import app.cristal.cover.ui.fold.foldFocus
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.ContentScale
import org.junit.Assert.assertTrue
import kotlin.math.abs
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h1004dp-420dpi")
class TransitionTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `quanto ja fechou pela dobradica`() {
        assertEquals(0f, FoldTransitionOverlay.fractionFor(180f), 0.001f)
        assertEquals(0f, FoldTransitionOverlay.fractionFor(165f), 0.001f)
        assertEquals(0.5f, FoldTransitionOverlay.fractionFor(105f), 0.001f)
        assertEquals(1f, FoldTransitionOverlay.fractionFor(40f), 0.001f)
    }

    /** Uma "tela de app" de mentira: fundo, barra e cartões. */
    private fun fakeScreen(): Bitmap {
        val bmp = Bitmap.createBitmap(540, 1320, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 540f, 1320f, 0xFFF6E7FF.toInt(), 0xFFB8D4FF.toInt(), Shader.TileMode.CLAMP) })
        val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF6C4BD6.toInt() }
        c.drawRect(0f, 0f, 540f, 150f, accent)
        for (i in 0 until 6) c.drawRoundRect(RectF(30f, 190f + i * 180f, 510f, 340f + i * 180f), 28f, 28f, card)
        return bmp
    }

    @Test
    fun `transicao de fechamento`() {
        val shot = fakeScreen().asImageBitmap()
        rule.setContent {
            Row(Modifier.fillMaxSize().background(Color(0xFF111111))) {
                Box(Modifier.weight(1f).fillMaxHeight().padding(4.dp)) { FoldContent(shot, { 0.55f }) }
                Box(Modifier.weight(1f).fillMaxHeight().padding(4.dp).background(Color(0xFF8FA8D8))) { FoldContent(null, { 0.55f }) }
            }
        }
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/outputs/telas").apply { mkdirs() }
        File(dir, "53-transicao-fechamento.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // --- Transição de dobra (estilo iPhone Duo) ---------------------------------------------------

    @Test
    fun `abrindo, o foco segue a dobradica sem saltos e assenta`() {
        val m = FoldMotionModel()
        m.onDisplay(cover = false, angle = 80f, now = 0)
        assertEquals(0f, m.target, 0.001f)
        var last = m.target
        var t = 0L
        var angle = 80f
        while (angle < 180f) {
            angle += 2f
            t += 16
            m.onAngle(angle, t)
            assertTrue("o foco não volta atrás ao abrir", m.target >= last - 1e-4f)
            assertTrue("sem saltos: ${m.target - last}", m.target - last < 0.06f)
            last = m.target
        }
        assertEquals(1f, m.target, 0.001f)
    }

    @Test
    fun `fechando, comeca nitida (sem salto) e transforma ate apagar, e reabrir desfaz`() {
        val m = FoldMotionModel()
        m.onDisplay(cover = false, angle = 180f, now = 0)
        m.arrived()
        m.onAngle(178f, 16)
        assertEquals("tremer a mão não dispara", 1f, m.target, 0.001f)
        var t = 32L
        var angle = 178f
        var last = 1f
        while (angle > 64f) {
            angle -= 2f
            t += 16
            m.onAngle(angle, t)
            assertTrue(m.target <= last + 1e-4f)
            assertTrue("sem saltos: ${last - m.target}", last - m.target < 0.06f)
            last = m.target
        }
        assertTrue(m.target < 0.05f)
        m.onAngle(150f, t + 16)
        assertEquals("reabriu: volta a ficar nítida", 1f, m.target, 0.001f)
    }

    @Test
    fun `parou no meio (modo flex), volta a ficar nitida`() {
        val m = FoldMotionModel()
        m.onDisplay(cover = false, angle = 80f, now = 0)
        m.onAngle(110f, 100)
        assertTrue(m.target in 0.1f..0.9f)
        assertTrue(!m.tick(400))
        assertTrue(m.tick(100 + FoldMotionModel.STALL_MS))
        assertEquals(1f, m.target, 0.001f)
        // Continuar abrindo depois de parar não traz o desfoque de volta.
        m.onAngle(140f, 1_000)
        assertEquals(1f, m.target, 0.001f)
    }

    @Test
    fun `tela externa, chegada pelo tempo e desfaz ao comecar a abrir`() {
        val m = FoldMotionModel()
        m.onDisplay(cover = true, angle = 0f, now = 0)
        assertEquals(0f, m.arrivalStart, 0.001f)
        assertEquals(1f, m.target, 0.001f)
        m.arrived()
        m.onAngle(4f, 50)
        assertEquals(1f, m.target, 0.001f)
        m.onAngle(26f, 100)
        assertTrue(m.target < 1f && m.target > 0f)
        m.onAngle(46f, 150)
        assertEquals(0f, m.target, 0.001f)
    }

    @Test
    fun `sem sensor so a chegada e com animacoes desligadas nada`() {
        val m = FoldMotionModel()
        m.onDisplay(cover = false, angle = null, now = 0)
        assertEquals(0f, m.arrivalStart, 0.001f)
        assertEquals(1f, m.target, 0.001f)
        val reduced = FoldMotionModel(reduceMotion = true)
        reduced.onDisplay(cover = false, angle = 80f, now = 0)
        assertEquals(1f, reduced.arrivalStart, 0.001f)
        assertEquals(1f, reduced.target, 0.001f)
        reduced.onAngle(120f, 10)
        assertEquals(1f, reduced.target, 0.001f)
    }

    @Test
    fun `o desfoque e por regiao, a faixa da dobra por ultimo`() {
        // Longe da dobra, menos transformação que perto dela; com o tempo, tudo assenta.
        for (p in listOf(0.2f, 0.4f, 0.6f, 0.8f)) {
            assertTrue(FoldFocus.amount(p, 0.9f) < FoldFocus.amount(p, 0.05f))
        }
        assertTrue(FoldFocus.amount(0.3f, 0.1f) > FoldFocus.amount(0.7f, 0.1f))
        assertEquals(0f, FoldFocus.amount(1f, 0f), 0.001f)
        assertEquals(1f, FoldFocus.amount(0f, 0f), 0.001f)
        // Ritmos: o papel de parede assenta antes dos componentes.
        assertTrue(FoldFocus.paced(0.5f, FoldLayer.WALLPAPER) > FoldFocus.paced(0.5f, FoldLayer.CONTENT))
    }

    @Test
    fun `transicao de dobra desenhada na GPU, bordas nitidas longe da dobra`() {
        val shot = fakeScreen().asImageBitmap()
        val steps = listOf(0.15f, 0.4f, 0.7f, 1f)
        rule.setContent {
            Row(Modifier.fillMaxSize().background(Color(0xFF0B0A10))) {
                steps.forEach { p ->
                    val state = remember { mutableFloatStateOf(p) }
                    Box(Modifier.weight(1f).fillMaxHeight().padding(3.dp)) {
                        Image(shot, null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize().foldFocus(state, FoldGeometry.InnerPortrait, FoldLayer.CONTENT, 28.dp))
                    }
                }
            }
        }
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/outputs/telas").apply { mkdirs() }
        File(dir, "54-transicao-dobra.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

    }

    @Test
    fun `nitidez medida na GPU, longe da dobra mais nitido que perto`() {
        val shot = fakeScreen().asImageBitmap()
        val state = mutableFloatStateOf(0.7f)
        rule.setContent {
            Image(shot, null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize().foldFocus(state, FoldGeometry.InnerPortrait, FoldLayer.CONTENT, 28.dp))
        }
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        // Maior salto de brilho ao longo de uma linha inteira (as bordas dos cartões estão nela).
        fun sharpness(yFraction: Float): Int {
            val y = (bitmap.height * yFraction).toInt()
            var best = 0
            for (x in 2 until bitmap.width - 2) {
                best = maxOf(best, abs(rg(bitmap.getPixel(x - 1, y)) - rg(bitmap.getPixel(x + 1, y))))
            }
            return best
        }
        val far = sharpness(265f / 1320f)
        val near = sharpness(625f / 1320f)
        assertTrue("longe da dobra ($far) mais nítido que perto ($near)", far > near * 1.5f)
        // Assentada (progresso 1), a imagem sai sem efeito nenhum.
        state.floatValue = 1f
        rule.waitForIdle()
        val settled = rule.onRoot().captureToImage().asAndroidBitmap()
        val y = (settled.height * 625f / 1320f).toInt()
        var edge = 0
        for (x in 2 until settled.width - 2) edge = maxOf(edge, abs(rg(settled.getPixel(x - 1, y)) - rg(settled.getPixel(x + 1, y))))
        assertTrue("assentada fica nítida ($edge)", edge > near * 2)
    }

    /** Vermelho + verde (o azul do fundo de teste é 0xFF em toda parte, igual ao branco dos cartões). */
    private fun rg(pixel: Int): Int = (pixel shr 16 and 0xFF) + (pixel shr 8 and 0xFF)
}
