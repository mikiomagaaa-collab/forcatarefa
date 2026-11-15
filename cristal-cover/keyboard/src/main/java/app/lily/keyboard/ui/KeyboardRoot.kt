package app.lily.keyboard.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.KeyboardHide
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwipeLeft
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lily.keyboard.clipboard.ClipboardHistory
import app.lily.keyboard.core.KeyCode
import app.lily.keyboard.core.KeyLayouts
import app.lily.keyboard.core.KeyboardLanguage
import app.lily.keyboard.core.KeyboardSettings
import app.lily.keyboard.core.KeyboardStore
import app.lily.keyboard.core.KeyboardTheme
import app.lily.keyboard.core.LayoutId
import app.lily.keyboard.core.LayoutOptions
import app.lily.keyboard.core.OneHandSide
import app.lily.keyboard.core.ToolbarItem
import app.lily.keyboard.emoji.EmojiHistory
import app.lily.keyboard.engine.GestureDecoder
import app.lily.keyboard.engine.InputController
import app.lily.keyboard.engine.LanguageModels
import app.lily.keyboard.engine.Suggestion
import app.lily.keyboard.voice.VoiceInput
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Ações que dependem do serviço do teclado (ou da tela de prévia, que as ignora). */
interface KeyboardHost {
    fun openStudio(page: String? = null) {}
    fun showInputMethodPicker() {}
    fun requestVoicePermission() {}
    fun hideKeyboard() {}
}

enum class Panel { KEYS, EMOJI, CLIPBOARD, EDIT, VOICE, LANGUAGE }

/**
 * O teclado inteiro: barra de sugestões/atalhos, teclas ou painéis, fundo do tema e o modo de uma mão.
 */
