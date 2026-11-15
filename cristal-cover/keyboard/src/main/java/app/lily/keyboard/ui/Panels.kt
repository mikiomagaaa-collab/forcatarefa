package app.lily.keyboard.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.FirstPage
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowRight
import androidx.compose.material.icons.automirrored.rounded.LastPage
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.automirrored.rounded.TextSnippet
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lily.keyboard.clipboard.ClipItem
import app.lily.keyboard.clipboard.ClipKind
import app.lily.keyboard.clipboard.ClipboardHistory
import app.lily.keyboard.core.KeyboardLanguage
import app.lily.keyboard.core.KeyboardSettings
import app.lily.keyboard.core.KeyboardTheme
import app.lily.keyboard.emoji.EmojiData
import app.lily.keyboard.emoji.EmojiGroup
import app.lily.keyboard.emoji.EmojiHistory
import app.lily.keyboard.engine.EditAction
import app.lily.keyboard.voice.VoiceInput

private fun KeyboardTheme.surface(): Color =
    keyColor.asColor().let { if (it.alpha < 0.08f) textColor.asColor().copy(alpha = 0.10f) else it }

@Composable
fun PanelHeader(panel: Panel, theme: KeyboardTheme, onBack: () -> Unit) {
    val title = when (panel) {
        Panel.EMOJI -> "Emojis"
        Panel.CLIPBOARD -> "Área de transferência"
        Panel.EDIT -> "Edição de texto"
        Panel.VOICE -> "Ditado por voz"
        Panel.LANGUAGE -> "Idioma e teclados"
        Panel.KEYS -> ""
    }
    Row(Modifier.fillMaxSize().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Chip("ABC", theme, onClick = onBack)
        Spacer(Modifier.width(10.dp))
        Text(title, color = theme.textColor.asColor(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

// --- Emojis -------------------------------------------------------------------------------------

private const val TAB_RECENT = -1
private const val TAB_FAVORITES = -2
private const val TAB_SEARCH = -3

@Composable
fun EmojiPanel(
    height: Dp,
    theme: KeyboardTheme,
    emojis: EmojiData?,
    history: EmojiHistory?,
    onEmoji: (String) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    val recent = remember { mutableStateOf(history?.recent().orEmpty()) }
    val favorites = remember { mutableStateOf(history?.favorites().orEmpty()) }
    var tab by remember { mutableIntStateOf(if (recent.value.isEmpty()) EmojiGroup.SMILEYS.id else TAB_RECENT) }
    var query by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf<String?>(null) }
    val ink = theme.textColor.asColor()

    Column(Modifier.fillMaxWidth().height(height)) {
        val list: List<String> = when {
            emojis == null -> emptyList()
            tab == TAB_RECENT -> recent.value
            tab == TAB_FAVORITES -> favorites.value
            tab == TAB_SEARCH -> emojis.search(query).map { it.value }
            else -> EmojiGroup.entries.firstOrNull { it.id == tab }?.let { g -> emojis.group(g).map { it.value } }.orEmpty()
        }
        if (tab == TAB_SEARCH) {
            // A busca usa um teclado de letras simples, para não sair do painel.
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(18.dp)).background(theme.surface()).padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(if (query.isEmpty()) "Buscar emoji (ex.: coração)" else query, color = ink.copy(alpha = if (query.isEmpty()) 0.55f else 1f), fontSize = 14.sp, maxLines = 1)
                }
            }
            Row(Modifier.fillMaxWidth().height(44.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                for (e in list.take(40)) EmojiCell(e, 44.dp, onClick = {
                    onEmoji(e)
                    recent.value = history?.recent().orEmpty()
                })
                if (list.isEmpty() && query.isNotEmpty()) Text("Nada encontrado", color = ink.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
            }
            MiniLetters(theme, Modifier.weight(1f), onLetter = { query += it }, onDelete = { query = query.dropLast(1) }, onSpace = { query += " " })
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (emojis == null) {
                    Text("Carregando emojis…", color = ink.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.Center))
                } else if (list.isEmpty()) {
                    Text(
                        if (tab == TAB_FAVORITES) "Toque e segure um emoji para guardá-lo nos favoritos." else "Os emojis que você usar aparecem aqui.",
                        color = ink.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                } else {
                    LazyVerticalGrid(columns = GridCells.Adaptive(44.dp), modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
                        items(list, key = { it }) { e ->
                            EmojiCell(
                                e, 44.dp,
                                onClick = {
                                    onEmoji(e)
                                    recent.value = history?.recent().orEmpty()
                                },
                                onLongClick = {
                                    val fav = history?.toggleFavorite(e) ?: false
                                    favorites.value = history?.favorites().orEmpty()
                                    hint = if (fav) "$e nos favoritos" else "$e saiu dos favoritos"
                                },
                            )
                        }
                    }
                }
                hint?.let { h ->
                    LaunchedEffect(h) {
                        kotlinx.coroutines.delay(1_400)
                        hint = null
                    }
                    Chip(h, theme, Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp), highlight = true) { hint = null }
                }
            }
        }
        // Abas de categorias
        Row(
            Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                TabIcon(Icons.Rounded.Search, "Buscar", tab == TAB_SEARCH, theme) {
                    tab = TAB_SEARCH
                    query = ""
                }
                TabIcon(Icons.Rounded.History, "Recentes", tab == TAB_RECENT, theme) { tab = TAB_RECENT }
                TabIcon(Icons.Rounded.Star, "Favoritos", tab == TAB_FAVORITES, theme) { tab = TAB_FAVORITES }
                for (g in EmojiGroup.entries) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (tab == g.id) theme.surface() else Color.Transparent)
                            .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { tab = g.id },
                        contentAlignment = Alignment.Center,
                    ) { Text(g.icon, fontSize = 18.sp) }
                }
            }
            RoundButton(Icons.AutoMirrored.Rounded.Backspace, "Apagar", theme, size = 36.dp, onClick = onDelete)
        }
    }
}

