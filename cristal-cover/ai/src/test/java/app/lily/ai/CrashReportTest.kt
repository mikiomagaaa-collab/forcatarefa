package app.lily.ai

import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import app.lily.ai.diagnostics.CrashActivity
import app.lily.ai.diagnostics.CrashReport
import app.lily.ai.diagnostics.Report
import java.io.File
import java.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** O relatório de erro que a pessoa vê e copia no próprio aparelho, sem computador. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h1005dp-420dpi")
class CrashReportTest {
    private val app: LilyAiApp get() = ApplicationProvider.getApplicationContext()

    private val error = IllegalStateException("O motor não iniciou", RuntimeException("libOpenCL.so não encontrada"))

    @After
    fun clean() = CrashReport.clear(app)

    @Test
    fun `o relatório traz versão, aparelho, hora, processo e a pilha com a causa`() {
        val text = CrashReport.format("main", error, "app.lily.ai", Instant.parse("2026-11-16T12:00:00Z"))
        assertTrue(text.startsWith("L.I.L.Y AI ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) fechou por um erro"))
        assertTrue(text.contains("Android "))
        assertTrue(text.contains("16/11/2026"))
        assertTrue(text.contains("processo app.lily.ai · thread main"))
        assertTrue(text.contains("java.lang.IllegalStateException: O motor não iniciou"))
        assertTrue(text.contains("Caused by: java.lang.RuntimeException: libOpenCL.so não encontrada"))
        assertTrue(text.contains("at app.lily.ai.CrashReportTest"))
    }

    @Test
    fun `guarda, lê e apaga o último erro`() {
        assertNull(CrashReport.last(app))
        CrashReport.save(app, "main", error)
        assertTrue(CrashReport.last(app)!!.contains("O motor não iniciou"))
        CrashReport.clear(app)
        assertNull(CrashReport.last(app))
    }

    @Test
    fun `pilhas enormes são cortadas`() {
        var deep: Throwable = RuntimeException("fundo")
        repeat(2_000) { deep = RuntimeException("nível $it", deep) }
        val text = CrashReport.format("main", deep)
        assertTrue(text.length < 26_000)
        assertTrue(text.endsWith("…(cortado)"))
    }

    @Test
    fun `a tela do relatório mostra e copia o texto`() {
        CrashReport.save(app, "main", error)
        val activity = Robolectric.buildActivity(CrashActivity::class.java).setup().get()
        val texts = activity.window.decorView.allTexts()
        assertTrue(texts.any { it == "A L.I.L.Y AI fechou por um erro" })
        assertTrue(texts.any { it.contains("O motor não iniciou") })
        activity.window.decorView.findText("Copiar relatório")!!.performClick()
        val clip = app.getSystemService(ClipboardManager::class.java).primaryClip!!
        assertTrue(clip.getItemAt(0).text.toString().contains("Caused by: java.lang.RuntimeException"))
        screenshot(activity.window.decorView, "ai14-relatorio-de-erro.png")
    }

    @Test
    fun `sem relatório guardado, a tela fecha sozinha`() {
        val activity = Robolectric.buildActivity(CrashActivity::class.java).setup().get()
        assertTrue(activity.isFinishing)
    }

    @Test
    fun `o teste do aparelho leva o último erro junto`() {
        val report = Report(listOf("Teste do aparelho"), emptyList(), emptyList(), 1_000, lastCrash = "L.I.L.Y AI fechou por um erro\nx")
        assertTrue(report.text().contains("Último erro que fechou o app:\nL.I.L.Y AI fechou por um erro"))
        assertFalse(Report(listOf("Teste"), emptyList(), emptyList(), 1_000).text().contains("Último erro"))
    }

    @Test
    fun `o app instala a captura de erros ao iniciar`() {
        // O LilyAiApp do teste já passou pelo onCreate: o grafo existe e a captura está ligada.
        assertEquals("app.lily.ai", app.packageName)
        assertTrue(Thread.getDefaultUncaughtExceptionHandler() != null)
        app.graph // não lança
    }

    private fun View.allTexts(): List<String> = when (this) {
        is TextView -> listOf(text.toString())
        is ViewGroup -> (0 until childCount).flatMap { getChildAt(it).allTexts() }
        else -> emptyList()
    }

    private fun View.findText(text: String): View? = when {
        this is TextView && this.text.toString() == text -> this
        this is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { getChildAt(it).findText(text) }
        else -> null
    }

    private fun screenshot(view: View, name: String) {
        val bitmap = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("build/outputs/telas").apply { mkdirs() }
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