@Composable
fun KeyboardRoot(
    controller: InputController,
    settings: KeyboardSettings,
    host: KeyboardHost,
    store: KeyboardStore? = null,
    models: LanguageModels? = null,
    clipboard: ClipboardHistory? = null,
    voice: VoiceInput? = null,
    emojiHistory: EmojiHistory? = null,
    feedback: Feedback? = null,
    bottomInset: Dp = 0.dp,
    themeOverride: KeyboardTheme? = null,
    initialPanel: Panel = Panel.KEYS,
) {
    val ui by controller.state.collectAsState()
    val theme = themeOverride ?: settings.theme
    val scope = rememberCoroutineScope()
    val config = LocalConfiguration.current
    // Tela externa do Razr (ou janela pequena): teclas mais baixas e sem fileira de números.
    val compact = config.screenHeightDp < 560
    val keyHeight = (if (compact) settings.keyHeightDp.coerceAtMost(40f) else settings.keyHeightDp).dp
    val stripHeight = if (compact) 38.dp else 44.dp
    var panel by remember { mutableStateOf(initialPanel) }
    var toolbarOpen by remember { mutableStateOf(false) }
    // Campo novo: volta para as teclas (mas não na primeira composição, que pode abrir um painel).
    var lastConfig by remember { mutableStateOf(ui.config) }
    LaunchedEffect(ui.config) {
        if (ui.config != lastConfig) {
            lastConfig = ui.config
            panel = Panel.KEYS
            toolbarOpen = false
        }
    }

    val options = LayoutOptions(
        language = settings.language,
        numberRow = settings.numberRow && !compact && ui.layout == LayoutId.LETTERS,
        cedilla = settings.cedillaKey,
        punctuationWidth = settings.punctuationWidth,
        field = ui.config.field,
    )
    val layout = remember(ui.layout, options) { KeyLayouts.build(ui.layout, options) }
    val lettersRows = if (options.numberRow) 5 else 4
    val panelHeight = keyHeight * lettersRows + 8.dp

    val actions = remember(controller, feedback, settings) {
        KeyActions(
            onKey = { key ->
                when (key.code) {
                    KeyCode.CHAR -> controller.onChar(key.output)
                    KeyCode.SPACE -> controller.onSpace()
                    KeyCode.ENTER -> controller.onEnter()
                    KeyCode.SHIFT -> controller.onShift()
                    KeyCode.TO_SYMBOLS -> controller.setLayout(LayoutId.SYMBOLS)
                    KeyCode.TO_MORE_SYMBOLS -> controller.setLayout(LayoutId.MORE_SYMBOLS)
                    // Volta para o teclado base do campo (letras, ou números num campo numérico).
                    KeyCode.TO_LETTERS -> controller.setLayout(controller.state.value.config.layout)
                    KeyCode.EMOJI -> panel = Panel.EMOJI
                    KeyCode.DELETE -> Unit
                }
            },
            onDelete = controller::onDelete,
            onDeleteWord = controller::onDeleteWord,
            onAlternate = { controller.onChar(it) },
            onCursor = controller::moveCursor,
            onGesture = { trace, geometry ->
                val m = models
                val dict = m?.dictionary(settings.language)
                if (m != null && dict != null) {
                    scope.launch {
                        val words = withContext(Dispatchers.Default) {
                            GestureDecoder(geometry.letterCenters(), geometry.keyWidth).decode(trace, dict, m.user)
                        }
                        controller.onGesture(words)
                    }
                }
            },
            onSpaceLongPress = { panel = Panel.LANGUAGE },
            onFeedback = { code -> feedback?.key(code, settings) },
        )
    }

    val background = rememberThemeBackground(theme)
    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = 1f }
            .background(
                Brush.verticalGradient(
                    listOf(
                        theme.background.asColor().copy(alpha = theme.backgroundAlpha.coerceIn(0f, 1f)),
                        theme.background2.asColor().copy(alpha = theme.backgroundAlpha.coerceIn(0f, 1f)),
                    ),
                ),
            ),
    ) {
        if (background != null) {
            Image(
                bitmap = background,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        val r = theme.imageBlur.coerceIn(0f, 1f) * 24.dp.toPx()
                        renderEffect = if (r > 0.5f) BlurEffect(r, r, TileMode.Clamp) else null
                        clip = true
                    },
            )
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = theme.imageDim.coerceIn(0f, 0.85f))))
        }
        Column(Modifier.fillMaxWidth().padding(bottom = bottomInset + settings.bottomOffset.coerceIn(0f, 64f).dp)) {
            // Barra superior
            Box(Modifier.fillMaxWidth().height(stripHeight)) {
                when {
                    panel != Panel.KEYS -> PanelHeader(panel, theme) { panel = Panel.KEYS }
                    !toolbarOpen && (ui.composing.isNotEmpty() || ui.result.suggestions.isNotEmpty()) && ui.config.suggestions && settings.suggestions ->
                        SuggestionStrip(
                            theme = theme,
                            suggestions = ui.result.suggestions,
                            autoCorrection = ui.result.autoCorrection,
                            emoji = ui.result.emoji,
                            repeated = ui.result.repeated,
                            onToolbar = { toolbarOpen = true },
                            onPick = controller::onSuggestion,
                            onEmoji = { e ->
                                controller.commitRaw(e)
                                emojiHistory?.used(e)
                            },
                            onRemoveRepeated = controller::removeRepeatedWord,
                            onForget = { s -> models?.user?.forget(s.text) },
                        )
                    else -> Toolbar(
                        theme = theme,
                        items = settings.toolbar,
                        language = settings.language,
                        canClose = toolbarOpen,
                        onClose = { toolbarOpen = false },
                        onItem = { item ->
                            toolbarOpen = false
                            when (item) {
                                ToolbarItem.EMOJI -> panel = Panel.EMOJI
                                ToolbarItem.CLIPBOARD -> panel = Panel.CLIPBOARD
                                ToolbarItem.EDIT -> panel = Panel.EDIT
                                ToolbarItem.VOICE -> panel = Panel.VOICE
                                ToolbarItem.ONE_HAND -> store?.update { it.copy(oneHanded = !it.oneHanded) }
                                ToolbarItem.LANGUAGE -> store?.update { s ->
                                    val list = s.languages.ifEmpty { listOf(KeyboardLanguage.PT_BR) }
                                    s.copy(language = list[(list.indexOf(s.language) + 1).mod(list.size)])
                                }
                                ToolbarItem.THEMES -> host.openStudio("temas")
                                ToolbarItem.SETTINGS -> host.openStudio(null)
                            }
                        },
                        onHide = host::hideKeyboard,
                    )
                }
            }

            OneHandFrame(settings = settings, store = store, theme = theme, enabled = settings.oneHanded && !compact) {
                when (panel) {
                    Panel.KEYS -> KeysPad(
                        layout = layout,
                        shift = ui.shift,
                        enter = ui.config.enter,
                        theme = theme,
                        settings = settings,
                        keyHeight = keyHeight,
                        spaceLabel = if (ui.config.password) "Senha" else settings.language.shortLabel(),
                        actions = actions,
                        modifier = Modifier.padding(horizontal = settings.sideMargin.coerceIn(0f, 48f).dp),
                        gestureEnabled = ui.config.suggestions && ui.layout == LayoutId.LETTERS,
                    )
                    Panel.EMOJI -> EmojiPanel(
                        height = panelHeight,
                        theme = theme,
                        emojis = models?.emojis,
                        history = emojiHistory,
                        onEmoji = { e ->
                            controller.commitRaw(e)
                            emojiHistory?.used(e)
                            feedback?.key(KeyCode.CHAR, settings)
                        },
                        onDelete = {
                            controller.onDelete()
                            feedback?.key(KeyCode.DELETE, settings)
                        },
                        onBack = { panel = Panel.KEYS },
                    )
                    Panel.CLIPBOARD -> ClipboardPanel(
                        height = panelHeight,
                        theme = theme,
                        history = clipboard,
                        enabled = settings.clipboardHistory,
                        password = ui.config.password,
                        onEnable = { store?.update { it.copy(clipboardHistory = true) } },
                        onPaste = { text ->
                            controller.commitRaw(text)
                            panel = Panel.KEYS
                        },
                    )
                    Panel.EDIT -> EditPanel(height = panelHeight, theme = theme, onAction = controller::editAction, onDelete = controller::onDelete)
                    Panel.VOICE -> VoicePanel(
                        height = panelHeight,
                        theme = theme,
                        voice = voice,
                        language = settings.language,
                        offlineOnly = settings.voiceOfflineOnly,
                        onPermission = host::requestVoicePermission,
                        onInsert = { text ->
                            controller.commitRaw(text)
                            panel = Panel.KEYS
                        },
                        onClose = { panel = Panel.KEYS },
                    )
                    Panel.LANGUAGE -> LanguagePanel(
                        height = panelHeight,
                        theme = theme,
                        settings = settings,
                        onLanguage = { lang ->
                            store?.update { it.copy(language = lang) }
                            panel = Panel.KEYS
                        },
                        onOtherKeyboards = {
                            panel = Panel.KEYS
                            host.showInputMethodPicker()
                        },
                        onSettings = { host.openStudio(null) },
                    )
                }
            }
        }
    }
}

