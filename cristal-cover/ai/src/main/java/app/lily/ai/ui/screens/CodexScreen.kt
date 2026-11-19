package app.lily.ai.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lily.ai.avatar.AvatarPresentation
import app.lily.ai.avatar.PresenceAvatar
import app.lily.ai.codex.CodexEvent
import app.lily.ai.codex.CodexMode
import app.lily.ai.codex.CodexSession
import app.lily.ai.codex.Decision
import app.lily.ai.codex.FileDiff
import app.lily.ai.codex.Phase
import app.lily.ai.codex.Project
import app.lily.ai.codex.RemoteCodeLlm
import app.lily.ai.codex.RepoWorkspace
import app.lily.ai.codex.RestorePoints
import app.lily.ai.codex.SafWorkspace
import app.lily.ai.codex.TestPlan
import app.lily.ai.codex.Verification
import app.lily.ai.codex.Workspace
import app.lily.ai.remote.GatewayException
import app.lily.ai.remote.RemoteModel
import app.lily.ai.remote.RepoInfo
import app.lily.ai.ui.LocalGraph
import app.lily.ai.ui.Nav
import app.lily.ai.ui.Route
import app.lily.ai.ui.theme.ButtonKind
import app.lily.ai.ui.theme.Chip
import app.lily.ai.ui.theme.EmptyState
import app.lily.ai.ui.theme.GlassCard
import app.lily.ai.ui.theme.Lily
import app.lily.ai.ui.theme.PillButton
import app.lily.ai.ui.theme.Progress
import app.lily.ai.ui.theme.SectionHeader
import app.lily.ai.ui.theme.Toggle
import app.lily.ai.ui.theme.TopBar
import app.lily.ai.ui.theme.Type
import app.lily.ai.ui.theme.press
import java.io.File
import java.util.UUID
import kotlinx.coroutines.launch

/** O estado da tela do Codex que sobrevive enquanto ela está na pilha (a sessão continua). */
@Stable
class CodexHolder {
    var session by mutableStateOf<CodexSession?>(null)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CodexScreen(nav: Nav, key: Int, start: Route.Codex) {
    val graph = LocalGraph.current
    val c = Lily.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val holder = remember(key) { nav.retain(key, { CodexHolder() }, { it.session?.cancel() }) }
    val settings by graph.settings.value.collectAsStateWithLifecycle()
    val config by graph.gatewayStore.config.collectAsStateWithLifecycle()
    val projects by graph.codexProjects.items.collectAsStateWithLifecycle()
    var projectId by remember { mutableStateOf(projects.firstOrNull()?.id) }
    var mode by remember { mutableStateOf(if (start.error.isNotBlank()) CodexMode.REPAIR else CodexMode.TASK) }
    var task by remember { mutableStateOf(start.task) }
    var error by remember { mutableStateOf(start.error) }
    var manual by remember { mutableStateOf<String?>(null) }
    var models by remember { mutableStateOf(graph.gateway.cachedModels()) }
    var repos by remember { mutableStateOf<List<RepoInfo>?>(null) }
    var addingRepo by remember { mutableStateOf<RepoInfo?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val ready = settings.onlineConsent && config?.hasKey == true

    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        runCatching { models = graph.gateway.codeModels() }.onFailure { notice = it.message }
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            runCatching { SafWorkspace.persist(context, uri) }
            val name = uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')?.ifBlank { null } ?: "Pasta"
            val p = Project(UUID.randomUUID().toString(), name, "folder", uri = uri.toString())
            graph.codexProjects.add(p)
            projectId = p.id
        }
    }

