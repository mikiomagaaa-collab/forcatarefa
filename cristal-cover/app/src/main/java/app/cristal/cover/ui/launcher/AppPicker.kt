package app.cristal.cover.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cristal.cover.data.apps.AppInfo
import app.cristal.cover.ui.components.AppIconImage
import app.cristal.cover.ui.components.GlassTextField
import app.cristal.cover.ui.glass.rememberHaptic

/**
 * Lista de apps para adicionar à tela inicial com um toque. Os que já estão na tela aparecem
 * marcados.
 */
@Composable
fun AppPickerDialog(
    apps: List<AppInfo>,
    onHome: Set<String>,
    onAdd: (AppInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) { filterApps(apps, query) }
    val haptic = rememberHaptic()
    GlassDialog(onDismiss = onDismiss) {
        DialogTitle("Adicionar apps")
        DialogText("Toque em um app para colocá-lo na página atual.")
        GlassTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Buscar",
            leadingIcon = Icons.Rounded.Search,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        )
        if (filtered.isEmpty()) DialogText("Nenhum app encontrado.")
        for (row in filtered.chunked(4)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                for (app in row) {
                    val added = app.key in onHome
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                haptic()
                                onAdd(app)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            AppIconImage(app = app, size = 46.dp)
                            if (added) {
                                Box(
                                    Modifier.size(18.dp).clip(CircleShape).background(Color(0xFF34C759)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Rounded.Check, contentDescription = "Já está na tela", tint = Color.White, modifier = Modifier.size(13.dp))
                                }
                            }
                        }
                        IconLabel(app.label, Modifier.padding(top = 3.dp, start = 2.dp, end = 2.dp).fillMaxWidth(), color = LocalContentColor.current)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        GlassButton("Concluir", Modifier.fillMaxWidth().padding(top = 8.dp), primary = true, onClick = onDismiss)
    }
}
