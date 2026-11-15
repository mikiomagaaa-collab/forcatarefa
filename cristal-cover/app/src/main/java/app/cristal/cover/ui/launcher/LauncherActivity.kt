package app.cristal.cover.ui.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import app.cristal.cover.core.safely
import dagger.hilt.android.AndroidEntryPoint

/**
 * A tela inicial. Funciona como app comum (aberta pelo ícone ou pelo painel de apps da tela
 * externa), como launcher padrão (HOME) e como launcher de telas secundárias (SECONDARY_HOME).
 *
 * As mudanças de configuração mais comuns num dobrável (tamanho, densidade, troca de tela) estão
 * declaradas no manifesto, então fechar ou abrir o aparelho só refaz o layout, sem recriar a tela.
 */
@AndroidEntryPoint
class LauncherActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()
    private var governor: PerformanceGovernor? = null
    private var receiverRegistered = false

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Ao apagar a tela, fecha gaveta e painéis para a próxima vez começar "limpa".
            viewModel.requestHome()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        safely("configurar recorte de tela") {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            window.isNavigationBarContrastEnforced = false
        }
        val perf = PerformanceGovernor(window)
        governor = perf
        setContent { LauncherRoot(viewModel = viewModel, governor = perf) }

        receiverRegistered = safely("registrar receiver de tela apagada") {
            ContextCompat.registerReceiver(
                this,
                screenOffReceiver,
                IntentFilter(Intent.ACTION_SCREEN_OFF),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            true
        } ?: false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Tecla/gesto de início com o launcher já aberto: volta para a página principal.
        viewModel.requestHome()
    }

    override fun onResume() {
        super.onResume()
        governor?.start()
        viewModel.onResume()
    }

    override fun onPause() {
        governor?.stop()
        super.onPause()
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            safely("remover receiver de tela apagada") { unregisterReceiver(screenOffReceiver) }
            receiverRegistered = false
        }
        super.onDestroy()
    }
}
