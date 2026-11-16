package app.lily.gallery.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.Immutable
import kotlin.math.max
import kotlin.math.roundToInt

/** Os filtros prontos (matrizes de cor aplicadas antes dos ajustes). */
enum class Filter(val label: String) {
    ORIGINAL("Original"),
    VIVID("Vívido"),
    WARM("Quente"),
    COOL("Frio"),
    MONO("P&B"),
    NOIR("Noir"),
    SEPIA("Sépia"),
    FADE("Suave"),
}

/** Proporções do corte. */
enum class Aspect(val label: String, val ratio: Float?) {
    FREE("Livre", null),
    ORIGINAL("Original", -1f),
    SQUARE("1:1", 1f),
    PORTRAIT("4:5", 4f / 5f),
    STORY("9:16", 9f / 16f),
    WIDE("16:9", 16f / 9f),
}

/**
 * Uma edição não destrutiva: ajustes de -1 a 1, filtro, rotação em passos de 90°, espelho e o corte
 * (retângulo normalizado de 0 a 1, na imagem já girada).
 */
@Immutable
data class EditState(
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val tint: Float = 0f,
    val vignette: Float = 0f,
    val filter: Filter = Filter.ORIGINAL,
    val rotation: Int = 0,
    val flip: Boolean = false,
    val crop: RectF = RectF(0f, 0f, 1f, 1f),
) {
    val changed: Boolean get() = this != EditState()
}

