package app.lily.ai.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lily.ai.core.EngineChoice
import app.lily.ai.core.TaskKind
import app.lily.ai.imagegen.GenState
import app.lily.ai.imagegen.ImageStyle
import app.lily.ai.models.Catalog
import app.lily.ai.models.Compatibility
import app.lily.ai.models.formatBytes
import app.lily.ai.ui.LocalGraph
import app.lily.ai.ui.LocalPermissions
import app.lily.ai.ui.MainActivity
import app.lily.ai.ui.Nav
import app.lily.ai.ui.Route
import app.lily.ai.ui.theme.ButtonKind
import app.lily.ai.ui.theme.Chip
import app.lily.ai.ui.theme.GlassCard
import app.lily.ai.ui.theme.IconButton
import app.lily.ai.ui.theme.Lily
import app.lily.ai.ui.theme.PillButton
import app.lily.ai.ui.theme.Progress
import app.lily.ai.ui.theme.SectionHeader
import app.lily.ai.ui.theme.TopBar
import app.lily.ai.ui.theme.Type
import kotlin.random.Random
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImageGenScreen(nav: Nav, route: Route.ImageGen) {
    val graph = LocalGraph.current
    val c = Lily.colors
    val context = LocalContext.current
    val permissions = LocalPermissions.current
    val gen by graph.imageGen.state.collectAsStateWithLifecycle()
    val store by graph.store.state.collectAsStateWithLifecycle()
    val device = rememberDevice()
    val spec = Catalog.iris
    val installed = remember(store) { graph.store.isInstalled(spec) }
    var prompt by rememberSaveable { mutableStateOf(route.prompt) }
    var style by rememberSaveable { mutableStateOf(ImageStyle.NONE) }
    var seed by rememberSaveable { mutableLongStateOf(Random.nextLong(1, 1_000_000)) }
    val plan = remember(store, device.thermalStatus, device.availableRamBytes / (256L shl 20)) { graph.tools.plan(TaskKind.GENERATE_IMAGE) }
    val gatewayStatus by graph.gateway.lastStatus.collectAsStateWithLifecycle()
    val freeImages = gatewayStatus?.free?.image
    val onlineAvailable = graph.onlineReady() && gatewayStatus?.features?.image == true
    LaunchedEffect(Unit) { if (graph.onlineReady()) runCatching { graph.gateway.status() } }
    // Online grátis vem primeiro quando o serviço está pronto (rápido e sem custo); "no aparelho" continua.
    var useOnline by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val online = (useOnline ?: true) && onlineAvailable

    fun generate(newSeed: Long = seed) {
        seed = newSeed
        if (online) {
            graph.imageGen.startOnline(graph.scope, graph.gateway, prompt.trim(), style, newSeed)
            return
        }
        // A notificação (Live Update na ilha) mostra o progresso fora do app; a geração roda mesmo sem ela.
        permissions.request(MainActivity.NOTIFICATIONS) { graph.imageGen.start(prompt.trim(), style, newSeed) }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar("Gerar imagem", onBack = { nav.back() }, subtitle = if (online) "FLUX.1 [schnell] · online grátis" else "${spec.name} · no aparelho · experimental")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionHeader("Onde gerar")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Online grátis", online, { useOnline = true }, enabled = onlineAvailable)
                Chip("No aparelho", !online, { useOnline = false })
            }
            Text(
                when {
                    online -> "FLUX.1 [schnell] no Workers AI do seu gateway: 1024 × 1024 em segundos, sem custo. " +
                        (freeImages?.let { f -> f.usedToday?.let { "Hoje: $it de ${f.perDay} grátis." } ?: "Até ${f.perDay} por dia." } ?: "")
                    graph.onlineReady() -> "O seu gateway ainda não tem imagens grátis: publique a versão 1.1 ou mais nova (veja docs/GATEWAY.md)."
                    else -> "Para gerar online de graça, conecte o serviço online em Configurações e permita o processamento externo."
                },
                style = Type.caption,
                color = c.tertiary,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp),
            )
            if (!online && !installed) {
                val report = Compatibility.evaluate(spec, device)
                GlassCard(Modifier.fillMaxWidth().padding(top = 8.dp), strong = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Gerador não instalado", style = Type.headline, color = c.content, modifier = Modifier.weight(1f))
                        CompatBadge(report.level)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "O ${spec.name} gera imagens de 768 × 768 inteiramente no aparelho. É experimental: o download tem ${formatBytes(spec.totalBytes)}, " +
                            "cada imagem leva minutos e usa cerca de 3,2 GB de memória.",
                        style = Type.footnote,
                        color = c.secondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    PillButton("Ver no Model Hub", { nav.go(Route.Model(spec.id)) }, kind = ButtonKind.TINTED, height = 40.dp)
                }
            }

            SectionHeader("Descrição")
            GlassCard(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().heightIn(min = 96.dp)) {
                    if (prompt.isEmpty()) Text("Descreva a imagem. Ex.: um farol na praia ao pôr do sol, céu laranja, ondas calmas", style = Type.body, color = c.tertiary)
                    BasicTextField(prompt, { prompt = it }, textStyle = Type.body.copy(color = c.content), cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp))
                }
            }
            Text(if (online) "Descreva em português ou inglês; inglês costuma ser mais fiel." else "Descrições em inglês costumam dar resultados mais fiéis com este modelo.", style = Type.caption, color = c.tertiary, modifier = Modifier.padding(top = 6.dp, start = 4.dp))

            SectionHeader("Estilo")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ImageStyle.entries.forEach { s -> Chip(s.label, style == s, { style = s }) }
            }

            SectionHeader("Formato")
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (online) "1:1 · 1024 × 1024 px" else "1:1 · 768 × 768 px", style = Type.callout, color = c.content)
                        Text("O único tamanho que o modelo gera. Para outra proporção, recorte depois na Gallery.", style = Type.caption, color = c.secondary)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Semente $seed", style = Type.callout, color = c.content)
                        Text("A mesma semente e a mesma descrição repetem a imagem.", style = Type.caption, color = c.secondary)
                    }
                    IconButton(Icons.Rounded.Casino, "Sortear outra semente", { seed = Random.nextLong(1, 1_000_000) })
                }
                Spacer(Modifier.height(10.dp))
                Text("Imagem de referência: ${if (online) "o FLUX.1 [schnell]" else "o ${spec.name}"} não aceita (só descrição em texto).", style = Type.caption, color = c.tertiary)
            }

            Spacer(Modifier.height(16.dp))
            val running = gen as? GenState.Running
            if (online && running == null) {
                PillButton("Gerar grátis", { generate() }, enabled = prompt.isNotBlank())
            } else if (installed && running == null) {
                val blocked = plan.engine as? EngineChoice.Unavailable
                if (blocked != null) Text(blocked.reason, style = Type.footnote, color = c.warning, modifier = Modifier.padding(bottom = 8.dp))
                plan.reasons.firstOrNull { it.startsWith("Memória livre") }?.let { Text(it, style = Type.footnote, color = c.warning, modifier = Modifier.padding(bottom = 8.dp)) }
                PillButton("Gerar", { generate() }, enabled = prompt.isNotBlank() && blocked == null)
            }

            when (val g = gen) {
                is GenState.Running -> RunningCard(g, online) { graph.imageGen.stop() }
                is GenState.Done -> DoneCard(
                    g,
                    onOpen = { openInGallery(context, g.uri) },
                    onShare = { shareImage(context, g.uri) },
                    onEnhance = { nav.go(Route.ImageTool(g.uri, TaskKind.UPSCALE)) },
                    onVariation = { generate(Random.nextLong(1, 1_000_000)) },
                )
                is GenState.Failed -> GlassCard(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                    Text("A geração falhou", style = Type.headline, color = c.danger)
                    Text(g.message, style = Type.footnote, color = c.secondary)
                }
                GenState.Stopped -> Text("Geração interrompida.", style = Type.footnote, color = c.secondary, modifier = Modifier.padding(top = 14.dp))
                GenState.Idle -> Unit
            }
            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }
}

