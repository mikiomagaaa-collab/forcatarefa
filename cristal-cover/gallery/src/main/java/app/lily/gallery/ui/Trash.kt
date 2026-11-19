package app.lily.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
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

/** A lixeira oficial do Android: itens ficam 30 dias e depois somem sozinhos. */
@Composable
fun TrashScreen(nav: GalleryNav) {
    val c = G.colors
    val repo = LocalRepo.current
    val requests = LocalRequests.current
    val trash by repo.trash.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var confirmEmpty by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        MediaGrid(
            items = trash,
            columns = 4,
            selected = selected,
            onOpen = { nav.go(Screen.Viewer(Source.Trash, it.id)) },
            onSelect = { item -> selected = if (item.id in selected) selected - item.id else selected + item.id },
            header = {
                Column(Modifier.statusBarsPadding().padding(top = 64.dp, start = 14.dp, end = 14.dp)) {
                    Text("Os itens ficam aqui por 30 dias e depois são apagados pelo Android.", style = GType.footnote, color = c.secondary)
                    trash.minOfOrNull { it.expires }?.takeIf { it > 0 }?.let { first ->
                        val days = ((first - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0)
                        Text("O próximo sai em $days ${if (days == 1L) "dia" else "dias"}.", style = GType.caption, color = c.tertiary)
                    }
                    Spacer(Modifier.height(6.dp))
                }
            },
            contentBottom = 40,
        )
        if (trash.isEmpty()) Text("A lixeira está vazia.", style = GType.callout, color = c.secondary, modifier = Modifier.align(Alignment.Center))
        if (selected.isNotEmpty()) {
            SelectionBar(trash.filter { it.id in selected }, onClear = { selected = emptySet() }, trash = true)
        } else {
            Box(Modifier.background(c.background.copy(alpha = 0.6f))) {
                GTopBar("Lixeira", onBack = { nav.back() }, subtitle = "${trash.size} ${if (trash.size == 1) "item" else "itens"}") {
                    if (trash.isNotEmpty()) GIconButton(Icons.Rounded.DeleteForever, "Esvaziar a lixeira", { confirmEmpty = true }, tint = c.danger)
                }
            }
        }
    }
    if (confirmEmpty) {
        GConfirm("Esvaziar a lixeira?", "${trash.size} itens serão apagados para sempre. O Android ainda vai pedir a sua confirmação.", "Esvaziar", onConfirm = {
            confirmEmpty = false
            requests.delete(trash.map { it.uri }) {}
        }, onDismiss = { confirmEmpty = false }, destructive = true)
    }
}
