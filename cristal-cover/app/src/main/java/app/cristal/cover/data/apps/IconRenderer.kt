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

/** Resultado da renderização de um ícone. */
class RenderedIcon(val full: Bitmap, val mono: Bitmap?, val maskable: Boolean)

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
        return when {
            fromIconPack -> RenderedIcon(drawFitted(drawable, size, 1f, null), null, maskable = false)
            drawable is AdaptiveIconDrawable -> renderAdaptive(drawable, size)
            else -> renderLegacy(drawable, size)
        }
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
