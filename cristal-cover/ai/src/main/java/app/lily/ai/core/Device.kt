package app.lily.ai.core

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.Immutable
import java.io.File

/** Uma leitura real do aparelho, usada pelo roteador e pelo Model Hub. */
@Immutable
data class DeviceSnapshot(
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val lowMemory: Boolean,
    val freeStorageBytes: Long,
    val soc: String,
    val cpuCores: Int,
    val arm64: Boolean,
    /** O aparelho tem OpenCL (o acelerador gráfico que o LiteRT usa). */
    val gpuCompute: Boolean,
    /** PowerManager.THERMAL_STATUS_* (0 = normal). */
    val thermalStatus: Int,
    val batteryPercent: Int,
    val charging: Boolean,
    val powerSave: Boolean,
) {
    val totalRamGb: Double get() = totalRamBytes / GIB
    val availableRamGb: Double get() = availableRamBytes / GIB
    val freeStorageGb: Double get() = freeStorageBytes / GIB

    val thermalLabel: String
        get() = when (thermalStatus) {
            0 -> "Normal"
            1 -> "Morno"
            2 -> "Quente"
            3 -> "Muito quente"
            else -> "Crítico"
        }

    /** Hora de economizar: bateria baixa sem carregador, economia de energia ou aparelho quente. */
    val shouldSave: Boolean get() = powerSave || (batteryPercent in 0..15 && !charging) || thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE

    companion object {
        const val GIB = 1024.0 * 1024.0 * 1024.0
    }
}

class DeviceMonitor(private val context: Context) {
    fun snapshot(): DeviceSnapshot {
        val am = context.getSystemService(ActivityManager::class.java)
        val mem = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        val power = context.getSystemService(PowerManager::class.java)
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val plugged = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        return DeviceSnapshot(
            totalRamBytes = mem.totalMem,
            availableRamBytes = mem.availMem,
            lowMemory = mem.lowMemory,
            freeStorageBytes = context.filesDir.usableSpace,
            soc = socName(),
            cpuCores = Runtime.getRuntime().availableProcessors(),
            arm64 = Build.SUPPORTED_64_BIT_ABIS.contains("arm64-v8a"),
            gpuCompute = hasOpenCl(),
            thermalStatus = power?.currentThermalStatus ?: 0,
            batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else -1,
            charging = plugged,
            powerSave = power?.isPowerSaveMode == true,
        )
    }

    private fun socName(): String {
        val model = Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN }
        val maker = Build.SOC_MANUFACTURER.takeIf { it.isNotBlank() && it != Build.UNKNOWN }
        val friendly = when (model?.uppercase()) {
            "SM8475" -> "Snapdragon 8+ Gen 1"
            "SM8450" -> "Snapdragon 8 Gen 1"
            "SM8550" -> "Snapdragon 8 Gen 2"
            "SM8650" -> "Snapdragon 8 Gen 3"
            "SM8750" -> "Snapdragon 8 Elite"
            else -> null
        }
        return friendly ?: listOfNotNull(maker, model).joinToString(" ").ifBlank { Build.HARDWARE }
    }

    /** O LiteRT usa OpenCL na GPU; a biblioteca fica numa destas pastas do fabricante. */
    private fun hasOpenCl(): Boolean = listOf(
        "/vendor/lib64/libOpenCL.so",
        "/system/vendor/lib64/libOpenCL.so",
        "/system/lib64/libOpenCL.so",
        "/vendor/lib64/egl/libGLES_mali.so",
    ).any { File(it).exists() }
}