@Composable
private fun TabIcon(icon: ImageVector, label: String, selected: Boolean, theme: KeyboardTheme, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (selected) theme.surface() else Color.Transparent)
            .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = theme.textColor.asColor(), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun EmojiCell(emoji: String, size: Dp, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Box(
        Modifier
            .size(size)
            .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onLongClick = onLongClick, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = 26.sp)
    }
}

/** Teclado de letras reduzido, só para a busca de emojis. */
@Composable
private fun MiniLetters(theme: KeyboardTheme, modifier: Modifier, onLetter: (String) -> Unit, onDelete: () -> Unit, onSpace: () -> Unit) {
    val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnmç")
    Column(modifier.fillMaxWidth().padding(horizontal = 3.dp), verticalArrangement = Arrangement.SpaceEvenly) {
        for ((i, row) in rows.withIndex()) {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.Center) {
                for (c in row) MiniKey(c.toString(), theme, Modifier.weight(1f)) { onLetter(c.toString()) }
                if (i == 2) MiniKey("⌫", theme, Modifier.weight(1.5f), onDelete)
            }
        }
        Row(Modifier.fillMaxWidth().weight(1f)) { MiniKey("espaço", theme, Modifier.weight(1f), onSpace) }
    }
}

@Composable
private fun MiniKey(label: String, theme: KeyboardTheme, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxHeight()
            .padding(2.dp)
            .clip(RoundedCornerShape(theme.cornerRadius.dp))
            .background(theme.surface())
            .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = theme.textColor.asColor(), fontSize = 15.sp)
    }
}

// --- Área de transferência ----------------------------------------------------------------------

@Composable
fun ClipboardPanel(
    height: Dp,
    theme: KeyboardTheme,
    history: ClipboardHistory?,
    enabled: Boolean,
    password: Boolean,
    onEnable: () -> Unit,
    onPaste: (String) -> Unit,
) {
    val ink = theme.textColor.asColor()
    LaunchedEffect(history) { history?.prune() }
    val items by (history?.items ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList())).collectAsState()
    var menuFor by remember { mutableStateOf<ClipItem?>(null) }
    Column(Modifier.fillMaxWidth().height(height).padding(horizontal = 8.dp)) {
        when {
            password -> CenterNote("Em campos de senha o histórico fica escondido.", theme)
            !enabled -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "O histórico está desligado. Ligado, ele guarda por 1 hora o que você copia (senhas e códigos ficam de fora).",
                    color = ink.copy(alpha = 0.8f), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp),
                )
                Chip("Ligar histórico", theme, highlight = true, onClick = onEnable)
            }
            items.isEmpty() -> CenterNote("Copie um texto em qualquer app e ele aparece aqui por 1 hora. Toque e segure para fixar.", theme)
            else -> {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Toque para colar · segure para fixar ou apagar", color = ink.copy(alpha = 0.65f), fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Chip("Limpar", theme) { history?.clear(keepPinned = true) }
                }
                LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize()) {
                    items(items, key = { it.id }) { item ->
                        ClipCard(item, theme, onClick = { onPaste(item.text) }, onLongClick = { menuFor = item })
                    }
                }
            }
        }
    }
    menuFor?.let { item ->
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            Chip(if (item.pinned) "Desafixar" else "Fixar", theme, highlight = true) {
                history?.togglePin(item.id)
                menuFor = null
            }
            Chip("Apagar", theme) {
                history?.remove(item.id)
                menuFor = null
            }
            Chip("Cancelar", theme) { menuFor = null }
        }
    }
}

