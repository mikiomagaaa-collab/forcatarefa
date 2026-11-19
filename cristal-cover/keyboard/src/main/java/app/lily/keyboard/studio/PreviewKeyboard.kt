package app.lily.keyboard.studio

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lily.keyboard.core.KeyboardSettings
import app.lily.keyboard.core.KeyboardStore
import app.lily.keyboard.core.KeyboardTheme
import app.lily.keyboard.emoji.EmojiHistory
import app.lily.keyboard.engine.EditorConfig
import app.lily.keyboard.engine.InputController
import app.lily.keyboard.engine.LanguageModels
import app.lily.keyboard.engine.SuggestionRunner
import app.lily.keyboard.engine.TextSink
import app.lily.keyboard.ui.Feedback
import app.lily.keyboard.ui.KeyboardHost
import app.lily.keyboard.ui.KeyboardRoot
import app.lily.keyboard.ui.Panel

/**
 * Campo de texto de mentira, para a prévia do Studio: o cursor fica sempre no fim e a parte em
 * composição é a palavra sublinhada.
 */
class PreviewSink : TextSink {
    var committed by mutableStateOf("")
    var composing by mutableStateOf("")

    private val full: String get() = committed + composing

    override fun textBeforeCursor(n: Int): CharSequence = full.takeLast(n)
    override fun textAfterCursor(n: Int): CharSequence = ""
    override fun hasSelection(): Boolean = false
    override fun commitText(text: CharSequence) {
        committed += text
        composing = ""
        trim()
    }
    override fun setComposingText(text: CharSequence) {
        composing = text.toString()
    }
    override fun finishComposingText() {
        committed += composing
        composing = ""
    }
    override fun deleteBefore(count: Int) {
        finishComposingText()
        committed = committed.dropLast(count)
    }
    override fun sendKey(keyCode: Int, meta: Int) {
        if (keyCode == KeyEvent.KEYCODE_DEL) deleteBefore(1)
    }
    override fun performEditorAction(action: Int) = Unit
    override fun performContextMenuAction(id: Int) = Unit
    override fun beginBatch() = Unit
    override fun endBatch() = Unit
    override fun capsMode(): Int {
        val t = full.trimEnd()
        return if (t.isEmpty() || t.last() in ".!?\n") 1 else 0
    }

    private fun trim() {
        if (committed.length > 400) committed = committed.takeLast(300)
    }
}

/** Teclado de verdade (mesmo código do serviço) ligado a um campo de demonstração. */
@Composable
fun PreviewKeyboard(
    settings: KeyboardSettings,
    theme: KeyboardTheme? = null,
    interactive: Boolean = true,
    showField: Boolean = true,
    initialPanel: Panel = Panel.KEYS,
) {
    val context = LocalContext.current
    val models = remember { LanguageModels.get(context) }
    val sink = remember { PreviewSink() }
    val controller = remember {
        InputController(sink, models.corrector, models.user, SuggestionRunner.Immediate).apply {
            this.settings = settings
            startInput(EditorConfig().copy(learning = false))
        }
    }
    LaunchedEffect(settings) {
        controller.settings = settings
        models.preload(settings.language)
    }
    val store = remember { KeyboardStore.get(context) }
    val feedback = remember { Feedback(context) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).border(1.dp, StudioColors.cardBorder, RoundedCornerShape(22.dp))) {
        if (showField) {
            Box(Modifier.fillMaxWidth().background(StudioColors.card).heightIn(min = 64.dp).padding(14.dp)) {
                val text = buildAnnotatedString {
                    append(sink.committed)
                    withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(sink.composing) }
                    append("▏")
                }
                if (sink.committed.isEmpty() && sink.composing.isEmpty()) {
                    Text("Digite aqui para testar (deslize o dedo pelas letras também)", color = StudioColors.secondary, fontSize = 14.sp)
                } else {
                    Text(text, color = StudioColors.text, fontSize = 16.sp)
                }
            }
        }
        Box(Modifier.fillMaxWidth()) {
            KeyboardRoot(
                controller = controller,
                settings = settings,
                host = object : KeyboardHost {},
                store = if (interactive) store else null,
                models = models,
                emojiHistory = EmojiHistory(store),
                feedback = if (interactive) feedback else null,
                themeOverride = theme,
                initialPanel = initialPanel,
            )
            if (!interactive) Box(Modifier.matchParentSize().background(androidx.compose.ui.graphics.Color.Transparent))
        }
    }
}
