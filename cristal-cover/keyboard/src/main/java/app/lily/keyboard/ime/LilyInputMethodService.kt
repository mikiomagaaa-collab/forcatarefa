package app.lily.keyboard.ime

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "lily-sugestoes") }
    private val main = Handler(Looper.getMainLooper())
    private val bottomInset = mutableStateOf(0f)

    private val ic: InputConnection? get() = currentInputConnection

    private val sink = object : TextSink {
        override fun textBeforeCursor(n: Int): CharSequence? = ic?.getTextBeforeCursor(n, 0)
        override fun textAfterCursor(n: Int): CharSequence? = ic?.getTextAfterCursor(n, 0)
        override fun hasSelection(): Boolean = !ic?.getSelectedText(0).isNullOrEmpty()
        override fun commitText(text: CharSequence) {
            ic?.commitText(text, 1)
        }
        override fun setComposingText(text: CharSequence) {
            ic?.setComposingText(text, 1)
        }
        override fun finishComposingText() {
            ic?.finishComposingText()
        }
        override fun deleteBefore(count: Int) {
            ic?.deleteSurroundingText(count, 0)
        }
        override fun sendKey(keyCode: Int, meta: Int) {
            val c = ic ?: return
            val now = SystemClock.uptimeMillis()
            c.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
            c.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, meta))
        }
        override fun performEditorAction(action: Int) {
            ic?.performEditorAction(action)
        }
        override fun performContextMenuAction(id: Int) {
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
        controller.startInput(EditorConfig.from(info))
        // Fundo transparente só quando o tema pede (para ver o app atrás).
        if (store.settings.value.theme.backgroundAlpha < 0.99f) {
            window.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
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
        controller.onSelectionChanged(newSelStart, newSelEnd, candidatesStart, candidatesEnd)
    }

    /** Nunca usa o modo de tela cheia (deitado, o app continua visível acima do teclado). */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onDestroy() {
        clipboard.detach()
        voice.reset()
        models.user.save()
        worker.shutdown()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
        super.onDestroy()
    }
}
