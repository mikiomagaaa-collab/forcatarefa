package app.lily.keyboard.engine

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.Immutable
import app.lily.keyboard.core.FieldKind
import app.lily.keyboard.core.KeyboardLanguage
import app.lily.keyboard.core.KeyboardSettings
import app.lily.keyboard.core.LayoutId
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** O que o teclado precisa do campo de texto. Na prática, um InputConnection; nos testes, um falso. */
interface TextSink {
    fun textBeforeCursor(n: Int): CharSequence?
    fun textAfterCursor(n: Int): CharSequence?
    fun hasSelection(): Boolean
    fun commitText(text: CharSequence)
    fun setComposingText(text: CharSequence)
    fun finishComposingText()
    fun deleteBefore(count: Int)
    fun sendKey(keyCode: Int, meta: Int = 0)
    fun performEditorAction(action: Int)
    fun performContextMenuAction(id: Int)
    fun beginBatch()
    fun endBatch()
    /** Resultado de InputConnection.getCursorCapsMode para o tipo do campo (0 = minúscula). */
    fun capsMode(): Int
}

enum class ShiftState { OFF, ONCE, LOCKED }

/** Ícone e comportamento da tecla Enter, conforme o campo pede. */
enum class EnterKind { NEWLINE, SEND, SEARCH, GO, NEXT, DONE, PREVIOUS }

