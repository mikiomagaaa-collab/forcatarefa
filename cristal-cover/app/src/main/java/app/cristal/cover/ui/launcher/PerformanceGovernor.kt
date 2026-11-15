package app.cristal.cover.ui.launcher

import android.os.Handler
import android.os.Looper
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.cristal.cover.core.safely
import app.cristal.cover.data.prefs.GlassQuality
import app.cristal.cover.data.prefs.RefreshMode
import app.cristal.cover.data.prefs.Settings

/**
 * Mede o tempo real de cada quadro (FrameMetrics) e reage quando o aparelho não dá conta:
 *
 * - se mais de 20% dos quadros de uma janela estouram 60 fps, a qualidade do vidro cai um nível
 *   (Líquido → Blur → Leve) até a tela ser recriada;
 * - se os quadros não cabem no orçamento de 144 Hz, o pedido de taxa máxima é retirado e o sistema
 *   volta a decidir a taxa sozinho. É assim que o app usa 144 Hz só "quando há folga".
 *
 * Só há medição enquanto algo está animando; com a tela parada nenhum quadro é produzido.
 */
class PerformanceGovernor(private val window: Window) {

    /** Quantos níveis de qualidade foram retirados (0 a 2). */
    var qualityPenalty by mutableIntStateOf(0)
        private set

    /** `false` quando os quadros não cabem em 144 Hz de forma consistente. */
    var highRateSustainable by mutableStateOf(true)
        private set

    private var frames = 0
    private var over60 = 0
    private var over144 = 0
    private var started = false

    private val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
        if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L) return@OnFrameMetricsAvailableListener
        val totalMs = metrics.getMetric(FrameMetrics.TOTAL_DURATION) / 1_000_000f
        frames++
        if (totalMs > BUDGET_60_MS) over60++
        if (totalMs > BUDGET_144_MS) over144++
        if (frames >= WINDOW) {
            if (over60 > frames * 0.20f && qualityPenalty < 2) qualityPenalty++
            if (over144 > frames * 0.35f) highRateSustainable = false
            frames = 0
            over60 = 0
            over144 = 0
        }
    }

    fun start() {
        if (started) return
        started = safely("iniciar medição de quadros") {
            window.addOnFrameMetricsAvailableListener(listener, Handler(Looper.getMainLooper()))
            true
        } ?: false
    }

    fun stop() {
        if (!started) return
        started = false
        safely("parar medição de quadros") { window.removeOnFrameMetricsAvailableListener(listener) }
        frames = 0
        over60 = 0
        over144 = 0
    }

    /** Qualidade efetiva do vidro, considerando economia de bateria e a penalidade medida. */
    fun effectiveQuality(settings: Settings, powerSave: Boolean): GlassQuality {
        if (powerSave) return GlassQuality.LIGHT
        if (!settings.adaptivePerformance) return settings.glassQuality
        val index = (settings.glassQuality.ordinal - qualityPenalty).coerceAtLeast(0)
        return GlassQuality.entries[index]
    }

    /** Aplica a preferência de taxa de atualização à janela. */
    fun applyRefreshMode(mode: RefreshMode, powerSave: Boolean) {
        safely("aplicar taxa de atualização") {
            val display = window.decorView.display ?: return@safely
            val attrs = window.attributes
            when {
                mode == RefreshMode.SAVER || powerSave -> {
                    attrs.preferredDisplayModeId = 0
                    attrs.preferredRefreshRate = 60f
                }

                mode == RefreshMode.MAX && highRateSustainable -> {
                    val current = display.mode
                    val best = display.supportedModes
                        .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                        .maxByOrNull { it.refreshRate }
                    attrs.preferredRefreshRate = 0f
                    attrs.preferredDisplayModeId = best?.modeId ?: 0
                }

                else -> {
                    attrs.preferredDisplayModeId = 0
                    attrs.preferredRefreshRate = 0f
                }
            }
            window.attributes = attrs
        }
    }

    private companion object {
        const val WINDOW = 150
        const val BUDGET_60_MS = 17.5f
        const val BUDGET_144_MS = 7.6f
    }
}
