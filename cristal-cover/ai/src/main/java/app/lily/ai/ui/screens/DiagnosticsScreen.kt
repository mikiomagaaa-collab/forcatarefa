package app.lily.ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lily.ai.AiGraph
import app.lily.ai.diagnostics.DiagState
import app.lily.ai.diagnostics.Diagnostics
import app.lily.ai.ui.LocalGraph
import app.lily.ai.ui.Nav
import app.lily.ai.ui.theme.ButtonKind
import app.lily.ai.ui.theme.GlassCard
import app.lily.ai.ui.theme.Lily
import app.lily.ai.ui.theme.PillButton
import app.lily.ai.ui.theme.Progress
import app.lily.ai.ui.theme.SettingRow
import app.lily.ai.ui.theme.Toggle
import app.lily.ai.ui.theme.TopBar
import app.lily.ai.ui.theme.Type
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** O teste em andamento (vive enquanto a tela estiver na pilha; sair dela interrompe). */
@Stable
class DiagnosticsSession(private val graph: AiGraph, context: android.content.Context) {
    val diagnostics = Diagnostics(graph, context)
    var includeGpu by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        error = null
        job = graph.scope.launch {
            try {
                diagnostics.run(includeGpu)
            } catch (c: CancellationException) {
                diagnostics.reset()
            } catch (t: Throwable) {
                error = t.message ?: t.javaClass.simpleName
                diagnostics.reset()
            }
        }
    }

    fun stop() {
        job?.cancel()
    }
}

@Composable
fun DiagnosticsScreen(nav: Nav, key: Int) {
    val graph = LocalGraph.current
    val c = Lily.colors
    val context = LocalContext.current
    val session = remember(key) { nav.retain(key, { DiagnosticsSession(graph, context.applicationContext) }, DiagnosticsSession::stop) }
    val state by session.diagnostics.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TopBar("Testar este aparelho", onBack = { nav.back() }, subtitle = "Medições reais, sem estimativas")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(
                    "Roda cada modelo de conversa instalado (na GPU e na CPU), o reconhecimento de texto, a ampliação e a voz, e mede carregamento, " +
                        "velocidade, memória e temperatura. Leva de 1 a 5 minutos e o aparelho pode esquentar um pouco. Feche outros apps antes.",
                    style = Type.callout,
                    color = c.content,
                )
                Spacer(Modifier.height(8.dp))
                SettingRow("Testar a GPU também", "Mede os modelos na GPU (OpenCL) além da CPU", trailing = {
                    Toggle(session.includeGpu, { session.includeGpu = it }, enabled = state !is DiagState.Running)
                })
            }
            Spacer(Modifier.height(14.dp))
            when (val s = state) {
                DiagState.Idle -> PillButton("Começar o teste", session::start)
                is DiagState.Running -> GlassCard(Modifier.fillMaxWidth(), strong = true) {
                    Text(s.step, style = Type.headline, color = c.content)
                    Spacer(Modifier.height(10.dp))
                    Progress(s.fraction)
                    Spacer(Modifier.height(10.dp))
                    PillButton("Parar", session::stop, kind = ButtonKind.GLASS, height = 38.dp)
                }
                is DiagState.Done -> {
                    val text = remember(s.report) { s.report.text() }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PillButton("Copiar o relatório", { copyToClipboard(context, text) }, height = 40.dp)
                        PillButton("Compartilhar", { shareText(context, text) }, kind = ButtonKind.GLASS, height = 40.dp)
                    }
                    Spacer(Modifier.height(12.dp))
                    GlassCard(Modifier.fillMaxWidth()) {
                        SelectionContainer {
                            Text(text, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp), color = c.content)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    PillButton("Testar de novo", session::start, kind = ButtonKind.GLASS, height = 40.dp)
                }
            }
            session.error?.let { Text("O teste parou: $it", style = Type.footnote, color = c.danger, modifier = Modifier.padding(top = 10.dp)) }
            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }
}