fun KeyboardLanguage.shortLabel(): String = when (this) {
    KeyboardLanguage.PT_BR -> "Português"
    KeyboardLanguage.EN -> "English"
}

@Composable
private fun rememberThemeBackground(theme: KeyboardTheme): ImageBitmap? {
    val context = LocalContext.current
    val name = theme.backgroundImage ?: return null
    return remember(name) {
        runCatching {
            val f = File(context.filesDir, name)
            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
        }.getOrNull()
    }
}

/** Modo de uma mão: o teclado encolhe para um lado e o outro lado ganha botões para voltar. */
@Composable
private fun OneHandFrame(
    settings: KeyboardSettings,
    store: KeyboardStore?,
    theme: KeyboardTheme,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }
    val fraction = settings.oneHandWidth.coerceIn(0.6f, 0.95f)
    val right = settings.oneHandSide == OneHandSide.RIGHT
    // A altura vem só das teclas; os botões ocupam a sobra ao lado, sem esticar o teclado.
    Box(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth(fraction).align(if (right) Alignment.CenterEnd else Alignment.CenterStart)) { content() }
        Box(Modifier.matchParentSize()) {
            Column(
                Modifier.fillMaxHeight().fillMaxWidth(1f - fraction).align(if (right) Alignment.CenterStart else Alignment.CenterEnd),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RoundButton(Icons.Rounded.OpenInFull, "Tamanho normal", theme) { store?.update { it.copy(oneHanded = false) } }
                RoundButton(Icons.Rounded.SwapHoriz, "Trocar de lado", theme) {
                    store?.update { it.copy(oneHandSide = if (it.oneHandSide == OneHandSide.LEFT) OneHandSide.RIGHT else OneHandSide.LEFT) }
                }
            }
        }
    }
}

