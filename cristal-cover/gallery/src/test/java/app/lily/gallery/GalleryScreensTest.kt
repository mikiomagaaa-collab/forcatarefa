package app.lily.gallery

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.lily.gallery.data.EditState
import app.lily.gallery.data.Edits
import app.lily.gallery.data.Filter
import app.lily.gallery.data.MediaItem
import app.lily.gallery.data.MediaRepo
import app.lily.gallery.data.Thumbs
import app.lily.gallery.ui.Access
import app.lily.gallery.ui.G
import app.lily.gallery.ui.GalleryApp
import app.lily.gallery.ui.GalleryNav
import app.lily.gallery.ui.GalleryTheme
import app.lily.gallery.ui.LocalRepo
import app.lily.gallery.ui.LocalRequests
import app.lily.gallery.ui.Screen
import app.lily.gallery.ui.SystemRequests
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private object NoRequests : SystemRequests {
    override fun trash(uris: List<Uri>, trashed: Boolean, done: (Boolean) -> Unit) = done(false)
    override fun delete(uris: List<Uri>, done: (Boolean) -> Unit) = done(false)
    override fun favorite(uris: List<Uri>, favorite: Boolean, done: (Boolean) -> Unit) = done(false)
    override fun write(uris: List<Uri>, done: (Boolean) -> Unit) = done(false)
    override fun canManageMedia() = false
    override fun openManageMedia() = Unit
    override fun requestMedia() = Unit
}

/** Telas reais da L.I.L.Y Gallery com fotos geradas no teste (o Robolectric não tem fotos no MediaStore). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h1005dp-420dpi")
class GalleryScreensTest {
    @get:Rule
    val rule = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    private val palette = listOf(
        0xFF7B61FF.toInt() to 0xFF3EC6FF.toInt(), 0xFFFF6AD5.toInt() to 0xFFFFB36B.toInt(), 0xFF34D399.toInt() to 0xFF0E4A6E.toInt(),
        0xFFFFB347.toInt() to 0xFFFF6B7A.toInt(), 0xFF1C8BD6.toInt() to 0xFF9DE36B.toInt(), 0xFF5B1FA8.toInt() to 0xFFE3B5FF.toInt(),
    )

    private fun picture(i: Int, w: Int = 400, h: Int = 400): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val (a, z) = palette[i % palette.size]
        val c = Canvas(b)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), a, z, Shader.TileMode.CLAMP) })
        c.drawCircle(w * 0.7f, h * 0.3f, w * 0.12f, Paint().apply { color = 0xFFFFF4D6.toInt() })
        val path = android.graphics.Path().apply {
            moveTo(0f, h * 0.8f); lineTo(w * 0.35f, h * 0.5f); lineTo(w * 0.55f, h * 0.68f); lineTo(w * 0.75f, h * 0.55f); lineTo(w.toFloat(), h * 0.8f)
            lineTo(w.toFloat(), h.toFloat()); lineTo(0f, h.toFloat()); close()
        }
        c.drawPath(path, Paint().apply { color = 0xCCFFFFFF.toInt() })
        return b
    }

    private fun file(i: Int, w: Int, h: Int): Uri {
        val f = File(context.cacheDir, "foto$i.png")
        f.outputStream().use { picture(i, w, h).compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Uri.fromFile(f)
    }

    private fun seed(): MediaRepo {
        val repo = MediaRepo(context)
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        val albums = listOf(1L to "Câmera" to "DCIM/Camera/", 2L to "Capturas de tela" to "Pictures/Screenshots/", 3L to "L.I.L.Y" to "Pictures/L.I.L.Y/")
        val items = (0 until 26).map { i ->
            val (idName, path) = albums[i % albums.size]
            val uri = file(i, 1200, 900)
            MediaItem(
                id = i + 1L, uri = uri, video = i % 9 == 4, mime = if (i % 9 == 4) "video/mp4" else "image/png",
                taken = now - i * day * 2, size = 2_400_000L + i * 1000, width = 1200, height = 900,
                bucketId = idName.first, bucket = idName.second, path = path, name = "IMG_20261$i.png",
                durationMs = 42_000, favorite = i % 7 == 0,
            ).also { m ->
                listOf(240, 360, 400, 1024).forEach { side -> Thumbs.put(m, side, picture(i, 240, 240)) }
            }
        }
        repo.seed(items, trash = items.take(2).map { it.copy(id = it.id + 1000, expires = now + 12 * day) }.onEach { m -> Thumbs.put(m, 240, picture(m.id.toInt(), 240, 240)) })
        return repo
    }

    private fun show(repo: MediaRepo, nav: GalleryNav, access: Access = Access.FULL) {
        rule.setContent {
            GalleryTheme(dark = true) {
                CompositionLocalProvider(LocalRepo provides repo, LocalRequests provides NoRequests) {
                    Box(Modifier.fillMaxSize().background(G.colors.background)) { GalleryApp(nav, access) }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/outputs/telas").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun `fotos agrupadas por mes`() {
        show(seed(), GalleryNav())
        rule.onNodeWithText("26 itens").assertExists()
        shot("g01-fotos")
    }

    @Test
    fun `albuns com o album da lily em destaque`() {
        show(seed(), GalleryNav())
        rule.onNodeWithText("Álbuns").performClick()
        rule.onNodeWithText("Criadas pela L.I.L.Y").assertExists()
        shot("g02-albuns")
    }

    @Test
    fun `visualizador com as acoes`() {
        val nav = GalleryNav().apply { go(Screen.Viewer(app.lily.gallery.ui.Source.All, 1L)) }
        show(seed(), nav)
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("Foto").fetchSemanticsNodes().isNotEmpty() }
        shot("g03-visualizador")
    }

    @Test
    fun `editor com filtros`() {
        val nav = GalleryNav().apply { go(Screen.Editor(2L)) }
        show(seed(), nav)
        rule.waitUntil(10_000) { rule.onAllNodesWithContentDescription("Prévia da edição").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Filtros").performClick()
        shot("g04-editor-filtros")
        rule.onNodeWithText("Cortar").performClick()
        rule.onNodeWithText("1:1").performClick()
        shot("g05-editor-corte")
    }

    @Test
    fun `primeira vez pede acesso so depois do toque`() {
        show(seed(), GalleryNav(), Access.NONE)
        rule.onNodeWithText("Permitir acesso às fotos").assertExists()
        shot("g06-permissao")
    }

    @Test
    fun `lixeira mostra quando expira`() {
        show(seed(), GalleryNav().apply { go(Screen.Trash) })
        rule.onNodeWithText("Lixeira").assertExists()
        shot("g07-lixeira")
    }

    @Test
    fun `render aplica corte giro e cores de verdade`() {
        val src = picture(0, 400, 200)
        val out = Edits.render(src, EditState(rotation = 90, crop = android.graphics.RectF(0f, 0f, 1f, 0.5f), filter = Filter.MONO))
        // Girada 90°: 200 × 400; metade de cima: 200 × 200.
        assertEquals(200, out.width)
        assertEquals(200, out.height)
        val p = out.getPixel(100, 150)
        val r = p shr 16 and 255
        val g = p shr 8 and 255
        val b = p and 255
        assertTrue("P&B deixa os canais iguais", kotlin.math.abs(r - g) <= 2 && kotlin.math.abs(g - b) <= 2)
    }

    @Test
    fun `automatico clareia foto escura`() {
        val dark = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF202020.toInt()) }
        val s = Edits.auto(dark)
        assertTrue(s.exposure > 0.3f)
    }
}
