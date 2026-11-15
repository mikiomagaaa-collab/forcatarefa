package app.cristal.cover.ui.widgets

import android.appwidget.AppWidgetHostView
import android.os.Bundle
import android.util.SizeF
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.cristal.cover.core.safely
import app.cristal.cover.data.widgets.WidgetHostManager
import app.cristal.cover.ui.glass.GlassSurface

/** Host de widgets de outros apps disponível nesta árvore (nulo em prévias e testes). */
val LocalWidgetHost = staticCompositionLocalOf<WidgetHostManager?> { null }

/**
 * Mostra um widget de outro app. Como a view do widget fica com os toques para si, o menu
 * (remover) abre pelo botão de três pontos no canto.
 */
@Composable
fun AppWidgetCell(appWidgetId: Int, modifier: Modifier = Modifier, onMenu: () -> Unit) {
    val host = LocalWidgetHost.current
    val density = LocalDensity.current
    val available = remember(appWidgetId, host) { host?.infoOf(appWidgetId) != null }
    var size by remember { mutableStateOf(IntSize.Zero) }
    Box(modifier) {
        if (host == null || !available) {
            GlassSurface(modifier = Modifier.fillMaxSize(), cornerRadius = 24.dp, reactToTouch = false) {
                Text(
                    "Widget indisponível. Toque nos três pontos para remover.",
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Center).padding(14.dp),
                )
            }
        } else {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .onSizeChanged { size = it },
                factory = { context -> host.createView(context, appWidgetId) ?: View(context) },
                update = { view ->
                    if (view is AppWidgetHostView && size.width > 0 && size.height > 0) {
                        safely("informar tamanho ao widget") {
                            val w = size.width / density.density
                            val h = size.height / density.density
                            view.updateAppWidgetSize(Bundle(), listOf(SizeF(w, h)))
                        }
                    }
                },
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(5.dp)
                .size(26.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.38f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onMenu),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "Opções do widget", tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}
