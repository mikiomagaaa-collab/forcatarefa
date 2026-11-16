package app.cristal.cover.data.apps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import kotlin.math.roundToInt

/**
 * Resultado da renderização de um ícone.
 *
 * @param mono glifo monocromático em ALPHA_8 (só a forma): o ícone temático do app ou, quando o
 * app não oferece, um glifo gerado a partir do próprio ícone.
 * @param accent cor principal do ícone (ARGB), para o estilo escuro.
 */
class RenderedIcon(val full: Bitmap, val mono: Bitmap?, val maskable: Boolean, val accent: Int = DEFAULT_ACCENT)

private const val DEFAULT_ACCENT = 0xFFD9D6E3.toInt()

/**
 * Transforma qualquer Drawable de ícone em um bitmap quadrado.
 *
 * - Ícones adaptativos são desenhados "sangrando" até a borda, para que qualquer forma possa ser
 *   aplicada depois sem deixar cantos vazios.
 * - Ícones antigos com transparência ganham uma placa clara por baixo.
 */
object IconRenderer {
    /** As camadas de um ícone adaptativo têm 108dp, dos quais 72dp são visíveis. */
    private const val ADAPTIVE_LAYER_SCALE = 108f / 72f
    private const val LEGACY_INSET_SCALE = 0.70f
    private const val PLATE_COLOR = 0xFFF4F5F8.toInt()

    fun render(drawable: Drawable, sizePx: Int, fromIconPack: Boolean): RenderedIcon {
        val size = sizePx.coerceIn(48, 512)
        val base = when {
            fromIconPack -> RenderedIcon(drawFitted(drawable, size, 1f, null), null, maskable = false)
            drawable is AdaptiveIconDrawable -> renderAdaptive(drawable, size)
            else -> renderLegacy(drawable, size)
        }
        // Glifo para os estilos monocromáticos: o temático do app, ou um gerado para qualquer app.
        val themed = base.mono?.let { argb -> runCatching { argb.extractAlpha() }.getOrNull().also { argb.recycle() } }
        val glyph = themed ?: runCatching { MonoGlyph.forced(drawable, size, base.full) }.getOrNull()
        return RenderedIcon(base.full, glyph, base.maskable, MonoGlyph.dominantColor(base.full))
    }

    private fun renderAdaptive(icon: AdaptiveIconDrawable, size: Int): RenderedIcon {
        val layer = (size * ADAPTIVE_LAYER_SCALE).roundToInt()
        val offset = -(layer - size) / 2
        val full = createBitmap(size, size)
        val canvas = Canvas(full)
        canvas.drawColor(Color.WHITE)
        icon.background?.let {
            it.setBounds(offset, offset, offset + layer, offset + layer)
            it.draw(canvas)
        }
        icon.foreground?.let {
            it.setBounds(offset, offset, offset + layer, offset + layer)
            it.draw(canvas)
        }
        val mono = icon.monochrome?.let { glyph ->
            runCatching {
                val bmp = createBitmap(size, size)
                val c = Canvas(bmp)
                val d = glyph.constantState?.newDrawable()?.mutate() ?: glyph.mutate()
                d.colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
                d.setBounds(offset, offset, offset + layer, offset + layer)
                d.draw(c)
                bmp
            }.getOrNull()
        }
        return RenderedIcon(full, mono, maskable = true)
    }

    private fun renderLegacy(drawable: Drawable, size: Int): RenderedIcon {
        val raw = drawFitted(drawable, size, 1f, null)
        if (isOpaqueSquare(raw)) return RenderedIcon(raw, null, maskable = true)
        val plated = drawFitted(drawable, size, LEGACY_INSET_SCALE, PLATE_COLOR)
        raw.recycle()
        return RenderedIcon(plated, null, maskable = true)
    }

    private fun drawFitted(drawable: Drawable, size: Int, scale: Float, background: Int?): Bitmap {
        val bmp = createBitmap(size, size)
        val canvas = Canvas(bmp)
        if (background != null) canvas.drawColor(background)
        val iw = drawable.intrinsicWidth.takeIf { it > 0 } ?: size
        val ih = drawable.intrinsicHeight.takeIf { it > 0 } ?: size
        val fit = minOf(size.toFloat() / iw, size.toFloat() / ih) * scale
        val w = (iw * fit).roundToInt().coerceAtLeast(1)
        val h = (ih * fit).roundToInt().coerceAtLeast(1)
        val left = (size - w) / 2
        val top = (size - h) / 2
        drawable.setBounds(left, top, left + w, top + h)
        drawable.draw(canvas)
        return bmp
    }

    /** Considera "quadrado cheio" quando os quatro cantos e as bordas centrais são opacos. */
    private fun isOpaqueSquare(bmp: Bitmap): Boolean {
        val last = bmp.width - 1
        val mid = bmp.width / 2
        val points = intArrayOf(1, 1, last - 1, 1, 1, last - 1, last - 1, last - 1, mid, 1, mid, last - 1, 1, mid, last - 1, mid)
        var i = 0
        while (i < points.size) {
            if (Color.alpha(bmp[points[i], points[i + 1]]) < 245) return false
            i += 2
        }
        return true
    }

    /** Ícone genérico para quando o sistema não entrega nenhum drawable. */
    fun placeholder(sizePx: Int): Bitmap {
        val bmp = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bmp)
        canvas.drawColor(0xFF8E8E93.toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx * 0.18f, paint)
        return bmp
    }
}

