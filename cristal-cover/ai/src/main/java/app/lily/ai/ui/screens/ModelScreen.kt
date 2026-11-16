package app.lily.ai.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lily.ai.core.TaskKind
import app.lily.ai.engine.EngineStatus
import app.lily.ai.engine.SpeechInput
import app.lily.ai.engine.Translator
import app.lily.ai.imagegen.GenState
import app.lily.ai.models.Catalog
import app.lily.ai.models.Category
import app.lily.ai.models.Compatibility
import app.lily.ai.models.Runtime
import app.lily.ai.models.formatBytes
import app.lily.ai.ui.LocalGraph
import app.lily.ai.ui.Nav
import app.lily.ai.ui.Route
import app.lily.ai.ui.theme.ButtonKind
import app.lily.ai.ui.theme.Chip
import app.lily.ai.ui.theme.ConfirmDialog
import app.lily.ai.ui.theme.EmptyState
import app.lily.ai.ui.theme.GlassCard
import app.lily.ai.ui.theme.Lily
import app.lily.ai.ui.theme.PillButton
import app.lily.ai.ui.theme.SectionHeader
import app.lily.ai.ui.theme.TopBar
import app.lily.ai.ui.theme.Type
import java.util.Locale
import kotlinx.coroutines.launch

private val PT = Locale.forLanguageTag("pt-BR")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelScreen(nav: Nav, id: String) {
    val graph = LocalGraph.current
    val c = Lily.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val spec = Catalog.byId(id)
    if (spec == null) {
        Column(Modifier.fillMaxSize()) {
            TopBar("Modelo", onBack = { nav.back() })
            EmptyState(Icons.Rounded.HelpOutline, "Modelo desconhecido", "Este modelo não está no catálogo desta versão.")
        }
        return
    }
    val device = rememberDevice()
    val store by graph.store.state.collectAsStateWithLifecycle()
    val downloads by graph.downloads.states.collectAsStateWithLifecycle()
    val engine by graph.engines.status.collectAsStateWithLifecycle()
    val gen by graph.imageGen.state.collectAsStateWithLifecycle()
    val perf by graph.perf.items.collectAsStateWithLifecycle()
    val report = Compatibility.evaluate(spec, device)
    val installed = remember(store) { graph.store.isInstalled(spec) }
    val download = rememberDownloadStarter()
    var confirmUninstall by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(spec.name, onBack = { nav.back() }, subtitle = spec.developer)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            GlassCard(Modifier.fillMaxWidth(), strong = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (spec.totalBytes > 0) formatBytes(spec.totalBytes) else spec.runtime.label, style = Type.title, color = c.content, modifier = Modifier.weight(1f))
                    CompatBadge(report.level)
                }
                Spacer(Modifier.height(8.dp))
                report.reasons.forEach { r -> Text("• $r", style = Type.footnote, color = c.secondary, modifier = Modifier.padding(vertical = 1.dp)) }
                Spacer(Modifier.height(12.dp))
                val defaults = store.defaults.filterValues { it == spec.id }.keys.mapNotNull { k -> Category.entries.firstOrNull { it.name == k }?.label }
                ModelStateRow(
                    spec, report.usable, installed, defaults, store, downloads[spec.id],
                    remember(downloads[spec.id], store) { graph.downloads.pausedOnDisk(spec) },
                    device,
                    onDownload = { download(spec) },
                    onPause = { graph.downloads.pause(spec.id) },
                    onCancel = { graph.downloads.cancel(spec) },
                    compact = false,
                )
                if (installed || spec.builtIn) {
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        tryRoute(spec.id)?.let { r -> PillButton("Testar agora", { nav.go(r) }, height = 40.dp) }
                        if (installed && !spec.builtIn && spec.repo != null) {
                            PillButton("Desinstalar", { confirmUninstall = true }, kind = ButtonKind.GLASS, height = 40.dp)
                        }
                    }
                }
                notice?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = Type.footnote, color = c.warning)
                }
            }

            // Padrão por categoria (o roteador usa quando o modelo cabe na memória).
            val routable = spec.categories.filter { it == Category.CHAT || it == Category.VISION || it == Category.SPEECH }
            if (installed && spec.runtime == Runtime.LITERT_LM && routable.isNotEmpty()) {
                SectionHeader("Usar como padrão")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    routable.forEach { cat ->
                        val isDefault = store.defaults[cat.name] == spec.id
                        Chip(cat.label, isDefault, { if (!isDefault) graph.store.setDefault(cat, spec) })
                    }
                }
                Text("O padrão vale no perfil Equilibrado e no Qualidade; o Eco e a falta de memória podem escolher um menor.", style = Type.caption, color = c.tertiary, modifier = Modifier.padding(top = 6.dp))
            }

            SectionHeader("Sobre")
            Text(spec.description, style = Type.callout, color = c.content)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                spec.purposes.forEach { Tag(it, c.accent) }
            }

            SectionHeader("Ficha técnica")
            GlassCard(Modifier.fillMaxWidth()) {
                Spec("Desenvolvedor", spec.developer)
                Spec("Arquitetura", spec.architecture)
                Spec("Parâmetros", spec.parameters)
                Spec("Quantização", spec.quantization)
                Spec("Entende", spec.modalities.joinToString(", ") { it.label })
                Spec("Categorias", spec.categories.joinToString(", ") { it.label })
                if (spec.totalBytes > 0) {
                    Spec("Download", "${formatBytes(spec.totalBytes)} em ${spec.files.size} ${if (spec.files.size == 1) "arquivo" else "arquivos"}")
                    Spec("Espaço necessário", "${formatBytes(spec.totalBytes + Compatibility.RESERVE)} (com 1 GB de folga) · livre: ${formatBytes(device.freeStorageBytes)}")
                }
                Spec("Memória", String.format(PT, "Aparelhos de %d GB de RAM ou mais · este informa %.1f GB", spec.ramClassGb, device.totalRamGb))
                spec.contextTokens?.let { Spec("Contexto", "$it tokens") }
                Spec("Mecanismo", spec.runtime.label + if (spec.needsGpu) " · precisa da GPU" else "")
                Spec("Internet", spec.connection)
                Spec("Licença", spec.license, link = spec.licenseUrl?.let { url -> { open(url) } })
                spec.revision?.let { rev ->
                    val mine = store.installed.firstOrNull { it.id == spec.id }?.revision
                    Spec("Versão", rev.take(7) + when {
                        mine == null -> ""
                        mine == rev -> " (instalada)"
                        else -> " · instalada: ${mine.take(7)}"
                    })
                }
                spec.pageUrl?.let { url -> Spec("Página oficial", url.removePrefix("https://"), link = { open(url) }) }
            }

            SectionHeader("Medido neste aparelho")
            val p = perf[spec.id]
            GlassCard(Modifier.fillMaxWidth()) {
                if (p == null || (p.loads == 0 && p.answers == 0)) {
                    Text("Ainda não foi usado aqui. Os números aparecem depois do primeiro uso (nada é estimado).", style = Type.footnote, color = c.secondary)
                } else {
                    if (p.loads > 0) Spec("Carregamento", "${seconds(p.lastLoadMillis)} na ${p.lastAccel} (${p.loads} ${if (p.loads == 1) "vez" else "vezes"})")
                    if (p.answers > 0) Spec("Velocidade", String.format(PT, "~%.0f caracteres por segundo (média de %d respostas)", p.charsPerSecond, p.answers))
                    Spec("Atualizado", whenText(p.updatedAt))
                }
                (engine as? EngineStatus.Ready)?.takeIf { it.model.id == spec.id }?.let {
                    Spacer(Modifier.height(6.dp))
                    Text("Carregado agora na ${it.accel.label}.", style = Type.footnote, color = c.success)
                }
            }

            spec.published?.let {
                SectionHeader("Publicado pelo desenvolvedor")
                Text(it, style = Type.footnote, color = c.secondary)
                Text("Números de outros aparelhos, divulgados na página oficial; não foram medidos neste.", style = Type.caption, color = c.tertiary, modifier = Modifier.padding(top = 4.dp))
            }
            spec.notes?.let {
                SectionHeader("Observações")
                Text(it, style = Type.footnote, color = c.secondary)
            }

            when (spec.id) {
                Catalog.mlkitTranslate.id -> TranslationPacks()
                Catalog.androidStt.id -> {
                    SectionHeader("Neste aparelho")
                    val onDevice = remember { SpeechInput.onDevice(context) }
                    Text(
                        if (onDevice) "O reconhecimento no aparelho está disponível." else "O reconhecimento no aparelho não está disponível; o Android pode usar a rede do serviço de voz para ditar.",
                        style = Type.footnote,
                        color = if (onDevice) c.success else c.warning,
                    )
                    Spacer(Modifier.height(8.dp))
                    PillButton("Configurações de voz do Android", {
                        runCatching { context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }, kind = ButtonKind.GLASS, height = 40.dp)
                }
                Catalog.androidTts.id -> {
                    SectionHeader("Neste aparelho")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton("Ouvir a voz", { graph.speak("Oi! Eu sou a L.I.L.Y. É assim que eu leio as respostas.") }, kind = ButtonKind.TINTED, height = 40.dp)
                        PillButton("Vozes do Android", {
                            runCatching { context.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }, kind = ButtonKind.GLASS, height = 40.dp)
                    }
                }
            }

            val events = store.history.filter { it.id == spec.id }
            if (events.isNotEmpty()) {
                SectionHeader("Histórico")
                events.take(10).forEach { e ->
                    Text("${e.action} · ${whenText(e.at)}${if (e.detail.isNotBlank()) " · ${e.detail}" else ""}", style = Type.footnote, color = c.secondary, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }

    if (confirmUninstall) {
        ConfirmDialog(
            "Desinstalar ${spec.name}?",
            "Os arquivos (${formatBytes(spec.totalBytes)}) saem do aparelho. Para usar de novo, será preciso baixar outra vez.",
            "Desinstalar",
            onConfirm = {
                confirmUninstall = false
                if (spec.id == Catalog.iris.id && gen is GenState.Running) {
                    notice = "Pare a geração de imagem antes de desinstalar."
                } else {
                    scope.launch {
                        val loaded = (graph.engines.status.value as? EngineStatus.Ready)?.model?.id ?: (graph.engines.status.value as? EngineStatus.Busy)?.model?.id
                        if (loaded == spec.id) graph.engines.unloadNow()
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { graph.store.uninstall(spec) }
                    }
                }
            },
            onDismiss = { confirmUninstall = false },
            destructive = true,
        )
    }
}

@Composable
private fun Spec(label: String, value: String, link: (() -> Unit)? = null) {
    val c = Lily.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, style = Type.footnote, color = c.tertiary, modifier = Modifier.width(118.dp))
        Text(
            value,
            style = if (link != null) Type.footnote.copy(textDecoration = TextDecoration.Underline) else Type.footnote,
            color = if (link != null) c.accent else c.content,
            modifier = Modifier.weight(1f).then(if (link != null) Modifier.clickable(onClick = link) else Modifier),
        )
    }
}

