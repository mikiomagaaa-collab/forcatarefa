package app.lily.ai

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import app.lily.ai.core.TaskKind
import app.lily.ai.data.ChatMessage
import app.lily.ai.data.Role
import app.lily.ai.data.LilyTheme as ThemeChoice
import app.lily.ai.ui.LilyApp
import app.lily.ai.ui.LocalGraph
import app.lily.ai.ui.LocalPermissions
import app.lily.ai.ui.Nav
import app.lily.ai.ui.Permissions
import app.lily.ai.ui.Route
import app.lily.ai.ui.theme.LilyTheme
import app.lily.ai.ui.screens.AssistPanel
import app.lily.ai.ui.screens.ChatSession
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers

/** Sem permissões concedidas (os testes não abrem diálogos). */
internal object NoPermissions : Permissions {
    override fun has(permission: String) = false
    override fun request(permission: String, then: (Boolean) -> Unit) = then(false)
}

/**
 * Monta as telas reais da L.I.L.Y AI (o mesmo código do app) e salva imagens em
 * ai/build/outputs/telas, na tela interna do Razr (411 × 1005 dp).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h1005dp-420dpi")
class AiScreensTest {
    @get:Rule
    val rule = createComposeRule()

    private val app: LilyAiApp get() = ApplicationProvider.getApplicationContext()

    @Before
    fun device() {
        // Um aparelho como o Razr 40 Ultra: 8 GB de RAM, ARM de 64 bits.
        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        shadowOf(am).setMemoryInfo(ActivityManager.MemoryInfo().apply {
            totalMem = 7_600L * 1024 * 1024
            availMem = 3_100L * 1024 * 1024
        })
        ReflectionHelpers.setStaticField(Build::class.java, "SUPPORTED_64_BIT_ABIS", arrayOf("arm64-v8a"))
    }

    private fun show(start: Route, theme: ThemeChoice = ThemeChoice.LIQUID): Nav {
        val nav = Nav(start)
        rule.setContent {
            LilyTheme(theme, 0xFFB79CFF) {
                CompositionLocalProvider(LocalGraph provides app.graph, LocalPermissions provides NoPermissions) {
                    LilyApp(nav)
                }
            }
        }
        rule.waitForIdle()
        return nav
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/outputs/telas").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun `inicio na primeira vez recomenda um modelo`() {
        show(Route.Home)
        rule.onNodeWithText("Comece por aqui").assertExists()
        rule.onNodeWithText("L.I.L.Y").assertExists()
        shot("ai01-inicio")
    }

    @Test
    fun `model hub mostra o aparelho e as fichas`() {
        show(Route.Hub())
        rule.onNodeWithText("Este aparelho").assertExists()
        rule.onNodeWithText("Qwen3 1.7B").assertExists()
        shot("ai02-model-hub")
    }

    @Test
    fun `ficha do gemma`() {
        show(Route.Model("gemma-4-e2b"))
        rule.onNodeWithText("Ficha técnica").assertExists()
        shot("ai03-ficha-gemma")
    }

    @Test
    fun `conversa com respostas formatadas`() {
        val graph = app.graph
        val id = runBlocking {
            val meta = graph.conversations.create("Plano de estudos")
            graph.conversations.save(
                meta.id,
                listOf(
                    ChatMessage(role = Role.USER, text = "Monta um plano de estudos pra prova de cálculo na sexta"),
                    ChatMessage(
                        role = Role.ASSISTANT,
                        text = "Claro! Um plano em **4 dias**:\n\n1. **Terça:** limites e continuidade\n2. **Quarta:** derivadas e regra da cadeia\n3. **Quinta:** integrais e revisão\n\n> Dica: resolva provas antigas com tempo marcado.\n\n```\nf'(x) = lim (f(x+h) - f(x)) / h\n```",
                        model = "Qwen3 1.7B · GPU",
                        millis = 6_400,
                    ),
                ),
            )
            meta.id
        }
        show(Route.Chat(id = id))
        rule.onNodeWithText("Plano de estudos").assertExists()
        shot("ai04-conversa")
    }

    @Test
    fun `ferramentas mostram o mecanismo de cada uma`() {
        show(Route.Tools)
        rule.onNodeWithText("Extrair texto").assertExists()
        shot("ai05-ferramentas")
    }

    @Test
    fun `reescrever com tom e jeito informal`() {
        show(Route.TextTool(TaskKind.REWRITE, "vc viu q a prova mudou pra sexta? tbm nn sabia"))
        rule.onNodeWithText("Tom").assertExists()
        shot("ai06-reescrever")
    }

    @Test
    fun `geracao de imagem sem o modelo explica e leva ao hub`() {
        show(Route.ImageGen())
        rule.onNodeWithText("Gerador não instalado").assertExists()
        shot("ai07-gerar-imagem")
    }

    @Test
    fun `configuracoes e memoria`() {
        app.graph.memory.add("Prefiro respostas curtas", "preferencia")
        app.graph.memory.add("Estudo engenharia elétrica")
        show(Route.Settings)
        rule.onNodeWithText("Privacidade").assertExists()
        shot("ai08-configuracoes")
    }

    @Test
    fun `memoria recusa dado sensivel`() {
        assert(app.graph.memory.add("minha senha do banco é 1234") == null)
        app.graph.memory.add("Moro em Recife")
        show(Route.Memory)
        rule.onNodeWithText("Moro em Recife").assertExists()
        shot("ai09-memoria")
    }

    @Test
    fun `historico agrupa por data`() {
        val graph = app.graph
        runBlocking {
            graph.conversations.create("Receita de bolo").also { graph.conversations.save(it.id, listOf(ChatMessage(role = Role.USER, text = "Como faço bolo de cenoura?"))) }
            graph.conversations.create("Viagem para Recife").also {
                graph.conversations.save(it.id, listOf(ChatMessage(role = Role.USER, text = "Roteiro de 3 dias em Recife")))
                graph.conversations.pin(it.id, true)
            }
        }
        show(Route.History)
        rule.onNodeWithText("Viagem para Recife").assertExists()
        shot("ai10-historico")
    }

    @Test
    fun `tema claro`() {
        show(Route.Home, ThemeChoice.LIGHT)
        shot("ai11-inicio-claro")
    }
}

/** O painel de voz (a lente que desce da Liquid Island), com uma pergunta e a resposta. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h1005dp-420dpi")
class AiAssistPanelTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `lily por voz numa lente`() {
        val app: LilyAiApp = ApplicationProvider.getApplicationContext()
        val graph = app.graph
        val id = runBlocking {
            val meta = graph.conversations.create("Capital do Brasil")
            graph.conversations.save(
                meta.id,
                listOf(
                    ChatMessage(role = Role.USER, text = "Qual é a capital do Brasil e quando foi inaugurada?"),
                    ChatMessage(role = Role.ASSISTANT, text = "A capital é **Brasília**, inaugurada em **21 de abril de 1960**, no governo de Juscelino Kubitschek.", model = "Qwen3 1.7B · GPU", millis = 3_200),
                ),
            )
            meta.id
        }
        val session = ChatSession(graph, app, Route.Chat(id = id))
        rule.setContent {
            LilyTheme(ThemeChoice.LIQUID, 0xFFB79CFF) {
                CompositionLocalProvider(LocalGraph provides graph, LocalPermissions provides NoPermissions) {
                    androidx.compose.foundation.layout.Box(
                        androidx.compose.ui.Modifier.fillMaxSize().background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFFE9C6F2), androidx.compose.ui.graphics.Color(0xFF3E5AA8))),
                        ),
                    ) {
                        AssistPanel(session, listenTrigger = 0, onClose = {}, onOpenApp = {}, onHub = {})
                    }
                }
            }
        }
        rule.waitUntil(10_000) { session.messages.size == 2 }
        rule.onNodeWithText("Abrir na L.I.L.Y").assertExists()
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/outputs/telas").apply { mkdirs() }
        File(dir, "ai13-lily-por-voz.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

/** A versão compacta, na tela externa do Razr (402 × 406 dp). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w402dp-h406dp-420dpi")
class AiCoverTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `tela externa com voz`() {
        val app: LilyAiApp = ApplicationProvider.getApplicationContext()
        rule.setContent {
            LilyTheme(ThemeChoice.LIQUID, 0xFFB79CFF) {
                CompositionLocalProvider(LocalGraph provides app.graph, LocalPermissions provides NoPermissions) {
                    LilyApp(Nav())
                }
            }
        }
        rule.onNodeWithText("Toque no orbe e fale").assertExists()
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/outputs/telas").apply { mkdirs() }
        File(dir, "ai12-tela-externa.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
