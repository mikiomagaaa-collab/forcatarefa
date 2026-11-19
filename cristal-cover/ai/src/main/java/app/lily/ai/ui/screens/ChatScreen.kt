package app.lily.ai.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lily.ai.bridge.CristalBridge
import app.lily.ai.core.EngineChoice
import app.lily.ai.data.ChatMessage
import app.lily.ai.data.Role
import app.lily.ai.engine.EngineStatus
import app.lily.ai.models.Catalog
import app.lily.ai.models.Compatibility
import app.lily.ai.models.Modality
import app.lily.ai.models.Runtime
import app.lily.ai.models.formatBytes
import app.lily.ai.ui.LocalGraph
import app.lily.ai.ui.LocalPermissions
import app.lily.ai.ui.MainActivity
import app.lily.ai.ui.Nav
import app.lily.ai.ui.Route
import app.lily.ai.ui.theme.ButtonKind
import app.lily.ai.ui.theme.ConfirmDialog
import app.lily.ai.ui.theme.IconButton
import app.lily.ai.ui.theme.Lily
import app.lily.ai.ui.theme.LilyOrb
import app.lily.ai.ui.theme.PillButton
import app.lily.ai.ui.theme.StatusPill
import app.lily.ai.ui.theme.TopBar
import app.lily.ai.ui.theme.Type
import app.lily.ai.ui.theme.glass
import app.lily.shared.brief.BriefDetector
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(nav: Nav, key: Int, route: Route.Chat) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val session = remember(key) { nav.retain(key, { ChatSession(graph, context.applicationContext, route) }, ChatSession::dispose) }
    LaunchedEffect(session.conversationId) {
        val id = session.conversationId ?: return@LaunchedEffect
        if (route.id != id) nav.update(key, Route.Chat(id = id))
    }
    ChatContent(nav, session)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatContent(nav: Nav, session: ChatSession, compact: Boolean = false) {
    val graph = LocalGraph.current
    val c = Lily.colors
    val context = LocalContext.current
    val permissions = LocalPermissions.current
    val scope = rememberCoroutineScope()
    val metas by graph.conversations.conversations.collectAsStateWithLifecycle()
    val projects by graph.conversations.projects.collectAsStateWithLifecycle()
    val engineStatus by graph.engines.status.collectAsStateWithLifecycle()
    val settings by graph.settings.value.collectAsStateWithLifecycle()
    val store by graph.store.state.collectAsStateWithLifecycle()
    val meta = session.conversationId?.let { id -> metas.firstOrNull { it.id == id } }
    val cristal = remember { CristalBridge.available(context) }
    val displayId = LocalView.current.display?.displayId ?: 0

    var showModels by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showAttach by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<ChatMessage?>(null) }
    var rememberText by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    val voice = rememberVoiceInput()

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { session.attachImage(it) } }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { session.attachAudio(it) } }
    val pickDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { session.attachDocument(it) } }

    var followUp by remember { mutableStateOf(false) }
    // Conversa contínua: depois de responder uma pergunta falada (e ler a resposta), ouve de novo.
    FollowUpEffect(session) {
        if (!listening && !session.busy && session.draft.isBlank() && permissions.has(MainActivity.MICROPHONE)) {
            followUp = true
            listening = true
        }
    }
    ListenEffect(
        voice,
        listening,
        onFinal = { text -> if (text.isNotBlank()) session.send(text, viaVoice = true) },
        onError = { if (!(followUp && it == app.lily.ai.engine.SpeechInput.NOTHING_HEARD)) session.notice = it },
        onEnd = {
            listening = false
            followUp = false
        },
    )
    LaunchedEffect(voice.partial) { if (listening && voice.partial.isNotEmpty()) session.draft = voice.partial }
    LaunchedEffect(session.notice) {
        if (session.notice != null) {
            delay(3_500)
            session.notice = null
        }
    }

    fun propose(text: String) {
        if (!CristalBridge.proposeReminder(context, text, displayId)) session.notice = "O Cristal Cover não respondeu. Abra o Cristal e tente de novo."
        else session.notice = "Confirme o lembrete na tela do Cristal."
    }

    fun withMic(then: () -> Unit) {
        permissions.request(MainActivity.MICROPHONE) { granted ->
            if (granted) then() else session.notice = "Sem a permissão do microfone, a L.I.L.Y não consegue ouvir."
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        if (!compact) {
            TopBar(
                title = meta?.title ?: "Nova conversa",
                onBack = { nav.back() },
                actions = {
                    Box {
                        IconButton(Icons.Rounded.MoreVert, "Mais opções", { showMenu = true })
                        ChatMenu(
                            expanded = showMenu,
                            hasConversation = meta != null,
                            pinned = meta?.pinned == true,
                            onDismiss = { showMenu = false },
                            onNew = { nav.reset(Route.Chat()) },
                            onRename = { renaming = true },
                            onPin = { meta?.let { m -> scope.launch { graph.conversations.pin(m.id, !m.pinned) } } },
                            onMove = { moving = true },
                            onCopy = {
                                copyToClipboard(context, session.messages.joinToString("\n\n") { (if (it.role == Role.USER) "Você: " else "L.I.L.Y: ") + it.text })
                                session.notice = "Conversa copiada."
                            },
                            onDelete = { confirmDelete = true },
                        )
                    }
                },
            )
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ModelPill(session, engineStatus, settings.profile.name + store.installed.size) { showModels = true }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                session.loading -> Unit
                session.messages.isEmpty() && session.streaming == null -> EmptyChat(compact) { session.draft = it }
                else -> MessageList(
                    session = session,
                    engineStatus = engineStatus,
                    cristal = cristal,
                    onBrief = { propose(it.text) },
                    onActions = { actionsFor = it },
                    onHub = { category -> nav.go(Route.Hub(category)) },
                )
            }
            androidx.compose.animation.AnimatedVisibility(session.notice != null, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), enter = fadeIn(), exit = fadeOut()) {
                Text(
                    session.notice.orEmpty(),
                    style = Type.footnote,
                    color = c.content,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp).glass(c, CircleShape, strong = true).padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
        session.recordingLevel?.let { level -> RecordingBar(level, session::stopRecording) }
        Box(Modifier.padding(horizontal = 12.dp).navigationBarsPadding().padding(bottom = 8.dp, top = 4.dp)) {
            Composer(
                text = session.draft,
                onText = { session.draft = it },
                onSend = {
                    // Atalhos para os módulos: "/codex tarefa" e "/video descrição".
                    val d = session.draft.trim()
                    when {
                        d.startsWith("/codex", ignoreCase = true) -> {
                            session.draft = ""
                            nav.go(Route.Codex(task = d.removeRange(0, 6).trim()))
                        }
                        d.startsWith("/video", ignoreCase = true) || d.startsWith("/vídeo", ignoreCase = true) -> {
                            session.draft = ""
                            nav.go(Route.Video(prompt = d.substringAfter(' ', "").trim()))
                        }
                        else -> session.send()
                    }
                },
                placeholder = if (session.preparing) "Preparando o anexo…" else "Pergunte qualquer coisa",
                image = session.image?.thumb,
                audioLabel = session.audio?.let { "${it.label} · ${it.seconds}s" },
                fileLabel = session.document?.let { d -> d.name.take(28) + " · " + if (d.chars >= 1000) "${d.chars / 1000} mil caracteres" else "${d.chars} caracteres" },
                onRemoveAttachment = session::removeAttachments,
                onAttach = { showAttach = true },
                onMic = {
                    if (listening) listening = false else withMic { listening = true }
                },
                listening = if (listening) voice.level ?: 0f else null,
                busy = session.busy,
                onStop = session::stop,
            )
            DropdownMenu(showAttach, { showAttach = false }, containerColor = sheetColor()) {
                MenuItem(Icons.Rounded.Image, "Foto da galeria") {
                    showAttach = false
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                MenuItem(Icons.Rounded.AudioFile, "Arquivo de áudio") {
                    showAttach = false
                    pickAudio.launch("audio/*")
                }
                MenuItem(Icons.Rounded.Description, "Documento (PDF ou texto)") {
                    showAttach = false
                    pickDocument.launch(arrayOf("application/pdf", "text/*"))
                }
                MenuItem(Icons.Rounded.Mic, "Gravar áudio (até 30 s)") {
                    showAttach = false
                    withMic { session.startRecording() }
                }
            }
        }
    }

    if (showModels) ModelSheet(session, onDismiss = { showModels = false }, onHub = { showModels = false; nav.go(Route.Hub()) })
    actionsFor?.let { msg ->
        MessageActionsSheet(
            msg = msg,
            cristal = cristal,
            speaking = session.speakingId == msg.id,
            onDismiss = { actionsFor = null },
            onEdit = { session.draft = msg.text },
            onRemember = { rememberText = msg.text },
            onReminder = { propose(msg.text) },
            onSpeak = { session.speak(msg) },
        )
    }
    rememberText?.let { text ->
        TextInputDialog(
            title = "Guardar na memória",
            initial = text.take(400),
            confirm = "Guardar",
            supporting = "A L.I.L.Y vai usar isto nas próximas conversas. Você pode ver, editar ou apagar em Memória.",
            singleLine = false,
            onConfirm = { edited ->
                rememberText = null
                session.notice = if (session.remember(edited)) "Guardado na memória." else "Parece informação sensível (senha, documento ou cartão): não foi guardada."
            },
            onDismiss = { rememberText = null },
        )
    }
    if (renaming && meta != null) {
        TextInputDialog("Renomear conversa", meta.title, "Salvar", { name -> renaming = false; scope.launch { graph.conversations.rename(meta.id, name) } }, { renaming = false })
    }
    if (moving && meta != null) {
        ProjectPicker(projects, meta.projectId, onPick = { p -> moving = false; scope.launch { graph.conversations.move(meta.id, p) } }, onDismiss = { moving = false })
    }
    if (confirmDelete && meta != null) {
        ConfirmDialog(
            "Apagar conversa?",
            "As mensagens e os anexos desta conversa saem do aparelho. Isso não pode ser desfeito.",
            "Apagar",
            onConfirm = {
                confirmDelete = false
                session.stop()
                scope.launch {
                    graph.conversations.delete(meta.id)
                    nav.back()
                }
            },
            onDismiss = { confirmDelete = false },
            destructive = true,
        )
    }
}

@Composable
private fun MessageList(
    session: ChatSession,
    engineStatus: EngineStatus,
    cristal: Boolean,
    onBrief: (ChatMessage) -> Unit,
    onActions: (ChatMessage) -> Unit,
    onHub: (String) -> Unit,
) {
    val list = session.messages
    val state = rememberLazyListState()
    LaunchedEffect(list.size) { if (list.isNotEmpty()) state.animateScrollToItem(0) }
    LazyColumn(
        state = state,
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Bottom),
        modifier = Modifier.fillMaxSize(),
    ) {
        session.streaming?.let { s -> item(key = s.id) { StreamingMessage(s, engineStatus) } }
        items(list.size, key = { list[list.lastIndex - it].id }) { i ->
            val msg = list[list.lastIndex - i]
            if (msg.role == Role.USER) {
                val brief = remember(msg.id, cristal) { if (cristal) briefFor(msg) else null }
                UserMessage(msg, brief, onLongPress = { onActions(msg) }, onBrief = { onBrief(msg) })
            } else {
                AssistantMessage(
                    msg = msg,
                    last = i == 0 && session.streaming == null,
                    speaking = session.speakingId == msg.id,
                    onSpeak = { session.speak(msg) },
                    onRegenerate = session::regenerate,
                    onMore = { onActions(msg) },
                    onHub = onHub,
                )
            }
        }
    }
}

