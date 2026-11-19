package app.cristal.cover

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.test.core.app.ApplicationProvider
import app.cristal.cover.data.apps.MonoGlyph
import app.cristal.cover.data.home.HomeItem
import app.cristal.cover.data.home.PhotoWidgetStore
import app.cristal.cover.data.home.WidgetKind
import app.cristal.cover.ui.launcher.WidgetSizing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class FreedomTest {

    /** Ícone "sem tema": quadrado vermelho com um círculo branco no meio. */
    private fun legacyIcon(): Bitmap {
        val bmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(230, 30, 60))
        c.drawCircle(64f, 64f, 26f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        return bmp
    }

    private fun alphaAt(bmp: Bitmap, x: Int, y: Int): Int = Color.alpha(bmp.getPixel(x, y))

    @Test
    fun `glifo forcado separa o simbolo do fundo`() {
        val icon = legacyIcon()
        val mask = MonoGlyph.contrastMask(icon)
        assertTrue("centro é símbolo", (mask[64 * 128 + 64].toInt() and 0xFF) > 200)
        assertTrue("canto é fundo", (mask[5 * 128 + 5].toInt() and 0xFF) < 10)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val glyph = MonoGlyph.forced(BitmapDrawable(context.resources, icon), 128, icon)
        assertNotNull(glyph)
        glyph!!
        assertEquals(Bitmap.Config.ALPHA_8, glyph.config)
        // Centralizado e ocupando cerca de 56% do ícone.
        assertTrue(alphaAt(glyph, 64, 64) > 200)
        assertTrue(alphaAt(glyph, 64, 64 - 34) > 100)
        assertTrue(alphaAt(glyph, 64, 64 - 44) < 40)
    }

    @Test
    fun `cor principal do icone`() {
        val accent = MonoGlyph.dominantColor(legacyIcon())
        assertTrue("avermelhada", Color.red(accent) > 200 && Color.green(accent) < 120)
    }

    @Test
    fun `tamanhos de widget que cabem`() {
        val widget = HomeItem.Widget(1, 0, 0, 0, 2, 2, WidgetKind.WEATHER)
        val other = HomeItem.Widget(2, 0, 3, 0, 1, 1, WidgetKind.DATE)
        val occupied = WidgetSizing.occupied(listOf(widget, other), ignoreId = 1)
        assertEquals(setOf(3 to 0), occupied)
        assertTrue(WidgetSizing.fits(0, 0, 3, 3, 4, 4, occupied))
        assertFalse("encosta no outro widget", WidgetSizing.fits(0, 0, 4, 1, 4, 4, occupied))
        assertFalse("sai da grade", WidgetSizing.fits(0, 0, 2, 5, 4, 4, occupied))
    }

    @Test
    fun `fotos do widget`() {
        val extra = PhotoWidgetStore.encode(listOf("a.jpg", "b.jpg"))
        assertEquals(listOf("a.jpg", "b.jpg"), PhotoWidgetStore.decode(extra))
        assertEquals(emptyList<String>(), PhotoWidgetStore.decode(null))
        assertEquals("ignora restos que não são fotos", listOf("c.jpg"), PhotoWidgetStore.decode("c.jpg|12"))
    }
}