object Edits {
    /** A matriz de cor de 4×5 (filtro + exposição + contraste + saturação + temperatura + matiz). */
    fun colorMatrix(s: EditState): ColorMatrix {
        val m = ColorMatrix()
        m.postConcat(filterMatrix(s.filter))
        // Exposição: escala de -1 a +1 diafragma.
        val gain = Math.pow(2.0, s.exposure.toDouble()).toFloat()
        m.postConcat(ColorMatrix(floatArrayOf(gain, 0f, 0f, 0f, 0f, 0f, gain, 0f, 0f, 0f, 0f, 0f, gain, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
        // Contraste em volta do cinza médio.
        val c = 1f + s.contrast * 0.6f
        val t = 128f * (1f - c)
        m.postConcat(ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f)))
        m.postConcat(ColorMatrix().apply { setSaturation(1f + s.saturation) })
        // Temperatura (âmbar ↔ azul) e matiz (verde ↔ magenta).
        val w = s.warmth * 24f
        val g = s.tint * 18f
        m.postConcat(ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, w + g * 0.5f, 0f, 1f, 0f, 0f, -g, 0f, 0f, 1f, 0f, -w + g * 0.5f, 0f, 0f, 0f, 1f, 0f)))
        return m
    }

    private fun filterMatrix(f: Filter): ColorMatrix = when (f) {
        Filter.ORIGINAL -> ColorMatrix()
        Filter.VIVID -> ColorMatrix().apply {
            setSaturation(1.35f)
            postConcat(contrast(1.12f))
        }
        Filter.WARM -> ColorMatrix(floatArrayOf(1.08f, 0f, 0f, 0f, 10f, 0f, 1.02f, 0f, 0f, 4f, 0f, 0f, 0.9f, 0f, -6f, 0f, 0f, 0f, 1f, 0f))
        Filter.COOL -> ColorMatrix(floatArrayOf(0.92f, 0f, 0f, 0f, -6f, 0f, 1.0f, 0f, 0f, 2f, 0f, 0f, 1.1f, 0f, 12f, 0f, 0f, 0f, 1f, 0f))
        Filter.MONO -> ColorMatrix().apply { setSaturation(0f) }
        Filter.NOIR -> ColorMatrix().apply {
            setSaturation(0f)
            postConcat(contrast(1.35f))
        }
        Filter.SEPIA -> ColorMatrix(floatArrayOf(0.393f, 0.769f, 0.189f, 0f, 0f, 0.349f, 0.686f, 0.168f, 0f, 0f, 0.272f, 0.534f, 0.131f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
        Filter.FADE -> ColorMatrix(floatArrayOf(0.85f, 0f, 0f, 0f, 28f, 0f, 0.85f, 0f, 0f, 28f, 0f, 0f, 0.85f, 0f, 32f, 0f, 0f, 0f, 1f, 0f)).apply {
            postConcat(ColorMatrix().apply { setSaturation(0.8f) })
        }
    }

    private fun contrast(c: Float): ColorMatrix {
        val t = 128f * (1f - c)
        return ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f))
    }

    /** "Automático": exposição e contraste a partir do histograma real da foto. */
    fun auto(bitmap: Bitmap): EditState {
        val small = Bitmap.createScaledBitmap(bitmap, 96, max(1, 96 * bitmap.height / max(1, bitmap.width)), true)
        val px = IntArray(small.width * small.height).also { small.getPixels(it, 0, small.width, 0, 0, small.width, small.height) }
        val lum = px.map { p -> ((p shr 16 and 255) * 0.299 + (p shr 8 and 255) * 0.587 + (p and 255) * 0.114).toFloat() }.sorted()
        val low = lum[(lum.size * 0.02).toInt()]
        val high = lum[(lum.size * 0.98).toInt().coerceAtMost(lum.lastIndex)]
        val mid = lum[lum.size / 2]
        val exposure = ((118f - mid) / 128f).coerceIn(-0.6f, 0.6f)
        val contrast = ((200f - (high - low)) / 200f).coerceIn(0f, 0.6f)
        return EditState(exposure = exposure, contrast = contrast, saturation = 0.12f)
    }

    /** Aplica rotação, espelho, corte, cores e vinheta numa cópia em resolução cheia. */
    fun render(src: Bitmap, s: EditState): Bitmap {
        val m = Matrix().apply {
            if (s.flip) postScale(-1f, 1f)
            postRotate(s.rotation.toFloat())
        }
        val turned = if (s.rotation % 360 == 0 && !s.flip) src else Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        val x = (s.crop.left * turned.width).roundToInt().coerceIn(0, turned.width - 1)
        val y = (s.crop.top * turned.height).roundToInt().coerceIn(0, turned.height - 1)
        val w = (s.crop.width() * turned.width).roundToInt().coerceIn(1, turned.width - x)
        val h = (s.crop.height() * turned.height).roundToInt().coerceIn(1, turned.height - y)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(colorMatrix(s)) }
        canvas.drawBitmap(turned, -x.toFloat(), -y.toFloat(), paint)
        if (s.vignette > 0f) {
            val r = max(w, h) * 0.75f
            val shader = RadialGradient(w / 2f, h / 2f, r, intArrayOf(0x00000000, 0x00000000, (s.vignette * 200).toInt().coerceIn(0, 255) shl 24), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { this.shader = shader })
        }
        return out
    }

    /** Lê a foto em resolução cheia (limitada a [maxSide] para caber na memória), já virada certa. */
    fun loadFull(context: Context, uri: Uri, maxSide: Int = 4096): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
            val side = max(info.size.width, info.size.height)
            if (side > maxSide) {
                val k = maxSide.toFloat() / side
                decoder.setTargetSize((info.size.width * k).roundToInt(), (info.size.height * k).roundToInt())
            }
        }
    }

    /** Salva como uma foto nova, ao lado da original e com a mesma data (fica junto na biblioteca). */
    fun saveCopy(context: Context, bitmap: Bitmap, original: MediaItem): Uri? {
        val base = original.name.substringBeforeLast('.').ifBlank { "foto" }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${base}_editada_${System.currentTimeMillis() % 100000}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, original.path.ifBlank { "Pictures/L.I.L.Y Gallery/" })
            put(MediaStore.Images.Media.DATE_TAKEN, original.taken)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: return null
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        return uri
    }

    /** Grava por cima da original (só depois da confirmação do Android em createWriteRequest). */
    fun overwrite(context: Context, bitmap: Bitmap, original: MediaItem) {
        val format = if (original.mime == "image/png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        context.contentResolver.openOutputStream(original.uri, "wt")?.use { bitmap.compress(format, 95, it) }
    }

    /** Copia uma foto escolhida no seletor (pode vir do Google Fotos) para a biblioteca do aparelho. */
    fun importCopy(context: Context, source: Uri): Uri? {
        val resolver = context.contentResolver
        val mime = resolver.getType(source) ?: "image/jpeg"
        val video = mime.startsWith("video/")
        val ext = when {
            video -> "mp4"
            mime == "image/png" -> "png"
            mime == "image/webp" -> "webp"
            mime == "image/heic" -> "heic"
            else -> "jpg"
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "importada_${System.currentTimeMillis()}.$ext")
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, if (video) "Movies/Importadas" else IMPORT_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = if (video) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val target = resolver.insert(collection, values) ?: return null
        try {
            resolver.openInputStream(source)?.use { input -> resolver.openOutputStream(target)?.use { input.copyTo(it) } }
            resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (t: Throwable) {
            resolver.delete(target, null, null)
            throw t
        }
        return target
    }
}