/** Para onde "Testar agora" leva cada modelo. */
private fun tryRoute(id: String): Route? = when (id) {
    Catalog.qwen3Small.id, Catalog.qwen3.id, Catalog.gemma4.id, Catalog.ministral.id, Catalog.phi4.id -> Route.Chat(model = id)
    Catalog.smolVlm.id, Catalog.qwenVl.id -> Route.ImageTool(task = TaskKind.DESCRIBE_IMAGE)
    Catalog.esrgan.id -> Route.ImageTool(task = TaskKind.UPSCALE)
    Catalog.iris.id -> Route.ImageGen()
    Catalog.mlkitOcr.id -> Route.ImageTool(task = TaskKind.OCR)
    Catalog.mlkitLabels.id -> Route.ImageTool(task = TaskKind.LABELS)
    Catalog.mlkitSubject.id -> Route.ImageTool(task = TaskKind.REMOVE_BACKGROUND)
    Catalog.mlkitLanguage.id -> Route.TextTool(TaskKind.LANGUAGE_ID)
    Catalog.mlkitTranslate.id -> Route.TextTool(TaskKind.TRANSLATE)
    Catalog.androidStt.id -> Route.TextTool(TaskKind.TRANSCRIBE_MIC)
    Catalog.androidTts.id -> Route.TextTool(TaskKind.SPEAK)
    else -> null
}