/** Regras do campo atual, lidas do EditorInfo. */
@Immutable
data class EditorConfig(
    val layout: LayoutId = LayoutId.LETTERS,
    val field: FieldKind = FieldKind.TEXT,
    val password: Boolean = false,
    val suggestions: Boolean = true,
    val autoCorrect: Boolean = true,
    val learning: Boolean = true,
    val autoCaps: Boolean = true,
    val enter: EnterKind = EnterKind.NEWLINE,
    val enterAction: Int = EditorInfo.IME_ACTION_NONE,
) {
    companion object {
        fun from(info: EditorInfo?): EditorConfig {
            if (info == null) return EditorConfig()
            val type = info.inputType
            val cls = type and EditorInfo.TYPE_MASK_CLASS
            val variation = type and EditorInfo.TYPE_MASK_VARIATION
            val flags = type and EditorInfo.TYPE_MASK_FLAGS
            val password = (cls == EditorInfo.TYPE_CLASS_TEXT && variation in setOf(
                EditorInfo.TYPE_TEXT_VARIATION_PASSWORD,
                EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            )) || (cls == EditorInfo.TYPE_CLASS_NUMBER && variation == EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD)
            val field = when (variation) {
                EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, EditorInfo.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> FieldKind.EMAIL
                EditorInfo.TYPE_TEXT_VARIATION_URI -> FieldKind.URL
                else -> FieldKind.TEXT
            }
            val layout = when (cls) {
                EditorInfo.TYPE_CLASS_NUMBER, EditorInfo.TYPE_CLASS_DATETIME -> LayoutId.NUMBERS
                EditorInfo.TYPE_CLASS_PHONE -> LayoutId.PHONE
                else -> LayoutId.LETTERS
            }
            val noSuggestions = (flags and EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0
            val textual = cls == EditorInfo.TYPE_CLASS_TEXT
            val suggestions = textual && !password && !noSuggestions && field == FieldKind.TEXT &&
                variation != EditorInfo.TYPE_TEXT_VARIATION_FILTER
            val incognito = (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
            val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
            val noEnterAction = (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
            val enter = if (noEnterAction) {
                EnterKind.NEWLINE
            } else {
                when (action) {
                    EditorInfo.IME_ACTION_SEND -> EnterKind.SEND
                    EditorInfo.IME_ACTION_SEARCH -> EnterKind.SEARCH
                    EditorInfo.IME_ACTION_GO -> EnterKind.GO
                    EditorInfo.IME_ACTION_NEXT -> EnterKind.NEXT
                    EditorInfo.IME_ACTION_DONE -> EnterKind.DONE
                    EditorInfo.IME_ACTION_PREVIOUS -> EnterKind.PREVIOUS
                    else -> EnterKind.NEWLINE
                }
            }
            return EditorConfig(
                layout = layout,
                field = field,
                password = password,
                suggestions = suggestions,
                autoCorrect = suggestions,
                learning = suggestions && !incognito,
                autoCaps = textual && !password && field == FieldKind.TEXT &&
                    (flags and (EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES or EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS or EditorInfo.TYPE_TEXT_FLAG_CAP_CHARACTERS)) != 0,
                enter = enter,
                enterAction = action,
            )
        }
    }
}

@Immutable
data class InputUiState(
    val layout: LayoutId = LayoutId.LETTERS,
    val shift: ShiftState = ShiftState.OFF,
    val result: CorrectionResult = CorrectionResult.EMPTY,
    val composing: String = "",
    val config: EditorConfig = EditorConfig(),
) {
    val upper: Boolean get() = shift != ShiftState.OFF
}

/** Roda o cálculo de sugestões fora da thread da interface e devolve o resultado nela. */
fun interface SuggestionRunner {
    fun run(task: () -> CorrectionResult, deliver: (CorrectionResult) -> Unit)

    companion object {
        /** Para testes: calcula e entrega na hora. */
        val Immediate = SuggestionRunner { task, deliver -> deliver(task()) }
    }
}

/**
 * O "cérebro" do teclado: decide o que vai para o campo a cada tecla.
 *
 * - A palavra em digitação fica como texto em composição (sublinhado), para a sugestão escolhida
 *   poder substituí-la.
 * - Espaço e pontuação confirmam a palavra e aplicam a correção automática (se clara).
 * - Apagar logo depois de uma correção desfaz a correção.
 * - Dois espaços viram ". "; pontuação depois de um espaço automático "cola" na palavra.
 * - Em campo de senha não há composição, sugestões, aprendizado nem nada guardado.
 */
class InputController(
    private val sink: TextSink,
    private val corrector: Corrector,
    private val user: UserDictionary,
    private val runner: SuggestionRunner,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(InputUiState())
    val state: StateFlow<InputUiState> = _state.asStateFlow()

    var settings: KeyboardSettings = KeyboardSettings()

    private val composing = StringBuilder()
    private var composingIsGesture = false
    private var previousWord: String? = null
    private var lastAuto: AutoCorrection? = null
    private var blockAuto: String? = null
    private var lastSpaceAt = 0L
    private var lastSpaceAuto = false
    private var lastShiftTap = 0L
    private var suggestionVersion = 0

    private data class AutoCorrection(val original: String, val corrected: String, val separator: String)

    private val config: EditorConfig get() = _state.value.config
    private val locale: Locale
        get() = if (settings.language == KeyboardLanguage.PT_BR) Locale.forLanguageTag("pt-BR") else Locale.US

    // --- Ciclo de vida do campo -------------------------------------------------------------------

    fun startInput(config: EditorConfig) {
        composing.clear()
        composingIsGesture = false
        lastAuto = null
        blockAuto = null
        lastSpaceAuto = false
        previousWord = TextTools.lastWord(sink.textBeforeCursor(64))
        _state.value = InputUiState(layout = config.layout, config = config)
        updateAutoShift()
    }

    fun finishInput() {
        if (composing.isNotEmpty()) sink.finishComposingText()
        composing.clear()
        _state.update { it.copy(composing = "", result = CorrectionResult.EMPTY) }
        user.save()
    }

    /** O cursor mudou. Se não foi por nós (o usuário tocou em outro lugar), abandona a palavra. */
    fun onSelectionChanged(newStart: Int, newEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        if (composing.isNotEmpty() && (candidatesStart < 0 || newStart != candidatesEnd || newEnd != candidatesEnd)) {
            sink.finishComposingText()
            composing.clear()
            composingIsGesture = false
            setResult(CorrectionResult.EMPTY)
            lastAuto = null
            lastSpaceAuto = false
            updateAutoShift()
        }
    }

    // --- Teclas -----------------------------------------------------------------------------------

    fun setLayout(layout: LayoutId) {
        _state.update { it.copy(layout = layout) }
    }

    fun onShift() {
        val now = clock()
        val next = when {
            _state.value.shift == ShiftState.LOCKED -> ShiftState.OFF
            now - lastShiftTap < DOUBLE_TAP_MS && _state.value.shift == ShiftState.ONCE -> ShiftState.LOCKED
            _state.value.shift == ShiftState.ONCE -> ShiftState.OFF
            else -> ShiftState.ONCE
        }
        lastShiftTap = now
        _state.update { it.copy(shift = next) }
    }

    /** Uma tecla de caractere (letra, número, símbolo). */
    fun onChar(raw: String) {
        if (raw.isEmpty()) return
        val shifted = _state.value.shift != ShiftState.OFF
        val text = if (shifted) raw.uppercase(locale) else raw
        val c = text[0]
        val isLetter = text.length == 1 && TextTools.isWordChar(c)
        val wordy = isLetter || (text.length == 1 && (c.isDigit() || (TextTools.isJoiner(c) && composing.isNotEmpty())))

        if (wordy && canCompose()) {
            if (composingIsGesture && composing.isNotEmpty() && isLetter) {
                // Depois de uma palavra deslizada, a próxima letra começa outra palavra.
                commitComposing(autoCorrect = false, separator = " ", auto = true)
            }
            if (composing.isEmpty()) adoptWordBeforeCursor()
            composing.append(text)
            composingIsGesture = false
            sink.setComposingText(composing)
            lastSpaceAuto = false
            afterTyping()
            requestSuggestions()
            return
        }

        if (composing.isNotEmpty()) {
            if (isLetter || c.isDigit()) {
                // Campo sem composição ou letra no meio de outra palavra: só escreve.
                commitComposing(autoCorrect = false, separator = "")
            } else {
                val gesture = composingIsGesture
                commitComposing(autoCorrect = !gesture, separator = "")
                sink.commitText(text)
                if (gesture && text in AUTO_SPACE_AFTER) {
                    sink.commitText(" ")
                    lastSpaceAuto = true
                } else {
                    lastSpaceAuto = false
                }
                afterSeparator(text)
                return
            }
        }

        if (lastSpaceAuto && text in ATTACHING_PUNCTUATION) {
            // "palavra ." → "palavra. "
            sink.deleteBefore(1)
            sink.commitText(text)
            sink.commitText(" ")
            lastSpaceAuto = true
            afterSeparator(text)
            return
        }
        sink.commitText(text)
        lastSpaceAuto = false
        if (isLetter || c.isDigit()) afterTyping() else afterSeparator(text)
    }

    fun onSpace() {
        val now = clock()
        if (composing.isNotEmpty()) {
            commitComposing(autoCorrect = !composingIsGesture, separator = " ")
            lastSpaceAt = now
            afterSeparator(" ")
            return
        }
        val before = sink.textBeforeCursor(3)?.toString().orEmpty()
        val recent = now - lastSpaceAt < DOUBLE_SPACE_MS || lastSpaceAuto
        if (settings.doubleSpacePeriod && !config.password && config.field == FieldKind.TEXT && recent &&
            before.length >= 2 && before.endsWith(" ") && !before[before.length - 2].isWhitespace() &&
            (before[before.length - 2].isLetterOrDigit() || before[before.length - 2] in ")\"'")
        ) {
            sink.beginBatch()
            sink.deleteBefore(1)
            sink.commitText(". ")
            sink.endBatch()
            lastSpaceAt = 0L
            lastSpaceAuto = false
            afterSeparator(".")
            return
        }
        sink.commitText(" ")
        lastSpaceAt = now
        lastSpaceAuto = false
        afterSeparator(" ")
    }

    fun onDelete() {
        lastSpaceAuto = false
        if (composing.isNotEmpty()) {
            val cut = TextTools.lastGraphemeLength(composing)
            composing.setLength((composing.length - cut).coerceAtLeast(0))
            composingIsGesture = false
            if (composing.isEmpty()) {
                sink.commitText("")
                setResult(CorrectionResult.EMPTY)
            } else {
                sink.setComposingText(composing)
                requestSuggestions()
            }
            updateAutoShift()
            return
        }
        val auto = lastAuto
        if (auto != null) {
            val tail = auto.corrected + auto.separator
            val before = sink.textBeforeCursor(tail.length)?.toString()
            lastAuto = null
            if (before == tail) {
                // Desfaz a correção automática e não corrige de novo esta palavra.
                sink.beginBatch()
                sink.deleteBefore(tail.length)
                sink.commitText(auto.original)
                sink.endBatch()
                blockAuto = auto.original
                if (canCompose()) adoptWordBeforeCursor(requestNow = true)
                updateAutoShift()
                return
            }
        }
        if (sink.hasSelection()) {
            sink.commitText("")
            updateAutoShift()
            return
        }
        val before = sink.textBeforeCursor(32)
        if (before.isNullOrEmpty()) {
            sink.sendKey(KeyEvent.KEYCODE_DEL)
        } else {
            sink.deleteBefore(TextTools.lastGraphemeLength(before))
            // Voltou para o fim de uma palavra: retoma as sugestões para ela.
            if (canCompose()) adoptWordBeforeCursor(requestNow = true)
        }
        updateAutoShift()
    }

    /** Apaga a palavra anterior inteira (deslizar a tecla apagar para a esquerda). */
    fun onDeleteWord() {
        lastAuto = null
        lastSpaceAuto = false
        if (composing.isNotEmpty()) {
            composing.clear()
            composingIsGesture = false
            sink.commitText("")
            setResult(CorrectionResult.EMPTY)
            updateAutoShift()
            return
        }
        if (sink.hasSelection()) {
            sink.commitText("")
            updateAutoShift()
            return
        }
        val before = sink.textBeforeCursor(64)?.toString().orEmpty()
        if (before.isEmpty()) return
        var i = before.length
        while (i > 0 && before[i - 1].isWhitespace()) i--
        if (i > 0 && !TextTools.isWordChar(before[i - 1]) && !before[i - 1].isDigit()) {
            i--
        } else {
            while (i > 0 && (TextTools.isWordChar(before[i - 1]) || before[i - 1].isDigit() || TextTools.isJoiner(before[i - 1]))) i--
        }
        sink.deleteBefore((before.length - i).coerceAtLeast(1))
        updateAutoShift()
    }

    fun onEnter() {
        if (composing.isNotEmpty()) commitComposing(autoCorrect = false, separator = "")
        lastSpaceAuto = false
        val cfg = config
        if (cfg.enter != EnterKind.NEWLINE) {
            sink.performEditorAction(cfg.enterAction)
        } else {
            sink.commitText("\n")
        }
        afterSeparator("\n")
    }

    /** Toque numa sugestão da barra. */
    fun onSuggestion(s: Suggestion) {
        if (s.kind == Suggestion.Kind.TYPED && config.learning && settings.personalLearning) user.learn(s.text, UserDictionary.KNOWN_AT)
        if (composing.isEmpty()) {
            sink.commitText(s.text)
        } else {
            sink.beginBatch()
            sink.commitText(s.text)
            sink.endBatch()
            composing.clear()
        }
        composingIsGesture = false
        lastAuto = null
        previousWord = s.text.substringAfterLast(' ')
        val after = sink.textAfterCursor(1)?.toString().orEmpty()
        if (after.isEmpty() || !after[0].isWhitespace()) {
            sink.commitText(" ")
            lastSpaceAuto = true
        }
        lastSpaceAt = clock()
        setResult(CorrectionResult.EMPTY)
        updateAutoShift()
    }

    /** Remove a palavra repetida ("de de" → "de"), quando a barra avisa. */
    fun removeRepeatedWord() {
        if (composing.isEmpty()) return
        composing.clear()
        sink.commitText("")
        val before = sink.textBeforeCursor(1)?.toString().orEmpty()
        if (before == " ") sink.deleteBefore(1)
        sink.commitText(" ")
        setResult(CorrectionResult.EMPTY)
    }

    /** Texto pronto (emoji, área de transferência, ditado): confirma a palavra atual como está e insere. */
    fun commitRaw(text: String) {
        if (composing.isNotEmpty()) commitComposing(autoCorrect = false, separator = "")
        sink.commitText(text)
        lastSpaceAuto = false
        afterSeparator(text.takeLast(1))
    }

    /** Resultado da digitação por deslize: a melhor palavra vai para o campo; as outras, para a barra. */
    fun onGesture(words: List<String>) {
        if (words.isEmpty()) return
        if (composing.isNotEmpty()) {
            commitComposing(autoCorrect = !composingIsGesture, separator = " ", auto = true)
        } else {
            val before = sink.textBeforeCursor(1)?.toString().orEmpty()
            if (before.isNotEmpty() && (TextTools.isWordChar(before[0]) || before[0] in ".,!?;:")) {
                sink.commitText(" ")
            }
        }
        val shift = _state.value.shift
        val cased = words.map { w ->
            when (shift) {
                ShiftState.LOCKED -> w.uppercase(locale)
                ShiftState.ONCE -> w.replaceFirstChar { it.titlecase(locale) }
                ShiftState.OFF -> w
            }
        }
        composing.setLength(0)
        composing.append(cased[0])
        composingIsGesture = true
        lastSpaceAuto = false
        if (canCompose()) sink.setComposingText(composing) else {
            sink.commitText(composing)
            composing.clear()
        }
        afterTyping()
        setResult(
            CorrectionResult(
                word = cased[0],
                suggestions = cased.drop(1).take(3).map { Suggestion(it, Suggestion.Kind.CORRECTION) },
            ),
        )
    }

    /** Move o cursor [delta] posições (arrastar no espaço). */
    fun moveCursor(delta: Int) {
        if (delta == 0) return
        if (composing.isNotEmpty()) commitComposing(autoCorrect = false, separator = "")
        lastAuto = null
        lastSpaceAuto = false
        val key = if (delta < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        repeat(kotlin.math.abs(delta)) { sink.sendKey(key) }
    }

    /** Ações do painel de edição (selecionar, copiar, colar, setas). */
    fun editAction(action: EditAction) {
        if (composing.isNotEmpty()) commitComposing(autoCorrect = false, separator = "")
        when (action) {
            EditAction.SELECT_ALL -> sink.performContextMenuAction(android.R.id.selectAll)
            EditAction.COPY -> sink.performContextMenuAction(android.R.id.copy)
            EditAction.CUT -> sink.performContextMenuAction(android.R.id.cut)
            EditAction.PASTE -> sink.performContextMenuAction(android.R.id.paste)
            EditAction.UNDO -> sink.sendKey(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON)
            EditAction.LEFT -> sink.sendKey(KeyEvent.KEYCODE_DPAD_LEFT)
            EditAction.RIGHT -> sink.sendKey(KeyEvent.KEYCODE_DPAD_RIGHT)
            EditAction.UP -> sink.sendKey(KeyEvent.KEYCODE_DPAD_UP)
            EditAction.DOWN -> sink.sendKey(KeyEvent.KEYCODE_DPAD_DOWN)
            EditAction.SELECT_LEFT -> sink.sendKey(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.META_SHIFT_ON)
            EditAction.SELECT_RIGHT -> sink.sendKey(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.META_SHIFT_ON)
            EditAction.HOME -> sink.sendKey(KeyEvent.KEYCODE_MOVE_HOME)
            EditAction.END -> sink.sendKey(KeyEvent.KEYCODE_MOVE_END)
        }
    }

    /** Recalcula as sugestões (ex.: o dicionário acabou de carregar ou o idioma mudou). */
    fun refreshSuggestions() {
        if (composing.isNotEmpty() && !composingIsGesture) requestSuggestions()
    }

    // --- Interno ----------------------------------------------------------------------------------

    private fun canCompose(): Boolean = config.suggestions && settings.suggestions && !config.password

    /**
     * Se o cursor está logo depois de uma palavra (e não no meio de outra), traz essa palavra de
     * volta para a composição, para continuar digitando com sugestões.
     */
    private fun adoptWordBeforeCursor(requestNow: Boolean = false) {
        val after = sink.textAfterCursor(1)?.toString().orEmpty()
        if (after.isNotEmpty() && TextTools.isWordChar(after[0])) return
        val before = sink.textBeforeCursor(48)?.toString().orEmpty()
        var i = before.length
        while (i > 0 && (TextTools.isWordChar(before[i - 1]) || (TextTools.isJoiner(before[i - 1]) && i - 1 > 0 && TextTools.isWordChar(before[i - 2])))) i--
        val word = before.substring(i)
        if (word.isEmpty() || i == 0 && before.length >= 48) return
        sink.beginBatch()
        sink.deleteBefore(word.length)
        sink.setComposingText(word)
        sink.endBatch()
        composing.setLength(0)
        composing.append(word)
        composingIsGesture = false
        previousWord = TextTools.lastWord(before.substring(0, i))
        if (requestNow) requestSuggestions()
    }

    /** Confirma a palavra em composição, com ou sem correção, seguida de [separator]. */
    private fun commitComposing(autoCorrect: Boolean, separator: String, auto: Boolean = false) {
        val typed = composing.toString()
        val result = resultFor(typed)
        val allowAuto = autoCorrect && settings.autoCorrect && config.autoCorrect && typed != blockAuto
        val correction = if (allowAuto) result.autoCorrection?.takeIf { it != typed } else null
        val word = correction ?: typed
        sink.beginBatch()
        sink.commitText(word)
        if (separator.isNotEmpty()) sink.commitText(separator)
        sink.endBatch()
        lastAuto = if (correction != null) AutoCorrection(typed, correction, separator) else null
        if (correction == null && config.learning && settings.personalLearning && typed.length >= 2 && typed.all { TextTools.isWordChar(it) }) {
            val known = corrector.isKnown(typed, settings.language)
            if (!known) user.learn(typed)
        }
        if (typed != blockAuto) blockAuto = null
        previousWord = word.substringAfterLast(' ')
        composing.clear()
        composingIsGesture = false
        lastSpaceAuto = auto && separator == " "
        setResult(CorrectionResult.EMPTY)
    }

    private fun resultFor(typed: String): CorrectionResult {
        val current = _state.value.result
        if (current.word == typed && (current.suggestions.isNotEmpty() || current.autoCorrection != null)) return current
        if (typed.isEmpty() || !canCompose()) return CorrectionResult.EMPTY
        return compute(typed)
    }

    private fun compute(typed: String): CorrectionResult = corrector.suggest(
        typed = typed,
        previous = previousWord,
        language = settings.language,
        style = settings.correctionStyle,
        allowAutoCorrect = settings.autoCorrect && config.autoCorrect,
        withEmoji = settings.emojiSuggestions,
    )

    private fun requestSuggestions() {
        val typed = composing.toString()
        val version = ++suggestionVersion
        _state.update { it.copy(composing = typed) }
        if (!canCompose()) return
        runner.run({ compute(typed) }) { result ->
            if (version == suggestionVersion && composing.toString() == typed) setResult(result)
        }
    }

    private fun setResult(result: CorrectionResult) {
        _state.update { it.copy(result = result, composing = composing.toString()) }
    }

    private fun afterTyping() {
        if (_state.value.shift == ShiftState.ONCE) _state.update { it.copy(shift = ShiftState.OFF) }
    }

    private fun afterSeparator(text: String) {
        if (text.isNotBlank() && text != "\n") {
            // Pontuação não muda a palavra anterior, mas fim de frase zera o contexto.
            if (text.last() in ".!?") previousWord = null
        } else if (text == "\n") {
            previousWord = null
        }
        updateAutoShift()
    }

    private fun updateAutoShift() {
        if (_state.value.shift == ShiftState.LOCKED) return
        val caps = settings.autoCapitalize && config.autoCaps && composing.isEmpty() && sink.capsMode() != 0
        val next = if (caps) ShiftState.ONCE else ShiftState.OFF
        if (_state.value.shift != next) _state.update { it.copy(shift = next) }
    }

    companion object {
        private const val DOUBLE_TAP_MS = 350L
        private const val DOUBLE_SPACE_MS = 1_500L
        private val ATTACHING_PUNCTUATION = setOf(".", ",", "!", "?", ";", ":", ")")
        private val AUTO_SPACE_AFTER = setOf(".", ",", "!", "?", ";", ":")
    }
}

enum class EditAction { SELECT_ALL, COPY, CUT, PASTE, UNDO, LEFT, RIGHT, UP, DOWN, SELECT_LEFT, SELECT_RIGHT, HOME, END }
