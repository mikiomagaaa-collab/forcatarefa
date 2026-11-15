package app.cristal.cover.ui.glass

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.cristal.cover.core.safely
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Direção da luz do vidro. Com [enabled], usa o sensor de gravidade para o brilho "escorrer" pela
 * borda quando o aparelho inclina. O sensor só fica ligado enquanto a tela está em primeiro plano
 * e as atualizações são filtradas, para não redesenhar a cada leitura.
 */
@Composable
fun rememberGlassLight(enabled: Boolean): State<Offset> {
    val state = remember { mutableStateOf(DefaultGlassLight) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(enabled, lifecycleOwner) {
        if (!enabled) {
            state.value = DefaultGlassLight
            return@DisposableEffect onDispose { }
        }
        val manager = context.getSystemService(SensorManager::class.java)
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var filtered = 0f
        var lastAngle = Float.NaN
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val gx = (event.values.getOrNull(0) ?: 0f) / SensorManager.GRAVITY_EARTH
                filtered += (gx.coerceIn(-1f, 1f) - filtered) * 0.15f
                val angle = BASE_ANGLE + filtered * TILT_RANGE
                if (lastAngle.isNaN() || abs(angle - lastAngle) > MIN_STEP) {
                    lastAngle = angle
                    state.value = Offset(cos(angle), sin(angle))
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        var registered = false
        fun register() {
            if (!registered && manager != null && sensor != null) {
                registered = safely("registrar sensor de gravidade") {
                    manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
                } ?: false
            }
        }
        fun unregister() {
            if (registered) {
                safely("remover sensor de gravidade") { manager?.unregisterListener(listener) }
                registered = false
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> register()
                Lifecycle.Event.ON_PAUSE -> unregister()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) register()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            unregister()
        }
    }
    return state
}

/** Ângulo de (-0.6, -0.8): luz vinda de cima, um pouco à esquerda. */
private const val BASE_ANGLE = -2.2143f
private const val TILT_RANGE = 1.0f
private const val MIN_STEP = 0.03f
