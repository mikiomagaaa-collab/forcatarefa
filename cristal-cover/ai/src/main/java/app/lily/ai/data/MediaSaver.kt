package app.lily.ai.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Imagens que a IA cria ou melhora vão para a galeria do aparelho, em Pictures/L.I.L.Y. */
object MediaSaver {
    const val FOLDER = "Pictures/L.I.L.Y"

    class Saved(val uri: Uri, val file: File)

    /** Salva na galeria e guarda uma cópia no app (para a conversa mostrar). */
    fun savePng(context: Context, bitmap: Bitmap, name: String, description: String): Saved {
        val local = File(context.filesDir, "results/$name.png").apply { parentFile?.mkdirs() }
        local.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, FOLDER)
            put(MediaStore.Images.Media.DESCRIPTION, description.take(250))
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: return Saved(Uri.fromFile(local), local)
        resolver.openOutputStream(uri)?.use { out -> local.inputStream().use { it.copyTo(out) } }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        return Saved(uri, local)
    }

    /** Lê uma imagem (de qualquer app que a compartilhou) já virada certa, limitada a [maxSide]. */
    fun load(context: Context, uri: Uri, maxSide: Int = 4096): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val side = max(info.size.width, info.size.height)
            if (side > maxSide) {
                val s = maxSide.toFloat() / side
                decoder.setTargetSize((info.size.width * s).roundToInt(), (info.size.height * s).roundToInt())
            }
        }
    }

    /** Cópia em JPEG no cache (para mandar ao modelo de visão), com no máximo 1024 px. */
    fun cacheForModel(context: Context, bitmap: Bitmap, name: String): File {
        val side = max(bitmap.width, bitmap.height)
        val scaled = if (side > 1024) {
            val s = 1024f / side
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * s).roundToInt(), (bitmap.height * s).roundToInt(), true)
        } else {
            bitmap
        }
        val file = File(context.cacheDir, "vision/$name.jpg").apply { parentFile?.mkdirs() }
        file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        return file
    }
}