@Composable
private fun CenterNote(text: String, theme: KeyboardTheme) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = theme.textColor.asColor().copy(alpha = 0.75f), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(20.dp))
    }
}

@Composable
private fun ClipCard(item: ClipItem, theme: KeyboardTheme, onClick: () -> Unit, onLongClick: () -> Unit) {
    val ink = theme.textColor.asColor()
    val icon = when (item.kind) {
        ClipKind.LINK -> Icons.Rounded.Link
        ClipKind.EMAIL -> Icons.Rounded.Email
        ClipKind.PHONE -> Icons.Rounded.Phone
        ClipKind.NUMBER -> Icons.Rounded.Numbers
        ClipKind.TEXT -> Icons.AutoMirrored.Rounded.TextSnippet
    }
    Column(
        Modifier
            .padding(3.dp)
            .fillMaxWidth()
            .height(74.dp)
            .clip(RoundedCornerShape(theme.cornerRadius.dp + 2.dp))
            .background(theme.surface())
            .then(if (item.pinned) Modifier.border(1.dp, theme.accentColor.asColor(), RoundedCornerShape(theme.cornerRadius.dp + 2.dp)) else Modifier)
            .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onLongClick = onLongClick, onClick = onClick)
            .padding(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = ink.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
            Spacer(Modifier.weight(1f))
            if (item.pinned) Icon(Icons.Rounded.PushPin, contentDescription = "Fixado", tint = theme.accentColor.asColor(), modifier = Modifier.size(14.dp))
        }
        Text(item.text, color = ink, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
    }
}

// --- Edição ---------------------------------------------------------------------------------------

@Composable
fun EditPanel(height: Dp, theme: KeyboardTheme, onAction: (EditAction) -> Unit, onDelete: () -> Unit) {
    val rows: List<List<Triple<ImageVector, String, () -> Unit>>> = listOf(
        listOf(
            Triple(Icons.Rounded.SelectAll, "Selecionar tudo") { onAction(EditAction.SELECT_ALL) },
            Triple(Icons.Rounded.ArrowUpward, "Para cima") { onAction(EditAction.UP) },
            Triple(Icons.Rounded.ContentCopy, "Copiar") { onAction(EditAction.COPY) },
        ),
        listOf(
            Triple(Icons.AutoMirrored.Rounded.ArrowBack, "Para a esquerda") { onAction(EditAction.LEFT) },
            Triple(Icons.Rounded.ContentPaste, "Colar") { onAction(EditAction.PASTE) },
            Triple(Icons.AutoMirrored.Rounded.ArrowForward, "Para a direita") { onAction(EditAction.RIGHT) },
        ),
        listOf(
            Triple(Icons.Rounded.KeyboardDoubleArrowLeft, "Selecionar à esquerda") { onAction(EditAction.SELECT_LEFT) },
            Triple(Icons.Rounded.ArrowDownward, "Para baixo") { onAction(EditAction.DOWN) },
            Triple(Icons.Rounded.KeyboardDoubleArrowRight, "Selecionar à direita") { onAction(EditAction.SELECT_RIGHT) },
        ),
        listOf(
            Triple(Icons.Rounded.FirstPage, "Início") { onAction(EditAction.HOME) },
            Triple(Icons.Rounded.ContentCut, "Recortar") { onAction(EditAction.CUT) },
            Triple(Icons.AutoMirrored.Rounded.LastPage, "Fim") { onAction(EditAction.END) },
        ),
    )
    Row(Modifier.fillMaxWidth().height(height).padding(6.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (row in rows) {
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((icon, label, action) in row) EditKey(icon, label, theme, Modifier.weight(1f), action)
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.width(72.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EditKey(Icons.AutoMirrored.Rounded.Backspace, "Apagar", theme, Modifier.weight(1f), onDelete)
            EditKey(Icons.AutoMirrored.Rounded.Undo, "Desfazer", theme, Modifier.weight(1f)) { onAction(EditAction.UNDO) }
        }
    }
}

@Composable
private fun EditKey(icon: ImageVector, label: String, theme: KeyboardTheme, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(theme.cornerRadius.dp + 2.dp))
            .background(theme.surface())
            .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = theme.textColor.asColor(), modifier = Modifier.size(22.dp))
    }
}