/** O compromisso numa mensagem da pessoa, contado a partir do momento em que ela escreveu (só se ainda não passou). */
private fun briefFor(msg: ChatMessage) = msg.text.takeIf { it.isNotBlank() }?.let { text ->
    val written = LocalDateTime.ofInstant(Instant.ofEpochMilli(msg.at), ZoneId.systemDefault())
    BriefDetector.detect(text, written)?.takeIf { c ->
        val at = c.at
        at == null || at.isAfter(LocalDateTime.now())
    }
}

@Composable
private fun EmptyChat(compact: Boolean, onSuggestion: (String) -> Unit) {
    val c = Lily.colors
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        app.lily.ai.avatar.PresenceAvatar(if (compact) 96.dp else 150.dp)
        Text("Como posso ajudar?", style = Type.title, color = c.content)
        Spacer(Modifier.height(6.dp))
        Text("Tudo roda no aparelho, sem enviar nada para a internet.", style = Type.footnote, color = c.secondary, textAlign = TextAlign.Center)
        if (!compact) {
            Spacer(Modifier.height(20.dp))
            listOf(
                "Escreva uma mensagem de aniversário para minha mãe",
                "Explique o que é inflação de um jeito simples",
                "Monte um plano de estudos para a semana",
                "Me ajude a responder um e-mail com educação",
            ).forEach { s ->
                Text(
                    s,
                    style = Type.subhead,
                    color = c.content,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).glass(c, RoundedCornerShape(18.dp)).clickable { onSuggestion(s) }.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/** Qual modelo responde e onde (sempre no aparelho nesta versão). */
@Composable
private fun ModelPill(session: ChatSession, status: EngineStatus, refreshKey: String, onClick: () -> Unit) {
    val c = Lily.colors
    val plan = remember(session.manualModel, session.image, session.audio, refreshKey, session.lastPlan) { runCatching { session.previewPlan() }.getOrNull() }
    val (text, dot) = when {
        status is EngineStatus.Loading -> "${status.model.name} · carregando" to c.warning
        status is EngineStatus.Busy -> "${status.model.name} · ${status.accel.label} · No aparelho" to c.accent
        plan?.engine is EngineChoice.Llm -> {
            val e = plan.engine as EngineChoice.Llm
            ("${e.model.name} · No aparelho" + (if (session.manualModel != null) " · fixo" else "")) to c.success
        }
        else -> "Sem modelo de conversa · Model Hub" to c.danger
    }
    StatusPill(text, dot, Modifier.padding(bottom = 4.dp), onClick = onClick)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSheet(session: ChatSession, onDismiss: () -> Unit, onHub: () -> Unit) {
    val graph = LocalGraph.current
    val c = Lily.colors
    val device = rememberDevice()
    val installed = remember { graph.store.installedIds() }
    val models = Catalog.all.filter { it.runtime == Runtime.LITERT_LM && it.id in installed }
    val plan = remember(session.manualModel) { session.previewPlan() }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = sheetColor()) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Modelo desta conversa", style = Type.title, color = c.content)
            Spacer(Modifier.height(4.dp))
            Text("Tudo roda no aparelho. O automático escolhe pelo perfil, pela memória livre, pela bateria e pela temperatura.", style = Type.footnote, color = c.secondary)
            Spacer(Modifier.height(14.dp))
            SheetOption("Automático", "O roteador decide a cada mensagem", session.manualModel == null, c.accent) { session.chooseModel(null) }
            models.forEach { m ->
                val report = Compatibility.evaluate(m, device)
                val what = buildList {
                    add(formatBytes(m.totalBytes))
                    if (m.has(Modality.IMAGE)) add("imagens")
                    if (m.has(Modality.AUDIO)) add("áudio")
                    add(report.level.label)
                }.joinToString(" · ")
                SheetOption(m.name, what, session.manualModel == m.id, compatColor(report.level, c)) { session.chooseModel(m.id) }
            }
            if (models.isEmpty()) {
                Text("Nenhum modelo de conversa instalado ainda.", style = Type.callout, color = c.secondary, modifier = Modifier.padding(vertical = 8.dp))
            }
            Text("Por que este modelo", style = Type.headline, color = c.content, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
            plan.reasons.forEach { r -> Text("• $r", style = Type.footnote, color = c.secondary, modifier = Modifier.padding(vertical = 2.dp)) }
            Spacer(Modifier.height(16.dp))
            PillButton("Abrir o Model Hub", onHub, kind = ButtonKind.GLASS)
        }
    }
}

@Composable
fun SheetOption(title: String, subtitle: String?, selected: Boolean, dot: Color, onClick: () -> Unit) {
    val c = Lily.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) c.accent.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = c.content)
            if (subtitle != null) Text(subtitle, style = Type.caption, color = c.secondary)
        }
        if (selected) Icon(Icons.Rounded.Check, contentDescription = "Selecionado", tint = c.accent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageActionsSheet(
    msg: ChatMessage,
    cristal: Boolean,
    speaking: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onRemember: () -> Unit,
    onReminder: () -> Unit,
    onSpeak: () -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = sheetColor()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 24.dp)) {
            fun act(block: () -> Unit) {
                block()
                onDismiss()
            }
            SheetAction("Copiar") { act { copyToClipboard(context, msg.text) } }
            if (msg.role == Role.USER) SheetAction("Editar e reenviar") { act(onEdit) }
            SheetAction(if (speaking) "Parar a leitura" else "Ler em voz alta") { act(onSpeak) }
            SheetAction("Compartilhar") { act { shareText(context, msg.text) } }
            SheetAction("Guardar na memória") { act(onRemember) }
            if (cristal) SheetAction("Criar lembrete no Cristal") { act(onReminder) }
        }
    }
}

