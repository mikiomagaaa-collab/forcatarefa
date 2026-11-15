package app.cristal.cover.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot

/**
 * Um "fundo" que os elementos de vidro podem amostrar.
 *
 * Quem é fundo grava o próprio conteúdo em uma [GraphicsLayer] com [glassBackdropSource]; cada
 * elemento de vidro redesenha essa camada dentro de si, deslocada para a própria posição, e aplica
 * blur e refração por cima.
 *
 * Regra importante: um elemento de vidro nunca pode estar DENTRO do conteúdo que serve de fundo
 * para ele mesmo (a camada se desenharia dentro de si própria).
 */
@Stable
class GlassBackdrop internal constructor(internal val layer: GraphicsLayer) {
    internal var sourceCoordinates: LayoutCoordinates? = null

    /** Posição de [coordinates] no espaço do fundo. */
    internal fun positionOf(coordinates: LayoutCoordinates): Offset {
        val source = sourceCoordinates
        return if (source != null && source.isAttached && coordinates.isAttached) {
            try {
                source.localPositionOf(coordinates, Offset.Zero)
            } catch (_: IllegalArgumentException) {
                // Árvores de layout diferentes (não deveria acontecer): aproxima pela raiz.
                coordinates.positionInRoot() - source.positionInRoot()
            }
        } else {
            coordinates.positionInRoot()
        }
    }
}

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

/** Marca este conteúdo como fundo para o vidro e continua desenhando-o normalmente. */
fun Modifier.glassBackdropSource(backdrop: GlassBackdrop): Modifier = this
    .onGloballyPositioned { backdrop.sourceCoordinates = it }
    .drawWithContent {
        backdrop.layer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
    }
