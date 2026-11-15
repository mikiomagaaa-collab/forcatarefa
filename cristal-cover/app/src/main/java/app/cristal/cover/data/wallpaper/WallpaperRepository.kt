package app.cristal.cover.data.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.ui.graphics.asImageBitmap
import app.cristal.cover.core.AppScope
import app.cristal.cover.core.IoDispatcher
import app.cristal.cover.core.Logx
import app.cristal.cover.core.safely
import app.cristal.cover.data.prefs.SettingsRepository
import app.cristal.cover.data.prefs.WallpaperConfig
import app.cristal.cover.data.prefs.WallpaperImages
import app.cristal.cover.data.prefs.WallpaperKeys
import app.cristal.cover.data.prefs.WallpaperKind
import app.cristal.cover.data.prefs.WallpaperSlot
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
 * Papéis de parede próprios do launcher, um por área (início e bloqueio, interna e externa).
 *
 * O vidro precisa "enxergar" a imagem para refratar, e o Android não deixa apps comuns lerem o
 * papel de parede do sistema; por isso o app guarda uma cópia reduzida de cada imagem escolhida
 * na pasta privada dele.
 */
@Singleton
class WallpaperRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    @AppScope scope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private fun file(slot: WallpaperSlot): File =
        File(context.filesDir, if (slot.key.isEmpty()) "wallpaper.jpg" else "wallpaper_${slot.key}.jpg")

    private val _images = MutableStateFlow<WallpaperImages>(emptyMap())

    /** Imagens das áreas que têm imagem própria. Áreas com preset ou que acompanham outra ficam de fora. */
    val images: StateFlow<WallpaperImages> = _images.asStateFlow()

    init {
        scope.launch {
            settingsRepository.settings
                .map { s -> s.wallpapers.mapValues { (_, c) -> Triple(c.kind, c.version, c.follows) } }
                .distinctUntilChanged()
                .collectLatest { states ->
                    _images.value = withContext(io) {
                        buildMap {
                            for ((slot, state) in states) {
                                if (state.first != WallpaperKind.IMAGE || state.third != null) continue
                                loadFromDisk(slot)?.let { put(slot, it.asImageBitmap()) }
                            }
                        }
                    }
                }
        }
    }

    private fun loadFromDisk(slot: WallpaperSlot): Bitmap? = safely("carregar papel de parede ${slot.name}") {
        val f = file(slot)
        if (!f.exists()) return@safely null
        BitmapFactory.decodeFile(f.absolutePath)
    }

    /** Copia a imagem escolhida para o armazenamento do app. Devolve `false` se não for uma imagem válida. */
    suspend fun setFromUri(slot: WallpaperSlot, uri: Uri): Boolean = withContext(io) {
        try {
            val maxSide = if (slot.onCover) MAX_SIDE_COVER else MAX_SIDE
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val longest = max(info.size.width, info.size.height)
                if (longest > maxSide) decoder.setTargetSampleSize(ceil(longest / maxSide.toFloat()).toInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val target = file(slot)
            val tmp = File(context.filesDir, "${target.name}.tmp")
            tmp.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out) }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            val dark = isDark(bitmap)
            update(slot) {
                it.copy(
                    kind = WallpaperKind.IMAGE,
                    version = System.currentTimeMillis(),
                    imageDark = dark,
                    follows = null,
                    zoom = 1f,
                    offsetX = 0f,
                    offsetY = 0f,
                )
            }
            true
        } catch (t: Throwable) {
            Logx.w("Falha ao importar papel de parede", t)
            false
        }
    }

    suspend fun usePreset(slot: WallpaperSlot, index: Int) {
        update(slot) { it.copy(kind = WallpaperKind.PRESET, preset = index, follows = null) }
    }

    /** Altera a configuração de uma área, partindo do que está gravado. */
    suspend fun update(slot: WallpaperSlot, change: (WallpaperConfig) -> WallpaperConfig) {
        val current = settingsRepository.current().wallpapers[slot] ?: WallpaperConfig()
        val next = change(current)
        settingsRepository.edit { WallpaperKeys.of(slot).write(it, next) }
    }

    /** Brilho médio da imagem, medido em uma miniatura: decide se o texto sobre o vidro fica claro. */
    private fun isDark(bitmap: Bitmap): Boolean = safely("medir brilho do papel de parede") {
        val small = Bitmap.createScaledBitmap(bitmap, 24, 24, true)
        var sum = 0.0
        for (y in 0 until 24) {
            for (x in 0 until 24) {
                val c = small.getPixel(x, y)
                sum += 0.2126 * Color.red(c) + 0.7152 * Color.green(c) + 0.0722 * Color.blue(c)
            }
        }
        sum / (24 * 24 * 255.0) < 0.6
    } ?: true

    private companion object {
        const val MAX_SIDE = 1600
        const val MAX_SIDE_COVER = 1280
    }
}
