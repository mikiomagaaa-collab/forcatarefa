package app.lily.ai.engine

import android.graphics.Bitmap
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Aprimoramento clássico, sem modelo: remoção de ruído que preserva bordas (filtro bilateral),
 * nitidez (máscara de nitidez) e ajuste automático de tons. Tudo no aparelho, em segundos.
 */
object Enhance {
    /** Lado máximo processado (fotos maiores são reduzidas antes, para caber no tempo e na memória). */
    const val MAX_SIDE = 2048

    fun fit(bitmap: Bitmap, maxSide: Int = MAX_SIDE): Bitmap {
        val side = max(bitmap.width, bitmap.height)
        if (side <= maxSide) return bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val s = maxSide.toFloat() / side
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * s).roundToInt(), (bitmap.height * s).roundToInt(), true)
    }

    /**
     * Filtro bilateral 5×5: suaviza o ruído dentro das áreas e mantém as bordas. [strength] de 0 a 1
     * controla o quanto diferenças de cor contam como "borda".
     */
    fun denoise(src: Bitmap, strength: Float = 0.5f, progress: (Float) -> Unit = {}): Bitmap {
        val w = src.width
        val h = src.height
        val input = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
        val output = IntArray(w * h)
        val radius = 2
        val sigmaRange = 10f + 40f * strength.coerceIn(0f, 1f)
        val rangeLut = FloatArray(256 * 3) { d -> exp(-(d * d) / (2f * sigmaRange * sigmaRange * 3f)) }
        val spatial = FloatArray((2 * radius + 1) * (2 * radius + 1)) { i ->
            val dx = i % (2 * radius + 1) - radius
            val dy = i / (2 * radius + 1) - radius
            exp(-(dx * dx + dy * dy) / (2f * 1.8f * 1.8f))
        }
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = input[y * w + x]
                val cr = (c shr 16) and 0xFF
                val cg = (c shr 8) and 0xFF
                val cb = c and 0xFF
                var sr = 0f
                var sg = 0f
                var sb = 0f
                var sw = 0f
                var k = 0
                for (dy in -radius..radius) {
                    val yy = min(h - 1, max(0, y + dy))
                    for (dx in -radius..radius) {
                        val xx = min(w - 1, max(0, x + dx))
                        val p = input[yy * w + xx]
                        val pr = (p shr 16) and 0xFF
                        val pg = (p shr 8) and 0xFF
                        val pb = p and 0xFF
                        val diff = kotlin.math.abs(pr - cr) + kotlin.math.abs(pg - cg) + kotlin.math.abs(pb - cb)
                        val wgt = spatial[k++] * rangeLut[diff]
                        sr += pr * wgt
                        sg += pg * wgt
                        sb += pb * wgt
                        sw += wgt
                    }
                }
                output[y * w + x] = (c and 0xFF000000.toInt()) or
                    ((sr / sw).roundToInt() shl 16) or ((sg / sw).roundToInt() shl 8) or (sb / sw).roundToInt()
            }
            if (y % 64 == 0) progress(y.toFloat() / h)
        }
        progress(1f)
        return Bitmap.createBitmap(output, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Máscara de nitidez: realça bordas finas ([amount] 0–1.5). */
    fun sharpen(src: Bitmap, amount: Float = 0.6f): Bitmap {
        val w = src.width
        val h = src.height
        val p = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
        val out = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var r = 0
                var g = 0
                var b = 0
                for (dy in -1..1) for (dx in -1..1) {
                    val q = p[min(h - 1, max(0, y + dy)) * w + min(w - 1, max(0, x + dx))]
                    r += (q shr 16) and 0xFF
                    g += (q shr 8) and 0xFF
                    b += q and 0xFF
                }
                val c = p[y * w + x]
                fun ch(v: Int, blur: Int): Int = (v + amount * (v - blur / 9f)).roundToInt().coerceIn(0, 255)
                out[y * w + x] = (c and 0xFF000000.toInt()) or (ch((c shr 16) and 0xFF, r) shl 16) or (ch((c shr 8) and 0xFF, g) shl 8) or ch(c and 0xFF, b)
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Tons automáticos: estica os níveis (cortando 0,5% nas pontas) e dá um pouco de vida às cores. */
    fun autoTone(src: Bitmap, saturation: Float = 1.08f): Bitmap {
        val w = src.width
        val h = src.height
        val p = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
        val hist = IntArray(256)
        for (c in p) hist[(((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000]++
        val cut = (p.size * 0.005).toInt()
        var lo = 0
        var acc = 0
        while (lo < 255 && acc + hist[lo] <= cut) acc += hist[lo++]
        var hi = 255
        acc = 0
        while (hi > 0 && acc + hist[hi] <= cut) acc += hist[hi--]
        val range = max(1, hi - lo)
        val lut = IntArray(256) { v -> ((v - lo) * 255f / range).roundToInt().coerceIn(0, 255) }
        for (i in p.indices) {
            val c = p[i]
            var r = lut[(c shr 16) and 0xFF].toFloat()
            var g = lut[(c shr 8) and 0xFF].toFloat()
            var b = lut[c and 0xFF].toFloat()
            val l = 0.299f * r + 0.587f * g + 0.114f * b
            r = l + (r - l) * saturation
            g = l + (g - l) * saturation
            b = l + (b - l) * saturation
            p[i] = (c and 0xFF000000.toInt()) or (r.roundToInt().coerceIn(0, 255) shl 16) or (g.roundToInt().coerceIn(0, 255) shl 8) or b.roundToInt().coerceIn(0, 255)
        }
        return Bitmap.createBitmap(p, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Ampliação clássica (sem modelo): interpolação suave e um pouco de nitidez. */
    fun upscaleClassic(src: Bitmap, factor: Int = 2): Bitmap {
        val maxSide = max(src.width, src.height) * factor
        val f = if (maxSide > 4096) 4096f / max(src.width, src.height) else factor.toFloat()
        val big = Bitmap.createScaledBitmap(src, (src.width * f).roundToInt(), (src.height * f).roundToInt(), true)
        return sharpen(big, 0.35f)
    }
}
