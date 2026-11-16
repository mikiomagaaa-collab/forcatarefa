package app.lily.shared.bridge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log

/**
 * Contrato da ponte com a L.I.L.Y AI. O teclado e a Gallery pedem uma tarefa de texto e recebem a
 * resposta em pedaços. Só apps com a mesma assinatura (permissão de assinatura) conseguem se ligar.
 * Nada é enviado sem um toque explícito de quem usa.
 */
object AiBridge {
    const val PACKAGE = "app.lily.ai"
    const val SERVICE = "app.lily.ai.bridge.AiBridgeService"
    const val PERMISSION = "app.lily.ai.permission.USE_AI"

    const val MSG_REQUEST = 1
    const val MSG_CHUNK = 2
    const val MSG_DONE = 3
    const val MSG_ERROR = 4
    const val MSG_CANCEL = 5

    const val KEY_ID = "id"
    const val KEY_ACTION = "action"
    const val KEY_TEXT = "text"
    const val KEY_STYLE = "style"
    const val KEY_TONE = "tone"
    const val KEY_LANGUAGE = "language"
    const val KEY_LANGUAGE_CODE = "language_code"
    const val KEY_INSTRUCTION = "instruction"
    const val KEY_MODEL = "model"
    const val KEY_MILLIS = "millis"
    const val KEY_MESSAGE = "message"
    const val KEY_CODE = "code"

    /** Ações aceitas (iguais aos nomes de [app.lily.ai.core.TaskKind]). */
    val ACTIONS = setOf("CORRECT", "REWRITE", "TRANSLATE", "SUMMARIZE", "EXPAND", "EXPLAIN", "ORGANIZE", "REPLY", "ASK", "GENERATE_TEXT")

    const val CODE_NO_MODEL = "no_model"
    const val CODE_FAILED = "failed"
    const val CODE_BUSY = "busy"

    /** Abre a L.I.L.Y AI no Model Hub (para instalar um modelo). */
    fun hubIntent(): Intent = Intent().setClassName(PACKAGE, "app.lily.ai.ui.MainActivity").putExtra("route", "hub").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun installed(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    }.getOrDefault(false)

    fun permitted(context: Context): Boolean =
        context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED
}

/** Um pedido para a IA. [style]: FORMAL, NATURAL ou INFORMAL; [tone]: nome de um tom. */
data class AiRequest(
    val action: String,
    val text: String,
    val style: String = "NATURAL",
    val tone: String = "NEUTRAL",
    val language: String = "inglês",
    val languageCode: String = "en",
    val instruction: String? = null,
)

/** Callbacks de uma resposta (sempre na thread principal). */
interface AiListener {
    fun onChunk(text: String, model: String?)
    fun onDone(text: String, model: String?, millis: Long)
    fun onError(message: String, code: String)
}

/**
 * Cliente da ponte: liga-se ao serviço da L.I.L.Y AI quando precisa e manda os pedidos. Um pedido
 * por vez; [cancel] interrompe a geração no modelo.
 */
class AiClient(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var service: Messenger? = null
    private var bound = false
    private var pending: Pair<Long, AiRequest>? = null
    private var listener: AiListener? = null
    private var currentId = 0L

    private val incoming = Messenger(Handler(Looper.getMainLooper()) { msg ->
        val data = msg.data
        if (data.getLong(AiBridge.KEY_ID) != currentId) return@Handler true
        when (msg.what) {
            AiBridge.MSG_CHUNK -> listener?.onChunk(data.getString(AiBridge.KEY_TEXT).orEmpty(), data.getString(AiBridge.KEY_MODEL))
            AiBridge.MSG_DONE -> listener?.onDone(data.getString(AiBridge.KEY_TEXT).orEmpty(), data.getString(AiBridge.KEY_MODEL), data.getLong(AiBridge.KEY_MILLIS))
            AiBridge.MSG_ERROR -> listener?.onError(data.getString(AiBridge.KEY_MESSAGE).orEmpty(), data.getString(AiBridge.KEY_CODE) ?: AiBridge.CODE_FAILED)
        }
        true
    })

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = Messenger(binder)
            pending?.let { (id, request) -> send(id, request) }
            pending = null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            if (currentId != 0L) listener?.onError("A L.I.L.Y AI foi fechada pelo sistema. Tente de novo.", AiBridge.CODE_FAILED)
        }
    }

    /** Manda um pedido. Devolve false se a IA não está instalada ou a ponte não está liberada. */
    fun request(request: AiRequest, listener: AiListener): Boolean {
        if (!AiBridge.installed(context) || !AiBridge.permitted(context)) return false
        this.listener = listener
        currentId = System.nanoTime()
        val s = service
        if (s != null) {
            send(currentId, request)
            return true
        }
        pending = currentId to request
        if (!bound) {
            bound = runCatching {
                context.bindService(Intent().setClassName(AiBridge.PACKAGE, AiBridge.SERVICE), connection, Context.BIND_AUTO_CREATE)
            }.getOrDefault(false)
            if (!bound) {
                pending = null
                return false
            }
        }
        return true
    }

    fun cancel() {
        val id = currentId
        currentId = 0L
        listener = null
        val s = service ?: return
        runCatching { s.send(Message.obtain(null, AiBridge.MSG_CANCEL).apply { data = Bundle().apply { putLong(AiBridge.KEY_ID, id) } }) }
    }

    /** Solta a ligação (o serviço da IA pode descarregar o modelo). */
    fun release() {
        cancel()
        if (bound) runCatching { context.unbindService(connection) }
        bound = false
        service = null
    }

    private fun send(id: Long, request: AiRequest) {
        val msg = Message.obtain(null, AiBridge.MSG_REQUEST).apply {
            replyTo = incoming
            data = Bundle().apply {
                putLong(AiBridge.KEY_ID, id)
                putString(AiBridge.KEY_ACTION, request.action)
                putString(AiBridge.KEY_TEXT, request.text)
                putString(AiBridge.KEY_STYLE, request.style)
                putString(AiBridge.KEY_TONE, request.tone)
                putString(AiBridge.KEY_LANGUAGE, request.language)
                putString(AiBridge.KEY_LANGUAGE_CODE, request.languageCode)
                putString(AiBridge.KEY_INSTRUCTION, request.instruction)
            }
        }
        try {
            service?.send(msg)
        } catch (e: RemoteException) {
            Log.w("LilyAiClient", "A ponte caiu", e)
            main.post { listener?.onError("Não foi possível falar com a L.I.L.Y AI.", AiBridge.CODE_FAILED) }
        }
    }
}
