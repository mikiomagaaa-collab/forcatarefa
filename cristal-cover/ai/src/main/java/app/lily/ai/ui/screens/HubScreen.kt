package app.lily.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lily.ai.core.DeviceSnapshot
import app.lily.ai.core.Profile
import app.lily.ai.engine.EngineStatus
import app.lily.ai.models.Catalog
import app.lily.ai.models.Category
import app.lily.ai.models.Compatibility
import app.lily.ai.models.DlState
import app.lily.ai.models.InstallEvent
import app.lily.ai.models.ModelSpec
import app.lily.ai.models.Runtime
import app.lily.ai.models.StoreState
import app.lily.ai.models.formatBytes
import app.lily.ai.ui.LocalGraph
import app.lily.ai.ui.LocalPermissions
import app.lily.ai.ui.MainActivity
import app.lily.ai.ui.Nav
import app.lily.ai.ui.Route
import app.lily.ai.ui.theme.ButtonKind
import app.lily.ai.ui.theme.Chip
import app.lily.ai.ui.theme.ConfirmDialog
import app.lily.ai.ui.theme.GlassCard
import app.lily.ai.ui.theme.IconButton
import app.lily.ai.ui.theme.Lily
import app.lily.ai.ui.theme.PillButton
import app.lily.ai.ui.theme.Progress
import app.lily.ai.ui.theme.SectionHeader
import app.lily.ai.ui.theme.TopBar
import app.lily.ai.ui.theme.Type
import java.util.Locale

private val PT = Locale.forLanguageTag("pt-BR")

@Composable
fun HubScreen(nav: Nav, route: Route.Hub) {
    val graph = LocalGraph.current
    val c = Lily.colors
    val device = rememberDevice()
    val store by graph.store.state.collectAsStateWithLifecycle()
    val downloads by graph.downloads.states.collectAsStateWithLifecycle()
    val engine by graph.engines.status.collectAsStateWithLifecycle()
    val settings by graph.settings.value.collectAsStateWithLifecycle()
    var category by remember { mutableStateOf(route.category?.let { n -> Category.entries.firstOrNull { it.name == n } }) }
    var installedOnly by remember { mutableStateOf(false) }
    var allHistory by remember { mutableStateOf(false) }
    val installed = remember(store) { graph.store.installedIds() }
    val download = rememberDownloadStarter()
    val models = Catalog.all.filter { (category == null || category in it.categories) && (!installedOnly || it.id in installed) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item { TopBar("Model Hub", onBack = { nav.back() }, subtitle = "Modelos que rodam no aparelho, sem enviar seus dados") }
        item { DeviceCard(device, settings.profile) { p -> graph.settings.update { it.copy(profile = p) } } }
        item { EngineCard(engine) { graph.engines.unload() } }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                item { Chip("Todos", category == null && !installedOnly, { category = null; installedOnly = false }) }
                item { Chip("Instalados", installedOnly, { installedOnly = !installedOnly }) }
                items(Category.entries) { cat -> Chip(cat.label, category == cat, { category = if (category == cat) null else cat }) }
            }
        }
        items(models, key = { it.id }) { m ->
            ModelCard(
                spec = m,
                device = device,
                installed = m.id in installed,
                store = store,
                state = downloads[m.id],
                pausedBytes = remember(m.id, downloads[m.id], store) { graph.downloads.pausedOnDisk(m) },
                onOpen = { nav.go(Route.Model(m.id)) },
                onDownload = { download(m) },
                onPause = { graph.downloads.pause(m.id) },
                onCancel = { graph.downloads.cancel(m) },
            )
        }
        if (models.isEmpty()) {
            item { Text("Nenhum modelo nesta seleção.", style = Type.callout, color = c.secondary, modifier = Modifier.padding(24.dp)) }
        }
        if (store.history.isNotEmpty()) {
            item {
                SectionHeader("Histórico de instalações", Modifier.padding(horizontal = 16.dp), action = if (allHistory) "Menos" else "Ver tudo", onAction = { allHistory = !allHistory })
            }
            items(if (allHistory) store.history else store.history.take(5)) { e -> HistoryRow(e) }
        }
        item {
            Text(
                "Os modelos vêm das páginas oficiais no Hugging Face, fixados numa versão e conferidos por SHA-256 depois do download. " +
                    "A internet só é usada para baixar; as respostas são geradas no aparelho.",
                style = Type.caption,
                color = c.tertiary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp).navigationBarsPadding(),
            )
        }
    }
}

