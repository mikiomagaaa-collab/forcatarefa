package app.lily.ai.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lily.ai.diagnostics.CrashReport
import app.lily.ai.ui.LocalGraph
import app.lily.ai.ui.theme.Lily
import app.lily.ai.ui.theme.glass

/**
 * Aviso no topo quando a L.I.L.Y abriu em modo seguro ou sem algum módulo opcional: diz o que
 * ficou de fora e leva ao diagnóstico. Some com "Ok" (até a próxima abertura).
 */
@Composable
fun ModuleNotice(compact: Boolean) {
    val graph = LocalGraph.current
    val failures = graph.failures
    if (!graph.safeMode && failures.isEmpty()) return
    var visible by rememberSaveable { mutableStateOf(true) }
    val context = LocalContext.current
    val c = Lily.colors
    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = if (compact) 6.dp else 10.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(visible, enter = fadeIn() + slideInVertically { -it / 2 }, exit = fadeOut() + slideOutVertically { -it / 2 }) {
            Column(
                Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .glass(c, RoundedCornerShape(22.dp), strong = true)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(c.warning))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (graph.safeMode) "Modo seguro" else "Abriu sem algumas partes",
                        color = c.content,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                val body = buildString {
                    if (graph.safeMode) append("A L.I.L.Y fechou ao abrir nas últimas vezes; agora ela abriu sem os efeitos mais pesados. ")
                    if (failures.isNotEmpty()) append("Indisponível nesta abertura: ").append(failures.joinToString { it.module }).append('.')
                }
                Text(body.trim(), color = c.secondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) {
                    NoticeButton("Ver diagnóstico", c.content) { context.startActivity(CrashReport.intent(context)) }
                    Spacer(Modifier.size(8.dp))
                    NoticeButton("Ok", c.accent) { visible = false }
                }
            }
        }
    }
}

@Composable
private fun NoticeButton(label: String, color: Color, onClick: () -> Unit) {
    Text(
        label,
        color = color,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
