package app.lily.ai.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lily.ai.AiGraph
import app.lily.ai.LilyAiApp
import app.lily.ai.core.TaskKind
import app.lily.ai.ui.screens.ChatScreen
import app.lily.ai.ui.screens.CoverScreen
import app.lily.ai.ui.screens.HistoryScreen
import app.lily.ai.ui.screens.HomeScreen
import app.lily.ai.ui.screens.HubScreen
import app.lily.ai.ui.screens.ImageGenScreen
import app.lily.ai.ui.screens.ImageToolScreen
import app.lily.ai.ui.screens.MemoryScreen
import app.lily.ai.ui.screens.ModelScreen
import app.lily.ai.ui.screens.SettingsScreen
import app.lily.ai.ui.screens.TextToolScreen
import app.lily.ai.ui.screens.ToolsScreen
import app.lily.ai.ui.theme.AuroraBackground
import app.lily.ai.ui.theme.LilyTheme

/** Pedidos de permissão feitos só depois de um toque (microfone e notificações). */
interface Permissions {
    fun has(permission: String): Boolean
    fun request(permission: String, then: (Boolean) -> Unit)
}

private class ActivityPermissions(private val activity: MainActivity) : Permissions {
    override fun has(permission: String): Boolean = activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    override fun request(permission: String, then: (Boolean) -> Unit) = activity.requestPermission(permission, then)
}

val LocalPermissions = staticCompositionLocalOf<Permissions> { error("sem permissões") }
val LocalGraph = staticCompositionLocalOf<AiGraph> { error("sem grafo") }

class MainActivity : ComponentActivity() {
    private var pendingPermission: ((Boolean) -> Unit)? = null
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingPermission?.invoke(granted)
        pendingPermission = null
    }
    private val nav = Nav()

    fun requestPermission(permission: String, then: (Boolean) -> Unit) {
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) return then(true)
        pendingPermission = then
        permissionLauncher.launch(permission)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val graph = (application as LilyAiApp).graph
        if (savedInstanceState == null) handle(intent)
        setContent {
            val settings by graph.settings.value.collectAsStateWithLifecycle()
            LilyTheme(settings.theme, settings.accent) {
                CompositionLocalProvider(LocalGraph provides graph, LocalPermissions provides remember { ActivityPermissions(this) }) {
                    LilyApp(nav)
                }
            }
        }
    }

    override fun onDestroy() {
        nav.clear()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** O que outros apps mandaram: imagem, texto ou áudio, ou uma tela pedida (Model Hub, geração). */
    private fun handle(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val stream = intent.parcelableStream()
                val type = intent.type.orEmpty()
                when {
                    type.startsWith("image/") && stream != null -> nav.go(Route.ImageTool(stream, null))
                    type.startsWith("audio/") && stream != null -> nav.go(Route.Chat(audio = stream))
                    type == "text/plain" -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { nav.go(Route.Chat(draft = it)) }
                }
            }
            ACTION_IMAGE_TOOL -> {
                // Vindo da L.I.L.Y Gallery: a foto chega com permissão de leitura só dela.
                val uri = intent.data ?: intent.parcelableStream()
                val tool = intent.getStringExtra(EXTRA_TOOL)
                val task = tool?.let { name -> TaskKind.entries.firstOrNull { it.name == name } }
                if (uri != null) nav.go(if (tool == "ASK") Route.Chat(image = uri) else Route.ImageTool(uri, task))
            }
        }
        // "Perguntar à L.I.L.Y" do Cristal: só pela entrada protegida por assinatura (envia direto).
        if (intent.action == ACTION_ASK && intent.component?.className == ASK_ENTRY) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { nav.go(Route.Chat(draft = it.take(4_000), send = true)) }
        }
        intent.getStringExtra(EXTRA_CONVERSATION)?.let { nav.go(Route.Chat(id = it)) }
        when (intent.getStringExtra(EXTRA_ROUTE)) {
            "hub" -> nav.go(Route.Hub())
            "imagegen" -> nav.go(Route.ImageGen())
            "chat" -> nav.go(Route.Chat())
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.parcelableStream(): Uri? =
        if (android.os.Build.VERSION.SDK_INT >= 33) getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else getParcelableExtra(Intent.EXTRA_STREAM)

    companion object {
        const val EXTRA_ROUTE = "route"
        const val EXTRA_CONVERSATION = "conversation"
        const val ACTION_ASK = "app.lily.ai.action.ASK"
        const val ASK_ENTRY = "app.lily.ai.ui.AskEntry"
        const val EXTRA_TOOL = "tool"
        const val ACTION_IMAGE_TOOL = "app.lily.ai.action.IMAGE_TOOL"
        val NOTIFICATIONS = Manifest.permission.POST_NOTIFICATIONS
        val MICROPHONE = Manifest.permission.RECORD_AUDIO
    }
}

/**
 * A L.I.L.Y AI: na tela externa do Razr (janela pequena e quase quadrada), uma versão compacta de
 * voz; na interna, o app completo. A conversa da tela externa continua na interna ao abrir o aparelho.
 */
@Composable
fun LilyApp(nav: Nav) {
    val config = LocalConfiguration.current
    val compact = config.screenWidthDp < 600 && config.screenHeightDp < 520
    AuroraBackground(Modifier.fillMaxSize()) {
        if (compact) {
            CoverScreen(nav)
        } else {
            FullApp(nav)
        }
    }
}

@Composable
private fun FullApp(nav: Nav) {
    // Abriu o aparelho: a conversa começada na tela externa segue aqui, com a resposta em andamento.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val cover = nav.release<app.lily.ai.ui.screens.ChatSession>(Nav.COVER_KEY)
        if (cover != null) {
            val id = cover.conversationId
            if (id != null || cover.busy) nav.goWith(Route.Chat(id = id), cover) { it.dispose() } else cover.dispose()
        }
    }
    BackHandler(enabled = nav.stack.size > 1) { nav.back() }
    AnimatedContent(
        targetState = nav.current,
        transitionSpec = {
            val forward = nav.forward
            (slideInHorizontally(tween(320)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(220))) togetherWith
                (slideOutHorizontally(tween(280)) { if (forward) -it / 6 else it / 6 } + fadeOut(tween(160)))
        },
        contentKey = { it.key },
        label = "telas",
    ) { entry ->
        when (val r = entry.route) {
            Route.Home -> HomeScreen(nav)
            is Route.Chat -> ChatScreen(nav, entry.key, r)
            Route.History -> HistoryScreen(nav)
            is Route.Hub -> HubScreen(nav, r)
            is Route.Model -> ModelScreen(nav, r.id)
            Route.Tools -> ToolsScreen(nav)
            is Route.TextTool -> TextToolScreen(nav, entry.key, r)
            is Route.ImageTool -> ImageToolScreen(nav, entry.key, r)
            is Route.ImageGen -> ImageGenScreen(nav, r)
            Route.Settings -> SettingsScreen(nav)
            Route.Memory -> MemoryScreen(nav)
            Route.Diagnostics -> app.lily.ai.ui.screens.DiagnosticsScreen(nav, entry.key)
        }
    }
}
