package app.cristal.cover.data.display

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import app.cristal.cover.core.AppScope
import app.cristal.cover.core.safely
import app.cristal.cover.data.prefs.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** Retrato imutável de uma tela do aparelho. */
data class DisplayDesc(
    val id: Int,
    val name: String,
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val refreshRate: Float,
    val maxRefreshRate: Float,
    val state: Int,
    val flags: Int,
) {
    val isDefault: Boolean get() = id == Display.DEFAULT_DISPLAY
    val isOn: Boolean get() = state == Display.STATE_ON
    val aspect: Float get() = if (heightPx == 0) 1f else widthPx.toFloat() / heightPx

    /** Tela pequena e quase quadrada: o formato típico de uma tela externa de flip. */
    val looksLikeCover: Boolean
        get() = max(widthPx, heightPx) in 200..1500 && aspect in 0.72f..1.4f
}

data class CoverState(
    val displays: List<DisplayDesc> = emptyList(),
    /** Tela externa detectada (ou escolhida nas configurações). `null` se não houver. */
    val cover: DisplayDesc? = null,
    /** `true` fechado, `false` aberto, `null` quando o aparelho não informa. */
    val folded: Boolean? = null,
    val hingeAngle: Float? = null,
) {
    val coverId: Int get() = cover?.id ?: Display.INVALID_DISPLAY
    val hasCover: Boolean get() = cover != null
}

/**
 * Descobre e acompanha a tela externa.
 *
 * O Razr expõe a tela externa como uma tela separada (normalmente id 1). Como isso pode variar
 * entre versões do sistema, a detecção é por formato (pequena e quase quadrada) e o usuário pode
 * forçar um id nas configurações. Tudo aqui tolera telas que somem no meio do caminho.
 */
@Singleton
class CoverDisplayRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    settingsRepository: SettingsRepository,
    @AppScope scope: CoroutineScope,
) {
    private val displayManager: DisplayManager? = context.getSystemService(DisplayManager::class.java)
    private val sensorManager: SensorManager? = context.getSystemService(SensorManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val displaysFlow: Flow<List<DisplayDesc>> = callbackFlow {
        val dm = displayManager
        if (dm == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {
                trySend(snapshot())
            }

            override fun onDisplayRemoved(displayId: Int) {
                trySend(snapshot())
            }

            override fun onDisplayChanged(displayId: Int) {
                trySend(snapshot())
            }
        }
        safely("registrar DisplayListener") { dm.registerDisplayListener(listener, mainHandler) }
        trySend(snapshot())
        awaitClose { safely("remover DisplayListener") { dm.unregisterDisplayListener(listener) } }
    }.conflate().distinctUntilChanged()

    private val hingeFlow: Flow<Float?> = callbackFlow<Float?> {
        val sm = sensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        if (sm == null || sensor == null) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                event.values.firstOrNull()?.let { trySend(it) }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val ok = safely("registrar sensor da dobradiça") {
            // O sensor só avisa quando o ângulo muda; um intervalo curto deixa a transição fluida.
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        } ?: false
        if (!ok) trySend(null)
        awaitClose { safely("remover sensor da dobradiça") { sm.unregisterListener(listener) } }
    }.onStart { emit(null) }.conflate()
        // Passos de 2°: suficiente para a transição de fechamento, sem recompor a cada décimo de grau.
        .map { angle -> angle?.let { (it / 2f).toInt() * 2f } }
        .distinctUntilChanged()

    val state: StateFlow<CoverState> = combine(
        displaysFlow,
        hingeFlow,
        settingsRepository.settings.map { it.coverDisplayOverride }.distinctUntilChanged(),
    ) { displays, hinge, override ->
        val cover = pickCover(displays, override)
        val folded = when {
            hinge != null -> hinge < FOLDED_ANGLE
            // Sem sensor: fechado se a externa está ligada e a principal não.
            cover != null && !cover.isDefault -> {
                val main = displays.firstOrNull { it.isDefault }
                if (main == null) null else cover.isOn && !main.isOn
            }
            else -> null
        }
        CoverState(displays = displays, cover = cover, folded = folded, hingeAngle = hinge)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), CoverState(displays = snapshot()).let {
        it.copy(cover = pickCover(it.displays, settingsRepository.settings.value.coverDisplayOverride))
    })

    /** Leitura imediata, sem esperar o fluxo (útil em serviços). */
    fun currentCover(overrideId: Int): DisplayDesc? = pickCover(snapshot(), overrideId)

    // DISPLAY_CATEGORY_BUILT_IN_DISPLAYS é uma String embutida na compilação; em versões que não a
    // conhecem, getDisplays devolve uma lista vazia e seguimos só com a lista padrão.
    @SuppressLint("InlinedApi")
    fun snapshot(): List<DisplayDesc> {
        val dm = displayManager ?: return emptyList()
        val all = safely("listar telas") {
            val base = dm.displays.toMutableList()
            // Inclui telas embutidas que não aparecem na lista padrão em alguns aparelhos.
            safely("listar telas embutidas") {
                dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_BUILT_IN_DISPLAYS)
            }?.forEach { d -> if (base.none { it.displayId == d.displayId }) base.add(d) }
            base
        } ?: return emptyList()
        return all.mapNotNull { describe(it) }.sortedBy { it.id }
    }

    fun getDisplay(id: Int): Display? = safely("obter tela $id") { displayManager?.getDisplay(id) }

    private fun describe(display: Display): DisplayDesc? = safely("descrever tela") {
        if (!display.isValid) return@safely null
        val mode = display.mode
        val density = safely("densidade da tela") {
            context.createDisplayContext(display).resources.displayMetrics.densityDpi
        } ?: context.resources.displayMetrics.densityDpi
        val maxRate = display.supportedModes
            .filter { it.physicalWidth == mode.physicalWidth && it.physicalHeight == mode.physicalHeight }
            .maxOfOrNull { it.refreshRate } ?: mode.refreshRate
        DisplayDesc(
            id = display.displayId,
            name = display.name.orEmpty(),
            widthPx = mode.physicalWidth,
            heightPx = mode.physicalHeight,
            densityDpi = density,
            refreshRate = display.refreshRate,
            maxRefreshRate = maxRate,
            state = display.state,
            flags = display.flags,
        )
    }

    companion object {
        /** Abaixo deste ângulo consideramos o aparelho fechado. */
        const val FOLDED_ANGLE = 25f

        /** Escolhe a tela externa. A regra é pública para poder ser testada em JVM. */
        fun pickCover(displays: List<DisplayDesc>, overrideId: Int): DisplayDesc? {
            if (overrideId >= 0) {
                displays.firstOrNull { it.id == overrideId }?.let { return it }
            }
            val secondary = displays.filter { !it.isDefault && it.flags and Display.FLAG_PRIVATE == 0 }
            // 1) Tela secundária pequena e quase quadrada.
            secondary.filter { it.looksLikeCover }
                .minByOrNull { min(it.widthPx, it.heightPx) }
                ?.let { return it }
            // 2) Aparelhos que trocam a tela lógica: a própria tela padrão vira a externa.
            displays.firstOrNull { it.isDefault && it.looksLikeCover }?.let { return it }
            return null
        }
    }
}
