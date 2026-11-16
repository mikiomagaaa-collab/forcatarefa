package app.lily.keyboard.brief

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import app.lily.keyboard.core.KeyboardSettings
import app.lily.keyboard.engine.EditorConfig
import java.time.LocalDateTime
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** O que o teclado está mostrando do Info Brief. */
enum class BriefPhase {
    /** A expressão brilha na barra e vira a esfera. */
    GLOW,

    /** A esfera saiu do teclado; o Cristal continua o voo até a ilha. */
    LAUNCHED,

    /** Sem a ilha: a sugestão fica num chip na barra, com "Criar lembrete". */
    CHIP,
}

@Immutable
data class BriefUi(
    val id: Long,
    val candidate: BriefCandidate,
    val phase: BriefPhase,
    /** A esfera voa até a Liquid Island (Cristal instalado, tela interna). */
    val fly: Boolean,
    /** No chip, "Criar lembrete" passa pelo Cristal (senão, abre a agenda do aparelho). */
    val viaCristal: Boolean = false,
)

/**
 * O Info Brief dentro do teclado. Dois gatilhos independentes: 1,5 s depois de parar de digitar e
 * ao enviar a mensagem (pela tecla de enviar ou quando o app limpa o campo). Só analisa campos
 * comuns de texto: nunca senha, código, e-mail, endereço web, campo anônimo, nem o próprio Cristal.
 * Nada é criado aqui: o teclado só mostra a sugestão e a manda ao Cristal (ou à agenda) quando a
 * pessoa confirma.
 */
