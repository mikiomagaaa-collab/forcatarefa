package app.lily.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.PhotoSizeSelectLarge
import androidx.compose.material.icons.rounded.Spellcheck
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lily.ai.core.EngineChoice
import app.lily.ai.core.RoutePlan
import app.lily.ai.core.TaskKind
import app.lily.ai.ui.LocalGraph
import app.lily.ai.ui.Nav
import app.lily.ai.ui.Route
import app.lily.ai.ui.theme.GlassCard
import app.lily.ai.ui.theme.Lily
import app.lily.ai.ui.theme.SectionHeader
import app.lily.ai.ui.theme.TopBar
import app.lily.ai.ui.theme.Type

private data class Tool(val task: TaskKind, val icon: ImageVector, val route: Route)

private val textTools = listOf(
    Tool(TaskKind.CORRECT, Icons.Rounded.Spellcheck, Route.TextTool(TaskKind.CORRECT)),
    Tool(TaskKind.REWRITE, Icons.Rounded.Edit, Route.TextTool(TaskKind.REWRITE)),
    Tool(TaskKind.SUMMARIZE, Icons.AutoMirrored.Rounded.Notes, Route.TextTool(TaskKind.SUMMARIZE)),
    Tool(TaskKind.EXPAND, Icons.Rounded.OpenInFull, Route.TextTool(TaskKind.EXPAND)),
    Tool(TaskKind.TRANSLATE, Icons.Rounded.Translate, Route.TextTool(TaskKind.TRANSLATE)),
    Tool(TaskKind.EXPLAIN, Icons.Rounded.Lightbulb, Route.TextTool(TaskKind.EXPLAIN)),
    Tool(TaskKind.ORGANIZE, Icons.Rounded.FormatListBulleted, Route.TextTool(TaskKind.ORGANIZE)),
    Tool(TaskKind.GENERATE_TEXT, Icons.Rounded.AutoAwesome, Route.TextTool(TaskKind.GENERATE_TEXT)),
    Tool(TaskKind.REPLY, Icons.AutoMirrored.Rounded.Reply, Route.TextTool(TaskKind.REPLY)),
)

private val imageTools = listOf(
    Tool(TaskKind.DESCRIBE_IMAGE, Icons.Rounded.ImageSearch, Route.ImageTool(task = TaskKind.DESCRIBE_IMAGE)),
    Tool(TaskKind.OCR, Icons.Rounded.DocumentScanner, Route.ImageTool(task = TaskKind.OCR)),
    Tool(TaskKind.LABELS, Icons.Rounded.Category, Route.ImageTool(task = TaskKind.LABELS)),
    Tool(TaskKind.ENHANCE, Icons.Rounded.AutoFixHigh, Route.ImageTool(task = TaskKind.ENHANCE)),
    Tool(TaskKind.DENOISE, Icons.Rounded.Grain, Route.ImageTool(task = TaskKind.DENOISE)),
    Tool(TaskKind.UPSCALE, Icons.Rounded.PhotoSizeSelectLarge, Route.ImageTool(task = TaskKind.UPSCALE)),
    Tool(TaskKind.REMOVE_BACKGROUND, Icons.Rounded.ContentCut, Route.ImageTool(task = TaskKind.REMOVE_BACKGROUND)),
    Tool(TaskKind.GENERATE_IMAGE, Icons.Rounded.Brush, Route.ImageGen()),
)

private val audioTools = listOf(
    Tool(TaskKind.TRANSCRIBE_AUDIO, Icons.Rounded.GraphicEq, Route.TextTool(TaskKind.TRANSCRIBE_AUDIO)),
    Tool(TaskKind.TRANSCRIBE_MIC, Icons.Rounded.Mic, Route.TextTool(TaskKind.TRANSCRIBE_MIC)),
    Tool(TaskKind.SPEAK, Icons.AutoMirrored.Rounded.VolumeUp, Route.TextTool(TaskKind.SPEAK)),
    Tool(TaskKind.LANGUAGE_ID, Icons.Rounded.Language, Route.TextTool(TaskKind.LANGUAGE_ID)),
)

/** O mecanismo que o roteador usaria agora para a tarefa (o mesmo que vai rodar). */
fun engineLabel(plan: RoutePlan): Pair<String, Boolean> = when (val e = plan.engine) {
    is EngineChoice.Llm -> "${e.model.name} · ${e.accel.label}" to true
    is EngineChoice.SuperResolution -> e.model.name to true
    is EngineChoice.ImageGen -> "${e.model.name} · experimental" to true
    EngineChoice.Ocr, EngineChoice.Labels, EngineChoice.Subject, EngineChoice.LanguageId -> "ML Kit" to true
    EngineChoice.Translator -> "Tradução do ML Kit" to true
    EngineChoice.Classic -> "Algoritmo clássico" to true
    EngineChoice.SpeechInput -> "Reconhecimento do Android" to true
    EngineChoice.Voice -> "Voz do Android" to true
    is EngineChoice.Remote -> "${e.model.label} · online" to true
    is EngineChoice.Unavailable -> "Precisa de um modelo" to false
}

@Composable
fun ToolsScreen(nav: Nav) {
    val graph = LocalGraph.current
    val store by graph.store.state.collectAsStateWithLifecycle()
    val settings by graph.settings.value.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TopBar("Ferramentas", onBack = { nav.back() }, subtitle = "Cada uma mostra o mecanismo que vai usar")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            listOf("Texto" to textTools, "Imagem" to imageTools, "Áudio" to audioTools).forEach { (title, tools) ->
                SectionHeader(title)
                tools.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { t ->
                            val plan = remember(store, settings.profile, t.task) { graph.tools.plan(t.task) }
                            ToolCard(t, engineLabel(plan), Modifier.weight(1f)) { nav.go(t.route) }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
            Spacer(Modifier.height(16.dp).navigationBarsPadding())
        }
    }
}

@Composable
private fun ToolCard(t: Tool, engine: Pair<String, Boolean>, modifier: Modifier, onClick: () -> Unit) {
    val c = Lily.colors
    GlassCard(modifier, onClick = onClick, padding = 14.dp) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(c.accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(t.icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(t.task.label, style = Type.callout, color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(engine.first, style = Type.caption, color = if (engine.second) c.secondary else c.warning, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
