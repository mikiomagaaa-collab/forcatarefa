package app.lily.ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lily.ai.data.ConversationMeta
import app.lily.ai.ui.LocalGraph
import app.lily.ai.ui.Nav
import app.lily.ai.ui.Route
import app.lily.ai.ui.theme.Chip
import app.lily.ai.ui.theme.ConfirmDialog
import app.lily.ai.ui.theme.EmptyState
import app.lily.ai.ui.theme.IconButton
import app.lily.ai.ui.theme.Lily
import app.lily.ai.ui.theme.TopBar
import app.lily.ai.ui.theme.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PINNED = "__fixadas"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(nav: Nav) {
    val graph = LocalGraph.current
    val c = Lily.colors
    val scope = rememberCoroutineScope()
    val metas by graph.conversations.conversations.collectAsStateWithLifecycle()
    val projects by graph.conversations.projects.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<ConversationMeta>?>(null) }
    var menu by remember { mutableStateOf(false) }
    var newProject by remember { mutableStateOf(false) }
    var deleteAll by remember { mutableStateOf(false) }
    var deleteProject by remember { mutableStateOf(false) }
    var optionsFor by remember { mutableStateOf<ConversationMeta?>(null) }
    var renaming by remember { mutableStateOf<ConversationMeta?>(null) }
    var moving by remember { mutableStateOf<ConversationMeta?>(null) }
    var deleting by remember { mutableStateOf<ConversationMeta?>(null) }

    // A busca olha títulos, prévias e o texto de todas as mensagens.
    LaunchedEffect(query, metas) {
        if (query.isBlank()) {
            results = null
        } else {
            delay(250)
            results = graph.conversations.search(query)
        }
    }
    val shown = (results ?: metas).filter {
        when (filter) {
            null -> true
            PINNED -> it.pinned
            else -> it.projectId == filter
        }
    }
    val selectedProject = projects.firstOrNull { it.id == filter }

    Column(Modifier.fillMaxSize()) {
        TopBar("Histórico", onBack = { nav.back() }, subtitle = "${metas.size} ${if (metas.size == 1) "conversa" else "conversas"} no aparelho", actions = {
            Box {
                IconButton(Icons.Rounded.MoreVert, "Mais opções", { menu = true })
                DropdownMenu(menu, { menu = false }, containerColor = sheetColor()) {
                    DropdownMenuItem(text = { Text("Novo projeto", style = Type.callout, color = c.content) }, onClick = { menu = false; newProject = true })
                    if (selectedProject != null) {
                        DropdownMenuItem(text = { Text("Apagar o projeto “${selectedProject.name}”", style = Type.callout, color = c.danger) }, onClick = { menu = false; deleteProject = true })
                    }
                    if (metas.isNotEmpty()) {
                        DropdownMenuItem(text = { Text("Apagar todas as conversas", style = Type.callout, color = c.danger) }, onClick = { menu = false; deleteAll = true })
                    }
                }
            }
        })
        SearchField(query, { query = it }, "Buscar nas conversas", Modifier.padding(horizontal = 16.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            item { Chip("Todas", filter == null, { filter = null }) }
            item { Chip("Fixadas", filter == PINNED, { filter = PINNED }, icon = Icons.Rounded.PushPin) }
            items(projects, key = { it.id }) { p -> Chip(p.name, filter == p.id, { filter = p.id }) }
            item { Chip("Projeto", false, { newProject = true }, icon = Icons.Rounded.Add) }
        }
        if (shown.isEmpty()) {
            if (query.isNotBlank()) {
                EmptyState(Icons.Rounded.SearchOff, "Nada encontrado", "Nenhuma conversa tem “$query”.")
            } else {
                EmptyState(Icons.Rounded.Forum, "Nenhuma conversa aqui", "As conversas ficam só no aparelho. Comece uma nova na tela inicial.", action = "Nova conversa", onAction = { nav.go(Route.Chat()) })
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
                val grouped = if (query.isBlank() && filter == null) {
                    listOf("Fixadas" to shown.filter { it.pinned }) + shown.filterNot { it.pinned }.groupBy { dateGroup(it.updatedAt) }.toList()
                } else {
                    shown.groupBy { dateGroup(it.updatedAt) }.toList()
                }
                grouped.filter { it.second.isNotEmpty() }.forEach { (title, list) ->
                    item(key = "h-$title") { Text(title, style = Type.footnote, color = c.secondary, modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp)) }
                    items(list, key = { it.id }) { m ->
                        ConversationRow(m, projects.firstOrNull { it.id == m.projectId }, onClick = { nav.go(Route.Chat(id = m.id)) }, onMore = { optionsFor = m })
                    }
                }
                item { Box(Modifier.navigationBarsPadding()) }
            }
        }
    }

    optionsFor?.let { m ->
        ModalBottomSheet(onDismissRequest = { optionsFor = null }, containerColor = sheetColor()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 24.dp)) {
                Text(m.title, style = Type.headline, color = c.content, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                SheetAction("Abrir") { optionsFor = null; nav.go(Route.Chat(id = m.id)) }
                SheetAction("Renomear") { optionsFor = null; renaming = m }
                SheetAction(if (m.pinned) "Desafixar" else "Fixar") { optionsFor = null; scope.launch { graph.conversations.pin(m.id, !m.pinned) } }
                SheetAction("Mover para projeto") { optionsFor = null; moving = m }
                SheetAction("Apagar") { optionsFor = null; deleting = m }
            }
        }
    }
    renaming?.let { m -> TextInputDialog("Renomear conversa", m.title, "Salvar", { name -> renaming = null; scope.launch { graph.conversations.rename(m.id, name) } }, { renaming = null }) }
    moving?.let { m -> ProjectPicker(projects, m.projectId, onPick = { p -> moving = null; scope.launch { graph.conversations.move(m.id, p) } }, onDismiss = { moving = null }) }
    deleting?.let { m ->
        ConfirmDialog("Apagar “${m.title}”?", "As mensagens e os anexos saem do aparelho.", "Apagar", onConfirm = {
            deleting = null
            scope.launch { graph.conversations.delete(m.id) }
        }, onDismiss = { deleting = null }, destructive = true)
    }
    if (newProject) {
        TextInputDialog("Novo projeto", "", "Criar", { name ->
            newProject = false
            scope.launch {
                val p = graph.conversations.addProject(name, ProjectColors[projects.size % ProjectColors.size])
                filter = p.id
            }
        }, { newProject = false }, supporting = "Projetos agrupam conversas (estudos, trabalho, viagem…).")
    }
    if (deleteProject && selectedProject != null) {
        ConfirmDialog("Apagar o projeto?", "As conversas de “${selectedProject.name}” continuam no histórico, sem projeto.", "Apagar", onConfirm = {
            deleteProject = false
            filter = null
            scope.launch { graph.conversations.deleteProject(selectedProject.id) }
        }, onDismiss = { deleteProject = false }, destructive = true)
    }
    if (deleteAll) {
        ConfirmDialog("Apagar todas as conversas?", "Todas as conversas e anexos saem do aparelho. As memórias guardadas continuam (apague em Memória).", "Apagar tudo", onConfirm = {
            deleteAll = false
            scope.launch { graph.conversations.deleteAll() }
        }, onDismiss = { deleteAll = false }, destructive = true)
    }
}
