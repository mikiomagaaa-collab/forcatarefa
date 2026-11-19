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

/**
 * Inclinação do aparelho em relação à posição em que ele está sendo segurado, de -1 a 1 em cada
 * eixo. A referência acompanha devagar a mão do usuário, então segurar parado volta ao centro.
 * Usado pelo papel de parede espacial; o valor deve ser lido só na fase de desenho.
 */
@Composable
fun rememberDeviceTilt(enabled: Boolean): State<Offset> {
    val state = remember { mutableStateOf(Offset.Zero) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(enabled, lifecycleOwner) {
        if (!enabled) {
            state.value = Offset.Zero
            return@DisposableEffect onDispose { }
        }
        val manager = context.getSystemService(SensorManager::class.java)
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var fx = Float.NaN
        var fy = 0f
        var bx = 0f
        var by = 0f
        var last = Offset.Zero
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val gx = ((event.values.getOrNull(0) ?: 0f) / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
                val gy = ((event.values.getOrNull(1) ?: 0f) / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f)
                if (fx.isNaN()) {
                    fx = gx; fy = gy; bx = gx; by = gy
                }
                fx += (gx - fx) * 0.18f
                fy += (gy - fy) * 0.18f
                bx += (fx - bx) * BASELINE_RATE
                by += (fy - by) * BASELINE_RATE
                val next = Offset(
                    (-(fx - bx) / TILT_SPAN).coerceIn(-1f, 1f),
                    ((fy - by) / TILT_SPAN).coerceIn(-1f, 1f),
                )
                if (abs(next.x - last.x) > TILT_STEP || abs(next.y - last.y) > TILT_STEP) {
                    last = next
                    state.value = next
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        var registered = false
        fun register() {
            if (!registered && manager != null && sensor != null) {
                registered = safely("registrar sensor de inclinação") {
                    manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
                } ?: false
            }
        }
        fun unregister() {
            if (registered) {
                safely("remover sensor de inclinação") { manager?.unregisterListener(listener) }
                registered = false
            }
            fx = Float.NaN
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

/** Quanto da gravidade (fração de g) corresponde à inclinação máxima (cerca de 17 graus). */
private const val TILT_SPAN = 0.3f
private const val TILT_STEP = 0.006f
/** A referência alcança a nova posição da mão em cerca de 2 segundos. */
private const val BASELINE_RATE = 0.012f

/** Ângulo de (-0.6, -0.8): luz vinda de cima, um pouco à esquerda. */
private const val BASE_ANGLE = -2.2143f
private const val TILT_RANGE = 1.0f
private const val MIN_STEP = 0.03f