    fun workspaceOf(p: Project): Workspace = when (p.kind) {
        "repo" -> RepoWorkspace(graph.gateway, p.repo!!, p.base ?: "main", CodexSession.branchName(), p.tests)
        else -> SafWorkspace(context, Uri.parse(p.uri), p.name)
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Codex", onBack = { nav.back() }, subtitle = "Programação com a L.I.L.Y")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            if (!ready) {
                EmptyState(
                    Icons.Rounded.Cloud,
                    "Conecte o serviço online",
                    "O Codex usa modelos de programação grandes pelo seu L.I.L.Y Gateway (sem baixar nada enorme no celular). O código que ele ler é enviado ao gateway e ao provedor do modelo.",
                    action = "Configurar",
                    onAction = { nav.go(Route.Online) },
                )
                return@Column
            }
            val session = holder.session
            if (session == null) {
                SectionHeader("Projeto autorizado")
                GlassCard(Modifier.fillMaxWidth()) {
                    if (projects.isEmpty()) {
                        Text("O Codex só lê e altera projetos que você autorizar aqui.", style = Type.footnote, color = c.secondary)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        projects.forEach { p -> Chip(p.name + if (p.kind == "repo") " · GitHub" else "", projectId == p.id, { projectId = p.id }) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton("Pasta do aparelho", { pickFolder.launch(null) }, icon = Icons.Rounded.CreateNewFolder, kind = ButtonKind.GLASS, height = 40.dp)
                        PillButton("Repositório", {
                            scope.launch {
                                repos = try {
                                    graph.gateway.repos().repos
                                } catch (e: GatewayException) {
                                    notice = e.message
                                    emptyList()
                                }
                                if (repos?.isEmpty() == true) notice = "Nenhum repositório liberado no gateway (GITHUB_TOKEN e GITHUB_REPOS)."
                            }
                        }, icon = Icons.Rounded.Code, kind = ButtonKind.GLASS, height = 40.dp)
                    }
                    repos?.filter { r -> projects.none { it.repo == r.repo } }?.takeIf { it.isNotEmpty() }?.let { list ->
                        Spacer(Modifier.height(8.dp))
                        list.forEach { r -> Chip("${r.repo}${if (!r.available) " (indisponível)" else ""}", false, { addingRepo = r }, enabled = r.available) }
                    }
                    addingRepo?.let { r -> RepoForm(r, onCancel = { addingRepo = null }) { tests ->
                        val p = Project(UUID.randomUUID().toString(), r.repo.substringAfter('/'), "repo", repo = r.repo, base = r.defaultBranch ?: "main", tests = tests)
                        graph.codexProjects.add(p)
                        projectId = p.id
                        addingRepo = null
                    } }
                    projects.firstOrNull { it.id == projectId }?.let { p ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (p.kind == "repo") "As mudanças vão para um ramo lily-codex/… (o principal nunca é tocado) e os testes rodam no GitHub Actions: ${p.tests?.let { "${it.runner} ${it.tasks}" } ?: "sem testes"}."
                            else "As mudanças aprovadas são gravadas na pasta, com ponto de restauração. Os testes do projeto não rodam no celular: a verificação é de sintaxe.",
                            style = Type.caption,
                            color = c.tertiary,
                        )
                        Text(
                            "Remover este projeto",
                            style = Type.caption,
                            color = c.danger,
                            modifier = Modifier.padding(top = 6.dp).press({
                                graph.codexProjects.remove(p.id)
                                projectId = projects.firstOrNull { it.id != p.id }?.id
                            }),
                        )
                    }
                }

                SectionHeader("O que fazer")
                GlassCard(Modifier.fillMaxWidth(), strong = true) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CodexMode.entries.forEach { m -> Chip(m.label, mode == m, { mode = m }) }
                    }
                    Text(mode.detail, style = Type.caption, color = c.tertiary, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
                    GlassField(
                        when (mode) {
                            CodexMode.REPAIR -> "O que deveria acontecer (opcional)"
                            CodexMode.EXPLAIN -> "O que você quer entender"
                            CodexMode.SECURITY -> "Foco da revisão (opcional)"
                            CodexMode.TASK -> "A tarefa"
                        },
                        task,
                        { task = it },
                        placeholder = when (mode) {
                            CodexMode.TASK -> "Ex.: adicione um botão de compartilhar na tela de vídeos"
                            CodexMode.EXPLAIN -> "Ex.: como o roteador escolhe o modelo?"
                            else -> ""
                        },
                        minLines = 3,
                    )
                    if (mode == CodexMode.REPAIR) {
                        Spacer(Modifier.height(10.dp))
                        GlassField("O erro ou o log", error, { error = it }, placeholder = "Cole a mensagem de erro, o log do build ou o relatório do L.I.L.Y Diagnóstico", minLines = 5)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Modelo", style = Type.caption, color = c.secondary)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip("Automático", manual == null, { manual = null })
                        models.sortedWith(compareBy({ listOf("frontier", "strong", "fast").indexOf(it.tier) }, { it.typicalUsd })).forEach { m ->
                            Chip(m.label, manual == m.id, { manual = m.id })
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Pesquisar na internet", style = Type.callout, color = c.content)
                            Text("Documentação oficial, erros conhecidos e mudanças de versão, com as fontes", style = Type.caption, color = c.tertiary)
                        }
                        Toggle(settings.codexWeb, { on -> graph.settings.update { it.copy(codexWeb = on) } })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Aplicar correções simples sozinho", style = Type.callout, color = c.content)
                            Text("Até ${settings.codexAutoLines} linhas em um arquivo, sem problemas na verificação. Dá para desfazer.", style = Type.caption, color = c.tertiary)
                        }
                        Toggle(settings.codexAuto, { on -> graph.settings.update { it.copy(codexAuto = on) } })
                    }
                    Spacer(Modifier.height(12.dp))
                    val project = projects.firstOrNull { it.id == projectId }
                    PillButton(
                        "Começar",
                        {
                            val p = project ?: return@PillButton
                            val s = CodexSession(
                                scope = graph.scope,
                                workspace = workspaceOf(p),
                                mode = mode,
                                task = task,
                                errorText = error,
                                models = { graph.gateway.codeModels() },
                                llmFor = { m: RemoteModel -> RemoteCodeLlm(graph.gateway, m) },
                                gateway = graph.gateway,
                                restore = RestorePoints(File(context.filesDir, "codex/restauracao")),
                                profile = settings.profile,
                                manualModel = manual,
                                webAllowed = settings.codexWeb,
                                reviewEnabled = settings.codexReview,
                                autoApplyLines = if (settings.codexAuto) settings.codexAutoLines else null,
                            )
                            holder.session = s
                            s.start()
                        },
                        Modifier.fillMaxWidth(),
                        enabled = project != null && (task.isNotBlank() || (mode == CodexMode.REPAIR && error.isNotBlank()) || mode == CodexMode.SECURITY),
                    )
                    Text(
                        "Os arquivos que o Codex ler vão para o gateway e para o provedor do modelo escolhido (processamento externo). Nada é gravado sem você aprovar o diff.",
                        style = Type.caption,
                        color = c.tertiary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                notice?.let { Text(it, style = Type.footnote, color = c.warning, modifier = Modifier.padding(top = 8.dp)) }
            } else {
                SessionView(session, onNew = { holder.session = null })
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RepoForm(r: RepoInfo, onCancel: () -> Unit, onAdd: (TestPlan?) -> Unit) {
    val c = Lily.colors
    var runner by remember { mutableStateOf("gradle") }
    var tasks by remember { mutableStateOf("test") }
    var workdir by remember { mutableStateOf(".") }
    var withTests by remember { mutableStateOf(true) }
    GlassCard(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text(r.repo, style = Type.headline, color = c.content)
        Text("Ramo de origem: ${r.defaultBranch ?: "main"}", style = Type.caption, color = c.secondary)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Rodar os testes do projeto (GitHub Actions)", style = Type.callout, color = c.content, modifier = Modifier.weight(1f))
            Toggle(withTests, { withTests = it })
        }
        if (withTests) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("gradle", "npm", "pytest").forEach { k -> Chip(k, runner == k, { runner = k }) } }
            Spacer(Modifier.height(8.dp))
            GlassField("Tarefas", tasks, { tasks = it }, placeholder = ":app:testDebugUnitTest")
            Spacer(Modifier.height(8.dp))
            GlassField("Pasta do projeto no repositório", workdir, { workdir = it }, placeholder = ".")
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Adicionar", { onAdd(if (withTests) TestPlan(runner, tasks.trim(), workdir.trim().ifEmpty { "." }) else null) }, height = 40.dp)
            PillButton("Cancelar", onCancel, kind = ButtonKind.GLASS, height = 40.dp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SessionView(s: CodexSession, onNew: () -> Unit) {
    val c = Lily.colors
    val context = LocalContext.current
    var adjust by remember { mutableStateOf("") }
    GlassCard(Modifier.fillMaxWidth().padding(top = 8.dp), strong = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PresenceAvatar(56.dp, presentation = AvatarPresentation.FACE)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(s.phase.label, style = Type.headline, color = c.content)
                Text(
                    listOfNotNull(
                        s.mode.label,
                        s.workspace.name,
                        s.pick?.model?.let { "${it.label} (${it.vendor})" },
                        s.attempt.takeIf { it > 1 }?.let { "tentativa $it" },
                        s.cost.takeIf { it > 0 }?.let { usd(it) },
                    ).joinToString(" · "),
                    style = Type.caption,
                    color = c.secondary,
                )
            }
        }
        if (s.busy) {
            Spacer(Modifier.height(8.dp))
            Progress(0f, indeterminate = true)
        }
    }

    SectionHeader("Linha do tempo")
    GlassCard(Modifier.fillMaxWidth()) {
        if (s.events.isEmpty()) Text("Começando…", style = Type.footnote, color = c.secondary)
        s.events.forEach { e -> EventRow(e) }
    }

    if (s.diffs.isNotEmpty() && s.phase != Phase.DONE || (s.phase == Phase.DONE && s.applied != null && s.diffs.isNotEmpty())) {
        SectionHeader("Mudanças (${s.diffs.size} ${if (s.diffs.size == 1) "arquivo" else "arquivos"})")
        s.review?.let { r ->
            GlassCard(Modifier.fillMaxWidth()) {
                Text(if (r.ok) "Revisão independente: sem problemas" else "Revisão independente: pontos de atenção", style = Type.callout, color = if (r.ok) c.success else c.warning)
                Text("por ${r.reviewer}", style = Type.caption, color = c.tertiary)
                r.issues.forEach { Text("• $it", style = Type.footnote, color = c.secondary) }
            }
            Spacer(Modifier.height(8.dp))
        }
        s.diffs.forEach { d -> DiffCard(d); Spacer(Modifier.height(8.dp)) }
    }

    if (s.phase == Phase.AWAITING_APPROVAL) {
        GlassCard(Modifier.fillMaxWidth(), strong = true) {
            Text("Antes de gravar, um ponto de restauração é criado. Dá para desfazer depois.", style = Type.footnote, color = c.secondary)
            Spacer(Modifier.height(10.dp))
            PillButton("Aprovar e aplicar", { s.decide(Decision.Approve) }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            GlassField("Pedir um ajuste", adjust, { adjust = it }, placeholder = "Ex.: mantenha o nome antigo da função")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Enviar ajuste", { s.decide(Decision.Adjust(adjust)); adjust = "" }, kind = ButtonKind.GLASS, enabled = adjust.isNotBlank(), height = 40.dp)
                PillButton("Descartar", { s.decide(Decision.Reject) }, kind = ButtonKind.GLASS, height = 40.dp)
            }
        }
    }

    if (s.applied != null) {
        SectionHeader("Validação")
        GlassCard(Modifier.fillMaxWidth()) {
            val color = when (s.verification) {
                Verification.TESTS_PASSED, Verification.ERROR_GONE -> c.success
                Verification.SYNTAX_ONLY -> c.warning
                Verification.NONE -> c.secondary
                else -> c.danger
            }
            Text(s.verification.label, style = Type.callout, color = color)
            Text("Aplicado ${s.applied?.where}", style = Type.caption, color = c.secondary)
            s.checkUrl?.let { url -> Text("Ver os testes no GitHub", style = Type.caption, color = c.accent, modifier = Modifier.padding(top = 4.dp).press({ context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) })) }
            if (s.restoreId != null && !s.busy) {
                Spacer(Modifier.height(8.dp))
                PillButton("Desfazer", { s.undo() }, icon = Icons.Rounded.Undo, kind = ButtonKind.GLASS, height = 40.dp)
            }
        }
    }