// --- Voz ----------------------------------------------------------------------------------------

@Composable
fun VoicePanel(
    height: Dp,
    theme: KeyboardTheme,
    voice: VoiceInput?,
    language: KeyboardLanguage,
    offlineOnly: Boolean,
    onPermission: () -> Unit,
    onInsert: (String) -> Unit,
    onClose: () -> Unit,
) {
    val ink = theme.textColor.asColor()
    var startError by remember { mutableStateOf<String?>(null) }
    var needsPermission by remember { mutableStateOf(false) }
    fun start() {
        if (voice == null) return
        val err = voice.start(language.tag, offlineOnly)
        needsPermission = err == "PERMISSAO"
        startError = if (err == "PERMISSAO") null else err
    }
    DisposableEffect(voice) {
        start()
        onDispose { voice?.reset() }
    }
    val state = voice?.state?.collectAsState()?.value
    val level by animateFloatAsState(state?.level ?: 0f, label = "nível")
    Column(
        Modifier.fillMaxWidth().height(height).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        val message = when {
            voice == null -> "O ditado não está disponível nesta prévia."
            needsPermission -> "Para ditar, permita o uso do microfone pelo L.I.L.Y Keyboard."
            startError != null -> startError
            state?.error != null -> state.error
            state?.listening == true -> state.partial.ifBlank { "Fale agora…" }
            !state?.final.isNullOrBlank() -> state?.final
            else -> "Toque no microfone para falar."
        }
        Text(message.orEmpty(), color = ink, fontSize = 16.sp, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis)
        Box(
            Modifier
                .size(76.dp)
                .scale(1f + level * 0.25f)
                .clip(CircleShape)
                .background(if (state?.listening == true) theme.accentColor.asColor() else theme.surface())
                .combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    when {
                        needsPermission -> onPermission()
                        state?.listening == true -> voice?.finish()
                        else -> start()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Mic, contentDescription = "Microfone", tint = if (state?.listening == true) onAccentColor(theme) else ink, modifier = Modifier.size(34.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Chip("Cancelar", theme, onClick = onClose)
            val text = state?.final?.takeIf { it.isNotBlank() } ?: state?.partial?.takeIf { it.isNotBlank() && !state.listening }
            if (needsPermission) {
                Chip("Permitir microfone", theme, highlight = true, onClick = onPermission)
            } else if (text != null) {
                Chip("Inserir", theme, highlight = true) { onInsert(text) }
            }
        }
        Text(
            if (state?.offline == false) "Reconhecimento do sistema (pode usar a internet)" else "Reconhecimento no aparelho, sem internet",
            color = ink.copy(alpha = 0.55f),
            fontSize = 11.sp,
        )
    }
}

// --- Idioma ---------------------------------------------------------------------------------------

@Composable
fun LanguagePanel(
    height: Dp,
    theme: KeyboardTheme,
    settings: KeyboardSettings,
    onLanguage: (KeyboardLanguage) -> Unit,
    onOtherKeyboards: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().height(height).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (lang in settings.languages.ifEmpty { KeyboardLanguage.entries }) {
            Chip(
                (if (lang == settings.language) "✓ " else "") + lang.label,
                theme,
                Modifier.fillMaxWidth(),
                highlight = lang == settings.language,
            ) { onLanguage(lang) }
        }
        Chip("Outros teclados do aparelho", theme, Modifier.fillMaxWidth(), onClick = onOtherKeyboards)
        Chip("Configurações do L.I.L.Y Keyboard", theme, Modifier.fillMaxWidth(), onClick = onSettings)
    }
}