/** Leitura real do aparelho e o perfil do roteador. */
@Composable
private fun DeviceCard(d: DeviceSnapshot, profile: Profile, onProfile: (Profile) -> Unit) {
    val c = Lily.colors
    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), strong = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Este aparelho", style = Type.headline, color = c.content, modifier = Modifier.weight(1f))
            Text(d.soc, style = Type.footnote, color = c.secondary)
        }
        Spacer(Modifier.height(10.dp))
        val stats = listOf(
            "Memória" to String.format(PT, "%.1f de %.1f GB livres", d.availableRamGb, d.totalRamGb),
            "Armazenamento" to "${formatBytes(d.freeStorageBytes)} livres",
            "Temperatura" to d.thermalLabel,
            "Bateria" to if (d.batteryPercent >= 0) "${d.batteryPercent}%${if (d.charging) " · carregando" else ""}" else "—",
            "GPU (OpenCL)" to if (d.gpuCompute) "Disponível" else "Indisponível",
            "Processador" to "${d.cpuCores} núcleos · ${if (d.arm64) "ARM 64 bits" else "sem ARM 64"}",
        )
        stats.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                row.forEach { (label, value) ->
                    Column(Modifier.weight(1f)) {
                        Text(label, style = Type.caption, color = c.tertiary)
                        Text(value, style = Type.subhead, color = c.content)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Perfil do roteador", style = Type.caption, color = c.tertiary)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Profile.entries.forEach { p -> Chip(p.label, profile == p, { onProfile(p) }) }
        }
        Spacer(Modifier.height(6.dp))
        Text(profile.description, style = Type.footnote, color = c.secondary)
        if (d.shouldSave && profile != Profile.ECO) {
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    d.powerSave -> "Economia de energia ligada: o roteador está usando o Eco agora."
                    d.thermalStatus >= android.os.PowerManager.THERMAL_STATUS_SEVERE -> "Aparelho quente: o roteador está usando o Eco até esfriar."
                    else -> "Bateria baixa: o roteador está usando o Eco agora."
                },
                style = Type.footnote,
                color = c.warning,
            )
        }
    }
}

/** O que está na memória agora (no máximo um modelo de linguagem por vez). */
@Composable
private fun EngineCard(status: EngineStatus, onUnload: () -> Unit) {
    val c = Lily.colors
    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Na memória agora", style = Type.caption, color = c.tertiary)
                Text(
                    when (status) {
                        EngineStatus.Idle -> "Nenhum modelo carregado"
                        is EngineStatus.Loading -> "Carregando ${status.model.name} (${status.accel.label})…"
                        is EngineStatus.Ready -> "${status.model.name} · ${status.accel.label} · carregou em ${seconds(status.loadMillis)}"
                        is EngineStatus.Busy -> "${status.model.name} · ${status.accel.label} · respondendo"
                    },
                    style = Type.subhead,
                    color = c.content,
                )
            }
            if (status is EngineStatus.Ready) PillButton("Descarregar", onUnload, kind = ButtonKind.GLASS, height = 36.dp)
        }
    }
}

@Composable
fun CompatBadge(level: app.lily.ai.models.Compat, modifier: Modifier = Modifier) {
    val c = Lily.colors
    val color = compatColor(level, c)
    Row(modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(level.label, style = Type.caption, color = color, maxLines = 1)
    }
}