    s.report?.let { r ->
        SectionHeader("Relatório")
        GlassCard(Modifier.fillMaxWidth()) {
            if (r.summary.isNotBlank()) Text(r.summary, style = Type.callout, color = c.content)
            if (r.changes.isNotBlank()) Text(r.changes, style = Type.footnote, color = c.secondary, modifier = Modifier.padding(top = 6.dp))
            if (r.verified.isNotEmpty()) {
                Text("Verificado", style = Type.caption, color = c.success, modifier = Modifier.padding(top = 10.dp))
                r.verified.forEach { Text("✓ $it", style = Type.footnote, color = c.secondary) }
            }
            if (r.hypotheses.isNotEmpty()) {
                Text("Hipóteses (não confirmadas)", style = Type.caption, color = c.warning, modifier = Modifier.padding(top = 10.dp))
                r.hypotheses.forEach { Text("? $it", style = Type.footnote, color = c.secondary) }
            }
            if (r.risks.isNotEmpty()) {
                Text("Possíveis regressões", style = Type.caption, color = c.danger, modifier = Modifier.padding(top = 10.dp))
                r.risks.forEach { Text("! $it", style = Type.footnote, color = c.secondary) }
            }
            if (r.sources.isNotEmpty()) {
                Text("Fontes", style = Type.caption, color = c.accent, modifier = Modifier.padding(top = 10.dp))
                r.sources.forEach { url ->
                    Text(url, style = Type.footnote, color = c.accent, modifier = Modifier.press({
                        if (url.startsWith("https://")) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }))
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (s.busy) PillButton("Parar", { s.cancel() }, kind = ButtonKind.GLASS, height = 42.dp)
        if (!s.busy && s.phase != Phase.AWAITING_APPROVAL) PillButton("Nova tarefa", onNew, height = 42.dp)
    }
}

@Composable
private fun EventRow(e: CodexEvent) {
    val c = Lily.colors
    val (mark, color) = when (e) {
        is CodexEvent.Plan -> "◆" to c.accent
        is CodexEvent.Thought -> "…" to c.tertiary
        is CodexEvent.Action -> "›" to c.secondary
        is CodexEvent.Web -> "◎" to c.accent2
        is CodexEvent.Staged -> "±" to c.warning
        is CodexEvent.Checks -> (if (e.ok) "✓" else "✗") to (if (e.ok) c.success else c.danger)
        is CodexEvent.Note -> "•" to c.secondary
        is CodexEvent.Failure -> "✗" to c.danger
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text(mark, style = Type.callout, color = color, modifier = Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(e.text, style = if (e is CodexEvent.Thought) Type.footnote else Type.callout, color = if (e is CodexEvent.Thought) c.secondary else c.content)
            when (e) {
                is CodexEvent.Action -> if (e.detail.isNotBlank()) Text(e.detail, style = Type.caption, color = c.tertiary)
                is CodexEvent.Staged -> Text("+${e.added} −${e.removed}", style = Type.caption, color = c.tertiary)
                is CodexEvent.Web -> e.sources.take(5).forEach { Text("${it.title} — ${it.url}", style = Type.caption, color = c.tertiary, maxLines = 1) }
                else -> Unit
            }
        }
    }
}

@Composable
private fun DiffCard(d: FileDiff) {
    val c = Lily.colors
    var open by remember { mutableStateOf(true) }
    var all by remember { mutableStateOf(false) }
    GlassCard(Modifier.fillMaxWidth(), onClick = { open = !open }, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(d.path, style = Type.callout, color = c.content, modifier = Modifier.weight(1f), maxLines = 2)
            Text(
                when {
                    d.created -> "novo"
                    d.deleted -> "apagado"
                    else -> "+${d.added} −${d.removed}"
                },
                style = Type.caption,
                color = c.secondary,
            )
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            val lines = d.hunks.flatMap { h -> listOf('@' to h.header) + h.lines.map { it.kind to it.text } }
            val shown = if (all) lines else lines.take(160)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0x22000000)).horizontalScroll(rememberScrollState()).padding(8.dp)) {
                shown.forEach { (kind, text) ->
                    val color = when (kind) {
                        '+' -> Color(0xFF5BE49B)
                        '-' -> Color(0xFFFF7A85)
                        '@' -> c.accent
                        else -> c.secondary
                    }
                    Text("${if (kind == '@') "" else kind}$text", color = color, fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 1, softWrap = false)
                }
            }
            if (lines.size > shown.size) Text("Mostrar tudo (${lines.size} linhas)", style = Type.caption, color = c.accent, modifier = Modifier.padding(top = 6.dp).press({ all = true }))
        }
    }
}