@Composable
private fun RunningCard(g: GenState.Running, online: Boolean, onStop: () -> Unit) {
    val c = Lily.colors
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(g.startedAt) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }
    val elapsed = (now - g.startedAt).coerceAtLeast(0)
    GlassCard(Modifier.fillMaxWidth().padding(top = 14.dp), strong = true) {
        Text(g.stage, style = Type.headline, color = c.content)
        Spacer(Modifier.height(10.dp))
        Progress(g.fraction, indeterminate = g.fraction <= 0f)
        Spacer(Modifier.height(6.dp))
        // Restante calculado pelo próprio progresso medido (sem números inventados).
        val remaining = if (g.fraction > 0.05f) ((elapsed / g.fraction) * (1 - g.fraction)).toLong() else null
        Text(
            "${(g.fraction * 100).toInt()}% · ${seconds(elapsed)}" + (remaining?.let { " · faltam ~${seconds(it)}" } ?: ""),
            style = Type.caption,
            color = c.secondary,
        )
        Spacer(Modifier.height(10.dp))
        Text(if (online) "Leva alguns segundos." else "Pode sair do app: a notificação e a Liquid Island mostram o progresso.", style = Type.caption, color = c.tertiary)
        Spacer(Modifier.height(10.dp))
        PillButton("Parar", onStop, kind = ButtonKind.GLASS, height = 38.dp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DoneCard(g: GenState.Done, onOpen: () -> Unit, onShare: () -> Unit, onEnhance: () -> Unit, onVariation: () -> Unit) {
    val c = Lily.colors
    val bitmap = rememberFileBitmap(g.file.path, 1024)
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), contentDescription = g.prompt, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp)))
        }
        Spacer(Modifier.height(8.dp))
        Text(listOfNotNull("Salva em Pictures/L.I.L.Y", g.source.ifBlank { null }, seconds(g.seconds * 1000), "semente ${g.seed}").joinToString(" · "), style = Type.caption, color = c.secondary)
        g.note?.let { Text(it, style = Type.caption, color = c.tertiary) }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Abrir na Gallery", onOpen, height = 40.dp)
            PillButton("Compartilhar", onShare, kind = ButtonKind.GLASS, height = 40.dp)
            PillButton("Ampliar 4×", onEnhance, kind = ButtonKind.GLASS, height = 40.dp)
            PillButton("Outra variação", onVariation, kind = ButtonKind.GLASS, height = 40.dp)
        }
    }
}