@Composable
private fun ModelCard(
    spec: ModelSpec,
    device: DeviceSnapshot,
    installed: Boolean,
    store: StoreState,
    state: DlState?,
    pausedBytes: Long,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onCancel: () -> Unit,
) {
    val c = Lily.colors
    val report = Compatibility.evaluate(spec, device)
    val defaults = store.defaults.filterValues { it == spec.id }.keys.mapNotNull { k -> Category.entries.firstOrNull { it.name == k }?.label }
    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(spec.name, style = Type.headline, color = c.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(spec.developer.substringBefore(" ·"), if (spec.totalBytes > 0) formatBytes(spec.totalBytes) else null, spec.runtime.label.substringBefore(" (")).joinToString(" · "),
                    style = Type.caption,
                    color = c.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            CompatBadge(report.level)
        }
        Spacer(Modifier.height(8.dp))
        Text(spec.description, style = Type.subhead, color = c.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        ModelStateRow(spec, report.usable, installed, defaults, store, state, pausedBytes, device, onDownload, onPause, onCancel, compact = true)
    }
}

/** O estado do modelo e as ações reais: baixar, pausar, retomar, cancelar ou o motivo de não poder. */
@Composable
fun ModelStateRow(
    spec: ModelSpec,
    usable: Boolean,
    installed: Boolean,
    defaults: List<String>,
    store: StoreState,
    state: DlState?,
    pausedBytes: Long,
    device: DeviceSnapshot,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onCancel: () -> Unit,
    compact: Boolean,
) {
    val c = Lily.colors
    val update = installed && store.installed.firstOrNull { it.id == spec.id }?.revision?.let { it != spec.revision } == true
    when {
        spec.builtIn -> Tag(if (spec.runtime == Runtime.ANDROID) "Do sistema Android" else "Incluído no app", c.success)
        spec.repo == null -> Tag("Pacotes por idioma · abra para gerenciar", c.accent2)
        state is DlState.Running -> {
            val p = state.progress
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (p.verifying) "Conferindo a integridade…" else "${(p.fraction * 100).toInt()}% · ${formatBytes(p.done)} de ${formatBytes(p.total)}" +
                            (if (p.bytesPerSecond > 0) " · ${formatBytes(p.bytesPerSecond)}/s" + eta(p.total - p.done, p.bytesPerSecond) else ""),
                        style = Type.caption,
                        color = c.secondary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Progress(p.fraction, indeterminate = p.verifying)
                }
                Spacer(Modifier.width(8.dp))
                IconButton(Icons.Rounded.Pause, "Pausar o download", onPause, size = 36.dp)
                Spacer(Modifier.width(6.dp))
                IconButton(Icons.Rounded.Close, "Cancelar o download", onCancel, size = 36.dp)
            }
        }
        state == DlState.Queued -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Na fila para baixar", style = Type.caption, color = c.secondary, modifier = Modifier.weight(1f))
            IconButton(Icons.Rounded.Close, "Cancelar", onCancel, size = 36.dp)
        }
        state is DlState.Failed -> Column {
            Text("Falhou: ${state.message}", style = Type.caption, color = c.danger)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Tentar de novo", onDownload, kind = ButtonKind.TINTED, height = 36.dp)
                PillButton("Descartar", onCancel, kind = ButtonKind.GLASS, height = 36.dp)
            }
        }
        update -> Row(verticalAlignment = Alignment.CenterVertically) {
            Tag("Instalado · nova versão disponível", c.warning, Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(8.dp))
            PillButton("Atualizar", onDownload, kind = ButtonKind.TINTED, height = 36.dp)
        }
        installed -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tag("Instalado", c.success)
            defaults.take(if (compact) 2 else 6).forEach { Tag("Padrão: $it", c.accent) }
        }
        state is DlState.Paused || pausedBytes > 0 -> Row(verticalAlignment = Alignment.CenterVertically) {
            val done = (state as? DlState.Paused)?.done ?: pausedBytes
            Text("Pausado em ${formatBytes(done)} de ${formatBytes(spec.totalBytes)}", style = Type.caption, color = c.secondary, modifier = Modifier.weight(1f))
            IconButton(Icons.Rounded.PlayArrow, "Retomar o download", onDownload, size = 36.dp)
            Spacer(Modifier.width(6.dp))
            IconButton(Icons.Rounded.Close, "Cancelar e apagar as partes", onCancel, size = 36.dp)
        }
        !usable -> Text("Não roda neste aparelho.", style = Type.caption, color = c.danger)
        else -> {
            val missing = Compatibility.missingStorage(spec, device)
            if (missing > 0) {
                Text("Falta espaço: libere ${formatBytes(missing)} para instalar (${formatBytes(spec.totalBytes)} + 1 GB de folga).", style = Type.caption, color = c.warning)
            } else {
                PillButton("Baixar · ${formatBytes(spec.totalBytes)}", onDownload, kind = ButtonKind.TINTED, height = 38.dp)
            }
        }
    }
}

