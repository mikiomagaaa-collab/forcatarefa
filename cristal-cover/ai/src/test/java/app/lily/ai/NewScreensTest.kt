package app.lily.ai

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.lily.ai.codex.CodexMode
import app.lily.ai.codex.CodexSession
import app.lily.ai.codex.FileWorkspace
import app.lily.ai.codex.Phase
import app.lily.ai.codex.RemoteCodeLlm
import app.lily.ai.codex.RestorePoints
import app.lily.ai.core.OnlineMode
import app.lily.ai.core.Profile
import app.lily.ai.presence.LilyPresence
import app.lily.ai.remote.RemoteModel
import app.lily.ai.ui.LilyApp
import app.lily.ai.ui.LocalGraph
import app.lily.ai.ui.LocalPermissions
import app.lily.ai.ui.Nav
import app.lily.ai.ui.Route
import app.lily.ai.ui.screens.SessionView
import app.lily.ai.ui.theme.AuroraBackground
import app.lily.ai.ui.theme.LilyTheme
import app.lily.ai.video.Motion
import app.lily.ai.video.VideoMode
import app.lily.ai.video.VideoRequest
import java.io.File
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import app.lily.ai.data.LilyTheme as ThemeChoice

/** As telas novas desenhadas com dados do gateway de teste (nada de dados inventados no app). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h1005dp-420dpi")
class NewScreensTest {
    @get:Rule
    val rule = createComposeRule()
    private val app: LilyAiApp get() = ApplicationProvider.getApplicationContext()
    private lateinit var fake: FakeGateway

    @Before
    fun setup() {
        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        shadowOf(am).setMemoryInfo(ActivityManager.MemoryInfo().apply {
            totalMem = 7_600L * 1024 * 1024
            availMem = 3_100L * 1024 * 1024
        })
        ReflectionHelpers.setStaticField(Build::class.java, "SUPPORTED_64_BIT_ABIS", arrayOf("arm64-v8a"))
        fake = FakeGateway()
        val (store, client) = fake.connect(app)
        app.graph.gatewayStore = store
        app.graph.gateway = client
        app.graph.settings.update { it.copy(onlineConsent = true, online = OnlineMode.FALLBACK) }
        runBlocking {
            client.status()
            client.codeModels(refresh = true)
            client.videoModels(refresh = true)
        }
    }

    @After
    fun done() {
        fake.close()
        LilyPresence.reset()
    }

    private fun show(route: Route) {
        val nav = Nav(route)
        rule.setContent {
            LilyTheme(ThemeChoice.LIQUID, 0xFFB79CFF) {
                CompositionLocalProvider(LocalGraph provides app.graph, LocalPermissions provides NoPermissions) { LilyApp(nav) }
            }
        }
        settle()
    }

    private fun settle() {
        repeat(30) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20))
            Thread.sleep(10)
        }
        rule.waitForIdle()
    }

    private fun shot(name: String) {
        settle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/outputs/telas").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun `inicio com a avatar e os módulos`() {
        LilyPresence.working("video", "Gerando vídeo", 0.42f)
        show(Route.Home)
        shot("ai22-inicio-modulos")
    }

    @Test
    fun `servico online conectado`() {
        show(Route.Online)
        shot("ai23-servico-online")
    }

    @Test
    fun `configuracoes da avatar`() {
        show(Route.Avatar)
        shot("ai24-avatar-config")
    }

    @Test
    fun `video com um resultado pronto`() {
        val jobs = app.graph.videos
        runBlocking {
            val model = app.graph.gateway.videoModels().first { it.id == "ltx-2" }
            jobs.submit(VideoRequest(model, "Uma onda de vidro líquido se formando ao pôr do sol, câmera lenta", VideoMode.TEXT, 6, "1080p", "16:9", false, Motion.GENTLE))
            repeat(3) { jobs.pollOnce(autoSave = false) }
        }
        show(Route.Video("Um gato astronauta flutuando numa estação espacial"))
        shot("ai25-video")
    }

    @Test
    fun `codex esperando aprovacao do diff`() {
        val dir = File(app.cacheDir, "proj-tela").apply {
            File(this, "src/app/Calc.kt").apply { parentFile!!.mkdirs() }.writeText("package app\n\nobject Calc {\n    fun soma(a: Int, b: Int): Int = a - b\n}\n")
        }
        fake.replies += FakeGateway.action("search", "query" to "fun soma", "thought" to "Procurar onde a soma é feita")
        fake.replies += FakeGateway.action("read", "path" to "src/app/Calc.kt", "thought" to "Ler o arquivo antes de mudar")
        fake.replies += FakeGateway.action("web_search", "query" to "Kotlin Int plus operator", "thought" to "Conferir na documentação oficial")
        fake.replies += FakeGateway.action("edit", "path" to "src/app/Calc.kt", "find" to "a - b", "replace" to "a + b", "thought" to "A função subtraía em vez de somar")
        fake.replies += FakeGateway.action("check")
        fake.replies += FakeGateway.action("finish", "summary" to "A função soma subtraía os números. Corrigi para somar.", "changes" to "Calc.kt: a - b → a + b", "verified" to listOf("Calc.kt linha 4 usava subtração"), "hypotheses" to listOf("Não há testes cobrindo soma"), "risks" to listOf("Quem dependia do resultado antigo muda de comportamento"), "sources" to listOf("https://kotlinlang.org/docs/arithmetic.html"))
        fake.replies += """{"ok": true, "issues": []}"""
        val client = app.graph.gateway
        val s = CodexSession(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            workspace = FileWorkspace(dir, "calculadora"),
            mode = CodexMode.TASK,
            task = "A soma está errada",
            errorText = "",
            models = { client.codeModels() },
            llmFor = { m: RemoteModel -> RemoteCodeLlm(client, m) },
            gateway = client,
            restore = RestorePoints(File(app.cacheDir, "rp-tela")),
            profile = Profile.BALANCED,
            manualModel = null,
            webAllowed = true,
            reviewEnabled = true,
            autoApplyLines = null,
        )
        s.start()
        val end = System.currentTimeMillis() + 20_000
        while (s.phase != Phase.AWAITING_APPROVAL && System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10))
            Thread.sleep(10)
        }
        rule.setContent {
            LilyTheme(ThemeChoice.LIQUID, 0xFFB79CFF) {
                CompositionLocalProvider(LocalGraph provides app.graph, LocalPermissions provides NoPermissions) {
                    AuroraBackground {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) { SessionView(s) {} }
                    }
                }
            }
        }
        shot("ai26-codex-aprovacao")
    }

    @Test
    fun `codex inicio sem projeto`() {
        show(Route.Codex())
        shot("ai27-codex-inicio")
    }
}