/**
 * Gera um glifo monocromático para apps que não oferecem ícone temático, para os estilos "vidro
 * monocromático", "colorido" e "escuro" valerem para todos os apps.
 *
 * 1. Em ícones adaptativos, a camada da frente costuma ser o símbolo sobre fundo transparente: a
 *    transparência dela já é a forma.
 * 2. Nos outros (ou quando a camada da frente cobre quase tudo), separa o símbolo do fundo pela
 *    diferença de cor em relação à borda do ícone.
 *
 * O resultado é centralizado e ajustado para ocupar 56% do ícone, como os glifos temáticos.
 */
internal object MonoGlyph {
    private const val ADAPTIVE_LAYER_SCALE = 108f / 72f
    private const val TARGET = 0.56f

    fun forced(drawable: Drawable, size: Int, full: Bitmap): Bitmap? {
        if (drawable is AdaptiveIconDrawable) {
            val fg = drawable.foreground
            if (fg != null) {
                val layer = (size * ADAPTIVE_LAYER_SCALE).roundToInt()
                val offset = -(layer - size) / 2
                val bmp = createBitmap(size, size)
                fg.setBounds(offset, offset, offset + layer, offset + layer)
                fg.draw(Canvas(bmp))
                val pixels = IntArray(size * size)
                bmp.getPixels(pixels, 0, size, 0, 0, size, size)
                bmp.recycle()
                val alpha = ByteArray(pixels.size) { i -> (pixels[i] ushr 24).toByte() }
                val coverage = alpha.count { (it.toInt() and 0xFF) > 128 } / alpha.size.toFloat()
                if (coverage in 0.015f..0.62f) return normalize(alpha, size)
            }
        }
        return normalize(contrastMask(full), size)
    }

    /** Alfa de cada pixel pela distância de cor até o fundo (média da borda do ícone). */
    fun contrastMask(full: Bitmap): ByteArray {
        val w = full.width
        val h = full.height
        val pixels = IntArray(w * h)
        full.getPixels(pixels, 0, w, 0, 0, w, h)
        val inset = (w * 0.08f).roundToInt().coerceAtLeast(1)
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0
        for (i in inset until w - inset step 3) {
            for (p in intArrayOf(pixels[inset * w + i], pixels[(h - 1 - inset) * w + i], pixels[i * w + inset], pixels[i * w + (w - 1 - inset)])) {
                r += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF
                b += p and 0xFF
                n++
            }
        }
        if (n == 0) return ByteArray(pixels.size)
        val br = r / n
        val bg = g / n
        val bb = b / n
        return ByteArray(pixels.size) { i ->
            val p = pixels[i]
            val a = (p ushr 24) and 0xFF
            val dr = ((p shr 16) and 0xFF) - br
            val dg = ((p shr 8) and 0xFF) - bg
            val db = (p and 0xFF) - bb
            val distance = kotlin.math.sqrt((dr * dr + dg * dg + db * db).toDouble()).toFloat()
            val t = ((distance - 30f) / 70f).coerceIn(0f, 1f)
            (t * t * (3f - 2f * t) * a).roundToInt().toByte()
        }
    }

    /** Recorta pela caixa do glifo e redesenha centralizado, ocupando [TARGET] do ícone. */
    fun normalize(alpha: ByteArray, size: Int): Bitmap? {
        var left = size
        var top = size
        var right = -1
        var bottom = -1
        for (y in 0 until size) {
            for (x in 0 until size) {
                if ((alpha[y * size + x].toInt() and 0xFF) > 40) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        if (right < left || bottom < top) return null
        val source = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8)
        source.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(alpha))
        val boxW = (right - left + 1).toFloat()
        val boxH = (bottom - top + 1).toFloat()
        val scale = (size * TARGET) / maxOf(boxW, boxH)
        val dstW = boxW * scale
        val dstH = boxH * scale
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8)
        val dst = android.graphics.RectF((size - dstW) / 2f, (size - dstH) / 2f, (size + dstW) / 2f, (size + dstH) / 2f)
        Canvas(out).drawBitmap(source, android.graphics.Rect(left, top, right + 1, bottom + 1), dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        source.recycle()
        return out
    }

    /** Cor principal do ícone, realçada para aparecer bem sobre uma placa escura. */
    fun dominantColor(full: Bitmap): Int {
        val step = (full.width / 24).coerceAtLeast(1)
        var r = 0.0
        var g = 0.0
        var b = 0.0
        var weight = 0.0
        val hsv = FloatArray(3)
        var y = step / 2
        while (y < full.height) {
            var x = step / 2
            while (x < full.width) {
                val p = full[x, y]
                if (Color.alpha(p) > 200) {
                    Color.colorToHSV(p, hsv)
                    // Cores vivas pesam mais que branco, cinza e preto.
                    val w = (hsv[1] * hsv[2]).toDouble() + 0.02
                    r += Color.red(p) * w
                    g += Color.green(p) * w
                    b += Color.blue(p) * w
                    weight += w
                }
                x += step
            }
            y += step
        }
        if (weight <= 0.0) return DEFAULT_ACCENT
        val color = Color.rgb((r / weight).roundToInt(), (g / weight).roundToInt(), (b / weight).roundToInt())
        Color.colorToHSV(color, hsv)
        if (hsv[1] > 0.12f) hsv[1] = hsv[1].coerceAtLeast(0.45f)
        hsv[2] = hsv[2].coerceAtLeast(0.86f)
        return Color.HSVToColor(hsv)
    }
}
