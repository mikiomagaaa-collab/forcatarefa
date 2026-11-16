package app.cristal.cover.data.island

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Onde estão os ícones da barra de status, medidos pelo modo de precisão (opcional).
 *
 * @param leftEdgePx borda direita do último ícone à esquerda da câmera (relógio e notificações).
 * @param rightStartPx borda esquerda do primeiro ícone à direita da câmera (sinal, Wi-Fi, bateria).
 */
data class StatusBarMetrics(val leftEdgePx: Int, val rightStartPx: Int, val measuredAt: Long)

/** Ponte entre o serviço de precisão e a ilha (os dois rodam no mesmo processo). */
object StatusBarMetricsStore {
    private val _state = MutableStateFlow<StatusBarMetrics?>(null)

    /** null quando o modo de precisão está desligado: a ilha usa a estimativa. */
    val state: StateFlow<StatusBarMetrics?> = _state.asStateFlow()

    fun publish(metrics: StatusBarMetrics?) {
        _state.value = metrics
    }
}
