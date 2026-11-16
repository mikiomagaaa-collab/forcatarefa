package app.cristal.cover.data.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.cristal.cover.core.safely
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fotos do widget de foto. As imagens escolhidas no seletor do Android (que não pede permissão de
 * acesso à galeria) são copiadas, já reduzidas e na orientação certa, para a pasta do app. O widget
 * guarda só os nomes dos arquivos.
 */
@Singleton
class PhotoWidgetStore @Inject constructor(@ApplicationContext private val context: Context) {

    suspend fun import(uris: List<Uri>): List<String> = withContext(Dispatchers.IO) {
        val folder = folder(context)
        uris.take(MAX).mapNotNull { uri ->
            safely("copiar foto do widget") {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val longest = maxOf(info.size.width, info.size.height)
                    if (longest > MAX_SIDE) {
                        val scale = MAX_SIDE.toFloat() / longest
                        decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
                val name = "${UUID.randomUUID()}.jpg"
                File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                bitmap.recycle()
                name
            }
        }
    }

    fun delete(names: List<String>) {
        val folder = folder(context)
        names.forEach { File(folder, it).delete() }
    }

    companion object {
        const val MAX = 12
        private const val MAX_SIDE = 1600

        private fun folder(context: Context): File = File(context.filesDir, "widget_photos").apply { mkdirs() }

        fun encode(names: List<String>): String = names.joinToString("|")

        fun decode(extra: String?): List<String> = extra?.split("|")?.filter { it.endsWith(".jpg") }.orEmpty()

        /** Lê uma foto reduzida para o tamanho do widget. */
        fun load(context: Context, name: String, maxSide: Int): ImageBitmap? = safely("ler foto do widget") {
            val file = File(folder(context), name)
            if (!file.exists()) return@safely null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }
    }
}
