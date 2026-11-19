package app.lily.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Primeira vez: explica e só pede o acesso às fotos depois do toque. */
@Composable
fun PermissionScreen() {
    val c = G.colors
    val requests = LocalRequests.current
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(88.dp).clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(c.accent, c.accent2))), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("L.I.L.Y Gallery", style = GType.display, color = c.content)
        Spacer(Modifier.height(8.dp))
        Text(
            "Para mostrar suas fotos e vídeos, a Gallery precisa de acesso à mídia do aparelho. Você pode liberar tudo ou só algumas fotos. Nada sai do aparelho.",
            style = GType.callout,
            color = c.secondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))
        GPill("Permitir acesso às fotos", { requests.requestMedia() })
    }
}
