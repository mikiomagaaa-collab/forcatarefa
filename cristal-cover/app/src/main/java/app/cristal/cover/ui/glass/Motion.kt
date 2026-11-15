package app.cristal.cover.ui.glass

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/** Molas usadas em todo o app, para que os movimentos tenham a mesma "personalidade". */
object Motion {
    /** Ao pressionar: rápida e quase sem quique. */
    val press: SpringSpec<Float> = spring(dampingRatio = 0.75f, stiffness = 900f)

    /** Ao soltar: volta com um quique suave, como gel. */
    val release: SpringSpec<Float> = spring(dampingRatio = 0.45f, stiffness = 420f)

    /** Painéis e gavetas. */
    val sheet: SpringSpec<Float> = spring(dampingRatio = 0.86f, stiffness = 380f)

    /** Entradas com leve exagero (pastas, menus). */
    val pop: SpringSpec<Float> = spring(dampingRatio = 0.68f, stiffness = 520f)

    /** Mudanças discretas de valor. */
    val gentle: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = 300f)

    fun <T> popOf(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.68f, stiffness = 520f)

    fun <T> gentleOf(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 300f)
}
