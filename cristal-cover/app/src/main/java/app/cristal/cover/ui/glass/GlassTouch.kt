package app.cristal.cover.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import app.cristal.cover.ui.theme.LocalHapticsEnabled
import kotlinx.coroutines.launch

/** Onde o dedo está sobre o vidro e com que força o efeito aparece (0..1). */
@Stable
class GlassTouchState {
    var position by mutableStateOf(Offset.Zero)
        internal set
    val amount = Animatable(0f)
    val scale = Animatable(1f)
}

@Composable
fun rememberGlassTouch(): GlassTouchState = remember { GlassTouchState() }

/**
 * Observa o toque sem consumi-lo (cliques e arrastos continuam funcionando) e anima:
 * - [GlassTouchState.amount], que o shader usa para a lente e o brilho sob o dedo;
 * - [GlassTouchState.scale], um leve "afundar" com retorno elástico.
 */
@Composable
fun Modifier.glassTouch(state: GlassTouchState, pressedScale: Float = 0.965f): Modifier {
    val scope = rememberCoroutineScope()
    return this
        .pointerInput(state) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                state.position = down.position
                scope.launch { state.amount.animateTo(1f, Motion.press) }
                scope.launch { state.scale.animateTo(pressedScale, Motion.press) }
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        state.position = change.position
                    }
                } finally {
                    scope.launch { state.amount.animateTo(0f, Motion.gentle) }
                    scope.launch { state.scale.animateTo(1f, Motion.release) }
                }
            }
        }
        .graphicsLayer {
            val s = state.scale.value
            scaleX = s
            scaleY = s
        }
}

/** Vibração curta de toque, respeitando a preferência do usuário. */
@Composable
fun rememberHaptic(strong: Boolean = false): () -> Unit {
    val enabled = LocalHapticsEnabled.current
    val haptic = LocalHapticFeedback.current
    return remember(enabled, haptic, strong) {
        {
            if (enabled) {
                haptic.performHapticFeedback(
                    if (strong) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove,
                )
            }
        }
    }
}
