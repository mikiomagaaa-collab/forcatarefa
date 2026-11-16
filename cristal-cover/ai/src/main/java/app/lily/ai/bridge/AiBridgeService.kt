package app.lily.ai.bridge

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.SystemClock
import app.lily.ai.LilyAiApp
import app.lily.ai.core.EngineChoice
import app.lily.ai.core.TaskKind
import app.lily.ai.engine.EngineRefused
import app.lily.ai.engine.Tone
import app.lily.ai.engine.WritingStyle
import app.lily.shared.bridge.AiBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * A ponte da L.I.L.Y AI para o teclado e a Gallery: recebe um pedido de texto (sempre de um toque
 * explícito no outro app), roda no modelo escolhido pelo roteador e devolve a resposta em pedaços.
 * Só apps com a mesma assinatura se ligam (permissão de assinatura no manifesto).
 */
class AiBridgeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val jobs = HashMap<Long, Job>()
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { msg -> handle(msg); true })

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    private fun handle(msg: Message) {
        val data = msg.data
        val id = data.getLong(AiBridge.KEY_ID)
        val reply = msg.replyTo
        when (msg.what) {
            AiBridge.MSG_CANCEL -> jobs.remove(id)?.cancel()
            AiBridge.MSG_REQUEST -> {
                if (reply == null) return
                val action = data.getString(AiBridge.KEY_ACTION)?.takeIf { it in AiBridge.ACTIONS }
                    ?: return error(reply, id, "Pedido desconhecido.", AiBridge.CODE_FAILED)
                val text = data.getString(AiBridge.KEY_TEXT).orEmpty().take(MAX_TEXT)
                if (text.isBlank() && action != "GENERATE_TEXT") return error(reply, id, "Não há texto para a IA.", AiBridge.CODE_FAILED)
                val graph = (application as LilyAiApp).graph
                val style = WritingStyle.entries.firstOrNull { it.name == data.getString(AiBridge.KEY_STYLE) } ?: WritingStyle.NATURAL
                val tone = Tone.entries.firstOrNull { it.name == data.getString(AiBridge.KEY_TONE) } ?: Tone.NEUTRAL
                val (plan, flow) = graph.tools.textTool(
                    TaskKind.valueOf(action),
                    text,
                    style,
                    tone,
                    data.getString(AiBridge.KEY_LANGUAGE) ?: "inglês",
                    data.getString(AiBridge.KEY_LANGUAGE_CODE) ?: "en",
                    data.getString(AiBridge.KEY_INSTRUCTION),
                )
                val engine = plan.engine
                if (engine is EngineChoice.Unavailable) return error(reply, id, engine.reason, AiBridge.CODE_NO_MODEL)
                val model = plan.model?.name ?: if (engine is EngineChoice.Translator) "Tradução do ML Kit" else null
                jobs[id] = scope.launch {
                    val started = SystemClock.elapsedRealtime()
                    var last = ""
                    var sentAt = 0L
                    try {
                        flow.collect { piece ->
                            last = piece
                            val now = SystemClock.elapsedRealtime()
                            if (now - sentAt > 60) {
                                sentAt = now
                                send(reply, AiBridge.MSG_CHUNK, id) { putString(AiBridge.KEY_TEXT, piece); putString(AiBridge.KEY_MODEL, model) }
                            }
                        }
                        send(reply, AiBridge.MSG_DONE, id) {
                            putString(AiBridge.KEY_TEXT, last.trim())
                            putString(AiBridge.KEY_MODEL, model)
                            putLong(AiBridge.KEY_MILLIS, SystemClock.elapsedRealtime() - started)
                        }
                    } catch (c: CancellationException) {
                        throw c
                    } catch (e: EngineRefused) {
                        error(reply, id, e.message.orEmpty(), AiBridge.CODE_FAILED)
                    } catch (t: Throwable) {
                        error(reply, id, t.message ?: "Falha na IA.", AiBridge.CODE_FAILED)
                    } finally {
                        jobs.remove(id)
                    }
                }
            }
        }
    }

    private fun error(reply: Messenger, id: Long, message: String, code: String) =
        send(reply, AiBridge.MSG_ERROR, id) { putString(AiBridge.KEY_MESSAGE, message); putString(AiBridge.KEY_CODE, code) }

    private fun send(reply: Messenger, what: Int, id: Long, fill: Bundle.() -> Unit) {
        runCatching {
            reply.send(Message.obtain(null, what).apply { data = Bundle().apply { putLong(AiBridge.KEY_ID, id); fill() } })
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val MAX_TEXT = 8_000
    }
}