@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = Type.caption.copy(fontWeight = FontWeight.SemiBold),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.clip(CircleShape).background(color.copy(alpha = 0.13f)).padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun HistoryRow(e: InstallEvent) {
    val c = Lily.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (e.action == "Falhou") c.danger else if (e.action == "Desinstalado") c.tertiary else c.success))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("${e.action}: ${e.name}", style = Type.subhead, color = c.content)
            if (e.detail.isNotBlank()) Text(e.detail, style = Type.caption, color = c.tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(whenText(e.at), style = Type.caption, color = c.tertiary)
    }
}

private fun eta(remaining: Long, speed: Long): String {
    if (speed <= 0) return ""
    val s = remaining / speed
    return when {
        s < 60 -> " · menos de 1 min"
        s < 3600 -> " · ~${s / 60} min"
        else -> " · ~${s / 3600} h ${(s % 3600) / 60} min"
    }
}

/**
 * Começa um download com as verificações reais: espaço livre, permissão da notificação (o
 * progresso aparece como Live Update na Liquid Island) e "só Wi-Fi".
 */
@Composable
fun rememberDownloadStarter(): (ModelSpec) -> Unit {
    val graph = LocalGraph.current
    val permissions = LocalPermissions.current
    var askData by remember { mutableStateOf<ModelSpec?>(null) }
    var noSpace by remember { mutableStateOf<Pair<ModelSpec, Long>?>(null) }
    askData?.let { spec ->
        ConfirmDialog(
            "Sem Wi-Fi",
            "O download de ${spec.name} (${formatBytes(spec.totalBytes - graph.store.bytesOnDisk(spec))}) vai usar os dados móveis. Baixar mesmo assim?",
            "Baixar",
            onConfirm = {
                askData = null
                graph.downloads.start(spec)
            },
            onDismiss = { askData = null },
        )
    }
    noSpace?.let { (spec, missing) ->
        ConfirmDialog(
            "Falta espaço",
            "Para instalar ${spec.name} é preciso liberar ${formatBytes(missing)} (o modelo ocupa ${formatBytes(spec.totalBytes)} e o app deixa 1 GB livre para o sistema).",
            "Entendi",
            onConfirm = { noSpace = null },
            onDismiss = { noSpace = null },
        )
    }
    return remember(graph) {
        { spec: ModelSpec ->
            val device = graph.device.snapshot()
            val missing = Compatibility.missingStorage(spec, device, graph.store.bytesOnDisk(spec))
            if (missing > 0) {
                noSpace = spec to missing
            } else {
                permissions.request(MainActivity.NOTIFICATIONS) {
                    if (graph.settings.value.value.wifiOnly && Network.metered(graph.app)) askData = spec else graph.downloads.start(spec)
                }
            }
        }
    }
}
