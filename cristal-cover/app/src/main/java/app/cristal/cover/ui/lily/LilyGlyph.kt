package app.cristal.cover.ui.lily

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/** As cores do orbe da L.I.L.Y (as mesmas do app dela e da faixa de luz da ilha). */
val LilyColors = listOf(Color(0xFF7B61FF), Color(0xFF3EC6FF), Color(0xFFFF6AD5), Color(0xFFFFB36B))
val LilyAccent = Color(0xFFB79CFF)

/** O orbe da L.I.L.Y em miniatura: espectro em volta e um brilho no alto, à esquerda. */
@Composable
fun LilyGlyph(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2
        drawCircle(Brush.sweepGradient(LilyColors + LilyColors.first(), center), r)
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f), Color.Transparent), Offset(center.x - r * 0.3f, center.y - r * 0.35f), r * 0.9f), r)
        drawCircle(Color.White.copy(alpha = 0.35f), r, style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.08f))
    }
}
