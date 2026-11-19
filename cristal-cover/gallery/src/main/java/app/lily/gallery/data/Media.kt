package app.lily.gallery.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.Immutable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Uma foto ou um vídeo do aparelho (lido do MediaStore, o banco de mídia oficial do Android). */
@Immutable
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val video: Boolean,
    val mime: String,
    /** Quando foi tirada (ou, sem essa informação, quando entrou no aparelho), em ms. */
    val taken: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val bucketId: Long,
    val bucket: String,
    val path: String,
    val name: String,
    val durationMs: Long,
    val favorite: Boolean,
    /** Na lixeira: até quando fica (em ms); 0 fora da lixeira. */
    val expires: Long = 0,
)

/** Um álbum: uma pasta de mídia (Câmera, Capturas de tela, WhatsApp, L.I.L.Y…). */
@Immutable
data class Album(val id: Long, val name: String, val path: String, val count: Int, val cover: MediaItem) {
    val lily: Boolean get() = path.startsWith(LILY_PATH)
}

const val LILY_PATH = "Pictures/L.I.L.Y"
const val IMPORT_PATH = "Pictures/Importadas"

private val PT = Locale.forLanguageTag("pt-BR")

/** "Novembro de 2026" */
fun monthTitle(millis: Long): String =
    DateTimeFormatter.ofPattern("MMMM 'de' yyyy", PT).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())).replaceFirstChar { it.uppercase() }

fun dayTitle(millis: Long): String =
    DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy · HH:mm", PT).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())).replaceFirstChar { it.uppercase() }

/**
 * A biblioteca do aparelho. Só lê o que o Android permitiu (todas as fotos ou só as escolhidas) e se
 * atualiza sozinha quando algo muda (uma foto nova, uma imagem salva pela L.I.L.Y AI).
 */
class MediaRepo(private val context: Context) {
    private val resolver: ContentResolver = context.contentResolver
    private val _items = MutableStateFlow<List<MediaItem>>(emptyList())
    val items: StateFlow<List<MediaItem>> = _items.asStateFlow()
    private val _trash = MutableStateFlow<List<MediaItem>>(emptyList())
    val trash: StateFlow<List<MediaItem>> = _trash.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()
    private var pending: Job? = null
    private var observer: ContentObserver? = null

    suspend fun refresh() = withContext(Dispatchers.IO) {
        _items.value = runCatching { query(trashed = false) }.getOrDefault(emptyList())
        _trash.value = runCatching { query(trashed = true) }.getOrDefault(emptyList())
        _loaded.value = true
    }

    /** Recarrega quando o MediaStore avisa de mudanças (com um pequeno atraso para juntar avisos). */
    fun observe(scope: CoroutineScope) {
        if (observer != null) return
        val o = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                pending?.cancel()
                pending = scope.launch {
                    delay(400)
                    refresh()
                }
            }
        }
        observer = o
        resolver.registerContentObserver(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), true, o)
    }

    fun stop() {
        observer?.let { resolver.unregisterContentObserver(it) }
        observer = null
    }

    /** Para os testes (o Robolectric não tem um MediaStore com fotos). */
    @androidx.annotation.VisibleForTesting
    fun seed(items: List<MediaItem>, trash: List<MediaItem> = emptyList()) {
        _items.value = items
        _trash.value = trash
        _loaded.value = true
    }

    fun albums(items: List<MediaItem>): List<Album> = items.groupBy { it.bucketId }.map { (id, list) ->
        val first = list.first()
        Album(id, first.bucket.ifBlank { "Sem nome" }, first.path, list.size, first)
    }.sortedWith(compareByDescending<Album> { it.lily }.thenByDescending { it.cover.taken })

    fun byId(id: Long): MediaItem? = _items.value.firstOrNull { it.id == id } ?: _trash.value.firstOrNull { it.id == id }

    /** Encontra pelo URI (quando outro app pede "abrir na Gallery"). */
    fun byUri(uri: Uri): MediaItem? {
        val id = runCatching { ContentUris.parseId(uri) }.getOrNull() ?: return null
        return byId(id)
    }

    private fun query(trashed: Boolean): List<MediaItem> {
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.ORIENTATION,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.IS_FAVORITE,
            MediaStore.MediaColumns.DATE_EXPIRES,
        )
        val args = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)")
            putStringArray(
                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()),
            )
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.MediaColumns.DATE_TAKEN} DESC, ${MediaStore.MediaColumns.DATE_ADDED} DESC")
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, if (trashed) MediaStore.MATCH_ONLY else MediaStore.MATCH_EXCLUDE)
            putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_EXCLUDE)
        }
        val out = ArrayList<MediaItem>()
        resolver.query(collection, projection, args, null)?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val iType = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val iMime = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val iTaken = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val iAdded = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val iW = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val iH = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val iOrient = c.getColumnIndexOrThrow(MediaStore.MediaColumns.ORIENTATION)
            val iBucketId = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
            val iBucket = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val iPath = c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val iDuration = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
            val iFav = c.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_FAVORITE)
            val iExpires = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_EXPIRES)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val video = c.getInt(iType) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val base = if (video) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                val taken = c.getLong(iTaken).takeIf { it > 0 } ?: (c.getLong(iAdded) * 1000)
                val rotated = c.getInt(iOrient).let { it == 90 || it == 270 }
                val w = c.getInt(iW)
                val h = c.getInt(iH)
                out += MediaItem(
                    id = id,
                    uri = ContentUris.withAppendedId(base, id),
                    video = video,
                    mime = c.getString(iMime).orEmpty(),
                    taken = taken,
                    size = c.getLong(iSize),
                    width = if (rotated) h else w,
                    height = if (rotated) w else h,
                    bucketId = c.getLong(iBucketId),
                    bucket = c.getString(iBucket).orEmpty(),
                    path = c.getString(iPath).orEmpty(),
                    name = c.getString(iName).orEmpty(),
                    durationMs = c.getLong(iDuration),
                    favorite = c.getInt(iFav) == 1,
                    expires = if (trashed) c.getLong(iExpires) * 1000 else 0,
                )
            }
        }
        return out
    }
}

/**
 * Miniaturas pelo cache oficial do MediaStore (loadThumbnail), guardadas na memória enquanto o app
 * está aberto. No máximo 4 leituras ao mesmo tempo para não travar a rolagem.
 */
@OptIn(ExperimentalCoroutinesApi::class)
object Thumbs {
    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    private val io = Dispatchers.IO.limitedParallelism(4)

    fun cached(item: MediaItem, side: Int): Bitmap? = cache.get("${item.id}:$side")

    suspend fun load(context: Context, item: MediaItem, side: Int): Bitmap? {
        val key = "${item.id}:$side"
        cache.get(key)?.let { return it }
        return withContext(io) {
            val signal = CancellationSignal()
            runCatching { context.contentResolver.loadThumbnail(item.uri, Size(side, side), signal) }.getOrNull()?.also { cache.put(key, it) }
        }
    }

    fun clear() = cache.evictAll()

    @androidx.annotation.VisibleForTesting
    fun put(item: MediaItem, side: Int, bitmap: Bitmap) {
        cache.put("${item.id}:$side", bitmap)
    }
}