/** Os pacotes de tradução do ML Kit no aparelho: baixar e apagar de verdade. */
@Composable
private fun TranslationPacks() {
    val graph = LocalGraph.current
    val c = Lily.colors
    val scope = rememberCoroutineScope()
    var downloaded by remember { mutableStateOf<Set<String>?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { downloaded = runCatching { Translator.downloaded() }.getOrElse { emptySet() } }
    SectionHeader("Pacotes de idioma")
    Text("Cada idioma tem cerca de 30 MB. O pacote é baixado uma vez e depois traduz sem internet.", style = Type.footnote, color = c.secondary)
    Spacer(Modifier.height(8.dp))
    Translator.languages.forEach { (code, name) ->
        val has = downloaded?.contains(code) == true
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = Type.callout.copy(fontWeight = FontWeight.Medium), color = c.content, modifier = Modifier.weight(1f))
            when {
                busy == code -> Text(if (has) "Apagando…" else "Baixando…", style = Type.caption, color = c.secondary)
                downloaded == null -> Unit
                has -> PillButton("Apagar", {
                    busy = code
                    scope.launch {
                        runCatching { Translator.delete(code) }.onFailure { error = it.message }
                        downloaded = runCatching { Translator.downloaded() }.getOrElse { emptySet() }
                        busy = null
                    }
                }, kind = ButtonKind.GLASS, height = 34.dp)
                else -> PillButton("Baixar", {
                    busy = code
                    val wifiOnly = graph.settings.value.value.wifiOnly
                    if (wifiOnly && Network.metered(graph.app)) error = "Esperando o Wi-Fi (você pode liberar os dados móveis em Configurações)."
                    scope.launch {
                        runCatching { Translator.download(code, wifiOnly) }.onFailure { error = it.message ?: "O download falhou." }
                        downloaded = runCatching { Translator.downloaded() }.getOrElse { emptySet() }
                        busy = null
                    }
                }, kind = ButtonKind.TINTED, height = 34.dp)
            }
        }
    }
    error?.let { Text(it, style = Type.caption, color = c.warning, modifier = Modifier.padding(top = 6.dp)) }
}