@Composable
fun SheetAction(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = Type.body,
        color = Lily.colors.content,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 14.dp),
    )
}

@Composable
private fun ChatMenu(
    expanded: Boolean,
    hasConversation: Boolean,
    pinned: Boolean,
    onDismiss: () -> Unit,
    onNew: () -> Unit,
    onRename: () -> Unit,
    onPin: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(expanded, onDismiss, containerColor = sheetColor()) {
        val items = ArrayList<Pair<String, () -> Unit>>()
        items += "Nova conversa" to onNew
        if (hasConversation) {
            items += "Renomear" to onRename
            items += (if (pinned) "Desafixar" else "Fixar") to onPin
            items += "Mover para projeto" to onMove
            items += "Copiar conversa" to onCopy
            items += "Apagar conversa" to onDelete
        }
        items.forEach { (text, action) ->
            DropdownMenuItem(
                text = { Text(text, style = Type.callout, color = if (text.startsWith("Apagar")) Lily.colors.danger else Lily.colors.content) },
                onClick = {
                    onDismiss()
                    action()
                },
            )
        }
    }
}

@Composable
fun MenuItem(icon: ImageVector, text: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, style = Type.callout, color = Lily.colors.content) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = Lily.colors.accent) },
        onClick = onClick,
    )
}

