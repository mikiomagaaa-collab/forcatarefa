package app.cristal.cover.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cristal.cover.data.home.HomeItem
import app.cristal.cover.ui.glass.LocalGlassSpec
import app.cristal.cover.ui.glass.rememberHaptic

/** Lógica do redimensionamento: quais tamanhos cabem a partir do canto do widget. */
internal object WidgetSizing {
    /** Células ocupadas por outros itens da página (o próprio widget fica de fora). */
    fun occupied(items: List<HomeItem>, ignoreId: Long): Set<Pair<Int, Int>> = buildSet {
        for (item in items) {
            if (item.id == ignoreId) continue
            for (x in item.cellX until item.cellX + item.spanX) {
                for (y in item.cellY until item.cellY + item.spanY) add(x to y)
            }
        }
    }

    fun fits(x: Int, y: Int, spanX: Int, spanY: Int, cols: Int, rows: Int, occupied: Set<Pair<Int, Int>>): Boolean {
        if (spanX < 1 || spanY < 1 || x + spanX > cols || y + spanY > rows) return false
        for (cx in x until x + spanX) for (cy in y until y + spanY) if ((cx to cy) in occupied) return false
        return true
    }
}

/**
 * Mudar o tamanho de um widget: a grade da página aparece em miniatura; toque numa célula para
 * levar o canto do widget até ela, ou use os botões de largura e altura. Células de outros itens
 * ficam bloqueadas.
 */
@Composable
fun WidgetResizeDialog(
    widget: HomeItem.Widget,
    pageItems: List<HomeItem>,
    cols: Int,
    rows: Int,
    onApply: (spanX: Int, spanY: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptic = rememberHaptic()
    val ink = LocalGlassSpec.current.contentColor
    val occupied = remember(pageItems, widget.id) { WidgetSizing.occupied(pageItems, widget.id) }
    var spanX by remember { mutableIntStateOf(widget.spanX) }
    var spanY by remember { mutableIntStateOf(widget.spanY) }
    fun tryResize(sx: Int, sy: Int) {
        if (WidgetSizing.fits(widget.cellX, widget.cellY, sx, sy, cols, rows, occupied)) {
            haptic()
            spanX = sx
            spanY = sy
        }
    }
    GlassDialog(onDismiss = onDismiss) {
        // Compacto o bastante para caber na tela externa (426 dp de altura).
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tamanho do widget", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = ink, modifier = Modifier.weight(1f))
            Text("$spanX × $spanY", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = ink)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
                .height((rows * 26).coerceIn(90, 170).dp)
                .semantics { contentDescription = "Grade da página: toque numa célula para levar o canto do widget até ela" },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (y in 0 until rows) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (x in 0 until cols) {
                        val inside = x >= widget.cellX && x < widget.cellX + spanX && y >= widget.cellY && y < widget.cellY + spanY
                        val blocked = (x to y) in occupied
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    when {
                                        inside -> Color(0xFFB9A2FF).copy(alpha = 0.85f)
                                        blocked -> ink.copy(alpha = 0.08f)
                                        else -> ink.copy(alpha = 0.18f)
                                    },
                                )
                                .border(if (x == widget.cellX && y == widget.cellY) 2.dp else 0.dp, Color.White, RoundedCornerShape(6.dp))
                                .pointerInput(x, y) {
                                    detectTapGestures {
                                        if (x >= widget.cellX && y >= widget.cellY) tryResize(x - widget.cellX + 1, y - widget.cellY + 1)
                                    }
                                },
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Stepper("Largura", spanX, { tryResize(spanX - 1, spanY) }, { tryResize(spanX + 1, spanY) })
            Stepper("Altura", spanY, { tryResize(spanX, spanY - 1) }, { tryResize(spanX, spanY + 1) })
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("Cancelar", Modifier.weight(1f), onClick = onDismiss)
            GlassButton("Aplicar", Modifier.weight(1f), primary = true) { onApply(spanX, spanY) }
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    val ink = LocalGlassSpec.current.contentColor
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = ink.copy(alpha = 0.7f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StepButton(Icons.Rounded.Remove, "Diminuir ${label.lowercase()}", onMinus)
            Text("$value", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = ink)
            StepButton(Icons.Rounded.Add, "Aumentar ${label.lowercase()}", onPlus)
        }
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    val ink = LocalGlassSpec.current.contentColor
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(ink.copy(alpha = 0.14f))
            .semantics { contentDescription = description }
            .pointerInput(Unit) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = ink)
    }
}
