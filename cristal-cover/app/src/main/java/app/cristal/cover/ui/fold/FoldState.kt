package app.cristal.cover.ui.fold

import android.animation.ValueAnimator
import android.os.SystemClock
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import app.cristal.cover.core.safely
import kotlinx.coroutines.delay

/**
 * O progresso da transição de dobra nesta tela (1 = assentada), animado por uma mola que segue a
 * dobradiça: suave em qualquer velocidade de abrir ou fechar, sem saltos. Desligado (ou com as
 * animações do Android desligadas), fica em 1.
 */
@Composable
fun rememberFoldProgress(enabled: Boolean, onCover: Boolean, displayId: Int, hinge: Float?): State<Float> {
    val reduceMotion = remember { !ValueAnimator.areAnimatorsEnabled() }
    val model = remember(reduceMotion) { FoldMotionModel(reduceMotion) }
    val progress = remember { Animatable(1f) }
    var target by remember { mutableFloatStateOf(1f) }
    var arrival by remember { mutableStateOf(0) }
    val hingeNow by rememberUpdatedState(hinge)
    val coverNow by rememberUpdatedState(onCover)

    // Trocou de tela física (abriu ou fechou): começa a transição.
    LaunchedEffect(displayId, enabled) {
        if (!enabled) {
            target = 1f
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        model.onDisplay(coverNow, hingeNow, SystemClock.uptimeMillis())
        progress.snapTo(model.arrivalStart)
        target = model.target
        arrival++
    }
    // A dobradiça mexeu.
    LaunchedEffect(hinge, enabled) {
        val angle = hinge
        if (enabled && angle != null) {
            model.onAngle(angle, SystemClock.uptimeMillis())
            target = model.target
        }
    }
    // Parou no meio (modo flex): volta a ficar nítida.
    LaunchedEffect(enabled) {
        while (enabled) {
            delay(120)
            if (model.tick(SystemClock.uptimeMillis())) target = model.target
        }
    }
    // A mola até o alvo; ela parte da velocidade atual quando o alvo muda (sem degraus).
    LaunchedEffect(target, arrival) {
        progress.animateTo(target, spring(dampingRatio = 1f, stiffness = if (target >= 1f) 220f else 420f))
        if (target >= 1f) model.arrived()
    }
    return progress.asState()
}

/**
 * Onde está a dobra nesta tela. Na interna, pelo Jetpack WindowManager (FoldingFeature: posição e
 * orientação reais da dobradiça) quando o aparelho informa; senão, o meio da tela, na horizontal em
 * pé e na vertical deitado. Na externa, a dobradiça fica na borda de baixo.
 */
@Composable
fun rememberFoldGeometry(onCover: Boolean): FoldGeometry {
    val config = LocalConfiguration.current
    val fallback = when {
        onCover -> FoldGeometry.Cover
        config.screenHeightDp >= config.screenWidthDp -> FoldGeometry.InnerPortrait
        else -> FoldGeometry(horizontal = false, position = 0.5f)
    }
    val activity = LocalActivity.current
    val feature by produceState<FoldGeometry?>(null, activity, onCover) {
        if (activity == null || onCover) return@produceState
        val tracker = safely("ler a dobra (WindowManager)") { WindowInfoTracker.getOrCreate(activity) } ?: return@produceState
        safely("acompanhar a dobra (WindowManager)") {
            tracker.windowLayoutInfo(activity).collect { info ->
                val fold = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
                val window = activity.window?.decorView
                value = if (fold == null || window == null || window.width == 0 || window.height == 0) {
                    null
                } else {
                    val horizontal = fold.orientation == FoldingFeature.Orientation.HORIZONTAL
                    val center = if (horizontal) fold.bounds.exactCenterY() / window.height else fold.bounds.exactCenterX() / window.width
                    FoldGeometry(horizontal, center.coerceIn(0f, 1f))
                }
            }
        }
    }
    return if (onCover) FoldGeometry.Cover else feature ?: fallback
}
