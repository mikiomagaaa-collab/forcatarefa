package app.cristal.cover.data.media

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale
import app.cristal.cover.core.safely
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
data class MediaState(
    val packageName: String,
    val title: String,
    val artist: String,
    val art: ImageBitmap?,
    val isPlaying: Boolean,
    val canSkipNext: Boolean,
    val canSkipPrevious: Boolean,
    /** Posição e duração em ms (0 quando o player não informa), para a barra de progresso. */
    val position: Long = 0L,
    val duration: Long = 0L,
    /** Quando a posição foi medida (SystemClock.elapsedRealtime) e a velocidade de reprodução. */
    val positionAt: Long = 0L,
    val speed: Float = 1f,
    /** Cor predominante da capa (ARGB), para tingir a Liquid Island e o widget. */
    val tint: Int? = null,
) {
    /** Posição estimada agora, contando o tempo desde a última medição. */
    fun positionNow(nowElapsed: Long): Long {
        if (duration <= 0L) return 0L
        val delta = if (isPlaying && positionAt > 0L) ((nowElapsed - positionAt) * speed).toLong() else 0L
        return (position + delta).coerceIn(0L, duration)
    }
}

/**
 * Acompanha a sessão de mídia ativa (Spotify, YouTube Music etc.) usando o [MediaSessionManager].
 * Só funciona enquanto o acesso às notificações estiver concedido; sem ele, [state] fica `null`.
 */
@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager: MediaSessionManager? = context.getSystemService(MediaSessionManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow<MediaState?>(null)
    val state: StateFlow<MediaState?> = _state.asStateFlow()

    private var listenerComponent: ComponentName? = null
    private var controllers: List<MediaController> = emptyList()
    private var current: MediaController? = null
    private var lastArtSource: Bitmap? = null
    private var lastArt: ImageBitmap? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        setControllers(list.orEmpty())
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = pickAndPublish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = pickAndPublish()
        override fun onSessionDestroyed() = reload()
    }

    fun onListenerConnected(component: ComponentName) {
        handler.post {
            listenerComponent = component
            safely("ouvir sessões de mídia") {
                manager?.removeOnActiveSessionsChangedListener(sessionsListener)
                manager?.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
            }
            reload()
        }
    }

    fun onListenerDisconnected() {
        handler.post {
            safely("parar de ouvir sessões de mídia") {
                manager?.removeOnActiveSessionsChangedListener(sessionsListener)
            }
            listenerComponent = null
            setControllers(emptyList())
        }
    }

    private fun reload() {
        val component = listenerComponent ?: return
        val list = safely("listar sessões de mídia") { manager?.getActiveSessions(component) }.orEmpty()
        setControllers(list)
    }

    private fun setControllers(list: List<MediaController>) {
        for (c in controllers) safely("remover callback de mídia") { c.unregisterCallback(controllerCallback) }
        controllers = list
        for (c in controllers) safely("registrar callback de mídia") { c.registerCallback(controllerCallback, handler) }
        pickAndPublish()
    }

    private fun pickAndPublish() {
        // Prefere quem está tocando; senão, a sessão mais recente (a primeira da lista).
        val chosen = controllers.firstOrNull { isPlaying(it.playbackState) } ?: controllers.firstOrNull()
        current = chosen
        _state.value = chosen?.let { toState(it) }
    }

    private fun isPlaying(state: PlaybackState?): Boolean = when (state?.state) {
        PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_FAST_FORWARDING,
        PlaybackState.STATE_REWINDING, PlaybackState.STATE_SKIPPING_TO_NEXT, PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
        -> true

        else -> false
    }

    private fun toState(controller: MediaController): MediaState? = safely("ler estado de mídia") {
        val metadata = controller.metadata
        val playback = controller.playbackState
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: ""
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: ""
        if (title.isBlank() && artist.isBlank() && !isPlaying(playback)) return@safely null
        val actions = playback?.actions ?: 0L
        val art = artOf(metadata)
        MediaState(
            packageName = controller.packageName.orEmpty(),
            title = title,
            artist = artist,
            art = art,
            isPlaying = isPlaying(playback),
            canSkipNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
            canSkipPrevious = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
            position = playback?.position?.coerceAtLeast(0L) ?: 0L,
            duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: 0L,
            positionAt = playback?.lastPositionUpdateTime ?: 0L,
            speed = playback?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            tint = lastTint,
        )
    }

    private fun artOf(metadata: MediaMetadata?): ImageBitmap? {
        val source = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (source == null) {
            lastArtSource = null
            lastArt = null
            lastTint = null
            return null
        }
        if (source === lastArtSource) return lastArt
        val converted = safely("converter capa do álbum") {
            if (source.isRecycled) return@safely null
            // Cópia reduzida: o app de música pode reciclar o bitmap original a qualquer momento.
            val side = minOf(source.width, source.height).coerceAtLeast(1)
            val target = minOf(side, ART_PX)
            val factor = target.toFloat() / side
            val w = (source.width * factor).toInt().coerceAtLeast(1)
            val h = (source.height * factor).toInt().coerceAtLeast(1)
            val scaled = source.scale(w, h)
            val safeCopy = if (scaled === source || scaled.config == Bitmap.Config.HARDWARE) {
                scaled.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                scaled
            }
            safeCopy?.asImageBitmap()
        }
        lastArtSource = source
        lastArt = converted
        lastTint = converted?.let { tintOf(it) }
        return converted
    }

    private var lastTint: Int? = null

    /** Cor média da capa, um pouco mais viva (para tingir a ilha e o widget). */
    private fun tintOf(art: ImageBitmap): Int? = safely("cor da capa") {
        val small = art.asAndroidBitmap().scale(12, 12)
        var r = 0L
        var g = 0L
        var b = 0L
        for (y in 0 until 12) for (x in 0 until 12) {
            val c = small.getPixel(x, y)
            r += (c shr 16) and 0xFF
            g += (c shr 8) and 0xFF
            b += c and 0xFF
        }
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV((r / 144).toInt(), (g / 144).toInt(), (b / 144).toInt(), hsv)
        hsv[1] = (hsv[1] * 1.3f).coerceIn(0f, 1f)
        hsv[2] = hsv[2].coerceIn(0.35f, 0.85f)
        android.graphics.Color.HSVToColor(hsv)
    }

    fun playPause() {
        val c = current ?: return
        safely("play/pause") {
            if (isPlaying(c.playbackState)) c.transportControls.pause() else c.transportControls.play()
        }
    }

    fun next() {
        safely("próxima faixa") { current?.transportControls?.skipToNext() }
    }

    fun previous() {
        safely("faixa anterior") { current?.transportControls?.skipToPrevious() }
    }

    /** Abre o app que está tocando. Devolve `false` se não for possível. */
    fun openPlayer(displayId: Int): Boolean {
        val c = current ?: return false
        return safely("abrir player") {
            val options = ActivityOptions.makeBasic()
            if (displayId != Display.INVALID_DISPLAY) options.launchDisplayId = displayId
            val session = c.sessionActivity
            if (session != null) {
                if (Build.VERSION.SDK_INT >= 36) {
                    options.pendingIntentBackgroundActivityStartMode =
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                } else if (Build.VERSION.SDK_INT >= 34) {
                    @Suppress("DEPRECATION")
                    options.pendingIntentBackgroundActivityStartMode =
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                }
                session.send(context, 0, null, null, null, null, options.toBundle())
                true
            } else {
                val intent = context.packageManager.getLaunchIntentForPackage(c.packageName) ?: return@safely false
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent, options.toBundle())
                true
            }
        } ?: false
    }

    private companion object {
        const val ART_PX = 320
    }
}
