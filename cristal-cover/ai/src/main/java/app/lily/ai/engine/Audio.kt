package app.lily.ai.engine

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.max
import kotlin.math.min

/** Eventos do ditado. */
sealed interface SpeechEvent {
    data object Ready : SpeechEvent
    data class Level(val rms: Float) : SpeechEvent
    data class Partial(val text: String) : SpeechEvent
    data class Final(val text: String) : SpeechEvent
    data class Failed(val message: String) : SpeechEvent
}

/**
 * Ditado pelo reconhecedor do Android. Só começa por um toque explícito (a tela pede a permissão do
 * microfone antes) e para sozinho no fim da fala: nada fica ouvindo em segundo plano.
 */
object SpeechInput {
    fun onDevice(context: Context): Boolean = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    fun listen(context: Context, language: String = "pt-BR"): Flow<SpeechEvent> = callbackFlow {
        val recognizer = if (onDevice(context)) SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else SpeechRecognizer.createSpeechRecognizer(context)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { trySend(SpeechEvent.Ready) }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) { trySend(SpeechEvent.Level(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))) }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onError(error: Int) {
                trySend(SpeechEvent.Failed(errorText(error)))
                close()
            }
            override fun onResults(results: Bundle?) {
                trySend(SpeechEvent.Final(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()))
                close()
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { trySend(SpeechEvent.Partial(it)) }
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        recognizer.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true),
        )
        awaitClose {
            runCatching { recognizer.stopListening() }
            recognizer.destroy()
        }
    }

    /** O aviso de silêncio (a conversa contínua não mostra quando a pessoa não continua). */
    const val NOTHING_HEARD = "Não ouvi nada. Tente de novo."

    fun errorText(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> NOTHING_HEARD
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permita o microfone para ditar."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "O reconhecimento precisa do pacote de português no aparelho (ou de internet)."
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE, SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "O português não está instalado no reconhecedor do aparelho."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "O reconhecedor está ocupado."
        else -> "O reconhecimento de fala falhou ($code)."
    }
}

/** Leitura em voz alta com as vozes do aparelho. */
class Voice(context: Context) {
    private var ready = false
    private var pendingText: String? = null
    private var onDone: (() -> Unit)? = null
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts.language = Locale.forLanguageTag("pt-BR")
            pendingText?.let { speak(it, onDone) }
            pendingText = null
        }
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                app.lily.ai.presence.LilyPresence.speaking(PRESENCE, 0.6f)
            }

            override fun onDone(utteranceId: String?) {
                app.lily.ai.presence.LilyPresence.clear(PRESENCE)
                onDone?.invoke()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                app.lily.ai.presence.LilyPresence.clear(PRESENCE)
                onDone?.invoke()
            }

            /** Cada palavra dita: a boca da avatar acompanha (palavras longas abrem mais). */
            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                app.lily.ai.presence.LilyPresence.speaking(PRESENCE, (0.45f + (end - start) * 0.06f).coerceAtMost(1f))
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                app.lily.ai.presence.LilyPresence.clear(PRESENCE)
            }
        })
    }

    var rate: Float = 1f
        set(value) {
            field = value
            tts.setSpeechRate(value)
        }

    fun speak(text: String, done: (() -> Unit)? = null) {
        onDone = done
        if (!ready) {
            pendingText = text
            return
        }
        tts.speak(text.take(TextToSpeech.getMaxSpeechInputLength() - 1), TextToSpeech.QUEUE_FLUSH, null, "lily")
    }

    fun stop() {
        tts.stop()
        app.lily.ai.presence.LilyPresence.clear(PRESENCE)
    }

    fun shutdown() {
        tts.shutdown()
    }

    companion object {
        const val PRESENCE = "voz"
    }
}

/**
 * Áudio para os modelos que ouvem (Gemma 4): gravação curta do microfone em WAV 16 kHz mono, ou
 * um arquivo de áudio qualquer convertido para esse formato.
 */
object AudioFiles {
    const val RATE = 16_000
    const val MAX_SECONDS = 30

    /** Grava até [MAX_SECONDS] segundos (ou até [stop]); chamado só por um toque explícito. */
    @SuppressLint("MissingPermission")
    fun record(target: File, stop: () -> Boolean, level: (Float) -> Unit): File {
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(min, RATE))
        val pcm = ByteArrayOutputStream()
        val buffer = ShortArray(RATE / 10)
        recorder.startRecording()
        try {
            while (!stop() && pcm.size() < RATE * 2 * MAX_SECONDS) {
                val n = recorder.read(buffer, 0, buffer.size)
                if (n <= 0) continue
                var peak = 0
                val bytes = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until n) {
                    bytes.putShort(buffer[i])
                    peak = max(peak, kotlin.math.abs(buffer[i].toInt()))
                }
                pcm.write(bytes.array())
                level(min(1f, peak / 12_000f))
            }
        } finally {
            recorder.stop()
            recorder.release()
        }
        writeWav(target, pcm.toByteArray())
        return target
    }

    /** Converte um áudio (m4a, mp3, ogg, wav…) para WAV 16 kHz mono, limitado a [MAX_SECONDS]. */
    fun toWav(context: Context, uri: Uri, target: File): File {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        val track = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            ?: throw IllegalArgumentException("O arquivo não tem áudio")
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()
        var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val mono = ArrayList<Short>()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        while (!outputDone && mono.size < rate * MAX_SECONDS) {
            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val buf = codec.getInputBuffer(inIndex)!!
                    val size = extractor.readSampleData(buf, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIndex = codec.dequeueOutputBuffer(info, 10_000)
            if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                rate = codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else if (outIndex >= 0) {
                val out = codec.getOutputBuffer(outIndex)!!.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                while (out.remaining() >= channels) {
                    var sum = 0
                    repeat(channels) { sum += out.get() }
                    mono.add((sum / channels).toShort())
                }
                codec.releaseOutputBuffer(outIndex, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
            }
        }
        codec.stop()
        codec.release()
        extractor.release()
        val resampled = resample(mono.toShortArray(), rate, RATE)
        val bytes = ByteBuffer.allocate(resampled.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        resampled.forEach { bytes.putShort(it) }
        writeWav(target, bytes.array())
        return target
    }

    /** Reamostragem linear (suficiente para fala). */
    fun resample(input: ShortArray, from: Int, to: Int): ShortArray {
        if (from == to || input.isEmpty()) return input
        val outLen = (input.size.toLong() * to / from).toInt()
        return ShortArray(outLen) { i ->
            val pos = i.toDouble() * from / to
            val a = pos.toInt().coerceAtMost(input.size - 1)
            val b = (a + 1).coerceAtMost(input.size - 1)
            val t = pos - a
            (input[a] * (1 - t) + input[b] * t).toInt().toShort()
        }
    }

    fun writeWav(target: File, pcm: ByteArray, rate: Int = RATE) {
        target.parentFile?.mkdirs()
        RandomAccessFile(target, "rw").use { f ->
            f.setLength(0)
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVE".toByteArray())
            header.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
            header.put("data".toByteArray()).putInt(pcm.size)
            f.write(header.array())
            f.write(pcm)
        }
    }
}