class InfoBrief(
    private val context: Context,
    private val settings: () -> KeyboardSettings,
    private val bridge: BriefBridge,
    private val clock: () -> LocalDateTime = LocalDateTime::now,
) {
    private val main = Handler(Looper.getMainLooper())
    private val _ui = MutableStateFlow<BriefUi?>(null)
    val ui: StateFlow<BriefUi?> = _ui.asStateFlow()

    /** O teclado destaca a expressão no campo (só no gatilho de pausa, com o texto ainda lá). */
    var highlighter: ((BriefCandidate, String) -> Unit)? = null

    /** Tela em que o teclado está (a ilha só existe na interna). */
    var displayId: () -> Int = { Display.DEFAULT_DISPLAY }

    /** O teclado está visível para animar a esfera. */
    var visible: () -> Boolean = { true }

    private var allowed = false
    private var lastText = ""
    private var lastLocalEdit = Long.MIN_VALUE / 2
    private var idleTask: Runnable? = null
    private var hideTask: Runnable? = null
    private val seen = LinkedHashMap<String, Long>()

    fun startInput(info: EditorInfo?, config: EditorConfig) {
        cancelIdle()
        lastText = ""
        allowed = isAllowed(info, config, context.packageName)
    }

    fun finishInput() {
        cancelIdle()
        lastText = ""
    }

    /** O próprio teclado mexeu no texto (para separar "apaguei tudo" de "o app enviou"). */
    fun onLocalEdit() {
        lastLocalEdit = SystemClock.uptimeMillis()
    }

    /** O texto antes do cursor mudou: digitação, sugestão, ou o app limpou o campo ao enviar. */
    fun onTextChanged(before: CharSequence?) {
        if (!enabled()) return
        val text = before?.toString().orEmpty()
        val previous = lastText
        lastText = text.takeLast(MAX_TEXT)
        if (text.isEmpty() && previous.length >= MIN_TEXT && SystemClock.uptimeMillis() - lastLocalEdit > LOCAL_EDIT_MS) {
            cancelIdle()
            analyze(previous, idle = false)
            return
        }
        if (text.length >= MIN_TEXT) scheduleIdle() else cancelIdle()
    }

    /** Tecla de enviar do campo (campos com "Enter envia"). */
    fun onSendAction(before: CharSequence?) {
        if (!enabled()) return
        cancelIdle()
        analyze(before?.toString().orEmpty().takeLast(MAX_TEXT), idle = false)
    }

    /** A esfera saiu do teclado em [origin] (px na tela): o Cristal continua daqui. */
    fun launched(id: Long, origin: Offset?) {
        val current = _ui.value?.takeIf { it.id == id } ?: return
        if (current.phase != BriefPhase.GLOW) return
        if (!current.fly) {
            toChip(current, viaCristal = false)
            return
        }
        _ui.value = current.copy(phase = BriefPhase.LAUNCHED)
        bridge.suggest(current.candidate, id, origin, displayId()) { result ->
            val now = _ui.value?.takeIf { it.id == id } ?: return@suggest
            when (result) {
                CristalBrief.RESULT_SHOWN, CristalBrief.RESULT_DUPLICATE, CristalBrief.RESULT_OFF -> _ui.value = null
                CristalBrief.RESULT_STORED -> toChip(now, viaCristal = true)
                else -> toChip(now, viaCristal = false)
            }
        }
    }

    fun accept(id: Long) {
        val current = _ui.value?.takeIf { it.id == id } ?: return
        _ui.value = null
        if (current.viaCristal && bridge.available()) {
            bridge.accept(current.candidate, id, displayId())
        } else {
            runCatching { context.startActivity(bridge.calendarIntent(current.candidate)) }
                .onFailure { Log.w(TAG, "Nenhum app de agenda", it) }
        }
    }

    fun dismiss(id: Long) {
        if (_ui.value?.id == id) _ui.value = null
    }

    private fun enabled(): Boolean = allowed && settings().infoBrief

    /** Vale a pena ler o texto do campo agora (Info Brief ligado e campo permitido). */
    fun wantsText(): Boolean = enabled()

    private fun scheduleIdle() {
        cancelIdle()
        idleTask = Runnable { analyze(lastText, idle = true) }.also { main.postDelayed(it, IDLE_MS) }
    }

    private fun cancelIdle() {
        idleTask?.let(main::removeCallbacks)
        idleTask = null
    }

    private fun analyze(text: String, idle: Boolean) {
        if (!enabled() || text.length < MIN_TEXT) return
        if (_ui.value?.phase.let { it == BriefPhase.GLOW || it == BriefPhase.LAUNCHED }) return
        if (idle && !BriefDetector.looksFinished(text)) return
        val candidate = runCatching { BriefDetector.detect(text, clock(), settings().briefKinds) }
            .onFailure { Log.w(TAG, "Falha ao analisar", it) }
            .getOrNull() ?: return
        val now = SystemClock.elapsedRealtime()
        seen.entries.removeAll { now - it.value > SEEN_MS }
        if (seen.containsKey(candidate.key)) return
        seen[candidate.key] = now
        if (idle && settings().briefHighlight) highlighter?.invoke(candidate, text)
        val id = System.currentTimeMillis()
        val fly = displayId() == Display.DEFAULT_DISPLAY && bridge.available()
        hideTask?.let(main::removeCallbacks)
        _ui.value = BriefUi(id, candidate, BriefPhase.GLOW, fly)
        // Sem o teclado na tela (campo fechou ao enviar), não há esfera para animar.
        if (!visible()) launched(id, null)
    }

    private fun toChip(current: BriefUi, viaCristal: Boolean) {
        _ui.value = current.copy(phase = BriefPhase.CHIP, viaCristal = viaCristal)
        hideTask?.let(main::removeCallbacks)
        hideTask = Runnable { dismiss(current.id) }.also { main.postDelayed(it, CHIP_MS) }
    }

    companion object {
        private const val TAG = "LilyInfoBrief"
        const val IDLE_MS = 1_500L
        private const val MIN_TEXT = 6
        private const val MAX_TEXT = 400
        private const val LOCAL_EDIT_MS = 400L
        private const val SEEN_MS = 30 * 60_000L
        private const val CHIP_MS = 9_000L
        private val sensitiveHints = listOf("senha", "password", "código", "codigo", "code", "pin", "otp", "token", "cpf", "cartão", "cartao", "card", "cvv")
        private val blockedVariations = setOf(
            EditorInfo.TYPE_TEXT_VARIATION_PASSWORD,
            EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            EditorInfo.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            EditorInfo.TYPE_TEXT_VARIATION_URI,
            EditorInfo.TYPE_TEXT_VARIATION_FILTER,
            EditorInfo.TYPE_TEXT_VARIATION_PHONETIC,
        )

        /** Campos em que o Info Brief pode olhar. Senha, código e campo anônimo, nunca. */
        fun isAllowed(info: EditorInfo?, config: EditorConfig, ownPackage: String): Boolean {
            if (info == null || config.password) return false
            if (info.inputType and EditorInfo.TYPE_MASK_CLASS != EditorInfo.TYPE_CLASS_TEXT) return false
            if (info.inputType and EditorInfo.TYPE_MASK_VARIATION in blockedVariations) return false
            if (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0) return false
            val pkg = info.packageName
            if (pkg == ownPackage || pkg == CristalBrief.PACKAGE) return false
            val hint = info.hintText?.toString()?.lowercase(Locale.ROOT).orEmpty()
            if (sensitiveHints.any { it in hint }) return false
            return true
        }
    }
}
