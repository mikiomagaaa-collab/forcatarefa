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
}
