package app.lily.ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lily.ai.data.Memory
import app.lily.ai.ui.LocalGraph
import app.lily.ai.ui.Nav
import app.lily.ai.ui.theme.ButtonKind
import app.lily.ai.ui.theme.Chip
import app.lily.ai.ui.theme.ConfirmDialog
import app.lily.ai.ui.theme.EmptyState
import app.lily.ai.ui.theme.GlassCard
import app.lily.ai.ui.theme.IconButton
import app.lily.ai.ui.theme.Lily
import app.lily.ai.ui.theme.PillButton
import app.lily.ai.ui.theme.Toggle
import app.lily.ai.ui.theme.TopBar
import app.lily.ai.ui.theme.Type

/**
 * Memórias: só o que a pessoa mandou guardar. Histórico de conversas não vira memória, e o que
 * parece senha, documento ou cartão é recusado.
 */
@Composable
fun MemoryScreen(nav: Nav) {
    val graph = LocalGraph.current
    val c = Lily.colors
    val settings by graph.settings.value.collectAsStateWithLifecycle()
    val items by graph.memory.items.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Memory?>(null) }
    var deleting by remember { mutableStateOf<Memory?>(null) }
    var clearAll by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopBar("Memória", onBack = { nav.back() }, subtitle = "${items.size} ${if (items.size == 1) "memória" else "memórias"} · ficam só no aparelho")
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                GlassCard(Modifier.fillMaxWidth(), strong = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Usar memórias nas conversas", style = Type.callout, color = c.content)
                            Text(
                                "A L.I.L.Y só lembra o que você mandar guardar (aqui ou no botão “Guardar na memória” de uma mensagem). As conversas do histórico não viram memória.",
                                style = Type.caption,
                                color = c.secondary,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Toggle(settings.memoryEnabled, { on -> graph.settings.update { it.copy(memoryEnabled = on) } })
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton("Nova memória", { adding = "fato" }, kind = ButtonKind.TINTED, icon = Icons.Rounded.Add, height = 38.dp)
                        PillButton("Preferência", { adding = "preferencia" }, kind = ButtonKind.GLASS, height = 38.dp)
                    }
                    notice?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = Type.caption, color = c.warning)
                    }
                }
            }
            if (items.isEmpty()) {
                item { EmptyState(Icons.Rounded.Psychology, "Nada guardado", "Exemplos: “Prefiro respostas curtas”, “Estudo engenharia”, “Moro em Recife”.") }
            }
            items(items, key = { it.id }) { m ->
                GlassCard(Modifier.fillMaxWidth(), padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.text, style = Type.callout, color = if (m.enabled) c.content else c.tertiary)
                            Spacer(Modifier.height(4.dp))
                            Text("${if (m.kind == "preferencia") "Preferência" else "Fato"} · ${whenText(m.at)}${if (!m.enabled) " · desligada" else ""}", style = Type.caption, color = c.tertiary)
                        }
                        Toggle(m.enabled, { on -> graph.memory.toggle(m.id, on) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(Icons.Rounded.Edit, "Editar", { editing = m }, size = 36.dp, filled = false, tint = c.secondary)
                        IconButton(Icons.Rounded.Delete, "Apagar", { deleting = m }, size = 36.dp, filled = false, tint = c.secondary)
                    }
                }
            }
            if (items.isNotEmpty()) {
                item {
                    Chip("Apagar todas", false, { clearAll = true }, icon = Icons.Rounded.Delete, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }

    adding?.let { kind ->
        TextInputDialog(
            if (kind == "preferencia") "Nova preferência" else "Nova memória",
            "",
            "Guardar",
            { text ->
                adding = null
                notice = if (graph.memory.add(text, kind) == null) "Parece informação sensível (senha, documento ou cartão): não foi guardada." else null
            },
            { adding = null },
            supporting = if (kind == "preferencia") "Ex.: “Prefiro respostas curtas e diretas”." else "Ex.: “Moro em Recife”, “Estudo engenharia elétrica”.",
            singleLine = false,
        )
    }
    editing?.let { m ->
        TextInputDialog("Editar memória", m.text, "Salvar", { text ->
            editing = null
            if (app.lily.ai.data.Sensitive.looksSensitive(text)) notice = "Parece informação sensível: a memória não foi alterada." else graph.memory.edit(m.id, text)
        }, { editing = null }, singleLine = false)
    }
    deleting?.let { m ->
        ConfirmDialog("Apagar memória?", "“${m.text.take(80)}” sai do aparelho.", "Apagar", onConfirm = {
            deleting = null
            graph.memory.delete(m.id)
        }, onDismiss = { deleting = null }, destructive = true)
    }
    if (clearAll) {
        ConfirmDialog("Apagar todas as memórias?", "A L.I.L.Y deixa de lembrar o que você mandou guardar.", "Apagar tudo", onConfirm = {
            clearAll = false
            graph.memory.clear()
        }, onDismiss = { clearAll = false }, destructive = true)
    }
}
