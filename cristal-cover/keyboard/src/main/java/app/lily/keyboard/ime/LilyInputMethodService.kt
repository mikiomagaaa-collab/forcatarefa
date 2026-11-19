package app.lily.keyboard.ime

import android.content.ClipDescription
import android.content.ClipboardManager
import app.lily.keyboard.ai.AiSource
import app.lily.keyboard.ai.KeyboardAi
import app.lily.shared.bridge.AiBridge
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.Display
import app.lily.keyboard.brief.BriefBridge
import app.lily.shared.brief.BriefCandidate
import app.lily.keyboard.brief.InfoBrief
import app.lily.keyboard.ui.BriefActions
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.lily.keyboard.clipboard.ClipboardHistory
import app.lily.keyboard.core.KeyboardStore
import app.lily.keyboard.emoji.EmojiHistory
import app.lily.keyboard.engine.EditorConfig
import app.lily.keyboard.engine.InputController
import app.lily.keyboard.engine.LanguageModels
import app.lily.keyboard.engine.SuggestionRunner
import app.lily.keyboard.engine.TextSink
import app.lily.keyboard.studio.StudioActivity
import app.lily.keyboard.studio.VoicePermissionActivity
import app.lily.keyboard.ui.Feedback
import app.lily.keyboard.ui.KeyboardHost
import app.lily.keyboard.ui.KeyboardRoot
import app.lily.keyboard.voice.VoiceInput
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.launch

private const val BRIEF_TEXT = 400
private const val HIGHLIGHT_MS = 1_500L
private const val AI_TEXT = 4_000

/**
 * O serviço do L.I.L.Y Keyboard. O Android o chama quando um campo de texto pede o teclado; ele
 * entrega a interface (Compose) e repassa as teclas ao [InputController].
 *
 * Funciona sozinho, sem depender do launcher: basta ativá-lo em Configurações > Teclados.
 */
