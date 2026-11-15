package app.lily.keyboard.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import app.lily.keyboard.core.Key
import app.lily.keyboard.core.KeyCode
import app.lily.keyboard.core.KeyboardLayout
import app.lily.keyboard.engine.TextTools
import app.lily.keyboard.engine.TracePoint

/** Tecla posicionada: [rect] é o desenho; [touch] é a área que responde ao toque (sem buracos). */
@Immutable
class PlacedKey(val index: Int, val key: Key, val row: Int, val rect: Rect, val touch: Rect)

/**
 * Posição de todas as teclas para uma largura. As áreas de toque cobrem os espaços entre as teclas
 * (um toque entre duas teclas vai para a mais próxima) e as bordas da fileira.
 */
@Immutable
class KeyGeometry(val keys: List<PlacedKey>, val width: Float, val height: Float, val keyWidth: Float, val rowHeight: Float) {

    fun keyAt(x: Float, y: Float): PlacedKey? {
        keys.firstOrNull { it.touch.contains(Offset(x, y)) }?.let { return it }
        // Fora de tudo (borda do teclado): a tecla mais próxima.
        return keys.minByOrNull { (it.rect.center - Offset(x, y)).getDistanceSquared() }
    }

    /** Centro de cada letra (sem acento), para a digitação por deslize. */
    fun letterCenters(): Map<Char, TracePoint> {
        val map = HashMap<Char, TracePoint>()
        for (p in keys) {
            if (p.key.code != KeyCode.CHAR || p.key.label.length != 1) continue
            val c = p.key.label[0]
            if (!c.isLetter()) continue
            val f = TextTools.foldChar(c.lowercaseChar())
            if (f == c.lowercaseChar() || !map.containsKey(f)) map[f] = TracePoint(p.rect.center.x, p.rect.center.y)
        }
        return map
    }

    companion object {
        /**
         * @param rowHeight altura de cada fileira, já com o espaço entre teclas.
         * @param lastRowScale a última fileira (espaço, enter) pode ser um pouco mais alta.
         */
        fun build(
            layout: KeyboardLayout,
            width: Float,
            rowHeight: Float,
            gapX: Float,
            gapY: Float,
            sidePadding: Float,
            lastRowScale: Float = 1f,
        ): KeyGeometry {
            val maxUnits = layout.rows.maxOf { r -> r.keys.sumOf { it.width.toDouble() }.toFloat() + r.startPad + r.endPad }
                .coerceAtLeast(1f)
            val unit = (width - 2 * sidePadding) / maxUnits
            val placed = ArrayList<PlacedKey>()
            var y = 0f
            var index = 0
            for ((r, row) in layout.rows.withIndex()) {
                val h = if (r == layout.rows.lastIndex) rowHeight * lastRowScale else rowHeight
                val rowUnits = row.keys.sumOf { it.width.toDouble() }.toFloat() + row.startPad + row.endPad
                var x = sidePadding + (maxUnits - rowUnits) / 2f * unit + row.startPad * unit
                for ((k, key) in row.keys.withIndex()) {
                    val w = key.width * unit
                    val rect = Rect(x + gapX / 2, y + gapY / 2, x + w - gapX / 2, y + h - gapY / 2)
                    val touchLeft = if (k == 0) 0f else x
                    val touchRight = if (k == row.keys.lastIndex) width else x + w
                    placed.add(PlacedKey(index++, key, r, rect, Rect(touchLeft, y, touchRight, y + h)))
                    x += w
                }
                y += h
            }
            return KeyGeometry(placed, width, y, unit, rowHeight)
        }
    }
}
