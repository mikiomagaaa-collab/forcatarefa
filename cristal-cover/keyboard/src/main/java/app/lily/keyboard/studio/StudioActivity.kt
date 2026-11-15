package app.lily.keyboard.studio

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Spellcheck
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.lily.keyboard.BuildConfig
import app.lily.keyboard.core.CorrectionStyle
import app.lily.keyboard.core.KeyboardLanguage
import app.lily.keyboard.core.KeyboardSettings
import app.lily.keyboard.core.KeyboardStore
import app.lily.keyboard.core.KeyboardTheme
import app.lily.keyboard.core.OneHandSide
import app.lily.keyboard.core.SizeMode
import app.lily.keyboard.core.ThemeStyle
import app.lily.keyboard.core.ToolbarItem
import app.lily.keyboard.engine.LanguageModels
import app.lily.keyboard.ui.asColor
import app.lily.keyboard.voice.VoiceInput
import java.io.File
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** Keyboard Studio: ativação, temas, ergonomia, correção, barra, voz e privacidade. */
class StudioActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val start = intent.getStringExtra(EXTRA_PAGE)
        setContent { StudioApp(start) }
    }

    companion object {
        const val EXTRA_PAGE = "page"
    }
}

private enum class Page { HOME, THEMES, EDITOR, LAYOUT, TYPING, TOOLBAR, LANGUAGES, VOICE, CLIPBOARD, PRIVACY, ABOUT }

