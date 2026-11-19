package app.lily.ai.engine

import android.graphics.Bitmap
import android.util.Log
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Real-ESRGAN General x4v3 no LiteRT: a imagem é cortada em blocos de 128×128 (com 8 px de
 * sobreposição para não marcar as emendas), cada bloco vira 512×512 e o resultado é costurado.
 * Usa a GPU e cai para a CPU se ela recusar o modelo.
 */
class SuperRes(private val modelFile: File) {

    /** Lado máximo de entrada (a saída é 4×): 1024 → 4096 px. */
    fun prepare(src: Bitmap): Bitmap {
        val side = max(src.width, src.height)
        if (side <= MAX_INPUT) return src.copy(Bitmap.Config.ARGB_8888, false)
        val s = MAX_INPUT.toFloat() / side
        return Bitmap.createScaledBitmap(src, (src.width * s).roundToInt(), (src.height * s).roundToInt(), true)
    }

    fun upscale(input: Bitmap, progress: (Float) -> Unit, cancelled: () -> Boolean): Bitmap {
        val model = open()
        try {
            val w = input.width
            val h = input.height
            val src = IntArray(w * h).also { input.getPixels(it, 0, w, 0, 0, w, h) }
            val inputs = model.createInputBuffers()
            val outputs = model.createOutputBuffers()
            try {
                // Formas conferidas no arquivo do modelo: entrada [1,128,128,3] e saída [1,3,512,512], FLOAT32.
                val out = tiled(src, w, h, { tile ->
                    inputs[0].writeFloat(tile)
                    model.run(inputs, outputs)
                    outputs[0].readFloat()
                }, progress, cancelled)
                return Bitmap.createBitmap(out, w * SCALE, h * SCALE, Bitmap.Config.ARGB_8888)
            } finally {
                inputs.forEach { runCatching { it.close() } }
                outputs.forEach { runCatching { it.close() } }
            }
        } finally {
            model.close()
        }
    }

    private fun open(): CompiledModel = try {
        CompiledModel.create(modelFile.absolutePath, CompiledModel.Options(Accelerator.GPU), null)
    } catch (t: Throwable) {
        Log.w("LilySuperRes", "GPU recusou o Real-ESRGAN; usando a CPU", t)
        CompiledModel.create(modelFile.absolutePath, CompiledModel.Options(Accelerator.CPU), null)
    }

    companion object {
        /**
         * Corta a imagem em blocos de 128×128 com 8 px de sobreposição, passa cada bloco por [run]
         * (entrada NHWC com valores de 0 a 1; saída NCHW 3×512×512) e costura só o miolo de cada
         * resultado, sem emendas. Devolve os pixels ARGB da imagem 4× maior.
         */
        fun tiled(
            src: IntArray,
            w: Int,
            h: Int,
            run: (FloatArray) -> FloatArray,
            progress: (Float) -> Unit = {},
            cancelled: () -> Boolean = { false },
        ): IntArray {
            val outW = w * SCALE
            val outH = h * SCALE
            val out = IntArray(outW * outH)
            val step = TILE - 2 * OVERLAP
            val cols = (w + step - 1) / step
            val rows = (h + step - 1) / step
            val tile = FloatArray(TILE * TILE * 3)
            val plane = OUT_TILE * OUT_TILE
            var done = 0
            for (ty in 0 until rows) {
                for (tx in 0 until cols) {
                    if (cancelled()) throw InterruptedException("cancelado")
                    // Canto do bloco (com a sobreposição), preso às bordas da imagem.
                    val x0 = (tx * step - OVERLAP).coerceIn(0, max(0, w - TILE))
                    val y0 = (ty * step - OVERLAP).coerceIn(0, max(0, h - TILE))
                    var i = 0
                    for (y in 0 until TILE) {
                        val sy = min(h - 1, y0 + y)
                        for (x in 0 until TILE) {
                            val c = src[sy * w + min(w - 1, x0 + x)]
                            tile[i++] = ((c shr 16) and 0xFF) / 255f
                            tile[i++] = ((c shr 8) and 0xFF) / 255f
                            tile[i++] = (c and 0xFF) / 255f
                        }
                    }
                    val up = run(tile)
                    // Só a parte central de cada bloco vai para a saída (a sobreposição é descartada).
                    val keepX0 = (tx * step) - x0
                    val keepY0 = (ty * step) - y0
                    val keepW = min(step, w - tx * step)
                    val keepH = min(step, h - ty * step)
                    for (y in 0 until keepH * SCALE) {
                        val oy = ty * step * SCALE + y
                        if (oy >= outH) break
                        val row = keepY0 * SCALE + y
                        for (x in 0 until keepW * SCALE) {
                            val ox = tx * step * SCALE + x
                            if (ox >= outW) break
                            val idx = row * OUT_TILE + keepX0 * SCALE + x
                            val r = (up[idx].coerceIn(0f, 1f) * 255f).roundToInt()
                            val g = (up[plane + idx].coerceIn(0f, 1f) * 255f).roundToInt()
                            val b = (up[2 * plane + idx].coerceIn(0f, 1f) * 255f).roundToInt()
                            out[oy * outW + ox] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                        }
                    }
                    done++
                    progress(done.toFloat() / (rows * cols))
                }
            }
            return out
        }

        const val TILE = 128
        const val OUT_TILE = 512
        const val SCALE = 4
        const val OVERLAP = 8
        const val MAX_INPUT = 1024
    }
}
