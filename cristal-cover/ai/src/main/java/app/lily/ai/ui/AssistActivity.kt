package app.lily.ai.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import app.lily.ai.LilyAiApp
import app.lily.ai.data.LilyTheme as ThemeChoice
import app.lily.ai.ui.screens.AssistPanel
import app.lily.ai.ui.theme.LilyTheme

/**
 * A L.I.L.Y por voz: um painel em lente no topo da tela, logo abaixo da Liquid Island, por cima do
 * app que estiver aberto. Abre ouvindo (a pessoa acabou de chamar: segurou a ilha, tocou no widget
 * ou usou o assistente do Android). Tocar fora fecha; uma resposta em andamento continua e aparece
 * na ilha.
 */
class AssistActivity : ComponentActivity() {
    private var pendingPermission: ((Boolean) -> Unit)? = null
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingPermission?.invoke(granted)
        pendingPermission = null
    }
    private val permissions = object : Permissions {
        override fun has(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        override fun request(permission: String, then: (Boolean) -> Unit) {
            if (has(permission)) return then(true)
            pendingPermission = then
            permissionLauncher.launch(permission)
        }
    }

    /** Cada chamada (abrir de novo) começa a ouvir outra vez. */
    private var listenTrigger by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val graph = (application as LilyAiApp).graphOrNull
        if (graph == null) {
            startActivity(app.lily.ai.diagnostics.CrashReport.intent(this))
            finish()
            return
        }
        app.lily.ai.ui.theme.OrbShaderEnabled = !graph.safeMode
        val session = graph.assist.session()
        if (savedInstanceState == null && intent?.getBooleanExtra(EXTRA_SILENT, false) != true) listenTrigger++
        setContent {
            LilyTheme(ThemeChoice.LIQUID, 0xFFB79CFF) {
                CompositionLocalProvider(LocalGraph provides graph, LocalPermissions provides permissions) {
                    AssistPanel(
                        session = session,
                        listenTrigger = listenTrigger,
                        onClose = ::finish,
                        onOpenApp = { id ->
                            startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_CONVERSATION, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            finish()
                        },
                        onHub = {
                            startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "hub").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            finish()
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!intent.getBooleanExtra(EXTRA_SILENT, false)) listenTrigger++
    }

    override fun onStart() {
        super.onStart()
        (application as LilyAiApp).graphOrNull?.assist?.attach()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) (application as LilyAiApp).graphOrNull?.assist?.detach()
    }

    companion object {
        /** Reabrir só para ver a resposta (pelo aviso da ilha), sem começar a ouvir. */
        const val EXTRA_SILENT = "silent"
    }
}
