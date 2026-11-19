package app.lily.ai.models

import app.lily.ai.core.DeviceSnapshot

/** As quatro classificações do Model Hub. */
enum class Compat(val label: String) {
    COMPATIBLE("Compatível"),
    LIMITED("Compatível com limitações"),
    EXPERIMENTAL("Experimental"),
    INCOMPATIBLE("Incompatível"),
}

data class CompatReport(val level: Compat, val reasons: List<String>) {
    val usable: Boolean get() = level != Compat.INCOMPATIBLE
}

/**
 * Compatibilidade de verdade: só diz "compatível" quando existe um mecanismo de execução para este
 * aparelho (arquitetura, GPU quando precisa) e a memória comporta o modelo.
 */
object Compatibility {
    fun evaluate(spec: ModelSpec, device: DeviceSnapshot): CompatReport {
        val reasons = ArrayList<String>()
        val nativeRuntime = spec.runtime == Runtime.LITERT_LM || spec.runtime == Runtime.LITERT || spec.runtime == Runtime.IRIS
        if (nativeRuntime && !device.arm64) {
            return CompatReport(Compat.INCOMPATIBLE, listOf("O motor ${spec.runtime.label} deste app só tem versão para ARM de 64 bits."))
        }
        if (spec.needsGpu && !device.gpuCompute) {
            return CompatReport(Compat.INCOMPATIBLE, listOf("Precisa do OpenCL da GPU, que este aparelho não oferece."))
        }
        val ram = device.totalRamGb
        if (ram + 0.25 < spec.minRamGb) {
            return CompatReport(Compat.INCOMPATIBLE, listOf("Pede um aparelho de ${spec.ramClassGb} GB de RAM; este informa ${fmt(ram)} GB ao Android."))
        }
        var level = Compat.COMPATIBLE
        if (ram < spec.minRamGb * 1.25) {
            level = Compat.LIMITED
            reasons += "A memória fica justa (${fmt(ram)} GB): feche outros apps antes de usar."
        }
        if (spec.totalBytes > 0 && spec.totalBytes > ram * 0.45 * 1024 * 1024 * 1024) {
            level = Compat.LIMITED
            reasons += "O arquivo é grande para a memória do aparelho: o Android pode fechar outros apps."
        }
        if (spec.experimental) {
            level = Compat.EXPERIMENTAL
            reasons += "Experimental: funciona no aparelho, mas é lento e pesado."
        }
        if (reasons.isEmpty()) reasons += "Roda no aparelho com ${spec.runtime.label}."
        return CompatReport(level, reasons)
    }

    /** Espaço livre que falta para instalar (0 se cabe). Guarda 1 GB de folga para o sistema. */
    fun missingStorage(spec: ModelSpec, device: DeviceSnapshot, alreadyOnDisk: Long = 0): Long {
        val need = spec.totalBytes - alreadyOnDisk + RESERVE
        return (need - device.freeStorageBytes).coerceAtLeast(0)
    }

    const val RESERVE = 1L * 1024 * 1024 * 1024

    private fun fmt(v: Double): String = String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.1f", v)
}
