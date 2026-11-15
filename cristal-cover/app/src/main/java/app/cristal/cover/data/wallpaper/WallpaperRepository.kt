package app.cristal.cover.data.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.cristal.cover.core.AppScope
import app.cristal.cover.core.IoDispatcher
import app.cristal.cover.core.Logx
import app.cristal.cover.core.safely
import app.cristal.cover.data.prefs.PrefKeys
import app.cristal.cover.data.prefs.SettingsRepository
import app.cristal.cover.data.prefs.WallpaperKind
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.max
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Papel de parede próprio do launcher. O vidro precisa "enxergar" a imagem para refratar, e o
 * Android não deixa apps comuns lerem o papel de parede do sistema; por isso o app guarda uma
 * cópia reduzida da imagem escolhida na pasta privada dele.
 */
@Singleton
class WallpaperRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    @AppScope scope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val file: File get() = File(context.filesDir, FILE_NAME)

    private val _image = MutableStateFlow<ImageBitmap?>(null)

    /** Imagem escolhida pelo usuário, ou `null` quando um preset está em uso. */
    val image: StateFlow<ImageBitmap?> = _image.asStateFlow()

    init {
        scope.launch {
            settingsRepository.settings
                .map { it.wallpaperKind to it.wallpaperVersion }
                .distinctUntilChanged()
                .collectLatest { (kind, _) ->
                    _image.value = if (kind == WallpaperKind.IMAGE) withContext(io) { loadFromDisk() } else null
                }
        }
    }

    private fun loadFromDisk(): ImageBitmap? = safely("carregar papel de parede") {
        val f = file
        if (!f.exists()) return@safely null
        BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
    }

    /** Copia a imagem escolhida para o armazenamento do app. Devolve `false` se não for uma imagem válida. */
    suspend fun setFromUri(uri: Uri): Boolean = withContext(io) {
        try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val longest = max(info.size.width, info.size.height)
                if (longest > MAX_SIDE) decoder.setTargetSampleSize(ceil(longest / MAX_SIDE.toFloat()).toInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val tmp = File(context.filesDir, "$FILE_NAME.tmp")
            tmp.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out) }
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
            settingsRepository.edit {
                it[PrefKeys.WALLPAPER_KIND] = WallpaperKind.IMAGE.name
                it[PrefKeys.WALLPAPER_VERSION] = System.currentTimeMillis()
            }
            true
        } catch (t: Throwable) {
            Logx.w("Falha ao importar papel de parede", t)
            false
        }
    }

    suspend fun usePreset(index: Int) {
        settingsRepository.edit {
            it[PrefKeys.WALLPAPER_KIND] = WallpaperKind.PRESET.name
            it[PrefKeys.WALLPAPER_PRESET] = index
        }
    }

    private companion object {
        const val FILE_NAME = "wallpaper.jpg"
        const val MAX_SIDE = 1600
    }
}