@Composable
private fun StudioApp(start: String?) {
    val context = LocalContext.current
    val store = remember { KeyboardStore.get(context) }
    val settings by store.settings.collectAsState()
    var page by rememberSaveable { mutableStateOf(if (start == "temas") Page.THEMES.name else Page.HOME.name) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val current = Page.valueOf(page)
    BackHandler(enabled = current != Page.HOME) { page = if (current == Page.EDITOR) Page.THEMES.name else Page.HOME.name }
    val back = { page = if (current == Page.EDITOR) Page.THEMES.name else Page.HOME.name }
    when (current) {
        Page.HOME -> HomePage(settings) { page = it.name }
        Page.THEMES -> ThemesPage(store, settings, onBack = back) { id ->
            editingId = id
            page = Page.EDITOR.name
        }
        Page.EDITOR -> ThemeEditor(store, settings, editingId, onBack = back)
        Page.LAYOUT -> LayoutPage(store, settings, back)
        Page.TYPING -> TypingPage(store, settings, back)
        Page.TOOLBAR -> ToolbarPage(store, settings, back)
        Page.LANGUAGES -> LanguagesPage(store, settings, back)
        Page.VOICE -> VoicePage(store, settings, back)
        Page.CLIPBOARD -> ClipboardPage(store, settings, back)
        Page.PRIVACY -> PrivacyPage(store, settings, back)
        Page.ABOUT -> AboutPage(back)
    }
}

// --- Início -------------------------------------------------------------------------------------

@Composable
private fun HomePage(settings: KeyboardSettings, go: (Page) -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    val imm = context.getSystemService(InputMethodManager::class.java)
    val enabled = remember(tick) { runCatching { imm.enabledInputMethodList.any { it.packageName == context.packageName } }.getOrDefault(false) }
    val selected = remember(tick) {
        runCatching { Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.startsWith(context.packageName) == true }
            .getOrDefault(false)
    }
    StudioScaffold("L.I.L.Y Keyboard", onBack = null) {
        item {
            Info("Teclado com vidro líquido, correção inteligente em português e digitação por deslize, feito para o Razr 40 Ultra. Tudo roda no aparelho: o app nem tem permissão de internet.")
        }
        if (!enabled || !selected) {
            item {
                Section("Ativar em dois passos") {
                    Step(1, "Ligar o L.I.L.Y Keyboard", enabled) {
                        runCatching { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
                    }
                    Step(2, "Escolher como teclado", selected) {
                        if (!enabled) {
                            Toast.makeText(context, "Primeiro faça o passo 1.", Toast.LENGTH_SHORT).show()
                        } else {
                            imm.showInputMethodPicker()
                        }
                    }
                    Info("O Android mostra um aviso padrão para qualquer teclado novo (\"pode coletar o que você digita\"). O L.I.L.Y não envia nada para fora: não tem acesso à internet.")
                }
            }
        }
        item {
            Section("Teste agora") {
                PreviewKeyboard(settings)
            }
        }
        item { NavItem(Icons.Rounded.Palette, "Temas e Keyboard Studio", "Liquid Glass, One UI, AMOLED, cores, fundo da galeria") { go(Page.THEMES) } }
        item { NavItem(Icons.Rounded.Straighten, "Tamanho e ergonomia", "Altura, uma mão, fileira de números, ç, espaço") { go(Page.LAYOUT) } }
        item { NavItem(Icons.Rounded.Spellcheck, "Digitação e L.I.L.Y Correct", "Correção, estilo formal/natural/informal, deslize") { go(Page.TYPING) } }
        item { NavItem(Icons.Rounded.ViewWeek, "Barra de ferramentas", "Escolha e organize os atalhos") { go(Page.TOOLBAR) } }
        item { NavItem(Icons.Rounded.Language, "Idiomas", "Português (Brasil) e inglês") { go(Page.LANGUAGES) } }
        item { NavItem(Icons.Rounded.Mic, "Ditado por voz", "Reconhecimento no aparelho") { go(Page.VOICE) } }
        item { NavItem(Icons.Rounded.ContentPaste, "Área de transferência", "Histórico de 1 hora, itens fixados") { go(Page.CLIPBOARD) } }
        item { NavItem(Icons.Rounded.Shield, "Privacidade", "Dicionário pessoal e o que fica guardado") { go(Page.PRIVACY) } }
        item { NavItem(Icons.Rounded.Info, "Sobre", "Versão, créditos e limitações") { go(Page.ABOUT) } }
    }
}

@Composable
private fun Step(n: Int, title: String, done: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (done) Color(0xFF52E3A4) else StudioColors.secondary,
        )
        Spacer(Modifier.width(12.dp))
        Text("$n. $title", color = StudioColors.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (!done) Text("Abrir", color = StudioColors.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

// --- Temas --------------------------------------------------------------------------------------

@Composable
private fun ThemesPage(store: KeyboardStore, settings: KeyboardSettings, onBack: () -> Unit, onEdit: (String) -> Unit) {
    val context = LocalContext.current
    // Prévia antes de aplicar: tocar num tema só mostra; "Aplicar" grava.
    var previewId by remember { mutableStateOf(settings.themeId) }
    val previewTheme = settings.allThemes.firstOrNull { it.id == previewId } ?: settings.theme
    StudioScaffold("Temas", onBack) {
        item {
            Section("Prévia: ${previewTheme.name}") {
                PreviewKeyboard(settings, theme = previewTheme, showField = false)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioButton(if (previewId == settings.themeId) "Em uso" else "Aplicar", Modifier.weight(1f), primary = previewId != settings.themeId) {
                        store.update { it.copy(themeId = previewId) }
                    }
                    StudioButton("Personalizar", Modifier.weight(1f)) {
                        val base = previewTheme
                        val id = if (base.builtIn) {
                            val copy = base.copy(id = "custom_${System.currentTimeMillis()}", name = "${base.name} (meu)", style = ThemeStyle.CUSTOM, builtIn = false)
                            store.update { it.copy(customThemes = it.customThemes + copy) }
                            copy.id
                        } else {
                            base.id
                        }
                        onEdit(id)
                    }
                }
            }
        }
        item {
            Section("Escolha um tema") {
                for (row in settings.allThemes.chunked(3)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (t in row) ThemeSwatch(t, selected = t.id == previewId, inUse = t.id == settings.themeId, modifier = Modifier.weight(1f)) { previewId = t.id }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        item {
            Section("Importar") {
                Info("Copie o texto de um tema exportado e toque em importar.")
                StudioButton("Importar da área de transferência", Modifier.fillMaxWidth()) {
                    val text = context.getSystemService(ClipboardManager::class.java)?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                    val theme = store.parseTheme(text)
                    if (theme == null) {
                        Toast.makeText(context, "A área de transferência não tem um tema válido.", Toast.LENGTH_SHORT).show()
                    } else {
                        val imported = theme.copy(id = "custom_${System.currentTimeMillis()}", style = ThemeStyle.CUSTOM)
                        store.update { it.copy(customThemes = it.customThemes + imported) }
                        previewId = imported.id
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeSwatch(theme: KeyboardTheme, selected: Boolean, inUse: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(theme.background.asColor(), theme.background2.asColor())))
                .border(if (selected) 2.dp else 1.dp, if (selected) StudioColors.accent else StudioColors.cardBorder, RoundedCornerShape(16.dp))
                .padding(8.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(4) {
                            Box(Modifier.size(width = 14.dp, height = 12.dp).clip(RoundedCornerShape((theme.cornerRadius / 3).dp)).background(theme.keyColor.asColor().let { if (it.alpha < 0.08f) theme.textColor.asColor().copy(alpha = 0.15f) else it }))
                        }
                    }
                }
                Box(Modifier.size(width = 30.dp, height = 10.dp).clip(RoundedCornerShape(5.dp)).background(theme.accentColor.asColor()))
            }
        }
        Text(theme.name + if (inUse) " ✓" else "", color = StudioColors.text, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
    }
}

private val palette = listOf(
    0xFFFFFFFF, 0xFFE9E6F3, 0xFFB8B4C8, 0xFF5A5F6E, 0xFF2A2833, 0xFF15131F, 0xFF000000,
    0xFFB79CFF, 0xFF9D6BFF, 0xFF6C4BD6, 0xFF3A1269, 0xFFFFC6F0, 0xFFFF6FB1, 0xFFFF453A,
    0xFFFF9F0A, 0xFFFFD60A, 0xFF52FFB0, 0xFF30D158, 0xFF0A84FF, 0xFF5BB8FF, 0xFF07122B,
)

@Composable
private fun ThemeEditor(store: KeyboardStore, settings: KeyboardSettings, id: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    val theme = settings.customThemes.firstOrNull { it.id == id }
    if (theme == null) {
        onBack()
        return
    }
    fun edit(change: (KeyboardTheme) -> KeyboardTheme) {
        store.update { s -> s.copy(customThemes = s.customThemes.map { if (it.id == theme.id) change(it) else it }) }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val name = "teclado_fundo_${theme.id}.jpg"
            if (saveBackground(context, uri, File(context.filesDir, name))) edit { it.copy(backgroundImage = name, imageDim = 0.25f, imageBlur = 0.4f) }
        }
    }
    StudioScaffold("Editar tema", onBack) {
        item {
            Section {
                PreviewKeyboard(settings, theme = theme, showField = false)
            }
        }
        item {
            Section("Nome") {
                OutlinedTextField(
                    value = theme.name,
                    onValueChange = { v -> edit { it.copy(name = v.take(30)) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = StudioColors.text, unfocusedTextColor = StudioColors.text,
                        focusedBorderColor = StudioColors.accent, unfocusedBorderColor = StudioColors.cardBorder,
                    ),
                )
                SwitchItem("Tema escuro", theme.dark, "Define a cor dos balões de prévia") { v -> edit { it.copy(dark = v) } }
            }
        }
        item {
            Section("Cores") {
                ColorRow("Teclas", theme.keyColor) { c -> edit { it.copy(keyColor = withAlpha(c, it.keyColor)) } }
                ColorRow("Teclas de função", theme.functionKeyColor) { c -> edit { it.copy(functionKeyColor = withAlpha(c, it.functionKeyColor)) } }
                ColorRow("Destaque (enter, shift)", theme.accentColor) { c -> edit { it.copy(accentColor = c) } }
                ColorRow("Letras", theme.textColor) { c -> edit { it.copy(textColor = c) } }
                ColorRow("Símbolos e dicas", theme.symbolColor) { c -> edit { it.copy(symbolColor = (c and 0xFFFFFF) or 0xB3000000) } }
                ColorRow("Fundo (topo)", theme.background) { c -> edit { it.copy(background = c) } }
                ColorRow("Fundo (base)", theme.background2) { c -> edit { it.copy(background2 = c) } }
            }
        }
        item {
            Section("Vidro e forma") {
                SliderItem("Opacidade das teclas", alphaOf(theme.keyColor), 0f..1f, { "${(it * 100).roundToInt()}%" }) { v ->
                    edit { it.copy(keyColor = setAlpha(it.keyColor, v), functionKeyColor = setAlpha(it.functionKeyColor, v * 0.7f)) }
                }
                SliderItem("Efeito vidro", theme.glass, 0f..1f, { "${(it * 100).roundToInt()}%" }) { v -> edit { it.copy(glass = v) } }
                SliderItem("Transparência do fundo", 1f - theme.backgroundAlpha, 0f..0.7f, { "${(it * 100).roundToInt()}%" }) { v -> edit { it.copy(backgroundAlpha = 1f - v) } }
                SliderItem("Arredondamento", theme.cornerRadius, 0f..24f, { "${it.roundToInt()} dp" }) { v -> edit { it.copy(cornerRadius = v) } }
                SliderItem("Espaço entre teclas", theme.keyGap, 2f..10f, { "${it.roundToInt()} dp" }) { v -> edit { it.copy(keyGap = v) } }
                SwitchItem("Borda nas teclas", theme.borders) { v -> edit { it.copy(borders = v) } }
            }
        }
        item {
            Section("Fundo da galeria") {
                StudioButton(if (theme.backgroundImage == null) "Escolher imagem" else "Trocar imagem", Modifier.fillMaxWidth(), primary = true) {
                    runCatching { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                }
                if (theme.backgroundImage != null) {
                    SliderItem("Escurecer", theme.imageDim, 0f..0.85f, { "${(it * 100).roundToInt()}%" }) { v -> edit { it.copy(imageDim = v) } }
                    SliderItem("Desfocar", theme.imageBlur, 0f..1f, { "${(it * 100).roundToInt()}%" }) { v -> edit { it.copy(imageBlur = v) } }
                    StudioButton("Remover imagem", Modifier.fillMaxWidth()) { edit { it.copy(backgroundImage = null) } }
                }
            }
        }
        item {
            Section("Tema") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioButton("Aplicar", Modifier.weight(1f), primary = true) { store.update { it.copy(themeId = theme.id) } }
                    StudioButton("Duplicar", Modifier.weight(1f)) {
                        val copy = theme.copy(id = "custom_${System.currentTimeMillis()}", name = "${theme.name} (cópia)")
                        store.update { it.copy(customThemes = it.customThemes + copy) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioButton("Exportar", Modifier.weight(1f)) {
                        val text = store.exportTheme(theme)
                        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Tema L.I.L.Y", text))
                        runCatching {
                            context.startActivity(
                                Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Exportar tema"),
                            )
                        }
                    }
                    StudioButton("Excluir", Modifier.weight(1f)) {
                        store.update { s ->
                            s.copy(
                                customThemes = s.customThemes.filterNot { it.id == theme.id },
                                themeId = if (s.themeId == theme.id) KeyboardSettings().themeId else s.themeId,
                            )
                        }
                        theme.backgroundImage?.let { File(context.filesDir, it).delete() }
                        onBack()
                    }
                }
                Info("A imagem de fundo não vai junto na exportação.")
            }
        }
    }
}

private fun alphaOf(c: Long): Float = ((c ushr 24) and 0xFF).toFloat() / 255f
private fun setAlpha(c: Long, a: Float): Long = ((a.coerceIn(0f, 1f) * 255).roundToInt().toLong() shl 24) or (c and 0xFFFFFF)
private fun withAlpha(color: Long, keepFrom: Long): Long = (keepFrom and 0xFF000000) or (color and 0xFFFFFF)

@Composable
private fun ColorRow(title: String, current: Long, onPick: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = StudioColors.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (c in palette) {
                val on = (c and 0xFFFFFF) == (current and 0xFFFFFF)
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(c.toInt()))
                        .border(if (on) 3.dp else 1.dp, if (on) StudioColors.accent else Color(0x55FFFFFF), CircleShape)
                        .clickable { onPick(c) },
                )
            }
        }
    }
}

private fun saveBackground(context: Context, uri: Uri, target: File): Boolean = runCatching {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val longest = max(info.size.width, info.size.height)
        if (longest > 1600) decoder.setTargetSampleSize(ceil(longest / 1600f).toInt())
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
    target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    true
}.getOrDefault(false)

// --- Layout ----------------------------------------------------------------------------------------

@Composable
private fun LayoutPage(store: KeyboardStore, settings: KeyboardSettings, onBack: () -> Unit) {
    StudioScaffold("Tamanho e ergonomia", onBack) {
        item { Section { PreviewKeyboard(settings, showField = false) } }
        item {
            Section("Tamanho") {
                ChoiceItem(null, SizeMode.entries.map { it to it.label }, settings.sizeMode) { v -> store.update { it.copy(sizeMode = v) } }
                SliderItem("Altura das teclas", settings.heightScale, 0.8f..1.3f, { "${(it * 100).roundToInt()}%" }) { v -> store.update { it.copy(heightScale = v) } }
                SliderItem("Margem nas laterais", settings.sideMargin, 0f..40f, { "${it.roundToInt()} dp" }) { v -> store.update { it.copy(sideMargin = v) } }
                SliderItem("Subir o teclado", settings.bottomOffset, 0f..48f, { "${it.roundToInt()} dp" }) { v -> store.update { it.copy(bottomOffset = v) } }
                SliderItem("Tamanho das letras", settings.fontScale, 0.8f..1.3f, { "${(it * 100).roundToInt()}%" }) { v -> store.update { it.copy(fontScale = v) } }
                Info("Na tela externa do Razr o teclado fica compacto sozinho (teclas mais baixas, sem fileira de números).")
            }
        }
        item {
            Section("Uma mão") {
                SwitchItem("Modo de uma mão", settings.oneHanded, "Encolhe o teclado para o lado do polegar") { v -> store.update { it.copy(oneHanded = v) } }
                if (settings.oneHanded) {
                    ChoiceItem("Lado", listOf(OneHandSide.LEFT to "Esquerda", OneHandSide.RIGHT to "Direita"), settings.oneHandSide) { v -> store.update { it.copy(oneHandSide = v) } }
                    SliderItem("Largura", settings.oneHandWidth, 0.6f..0.95f, { "${(it * 100).roundToInt()}%" }) { v -> store.update { it.copy(oneHandWidth = v) } }
                }
            }
        }
        item {
            Section("Teclas") {
                SwitchItem("Fileira de números", settings.numberRow) { v -> store.update { it.copy(numberRow = v) } }
                SwitchItem("Tecla Ç", settings.cedillaKey, "Sem ela, o ç fica no toque longo do c") { v -> store.update { it.copy(cedillaKey = v) } }
                SliderItem("Vírgula e ponto (largura)", settings.punctuationWidth, 0.8f..1.4f, { "${(it * 100).roundToInt()}%" }) { v -> store.update { it.copy(punctuationWidth = v) } }
                Info("Vírgula e ponto mais largos deixam o espaço um pouco menor.")
                SwitchItem("Última fileira mais alta", settings.bigBottomRow, "Espaço, apagar e enter maiores para o polegar") { v -> store.update { it.copy(bigBottomRow = v) } }
                SwitchItem("Prévia ao tocar", settings.keyPreview, "Mostra a letra ampliada acima do dedo") { v -> store.update { it.copy(keyPreview = v) } }
                SliderItem("Toque longo", settings.longPressMs.toFloat(), 150f..800f, { "${it.roundToInt()} ms" }) { v -> store.update { it.copy(longPressMs = v.roundToInt()) } }
            }
        }
    }
}

// --- Digitação ------------------------------------------------------------------------------------

@Composable
private fun TypingPage(store: KeyboardStore, settings: KeyboardSettings, onBack: () -> Unit) {
    StudioScaffold("Digitação", onBack) {
        item {
            Section("L.I.L.Y Correct") {
                SwitchItem("Correção automática", settings.autoCorrect, "Troca só quando a correção é clara; apagar logo em seguida desfaz") { v -> store.update { it.copy(autoCorrect = v) } }
                ChoiceItem("Estilo", CorrectionStyle.entries.map { it to it.label }, settings.correctionStyle) { v -> store.update { it.copy(correctionStyle = v) } }
                Info(settings.correctionStyle.description)
                SwitchItem("Sugestões de palavras", settings.suggestions) { v -> store.update { it.copy(suggestions = v) } }
                SwitchItem("Sugerir emojis", settings.emojiSuggestions, "Ex.: \"pizza\" mostra 🍕 na barra (nunca insere sozinho)") { v -> store.update { it.copy(emojiSuggestions = v) } }
            }
        }
        item {
            Section("Teste o corretor") {
                PreviewKeyboard(settings)
                Info("Experimente: \"nao\", \"voce\", \"obrigadp\", \"ele esta\", \"vc\" (muda conforme o estilo).")
            }
        }
        item {
            Section("Gestos") {
                SwitchItem("Digitação por deslize", settings.gestureTyping, "Passe o dedo pelas letras sem soltar") { v -> store.update { it.copy(gestureTyping = v) } }
                SwitchItem("Mostrar rastro", settings.gestureTrail, enabled = settings.gestureTyping) { v -> store.update { it.copy(gestureTrail = v) } }
                SwitchItem("Mover o cursor pelo espaço", settings.spaceCursor, "Arraste a barra de espaço para os lados") { v -> store.update { it.copy(spaceCursor = v) } }
                SwitchItem("Apagar palavras deslizando", settings.deleteSwipe, "Arraste a tecla apagar para a esquerda") { v -> store.update { it.copy(deleteSwipe = v) } }
            }
        }
        item {
            Section("Pontuação e maiúsculas") {
                SwitchItem("Dois espaços viram ponto", settings.doubleSpacePeriod) { v -> store.update { it.copy(doubleSpacePeriod = v) } }
                SwitchItem("Maiúscula automática", settings.autoCapitalize, "No começo de frases, quando o campo pede") { v -> store.update { it.copy(autoCapitalize = v) } }
            }
        }
        item {
            Section("Som e vibração") {
                SwitchItem("Vibrar ao tocar", settings.vibration) { v -> store.update { it.copy(vibration = v) } }
                if (settings.vibration) SliderItem("Força da vibração", settings.vibrationStrength, 0f..1f, { "${(it * 100).roundToInt()}%" }) { v -> store.update { it.copy(vibrationStrength = v) } }
                SwitchItem("Som ao tocar", settings.sound) { v -> store.update { it.copy(sound = v) } }
                if (settings.sound) SliderItem("Volume", settings.soundVolume, 0.05f..1f, { "${(it * 100).roundToInt()}%" }) { v -> store.update { it.copy(soundVolume = v) } }
                SliderItem("Animações", settings.animations, 0f..1f, { if (it < 0.05f) "desligadas" else "${(it * 100).roundToInt()}%" }) { v -> store.update { it.copy(animations = v) } }
            }
        }
    }
}

// --- Barra ------------------------------------------------------------------------------------------

@Composable
private fun ToolbarPage(store: KeyboardStore, settings: KeyboardSettings, onBack: () -> Unit) {
    StudioScaffold("Barra de ferramentas", onBack) {
        item { Info("A barra aparece acima das teclas quando você não está digitando uma palavra. Use as setas para mudar a ordem.") }
        items(settings.toolbar, key = { it.name }) { item ->
            ToolbarRow(item.label, shown = true,
                onUp = { store.update { it.copy(toolbar = it.toolbar.move(item, -1)) } },
                onDown = { store.update { it.copy(toolbar = it.toolbar.move(item, 1)) } },
                onToggle = { store.update { it.copy(toolbar = it.toolbar - item) } },
            )
        }
        val hidden = ToolbarItem.entries.filter { it !in settings.toolbar }
        if (hidden.isNotEmpty()) {
            item { Text("Escondidos", color = StudioColors.secondary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
            items(hidden, key = { "h-" + it.name }) { item ->
                ToolbarRow(item.label, shown = false, onUp = null, onDown = null, onToggle = { store.update { it.copy(toolbar = it.toolbar + item) } })
            }
        }
    }
}

private fun <T> List<T>.move(item: T, delta: Int): List<T> {
    val i = indexOf(item)
    val j = (i + delta).coerceIn(0, size - 1)
    if (i < 0 || i == j) return this
    return toMutableList().apply { add(j, removeAt(i)) }
}

@Composable
private fun ToolbarRow(label: String, shown: Boolean, onUp: (() -> Unit)?, onDown: (() -> Unit)?, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(StudioColors.card).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = StudioColors.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (onUp != null) SmallIcon(Icons.Rounded.ArrowUpward, "Subir", onUp)
        if (onDown != null) SmallIcon(Icons.Rounded.ArrowDownward, "Descer", onDown)
        Spacer(Modifier.width(6.dp))
        StudioButton(if (shown) "Esconder" else "Mostrar", primary = !shown, onClick = onToggle)
    }
}

@Composable
private fun SmallIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = label, tint = StudioColors.text)
    }
}

// --- Idiomas, voz, área de transferência, privacidade, sobre -----------------------------------------

@Composable
private fun LanguagesPage(store: KeyboardStore, settings: KeyboardSettings, onBack: () -> Unit) {
    StudioScaffold("Idiomas", onBack) {
        item {
            Section("Ativos") {
                for (lang in KeyboardLanguage.entries) {
                    val on = lang in settings.languages
                    SwitchItem(lang.label, on, if (lang == settings.language) "Em uso agora" else null, enabled = !(on && settings.languages.size == 1)) { v ->
                        store.update { s ->
                            val list = if (v) (s.languages + lang).distinct() else s.languages - lang
                            s.copy(languages = list, language = if (s.language in list) s.language else list.first())
                        }
                    }
                }
                Info("Troque de idioma pelo atalho da barra ou segurando a barra de espaço. O dicionário de cada idioma vem dentro do app (funciona offline).")
            }
        }
        item {
            Section("Idioma principal") {
                ChoiceItem(null, settings.languages.map { it to it.label.substringBefore(" (") }, settings.language) { v -> store.update { it.copy(language = v) } }
            }
        }
    }
}

@Composable
private fun VoicePage(store: KeyboardStore, settings: KeyboardSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    val voice = remember { VoiceInput(context) }
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    val permission = remember(tick) { voice.hasPermission() }
    val offline = remember(tick) { voice.offlineAvailable() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    StudioScaffold("Ditado por voz", onBack) {
        item {
            Section("Situação") {
                StatusLine("Microfone permitido", permission)
                StatusLine("Reconhecimento no aparelho (offline)", offline)
                if (!permission) StudioButton("Permitir microfone", Modifier.fillMaxWidth(), primary = true) { launcher.launch(Manifest.permission.RECORD_AUDIO) }
                if (!offline) {
                    Info("Para ditar sem internet, baixe o idioma em Configurações do Android > Sistema > Idiomas > Reconhecimento de voz no dispositivo.")
                    StudioButton("Abrir configurações de voz", Modifier.fillMaxWidth()) {
                        runCatching { context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)) }
                    }
                }
            }
        }
        item {
            Section("Privacidade") {
                SwitchItem(
                    "Só reconhecimento no aparelho",
                    settings.voiceOfflineOnly,
                    "Desligado, o ditado pode usar o serviço de voz do sistema, que às vezes manda o áudio para a internet",
                ) { v -> store.update { it.copy(voiceOfflineOnly = v) } }
                Info("O microfone só liga quando você toca no botão de voz, e desliga ao terminar ou cancelar. Você confere o texto antes de inserir.")
            }
        }
    }
}

@Composable
private fun StatusLine(text: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, contentDescription = null, tint = if (ok) Color(0xFF52E3A4) else StudioColors.secondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = StudioColors.text, fontSize = 14.sp)
    }
}

