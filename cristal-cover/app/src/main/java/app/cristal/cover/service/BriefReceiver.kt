package app.cristal.cover.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.Display
import app.cristal.cover.data.brief.BriefContract
import app.cristal.cover.data.brief.BriefRepository
import app.cristal.cover.ui.brief.BriefConfirmActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Recebe as sugestões do L.I.L.Y Keyboard. Protegido por permissão de assinatura: só um app
 * assinado com a mesma chave do Cristal consegue mandar. Responde ao teclado (broadcast ordenado)
 * se a ilha mostrou, se ficou guardado para o Now Brief ou se o Info Brief está desligado.
 */
@AndroidEntryPoint
class BriefReceiver : BroadcastReceiver() {

    @Inject lateinit var brief: BriefRepository

    override fun onReceive(context: Context, intent: Intent) {
        val now = System.currentTimeMillis()
        val suggestion = BriefContract.parse(intent, now) ?: return
        val display = intent.getIntExtra(BriefContract.EXTRA_DISPLAY, Display.DEFAULT_DISPLAY)
        when (intent.action) {
            BriefContract.ACTION_SUGGEST -> {
                val x = intent.getFloatExtra(BriefContract.EXTRA_ORIGIN_X, Float.NaN).takeIf { !it.isNaN() }
                val y = intent.getFloatExtra(BriefContract.EXTRA_ORIGIN_Y, Float.NaN).takeIf { !it.isNaN() }
                val code = brief.receive(suggestion, x, y, display)
                if (isOrderedBroadcast) resultCode = code
            }
            BriefContract.ACTION_ACCEPT -> {
                brief.receive(suggestion, null, null, Display.INVALID_DISPLAY)
                BriefConfirmActivity.open(context, suggestion, display)
                if (isOrderedBroadcast) resultCode = BriefContract.RESULT_SHOWN
            }
        }
    }
}