@Composable
private fun RecordingBar(level: Float, onStop: () -> Unit) {
    val c = Lily.colors
    var elapsed by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            elapsed++
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).glass(c, CircleShape, strong = true).padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(c.danger))
        Spacer(Modifier.width(10.dp))
        Text("Gravando · ${elapsed}s de 30s", style = Type.footnote, color = c.content, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(5) { i ->
                val h = (6 + 18 * (level * (1f - kotlin.math.abs(i - 2) * 0.22f))).coerceIn(4f, 24f)
                Box(Modifier.width(3.dp).height(h.dp).clip(CircleShape).background(c.accent))
            }
        }
        Spacer(Modifier.width(10.dp))
        PillButton("Parar", onStop, kind = ButtonKind.TINTED, height = 36.dp)
    }
}

@Composable
fun ProjectPicker(projects: List<app.lily.ai.data.Project>, current: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val c = Lily.colors
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (c.dark) Color(0xFF1B1826) else Color.White,
        title = { Text("Mover para projeto", style = Type.headline, color = c.content) },
        text = {
            Column {
                SheetOption("Sem projeto", null, current == null, c.tertiary) { onPick(null) }
                projects.forEach { p -> SheetOption(p.name, null, current == p.id, Color(p.color.toInt())) { onPick(p.id) } }
                if (projects.isEmpty()) Text("Crie projetos no Histórico para agrupar conversas.", style = Type.footnote, color = c.secondary, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {},
        dismissButton = {
            Text("Fechar", style = Type.callout, color = c.secondary, modifier = Modifier.clip(CircleShape).clickable { onDismiss() }.padding(horizontal = 14.dp, vertical = 8.dp))
        },
    )
}

@Composable
fun sheetColor(): Color = if (Lily.colors.dark) Color(0xFF17151F) else Color(0xFFFBFAFE)

