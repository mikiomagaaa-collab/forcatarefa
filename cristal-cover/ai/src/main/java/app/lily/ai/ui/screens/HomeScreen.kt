package app.lily.ai.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Spellcheck
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lily.ai.core.TaskKind
import app.lily.ai.engine.EngineStatus
import app.lily.ai.imagegen.GenState
import app.lily.ai.models.Catalog
import app.lily.ai.models.Compatibility
import app.lily.ai.models.DlState
import app.lily.ai.models.Runtime
import app.lily.ai.models.formatBytes
import app.lily.ai.ui.LocalGraph
import app.lily.ai.ui.LocalPermissions
import app.lily.ai.ui.MainActivity
import app.lily.ai.ui.Nav
import app.lily.ai.ui.Route
import app.lily.ai.ui.theme.ButtonKind
import app.lily.ai.ui.theme.Chip
import app.lily.ai.ui.theme.GlassCard
import app.lily.ai.ui.theme.IconButton
import app.lily.ai.ui.theme.Lily
import app.lily.ai.ui.theme.LilyOrb
import app.lily.ai.ui.theme.PillButton
import app.lily.ai.ui.theme.Progress
import app.lily.ai.ui.theme.SectionHeader
import app.lily.ai.ui.theme.Type
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(nav: Nav) {
    val graph = LocalGraph.current
    val c = Lily.colors
    val permissions = LocalPermissions.current
    val metas by graph.conversations.conversations.collectAsStateWithLifecycle()
    val projects by graph.conversations.projects.collectAsStateWithLifecycle()
    val store by graph.store.state.collectAsStateWithLifecycle()
    val downloads by graph.downloads.states.collectAsStateWithLifecycle()
    val engine by graph.engines.status.collectAsStateWithLifecycle()
    val gen by graph.imageGen.state.collectAsStateWithLifecycle()
    val settings by graph.settings.value.collectAsStateWithLifecycle()
    val device = rememberDevice()
    var draft by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val voice = rememberVoiceInput()
    val installed = remember(store) { graph.store.installedIds() }
    val chatModels = Catalog.all.filter { it.runtime == Runtime.LITERT_LM && it.id in installed }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            nav.go(Route.Chat(image = uri, draft = draft.ifBlank { null }))
            draft = ""
        }
    }
    ListenEffect(
        voice,
        listening,
        onFinal = { text -> if (text.isNotBlank()) nav.go(Route.Chat(draft = text, send = true, voice = true)) },
        onError = { notice = it },
        onEnd = { listening = false },
    )
    LaunchedEffect(voice.partial) { if (listening) draft = voice.partial }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(3_500)
            notice = null
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(greeting(), style = Type.subhead, color = c.secondary)
                Text("L.I.L.Y", style = Type.display, color = c.content)
            }
            IconButton(Icons.Rounded.History, "Histórico", { nav.go(Route.History) })
            androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
            IconButton(Icons.Rounded.Settings, "Configurações", { nav.go(Route.Settings) })
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                app.lily.ai.avatar.PresenceAvatar(176.dp, listeningLevel = voice.level)
            }
            val status = when (val e = engine) {
                is EngineStatus.Loading -> "Carregando ${e.model.name}…"
                is EngineStatus.Busy -> "${e.model.name} respondendo na ${e.accel.label}"
                is EngineStatus.Ready -> "${e.model.name} pronto na ${e.accel.label} · no aparelho"
                EngineStatus.Idle -> if (chatModels.isEmpty()) "Instale um modelo para conversar sem internet" else
                    "${chatModels.size} ${if (chatModels.size == 1) "modelo" else "modelos"} no aparelho · perfil ${settings.profile.label}"
            }
            val presence by app.lily.ai.presence.LilyPresence.state.collectAsStateWithLifecycle()
            val doing = presence.takeIf { it.mode != app.lily.ai.presence.PresenceMode.IDLE }?.let { p -> (p.label ?: p.mode.label) + (p.progress?.let { " · ${(it * 100).toInt()}%" } ?: "") }
            Text(listening.takeIf { it }?.let { "Ouvindo…" } ?: notice ?: doing ?: status, style = Type.footnote, color = c.secondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

            if (chatModels.isEmpty()) {
                Spacer(Modifier.height(16.dp))
                FirstModelCard(nav, device)
            }

            (gen as? GenState.Running)?.let { g ->
                Spacer(Modifier.height(14.dp))
                GlassCard(Modifier.fillMaxWidth(), onClick = { nav.go(Route.ImageGen()) }) {
                    Text("Gerando imagem", style = Type.headline, color = c.content)
                    Text("${g.stage} · ${(g.fraction * 100).toInt()}%", style = Type.footnote, color = c.secondary)
                    Spacer(Modifier.height(10.dp))
                    Progress(g.fraction)
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ModuleCard("Codex", "Programar, depurar e revisar projetos", Icons.Rounded.Code, Modifier.weight(1f)) { nav.go(Route.Codex()) }
                ModuleCard("Vídeo", "Criar vídeos a partir de texto ou imagem", Icons.Rounded.Movie, Modifier.weight(1f)) { nav.go(Route.Video()) }
            }

            SectionHeader("Ferramentas", action = "Ver todas", onAction = { nav.go(Route.Tools) })
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
                items(quickTools) { t ->
                    Chip(t.label, selected = false, onClick = { nav.go(t.route) }, icon = t.icon)
                }
            }

            val pinned = metas.filter { it.pinned }.take(3)
            val recent = metas.filterNot { it.pinned }.take(5)
            if (pinned.isNotEmpty() || recent.isNotEmpty()) {
                SectionHeader("Conversas", action = "Histórico", onAction = { nav.go(Route.History) })
                (pinned + recent).forEach { m ->
                    ConversationRow(m, projects.firstOrNull { it.id == m.projectId }, onClick = { nav.go(Route.Chat(id = m.id)) })
                }
            }

            SectionHeader("Model Hub")
            val running = downloads.entries.firstOrNull { it.value is DlState.Running }
            val installedBytes = Catalog.all.filter { it.id in installed && !it.builtIn }.sumOf { it.totalBytes }
            GlassCard(Modifier.fillMaxWidth(), onClick = { nav.go(Route.Hub()) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Modelos no aparelho", style = Type.headline, color = c.content)
                        Text(
                            "${chatModels.size} de conversa · ${formatBytes(installedBytes)} instalados · ${formatBytes(device.freeStorageBytes)} livres",
                            style = Type.footnote,
                            color = c.secondary,
                        )
                    }
                    androidx.compose.material3.Icon(Icons.Rounded.ViewInAr, contentDescription = null, tint = c.accent)
                }
                if (running != null) {
                    val p = (running.value as DlState.Running).progress
                    Spacer(Modifier.height(10.dp))
                    Text("Baixando ${Catalog.byId(running.key)?.name ?: running.key} · ${(p.fraction * 100).toInt()}%", style = Type.caption, color = c.secondary)
                    Spacer(Modifier.height(6.dp))
                    Progress(p.fraction)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        Box(Modifier.padding(horizontal = 12.dp).navigationBarsPadding().padding(bottom = 8.dp)) {
            Composer(
                text = draft,
                onText = { draft = it },
                onSend = {
                    nav.go(Route.Chat(draft = draft, send = true))
                    draft = ""
                },
                placeholder = "Pergunte qualquer coisa",
                onAttach = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onMic = {
                    if (listening) {
                        listening = false
                    } else {
                        permissions.request(MainActivity.MICROPHONE) { ok -> if (ok) listening = true else notice = "Sem a permissão do microfone, a L.I.L.Y não consegue ouvir." }
                    }
                },
                listening = if (listening) voice.level ?: 0f else null,
            )
        }
    }
}

