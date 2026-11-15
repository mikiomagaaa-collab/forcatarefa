package app.cristal.cover

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.media.ImageReader
import app.cristal.cover.ui.glass.GlassShaders
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Compila o shader AGSL de verdade (com o Skia nativo do Robolectric) e desenha um card com ele.
 * Um erro de sintaxe no shader só apareceria em tempo de execução no aparelho; este teste o pega no CI.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class GlassShaderTest {

    @Test
    fun `o shader do vidro liquido compila`() {
        assertNotNull("O AGSL não compilou", GlassShaders.newLiquid())
    }

    @Test
    fun `o shader recorta o retangulo arredondado e refrata o fundo`() {
        val width = 420
        val height = 220
        // Fundo: faixas coloridas, para a refração ficar visível na imagem gerada.
        val background = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(background).apply {
            val paint = Paint()
            paint.shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(Color.rgb(0, 212, 255), Color.rgb(255, 78, 205), Color.rgb(124, 255, 178)),
                null, Shader.TileMode.CLAMP,
            )
            drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.shader = null
            paint.color = Color.BLACK
            var x = 0f
            while (x < width) {
                drawRect(x, 0f, x + 6f, height.toFloat(), paint)
                x += 28f
            }
        }

        val shader = RuntimeShader(GlassShaders.LIQUID)
        shader.setInputShader("content", BitmapShader(background, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
        shader.setFloatUniform("uSize", width.toFloat(), height.toFloat())
        shader.setFloatUniform("uRadius", 48f)
        shader.setFloatUniform("uBezel", 44f)
        shader.setFloatUniform("uRefraction", 46f)
        shader.setFloatUniform("uDispersion", 0.45f)
        shader.setFloatUniform("uTint", 0f, 0f, 0f, 0.2f)
        shader.setFloatUniform("uLight", -0.6f, -0.8f)
        shader.setFloatUniform("uSpecular", 0.6f)
        shader.setFloatUniform("uSaturation", 1.4f)
        shader.setFloatUniform("uTouch", 300f, 120f)
        shader.setFloatUniform("uTouchAmt", 0.8f)

        // Mesmo encadeamento usado pelo app: primeiro o blur do fundo, depois o shader de refração.
        val effect = RenderEffect.createChainEffect(
            RenderEffect.createRuntimeShaderEffect(shader, "content"),
            RenderEffect.createBlurEffect(10f, 10f, Shader.TileMode.CLAMP),
        )
        val glass = RenderNode("vidro").apply {
            setPosition(0, 0, width, height)
            beginRecording().drawBitmap(background, 0f, 0f, null)
            endRecording()
            setRenderEffect(effect)
        }
        val output = renderToBitmap(glass, width, height)

        // Fora do canto arredondado o shader devolve transparente; no meio, opaco.
        assertEquals("o canto deveria ser transparente", 0, Color.alpha(output.getPixel(1, 1)))
        assertEquals("o centro deveria ser opaco", 255, Color.alpha(output.getPixel(width / 2, height / 2)))
        // Na borda há refração e brilho: os pixels não podem ser iguais aos do fundo no mesmo lugar.
        var different = 0
        for (y in 60 until 160) if (output.getPixel(8, y) != background.getPixel(8, y)) different++
        assertTrue("a borda deveria estar refratada", different > 50)

        File("build/outputs/shader").apply { mkdirs() }.let { dir ->
            File(dir, "fundo.png").outputStream().use { background.compress(Bitmap.CompressFormat.PNG, 100, it) }
            File(dir, "vidro.png").outputStream().use { output.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    /** Desenha um RenderNode com o renderizador de hardware (o mesmo caminho do aparelho) e lê os pixels. */
    private fun renderToBitmap(node: RenderNode, width: Int, height: Int): Bitmap {
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1)
        val renderer = HardwareRenderer()
        try {
            renderer.setSurface(reader.surface)
            val root = RenderNode("raiz").apply {
                setPosition(0, 0, width, height)
                beginRecording().drawRenderNode(node)
                endRecording()
            }
            renderer.setContentRoot(root)
            renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
            reader.acquireNextImage().use { image ->
                val plane = image.planes[0]
                val rowPixels = plane.rowStride / plane.pixelStride
                val padded = Bitmap.createBitmap(rowPixels, height, Bitmap.Config.ARGB_8888)
                padded.copyPixelsFromBuffer(plane.buffer)
                return Bitmap.createBitmap(padded, 0, 0, width, height)
            }
        } finally {
            renderer.destroy()
            reader.close()
        }
    }
}