class LilyInputMethodService : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()

    private lateinit var store: KeyboardStore
    private lateinit var models: LanguageModels
    private lateinit var controller: InputController
    private lateinit var clipboard: ClipboardHistory
    private lateinit var voice: VoiceInput
    private lateinit var emojiHistory: EmojiHistory
    private lateinit var feedback: Feedback
    private lateinit var brief: InfoBrief
    private lateinit var ai: KeyboardAi

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "lily-sugestoes") }
    private val main = Handler(Looper.getMainLooper())
    private val bottomInset = mutableStateOf(0f)

    private val ic: InputConnection? get() = currentInputConnection

    // Info Brief: posição do cursor e o destaque azul temporário no campo.
    private var selStart = -1
    private var selEnd = -1
    private var highlightActive = false
    private val endHighlightTask = Runnable { endHighlight() }

    /** Antes de qualquer edição do teclado: tira o destaque (para não mexer no texto destacado). */
    private fun beforeEdit() {
        endHighlight()
        if (::brief.isInitialized) brief.onLocalEdit()
    }

    private val sink = object : TextSink {
        override fun textBeforeCursor(n: Int): CharSequence? = ic?.getTextBeforeCursor(n, 0)
        override fun textAfterCursor(n: Int): CharSequence? = ic?.getTextAfterCursor(n, 0)
        override fun hasSelection(): Boolean = !ic?.getSelectedText(0).isNullOrEmpty()
        override fun commitText(text: CharSequence) {
            beforeEdit()
            ic?.commitText(text, 1)
        }
        override fun setComposingText(text: CharSequence) {
            beforeEdit()
            ic?.setComposingText(text, 1)
        }
        override fun finishComposingText() {
            beforeEdit()
            ic?.finishComposingText()
        }
        override fun deleteBefore(count: Int) {
            beforeEdit()
            ic?.deleteSurroundingText(count, 0)
        }
        override fun sendKey(keyCode: Int, meta: Int) {
            beforeEdit()
            val c = ic ?: return
            val now = SystemClock.uptimeMillis()
            c.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
            c.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, meta))
        }
        override fun performEditorAction(action: Int) {
            beforeEdit()
            // Enviar pela tecla do teclado: o Info Brief olha a mensagem antes de ela sair do campo.
            if (action == EditorInfo.IME_ACTION_SEND || action == EditorInfo.IME_ACTION_DONE) {
                brief.onSendAction(ic?.getTextBeforeCursor(BRIEF_TEXT, 0))
            }
            ic?.performEditorAction(action)
        }
        override fun performContextMenuAction(id: Int) {
            beforeEdit()
            ic?.performContextMenuAction(id)
        }
        override fun beginBatch() {
            ic?.beginBatchEdit()
        }
        override fun endBatch() {
            ic?.endBatchEdit()
        }
        override fun capsMode(): Int = ic?.getCursorCapsMode(currentInputEditorInfo?.inputType ?: 0) ?: 0
    }

    private val host = object : KeyboardHost {
        override fun openStudio(page: String?) {
            runCatching {
                startActivity(
                    Intent(this@LilyInputMethodService, StudioActivity::class.java)
                        .putExtra(StudioActivity.EXTRA_PAGE, page)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            requestHideSelf(0)
        }

        override fun showInputMethodPicker() {
            runCatching { getSystemService(InputMethodManager::class.java)?.showInputMethodPicker() }
        }

        override fun requestVoicePermission() {
            runCatching {
                startActivity(Intent(this@LilyInputMethodService, VoicePermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }

        override fun hideKeyboard() {
            requestHideSelf(0)
        }

        override fun aiSource(): AiSource {
            val config = controller.state.value.config
            val c = ic
            // Em campo protegido, nem lê o texto.
            if (config.password || c == null) return KeyboardAi.sourceFor(config, null, null)
            return KeyboardAi.sourceFor(config, c.getSelectedText(0)?.toString(), c.getTextBeforeCursor(AI_TEXT, 0)?.toString())
        }

        override fun replaceSource(source: AiSource, text: String): Boolean {
            val c = ic ?: return false
            val src = source as? AiSource.Text ?: return false
            controller.confirmComposing()
            beforeEdit()
            c.beginBatchEdit()
            try {
                if (src.selection) {
                    if (c.getSelectedText(0)?.toString() != src.text) return false
                    c.commitText(text, 1)
                } else {
                    if (c.getTextBeforeCursor(src.text.length, 0)?.toString() != src.text) return false
                    c.deleteSurroundingText(src.text.length, 0)
                    c.commitText(text, 1)
                }
            } finally {
                c.endBatchEdit()
            }
            return true
        }

        override fun insertText(text: String) {
            // Com um trecho selecionado, insere depois dele (sem apagar a seleção).
            if (selStart >= 0 && selEnd > selStart) ic?.setSelection(selEnd, selEnd)
            val before = ic?.getTextBeforeCursor(1, 0)?.toString().orEmpty()
            val sep = if (before.isEmpty() || before.last().isWhitespace()) "" else " "
            controller.commitRaw(sep + text)
        }

        override fun clipboardText(): String? {
            val clip = getSystemService(ClipboardManager::class.java)?.primaryClip ?: return null
            // O Android marca textos sensíveis copiados (senhas, códigos): esses não vão para a IA.
            if (clip.description?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true) return null
            return clip.getItemAt(0)?.coerceToText(this@LilyInputMethodService)?.toString()?.take(AI_TEXT)
        }

        override fun openAiHub() {
            runCatching { startActivity(AiBridge.hubIntent()) }
            requestHideSelf(0)
        }
    }

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        store = KeyboardStore.get(this)
        models = LanguageModels.get(this)
        clipboard = ClipboardHistory(File(noBackupFilesDir, "area_de_transferencia.json"))
        voice = VoiceInput(this)
        emojiHistory = EmojiHistory(store)
        feedback = Feedback(this)
        controller = InputController(
            sink = sink,
            corrector = models.corrector,
            user = models.user,
            // As sugestões são calculadas fora da thread da interface: digitar nunca espera por elas.
            runner = SuggestionRunner { task, deliver ->
                worker.execute {
                    val result = runCatching(task).getOrNull() ?: return@execute
                    main.post { deliver(result) }
                }
            },
        )
        controller.settings = store.settings.value
        brief = InfoBrief(this, { store.settings.value }, BriefBridge(this)).apply {
            highlighter = ::highlight
            displayId = { window?.window?.decorView?.display?.displayId ?: Display.DEFAULT_DISPLAY }
            visible = { isInputViewShown }
        }
        ai = KeyboardAi(this) { store.settings.value }
        models.preload(store.settings.value.language)
        clipboard.attach(this) { store.settings.value.clipboardHistory }

        lifecycleScope.launch {
            store.settings.collect { s ->
                val languageChanged = s.language != controller.settings.language
                controller.settings = s
                models.preload(s.language)
                if (languageChanged) controller.refreshSuggestions()
                if (!s.clipboardHistory) clipboard.clear(keepPinned = true)
            }
        }
        lifecycleScope.launch { models.ready.collect { controller.refreshSuggestions() } }
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onCreateInputView(): View {
        window.window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this)
            decor.setViewTreeSavedStateRegistryOwner(this)
            decor.setViewTreeViewModelStoreOwner(this)
        }
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@LilyInputMethodService)
            setViewTreeSavedStateRegistryOwner(this@LilyInputMethodService)
            setViewTreeViewModelStoreOwner(this@LilyInputMethodService)
            setContent {
                val settings by store.settings.collectAsState()
                val briefUi by brief.ui.collectAsState()
                KeyboardRoot(
                    controller = controller,
                    settings = settings,
                    host = host,
                    store = store,
                    models = models,
                    clipboard = clipboard,
                    voice = voice,
                    emojiHistory = emojiHistory,
                    feedback = feedback,
                    bottomInset = bottomInset.value.dp,
                    brief = briefUi,
                    briefActions = briefActions,
                    ai = ai,
                )
            }
        }
        // Barra de navegação por gestos: o teclado desenha o fundo por trás e afasta as teclas dela.
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val px = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            bottomInset.value = px / v.resources.displayMetrics.density
            insets
        }
        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val config = EditorConfig.from(info)
        controller.startInput(config)
        selStart = info?.initialSelStart ?: -1
        selEnd = info?.initialSelEnd ?: -1
        if (!restarting) brief.startInput(info, config)
        // Fundo transparente só quando o tema pede (para ver o app atrás).
        if (store.settings.value.theme.backgroundAlpha < 0.99f) {
            window.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        endHighlight()
        // O teclado saiu: solta a ligação com a IA (ela pode liberar a memória do modelo).
        if (::ai.isInitialized) ai.release()
        controller.finishInput()
        voice.reset()
        super.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        super.onFinishInput()
        if (::controller.isInitialized) controller.finishInput()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        selStart = newSelStart
        selEnd = newSelEnd
        controller.onSelectionChanged(newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // Info Brief: pausa de 1,5 s ou campo esvaziado pelo app (mensagem enviada).
        if (brief.wantsText() && newSelStart == newSelEnd) brief.onTextChanged(ic?.getTextBeforeCursor(BRIEF_TEXT, 0))
    }

    private val briefActions by lazy {
        BriefActions(onLaunch = brief::launched, onAccept = brief::accept, onDismiss = brief::dismiss)
    }

    /**
     * Destaque azul da expressão reconhecida, direto no campo, por um instante. Usa o texto em
     * composição do próprio Android (o mesmo mecanismo do sublinhado das sugestões), com o texto
     * idêntico: nada é reescrito. Só acontece com o cursor parado no fim do que foi analisado e
     * sem palavra em composição; qualquer tecla tira o destaque antes de agir.
     */
    private fun highlight(c: BriefCandidate, analyzed: String) {
        val conn = ic ?: return
        if (controller.state.value.composing.isNotEmpty() || selStart < 0 || selStart != selEnd) return
        if (conn.getTextBeforeCursor(analyzed.length, 0)?.toString() != analyzed) return
        val start = selStart - analyzed.length + c.start
        val end = selStart - analyzed.length + c.end
        if (start < 0 || end <= start || end > selStart) return
        val styled = SpannableString(analyzed.substring(c.start, c.end)).apply {
            setSpan(BackgroundColorSpan(0x4D0A84FF), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        conn.beginBatchEdit()
        val ok = conn.setComposingRegion(start, end)
        if (ok) conn.setComposingText(styled, 1 + (selStart - end))
        conn.endBatchEdit()
        if (ok) {
            highlightActive = true
            main.removeCallbacks(endHighlightTask)
            main.postDelayed(endHighlightTask, HIGHLIGHT_MS)
        }
    }

    private fun endHighlight() {
        if (!highlightActive) return
        highlightActive = false
        main.removeCallbacks(endHighlightTask)
        ic?.finishComposingText()
    }

    /** Nunca usa o modo de tela cheia (deitado, o app continua visível acima do teclado). */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onDestroy() {
        endHighlight()
        if (::ai.isInitialized) ai.release()
        clipboard.detach()
        voice.reset()
        models.user.save()
        worker.shutdown()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
        super.onDestroy()
    }
}
