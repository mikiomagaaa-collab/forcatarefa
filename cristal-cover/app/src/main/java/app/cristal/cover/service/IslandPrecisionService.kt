package app.cristal.cover.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.SystemClock
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import app.cristal.cover.core.safely
import app.cristal.cover.data.island.StatusBarMetrics
import app.cristal.cover.data.island.StatusBarMetricsStore

/**
 * Modo de precisão da Liquid Island (opcional, desligado até a pessoa ligar nas configurações de
 * acessibilidade do Android). Lê só a barra de status do sistema, para a ilha saber exatamente onde
 * estão o relógio e os ícones e nunca cobri-los. Só recebe eventos da interface do sistema
 * (com.android.systemui) e não guarda nada.
 */
class IslandPrecisionService : AccessibilityService() {
    private var lastMeasure = 0L

    override fun onServiceConnected() {
        measure()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val now = SystemClock.uptimeMillis()
        if (now - lastMeasure < MIN_INTERVAL_MS) return
        lastMeasure = now
        measure()
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        StatusBarMetricsStore.publish(null)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        StatusBarMetricsStore.publish(null)
        super.onDestroy()
    }

    private fun measure() {
        safely("medir a barra de status") {
            val wm = getSystemService(WindowManager::class.java) ?: return@safely
            val metrics = wm.currentWindowMetrics
            val width = metrics.bounds.width()
            val barHeight = metrics.windowInsets.getInsets(android.view.WindowInsets.Type.statusBars()).top.takeIf { it > 0 } ?: return@safely
            val cutout = metrics.windowInsets.displayCutout?.boundingRectTop?.takeIf { !it.isEmpty }
            val cameraX = cutout?.centerX() ?: (width / 2)
            val bar = windows.firstOrNull { w ->
                val r = Rect()
                w.getBoundsInScreen(r)
                w.type == AccessibilityWindowInfo.TYPE_SYSTEM && r.top <= 0 && r.height() in 1..(barHeight * 2) && r.width() >= width * 0.9
            } ?: return@safely
            val root = bar.root ?: return@safely
            var left = 0
            var right = width
            val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
            stack.add(root to 0)
            var visited = 0
            while (stack.isNotEmpty() && visited < MAX_NODES) {
                val (node, depth) = stack.removeLast()
                visited++
                if (!node.isVisibleToUser) continue
                val r = Rect()
                node.getBoundsInScreen(r)
                if (node.childCount == 0) {
                    // Folhas pequenas dentro da faixa da barra: relógio, ícones de notificação e do sistema.
                    if (r.height() in 1..barHeight && r.width() in 1..(width / 3) && r.top < barHeight) {
                        if (r.centerX() < cameraX) left = maxOf(left, r.right) else right = minOf(right, r.left)
                    }
                } else if (depth < MAX_DEPTH) {
                    for (i in 0 until node.childCount) node.getChild(i)?.let { stack.add(it to depth + 1) }
                }
            }
            StatusBarMetricsStore.publish(StatusBarMetrics(left, right, System.currentTimeMillis()))
        }
    }

    private companion object {
        const val MIN_INTERVAL_MS = 400L
        const val MAX_NODES = 400
        const val MAX_DEPTH = 14
    }
}