@Composable
fun RoundButton(icon: ImageVector, label: String, theme: KeyboardTheme, size: Dp = 40.dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(theme.functionKeyColor.asColor().let { if (it.alpha < 0.08f) theme.textColor.asColor().copy(alpha = 0.10f) else it })
            .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = theme.textColor.asColor(), modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
private fun SuggestionStrip(
    theme: KeyboardTheme,
    suggestions: List<Suggestion>,
    autoCorrection: String?,
    emoji: String?,
    repeated: Boolean,
    onToolbar: () -> Unit,
    onPick: (Suggestion) -> Unit,
    onEmoji: (String) -> Unit,
    onRemoveRepeated: () -> Unit,
    onForget: (Suggestion) -> Unit,
) {
    val ink = theme.textColor.asColor()
    Row(Modifier.fillMaxSize().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundButton(Icons.Rounded.Widgets, "Atalhos", theme, size = 34.dp, onClick = onToolbar)
        if (repeated) {
            Chip("Palavra repetida · remover", theme, Modifier.weight(1f).padding(horizontal = 6.dp), highlight = true, onClick = onRemoveRepeated)
        } else {
            // Ordem visual: segunda | primeira (centro) | terceira, como nos teclados do Android.
            val slots = listOf(suggestions.getOrNull(1), suggestions.getOrNull(0), suggestions.getOrNull(2))
            for ((i, s) in slots.withIndex()) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = s != null,
                            onLongClick = { s?.let(onForget) },
                            onClick = { s?.let(onPick) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (s != null) {
                        val primary = i == 1
                        val text = if (s.kind == Suggestion.Kind.TYPED) "“${s.text}”" else s.text
                        Text(
                            text,
                            color = if (primary && autoCorrection != null) theme.accentColor.asColor().let { if (theme.dark) it else ink } else ink,
                            fontSize = 16.sp,
                            fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
                if (i < 2) Box(Modifier.width(1.dp).height(18.dp).background(ink.copy(alpha = 0.15f)))
            }
        }
        if (emoji != null) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onEmoji(emoji) },
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, fontSize = 20.sp)
            }
        }
    }
}

@Composable
fun Chip(text: String, theme: KeyboardTheme, modifier: Modifier = Modifier, highlight: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (highlight) theme.accentColor.asColor().copy(alpha = 0.85f) else theme.keyColor.asColor().let { if (it.alpha < 0.08f) theme.textColor.asColor().copy(alpha = 0.10f) else it })
            .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (highlight) onAccentColor(theme) else theme.textColor.asColor(),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

fun onAccentColor(theme: KeyboardTheme): Color {
    val c = theme.accentColor.asColor()
    val lum = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
    return if (lum > 0.6f) Color(0xFF15131F) else Color.White
}

@Composable
private fun Toolbar(
    theme: KeyboardTheme,
    items: List<ToolbarItem>,
    language: KeyboardLanguage,
    canClose: Boolean,
    onClose: () -> Unit,
    onItem: (ToolbarItem) -> Unit,
    onHide: () -> Unit,
) {
    Row(Modifier.fillMaxSize().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (canClose) RoundButton(Icons.Rounded.Widgets, "Voltar às sugestões", theme, size = 34.dp, onClick = onClose)
            for (item in items) {
                val icon = when (item) {
                    ToolbarItem.EMOJI -> Icons.Rounded.EmojiEmotions
                    ToolbarItem.CLIPBOARD -> Icons.Rounded.ContentPaste
                    ToolbarItem.EDIT -> Icons.Rounded.TextFields
                    ToolbarItem.VOICE -> Icons.Rounded.Mic
                    ToolbarItem.ONE_HAND -> Icons.Rounded.SwipeLeft
                    ToolbarItem.LANGUAGE -> Icons.Rounded.Language
                    ToolbarItem.THEMES -> Icons.Rounded.Palette
                    ToolbarItem.SETTINGS -> Icons.Rounded.Settings
                }
                val label = if (item == ToolbarItem.LANGUAGE) "Idioma: ${language.shortLabel()}" else item.label
                RoundButton(icon, label, theme, size = 34.dp) { onItem(item) }
            }
        }
        Spacer(Modifier.width(6.dp))
        RoundButton(Icons.Rounded.KeyboardHide, "Esconder teclado", theme, size = 34.dp, onClick = onHide)
    }
}