@Composable
private fun ModuleCard(title: String, text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val c = Lily.colors
    GlassCard(modifier, onClick = onClick) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = c.accent)
        Spacer(Modifier.height(8.dp))
        Text(title, style = Type.headline, color = c.content)
        Text(text, style = Type.caption, color = c.secondary, maxLines = 2)
    }
}

private data class QuickTool(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val route: Route)

private val quickTools = listOf(
    QuickTool("Corrigir", Icons.Rounded.Spellcheck, Route.TextTool(TaskKind.CORRECT)),
    QuickTool("Resumir", Icons.AutoMirrored.Rounded.Notes, Route.TextTool(TaskKind.SUMMARIZE)),
    QuickTool("Traduzir", Icons.Rounded.Translate, Route.TextTool(TaskKind.TRANSLATE)),
    QuickTool("Analisar foto", Icons.Rounded.ImageSearch, Route.ImageTool()),
    QuickTool("Melhorar foto", Icons.Rounded.AutoFixHigh, Route.ImageTool(task = TaskKind.ENHANCE)),
    QuickTool("Gerar imagem", Icons.Rounded.Brush, Route.ImageGen()),
    QuickTool("Transcrever áudio", Icons.Rounded.GraphicEq, Route.TextTool(TaskKind.TRANSCRIBE_AUDIO)),
    QuickTool("Ditado", Icons.Rounded.Mic, Route.TextTool(TaskKind.TRANSCRIBE_MIC)),
    QuickTool("Escrever", Icons.Rounded.AutoAwesome, Route.TextTool(TaskKind.GENERATE_TEXT)),
)

/** Primeira vez: o modelo recomendado para ESTE aparelho (memória e espaço reais), com o download a um toque. */
@Composable
private fun FirstModelCard(nav: Nav, device: app.lily.ai.core.DeviceSnapshot) {
    val graph = LocalGraph.current
    val c = Lily.colors
    val downloads by graph.downloads.states.collectAsStateWithLifecycle()
    val pick = remember(device.totalRamBytes, device.freeStorageBytes / (512L shl 20)) { recommendedChat(device) }
    val report = Compatibility.evaluate(pick, device)
    val state = downloads[pick.id]
    val download = rememberDownloadStarter()
    GlassCard(Modifier.fillMaxWidth(), strong = true) {
        Text("Comece por aqui", style = Type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
        Spacer(Modifier.height(4.dp))
        Text(pick.name, style = Type.title, color = c.content)
        Text("${formatBytes(pick.totalBytes)} · ${report.level.label} com este aparelho", style = Type.footnote, color = compatColor(report.level, c))
        Spacer(Modifier.height(8.dp))
        Text(pick.description, style = Type.subhead, color = c.secondary)
        Spacer(Modifier.height(12.dp))
        when (state) {
            is DlState.Running -> {
                Text("Baixando · ${(state.progress.fraction * 100).toInt()}%", style = Type.footnote, color = c.secondary)
                Spacer(Modifier.height(6.dp))
                Progress(state.progress.fraction)
            }
            DlState.Queued -> Text("Na fila…", style = Type.footnote, color = c.secondary)
            else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Baixar", { download(pick) }, height = 42.dp)
                PillButton("Ver no Model Hub", { nav.go(Route.Model(pick.id)) }, kind = ButtonKind.GLASS, height = 42.dp)
            }
        }
    }
}