@Composable
private fun ClipboardPage(store: KeyboardStore, settings: KeyboardSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    StudioScaffold("Área de transferência", onBack) {
        item {
            Section {
                SwitchItem("Guardar histórico", settings.clipboardHistory, "O que você copia fica disponível no teclado por 1 hora") { v -> store.update { it.copy(clipboardHistory = v) } }
                Info("Não são guardados: itens marcados como sensíveis pelo app de origem, códigos de verificação (só números) e textos com cara de senha ou chave. Itens fixados ficam até você apagar.")
                StudioButton("Apagar tudo, inclusive fixados", Modifier.fillMaxWidth()) {
                    File(context.noBackupFilesDir, "area_de_transferencia.json").delete()
                    Toast.makeText(context, "Histórico apagado.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
private fun PrivacyPage(store: KeyboardStore, settings: KeyboardSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    val models = remember { LanguageModels.get(context) }
    var count by remember { mutableIntStateOf(models.user.all().size) }
    StudioScaffold("Privacidade", onBack) {
        item {
            Section("O que o teclado faz") {
                Info("• O app não tem permissão de internet: nada do que você digita sai do aparelho.")
                Info("• Campos de senha: sem sugestões, sem aprendizado, sem histórico.")
                Info("• Apps que pedem modo anônimo (ex.: aba anônima) não ensinam palavras ao teclado.")
                Info("• Nada vai para backup na nuvem.")
            }
        }
        item {
            Section("Dicionário pessoal") {
                SwitchItem("Aprender palavras novas", settings.personalLearning, "Nomes, gírias e termos que você usa") { v -> store.update { it.copy(personalLearning = v) } }
                Info("$count palavras aprendidas.")
                StudioButton("Apagar dicionário pessoal", Modifier.fillMaxWidth()) {
                    models.user.clear()
                    count = 0
                }
            }
        }
    }
}

@Composable
private fun AboutPage(onBack: () -> Unit) {
    StudioScaffold("Sobre", onBack) {
        item {
            Section {
                Text("L.I.L.Y Keyboard ${BuildConfig.VERSION_NAME}", color = StudioColors.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Info("Parte do Project Liquid Flip. Funciona sozinho, com ou sem o launcher Cristal.")
            }
        }
        item {
            Section("Ainda não tem") {
                Info("• Ferramentas de escrita com IA (reescrever, resumir, traduzir, mudar o tom) e o botão da L.I.L.Y: vêm com a L.I.L.Y AI, depois dos testes de desempenho no aparelho.")
                Info("• Concordância e gramática: precisam de um modelo de linguagem (também na L.I.L.Y AI). O L.I.L.Y Correct de hoje corrige digitação, acentos e casos de contexto simples.")
                Info("• GIFs: exigem um serviço online e o teclado não usa internet.")
            }
        }
        item {
            Section("Créditos") {
                Info("Frequência de palavras: wordfreq (Robyn Speer), dados sob licença CC BY-SA 4.0.")
                Info("Nomes de emojis em português: Emojibase (licença MIT).")
            }
        }
    }
}
