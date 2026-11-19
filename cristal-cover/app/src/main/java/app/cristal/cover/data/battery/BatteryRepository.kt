package app.cristal.cover.data.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import app.cristal.cover.core.AppScope
import app.cristal.cover.core.safely
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

data class BatteryState(
    val percent: Int = 100,
    val charging: Boolean = false,
    val full: Boolean = false,
    val powerSave: Boolean = false,
    val temperatureC: Float? = null,
)

/** Estado da bateria e do modo de economia, atualizado por broadcasts do sistema. */
@Singleton
class BatteryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @AppScope scope: CoroutineScope,
) {
    private val powerManager: PowerManager? = context.getSystemService(PowerManager::class.java)

    val state: StateFlow<BatteryState> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                trySend(read(if (intent?.action == Intent.ACTION_BATTERY_CHANGED) intent else null))
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        val sticky = safely("registrar receiver de bateria") {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
        trySend(read(sticky))
        awaitClose { safely("remover receiver de bateria") { context.unregisterReceiver(receiver) } }
    }.conflate().distinctUntilChanged().stateIn(scope, SharingStarted.WhileSubscribed(5_000), read(null))

    /** Leitura pontual, para quem não quer manter o fluxo vivo. */
    fun isPowerSave(): Boolean = safely("ler modo de economia") { powerManager?.isPowerSaveMode } ?: false

    private fun read(batteryIntent: Intent?): BatteryState {
        val intent = batteryIntent ?: safely("ler bateria") {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val tenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else 100
        return BatteryState(
            percent = percent,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING,
            full = status == BatteryManager.BATTERY_STATUS_FULL,
            powerSave = isPowerSave(),
            temperatureC = if (tenths == Int.MIN_VALUE) null else tenths / 10f,
        )
    }
}
