package app.lily.ai

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.Process
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import app.lily.ai.diagnostics.CrashReport
import app.lily.ai.diagnostics.DiagLog
import app.lily.ai.diagnostics.DiagnosticActivity
import app.lily.ai.diagnostics.DiagnosticReport
import app.lily.ai.diagnostics.ExitHistory
import app.lily.ai.diagnostics.Report
import app.lily.ai.diagnostics.Tombstone
import app.lily.ai.core.EngineChoice
import app.lily.ai.core.Accel
import app.lily.ai.engine.EngineRefused
import app.lily.ai.engine.UnavailableEngine
import app.lily.ai.models.Catalog
import app.lily.ai.ui.MainActivity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowActivityManager

/**
 * O diagnóstico da L.I.L.Y AI, que precisa funcionar justamente quando o app não abre: registro
 * por etapa, histórico de erros, módulo provável, o histórico de saídas do Android (com a lápide de
 * falha nativa), modo seguro depois de falhas seguidas, módulos opcionais com reserva e a tela
 * "L.I.L.Y Diagnóstico".
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h1005dp-420dpi")
class CrashReportTest {
    private val app: LilyAiApp get() = ApplicationProvider.getApplicationContext()

    private val error = IllegalStateException("O motor não iniciou", RuntimeException("libOpenCL.so não encontrada"))

    @Before
    fun fresh() {
        CrashReport.clear(app)
        DiagLog.dir(app).deleteRecursively()
        DiagLog.begin(app)
    }

    @After
    fun clean() {
        CrashReport.clear(app)
        DiagLog.dir(app).deleteRecursively()
    }

    // --- Relatório de erro ------------------------------------------------------------------------

    @Test
    fun `o relatório traz versão, aparelho, hora, processo, módulo e a pilha com a causa`() {
        val text = CrashReport.format("main", error, "app.lily.ai", Instant.parse("2026-11-16T12:00:00Z"))
        assertTrue(text.startsWith("L.I.L.Y AI ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) fechou por um erro"))
        assertTrue(text.contains("Android "))
        assertTrue(text.contains("16/11/2026"))
        assertTrue(text.contains("processo app.lily.ai · thread main"))
        assertTrue(text.contains("Módulo provável: "))
        assertTrue(text.contains("java.lang.IllegalStateException: O motor não iniciou"))
        assertTrue(text.contains("Caused by: java.lang.RuntimeException: libOpenCL.so não encontrada"))
    }

    @Test
    fun `o módulo provável sai da pilha (biblioteca e a parte do app que a chamou)`() {
        val mlkit = RuntimeException("falhou").apply {
            stackTrace = arrayOf(
                StackTraceElement("com.google.mlkit.vision.text.internal.TextRecognizerImpl", "process", "x", 1),
                StackTraceElement("app.lily.ai.engine.Vision", "ocr", "Vision.kt", 35),
                StackTraceElement("app.lily.ai.ui.screens.ChatSession", "attach", "ChatSession.kt", 462),
            )
        }
        assertEquals("ML Kit (Google) · chamado por: Visão e tradução (ML Kit)", CrashReport.module(mlkit))
        val ui = IllegalArgumentException("x").apply {
            stackTrace = arrayOf(StackTraceElement("app.lily.ai.ui.screens.HomeScreen", "Home", "HomeScreen.kt", 80))
        }
        assertEquals("Interface", CrashReport.module(ui))
        val system = IllegalStateException("x").apply { stackTrace = arrayOf(StackTraceElement("android.view.View", "draw", "View.java", 1)) }
        assertTrue(CrashReport.module(system).startsWith("Sistema Android"))
    }

    @Test
    fun `guarda um histórico dos últimos 10 erros, do mais novo ao mais antigo`() {
        assertNull(CrashReport.last(app))
        repeat(12) { i ->
            CrashReport.save(app, "main", RuntimeException("erro $i"))
            Thread.sleep(2)
        }
        val history = CrashReport.history(app)
        assertEquals(10, history.size)
        assertTrue(history.first().contains("erro 11"))
        assertTrue(CrashReport.last(app)!!.contains("erro 11"))
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

    // --- Registro por etapa e modo seguro ---------------------------------------------------------

    @Test
    fun `cada etapa fica no registro, com tempo, e a falha também`() {
        assertEquals(42, DiagLog.step("Etapa boa") { 42 })
        try {
            DiagLog.step("Etapa ruim") { error("quebrou") }
            fail("a falha tem que seguir adiante")
        } catch (_: IllegalStateException) {
        }
        val log = DiagLog.read(app)
        assertTrue(log.contains("▶ Etapa boa"))
        assertTrue(Regex("✓ Etapa boa \\(\\d+ ms\\)").containsMatchIn(log))
        assertTrue(log.contains("✗ Etapa ruim: java.lang.IllegalStateException: quebrou"))
    }

    @Test
    fun `duas partidas seguidas que não terminam abrem em modo seguro e a tela de pé zera`() {
        val marker = File(DiagLog.dir(app), "partida-em-andamento")
        assertFalse(DiagLog.safeMode)
        marker.writeText("999|tela principal")
        DiagLog.begin(app)
        assertEquals(1, DiagLog.failedStarts)
        assertFalse(DiagLog.safeMode)
        marker.writeText("998|app")
        DiagLog.begin(app)
        assertEquals(2, DiagLog.failedStarts)
        assertTrue(DiagLog.safeMode)
        DiagLog.uiReady("principal")
        DiagLog.begin(app)
        assertEquals(0, DiagLog.failedStarts)
        assertFalse(DiagLog.safeMode)
    }

    @Test
    fun `fechar pela lista de recentes não conta como falha`() {
        val am = app.getSystemService(ActivityManager::class.java)
        shadowOf(am).addApplicationExitInfo(
            ShadowActivityManager.ApplicationExitInfoBuilder.newBuilder()
                .setPid(777).setProcessName(app.packageName).setReason(ApplicationExitInfo.REASON_USER_REQUESTED).setTimestamp(1).build(),
        )
        File(DiagLog.dir(app), "partida-em-andamento").writeText("777|tela principal")
        DiagLog.begin(app)
        assertEquals(0, DiagLog.failedStarts)
    }

    @Test
    fun `modo seguro escolhido no diagnóstico vale até sair dele`() {
        DiagLog.forceSafeMode(app)
        DiagLog.begin(app)
        assertTrue(DiagLog.safeMode)
        DiagLog.leaveSafeMode(app)
        DiagLog.begin(app)
        assertFalse(DiagLog.safeMode)
    }

    // --- Módulos opcionais ------------------------------------------------------------------------

    @Test
    fun `um módulo opcional que falha vira indisponível e o app segue sem ele`() = runBlocking {
        val graph = AiGraph(app, failing = setOf(AiGraph.ENGINE, AiGraph.MEMORY, AiGraph.IMAGE_GEN))
        assertEquals(listOf(AiGraph.MEMORY, AiGraph.ENGINE, AiGraph.IMAGE_GEN), graph.failures.map { it.module })
        assertTrue(graph.engines is UnavailableEngine)
        assertTrue(graph.memory.active().isEmpty())
        try {
            graph.engines.generate(EngineChoice.Llm(Catalog.qwen3Small, Accel.CPU, 4), null, emptyList(), "oi").toList()
            fail("o motor indisponível tem que explicar")
        } catch (e: EngineRefused) {
            assertTrue(e.message!!.contains("L.I.L.Y Diagnóstico"))
        }
        val modules = DiagLog.modulesText(app)
        assertTrue(modules.contains("INDISPONÍVEL · ${AiGraph.ENGINE}"))
        assertTrue(modules.contains("ok · ${AiGraph.SETTINGS}"))
    }

    @Test
    fun `um módulo obrigatório que falha não fecha o app, o núcleo fica de fora e as telas mostram o diagnóstico`() {
        try {
            AiGraph(app, failing = setOf(AiGraph.SETTINGS))
            fail("módulo obrigatório tem que falhar")
        } catch (_: IllegalStateException) {
        }
        val saved = app.graphOrNull
        try {
            app.replaceGraph(null)
            val main = Robolectric.buildActivity(MainActivity::class.java).setup().get()
            assertTrue(main.isFinishing)
            val next = shadowOf(main).nextStartedActivity
            assertEquals(DiagnosticActivity::class.java.name, next.component?.className)
        } finally {
            app.replaceGraph(saved)
        }
    }

    // --- Histórico do Android ---------------------------------------------------------------------

    @Test
    fun `o histórico do Android diz como o app terminou, com a etapa e a lápide nativa`() {
        val am = app.getSystemService(ActivityManager::class.java)
        shadowOf(am).addApplicationExitInfo(
            ShadowActivityManager.ApplicationExitInfoBuilder.newBuilder()
                .setPid(4321).setProcessName(app.packageName).setReason(ApplicationExitInfo.REASON_CRASH_NATIVE).setStatus(11)
                .setTimestamp(1_763_300_000_000).setPss(512_000).setRss(700_000)
                .setProcessStateSummary("etapa: Núcleo da L.I.L.Y (AiGraph)".toByteArray())
                .setTraceInputStream(ByteArrayInputStream(fakeTombstone()))
                .build(),
        )
        val exits = ExitHistory.read(app)
        assertEquals(1, exits.size)
        val text = ExitHistory.format(exits)
        assertTrue(text, text.contains("Motivo: falha nativa (biblioteca em C/C++) · sinal 11"))
        assertTrue(text.contains("Última etapa registrada: Núcleo da L.I.L.Y (AiGraph)"))
        assertTrue(text.contains("Sinal: SIGSEGV (SEGV_MAPERR) em 0x10"))
        assertTrue(text.contains("Thread que caiu: RenderThread"))
        assertTrue(text.contains("#00 /system/lib64/libhwui.so (SkRuntimeEffect::make+24)"))
        assertTrue(text.contains("LilyDiag: ▶ Núcleo da L.I.L.Y (AiGraph)"))
        val summary = DiagnosticReport.summary(app, exits)
        assertTrue(summary.problem)
        assertEquals("a última vez, a L.I.L.Y terminou por: falha nativa (biblioteca em C/C++)", summary.headline)
        assertTrue(summary.detail.contains("Etapa em andamento: Núcleo da L.I.L.Y (AiGraph)"))
    }

    @Test
    fun `do relatório de ANR fica só a thread principal`() {
        val trace = "----- pid 1 -----\n\n\"main\" prio=5 tid=1 Blocked\n  at app.lily.ai.Foo.bar(Foo.kt:1)\n\n\"RenderThread\" prio=7\n  at x"
        val main = ExitHistory.anr(trace)
        assertTrue(main.startsWith("\"main\""))
        assertTrue(main.contains("app.lily.ai.Foo.bar"))
        assertFalse(main.contains("RenderThread"))
    }

    @Test
    fun `lápide ilegível não derruba o diagnóstico`() {
        assertTrue(Tombstone.summary(byteArrayOf(0x7F, 0x01, 0x02)).isNotEmpty())
    }

    // --- Relatório completo e tela ----------------------------------------------------------------

    @Test
    fun `o relatório completo junta situação, módulos, saídas, erros e registro`() {
        AiGraph(app, failing = setOf(AiGraph.PERF))
        CrashReport.save(app, "main", error)
        val report = DiagnosticReport.build(app, emptyList())
        assertTrue(report.startsWith("Diagnóstico da L.I.L.Y AI ${BuildConfig.VERSION_NAME}"))
        assertTrue(report.contains("Situação: há um erro guardado"))
        assertTrue(report.contains("== Módulos na última partida ==\n"))
        assertTrue(report.contains("INDISPONÍVEL · ${AiGraph.PERF}"))
        assertTrue(report.contains("== Erros guardados (1) =="))
        assertTrue(report.contains("O motor não iniciou"))
        assertTrue(report.contains("== Registro da partida (últimas linhas) =="))
        assertTrue(report.contains("Processo iniciado: L.I.L.Y AI"))
    }

    @Test
    fun `a tela de diagnóstico mostra, copia e compartilha o relatório`() {
        CrashReport.save(app, "main", error)
        val intent = Intent(app, DiagnosticActivity::class.java).putExtra(DiagnosticActivity.EXTRA_CRASHED, true)
        val activity = Robolectric.buildActivity(DiagnosticActivity::class.java, intent).setup().get()
        waitFor { activity.window.decorView.allTexts().any { it.contains("O motor não iniciou") } }
        val texts = activity.window.decorView.allTexts()
        assertTrue(texts.contains("A L.I.L.Y AI fechou por um erro"))
        assertTrue(texts.contains("Há um erro guardado"))
        activity.window.decorView.findText("Copiar relatório")!!.performClick()
        val clip = app.getSystemService(ClipboardManager::class.java).primaryClip!!
        assertTrue(clip.getItemAt(0).text.toString().contains("Caused by: java.lang.RuntimeException"))
        activity.window.decorView.findText("Compartilhar")!!.performClick()
        val share = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, share.action)
        screenshot(activity.window.decorView, "ai14-relatorio-de-erro.png")
    }

    @Test
    fun `pelo ícone, sem problemas, a tela diz que está tudo certo`() {
        val activity = Robolectric.buildActivity(DiagnosticActivity::class.java).setup().get()
        waitFor { activity.window.decorView.allTexts().any { it.startsWith("Diagnóstico da L.I.L.Y AI ") && it.contains("Situação") } }
        val texts = activity.window.decorView.allTexts()
        assertTrue(texts.contains("Diagnóstico da L.I.L.Y AI"))
        assertTrue(texts.contains("Nenhum problema registrado"))
        assertFalse(activity.isFinishing)
        screenshot(activity.window.decorView, "ai15-diagnostico.png")
    }

    @Test
    fun `o teste do aparelho leva o último erro junto`() {
        val report = Report(listOf("Teste do aparelho"), emptyList(), emptyList(), 1_000, lastCrash = "L.I.L.Y AI fechou por um erro\nx")
        assertTrue(report.text().contains("Último erro que fechou o app:\nL.I.L.Y AI fechou por um erro"))
        assertFalse(Report(listOf("Teste"), emptyList(), emptyList(), 1_000).text().contains("Último erro"))
    }

    @Test
    fun `um erro numa thread qualquer fica registrado, com o modulo provavel`() {
        CrashReport.install(app)
        assertTrue(Thread.getDefaultUncaughtExceptionHandler() != null)
        val worker = Thread({
            throw IllegalStateException("falha no download").apply {
                stackTrace = arrayOf(StackTraceElement("app.lily.ai.models.Downloader", "download", "Downloader.kt", 80))
            }
        }, "baixador")
        // No Android a thread entrega ao tratador padrão; no Robolectric o grupo dela intercepta antes.
        worker.uncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        worker.start()
        worker.join(5_000)
        val last = CrashReport.last(app)!!
        assertTrue(last.contains("thread baixador"))
        assertTrue(last.contains("Módulo provável: Model Hub e downloads"))
        assertTrue(DiagLog.read(app).contains("✗ ERRO FATAL (Model Hub e downloads)"))
        assertTrue(app.graphOrNull != null)
    }

    // --- Apoio ------------------------------------------------------------------------------------

    private fun waitFor(condition: () -> Boolean) {
        repeat(200) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(15)
        }
        fail("a tela não terminou de carregar")
    }

    /** Uma lápide no formato do debuggerd: sinal, pilha da thread que caiu e linhas do Logcat. */
    private fun fakeTombstone(): ByteArray {
        val signal = msg(varint(1, 11) + str(2, "SIGSEGV") + varint(3, 1) + str(4, "SEGV_MAPERR") + varint(9, 0x10))
        val frame = msg(varint(1, 0x1234) + str(4, "SkRuntimeEffect::make") + varint(5, 24) + str(6, "/system/lib64/libhwui.so"))
        val thread = msg(varint(1, 99) + str(2, "RenderThread") + field(4, frame))
        val entry = msg(varint(1, 99) + field(2, thread))
        val log = msg(str(5, "LilyDiag") + str(6, "▶ Núcleo da L.I.L.Y (AiGraph)"))
        val buffer = msg(str(1, "main") + field(2, log))
        return varint(5, 4321) + varint(6, 99) + field(10, signal) + field(16, entry) + field(18, buffer)
    }

    private fun msg(bytes: ByteArray) = bytes
    private fun key(field: Int, type: Int) = raw((field shl 3 or type).toLong())
    private fun varint(field: Int, value: Long) = key(field, 0) + raw(value)
    private fun varint(field: Int, value: Int) = varint(field, value.toLong())
    private fun str(field: Int, value: String) = field(field, value.toByteArray())
    private fun field(field: Int, bytes: ByteArray) = key(field, 2) + raw(bytes.size.toLong()) + bytes
    private fun raw(value: Long): ByteArray {
        val out = ByteArrayOutputStream()
        var v = value
        while (true) {
            if (v and 0x7F.inv().toLong() == 0L) {
                out.write(v.toInt())
                return out.toByteArray()
            }
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
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
