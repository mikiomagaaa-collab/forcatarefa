package app.lily.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lily.ai.BuildConfig
import app.lily.ai.bridge.CristalBridge
import app.lily.ai.core.Profile
import app.lily.ai.data.LilyTheme
import app.lily.ai.engine.EngineStatus
import app.lily.ai.engine.SpeechInput
import app.lily.ai.models.Catalog
import app.lily.ai.models.formatBytes
import app.lily.ai.ui.LocalGraph
import app.lily.ai.ui.Nav
import app.lily.ai.ui.Route
import app.lily.ai.ui.theme.Chip
import app.lily.ai.ui.theme.ConfirmDialog
import app.lily.ai.ui.theme.GlassCard
import app.lily.ai.ui.theme.Lily
import app.lily.ai.ui.theme.SectionHeader
import app.lily.ai.ui.theme.SettingRow
import app.lily.ai.ui.theme.Toggle
import app.lily.ai.ui.theme.TopBar
import app.lily.ai.ui.theme.Type
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Accents = listOf(0xFFB79CFF, 0xFF64D2FF, 0xFF34D399, 0xFFFFB347, 0xFFFF6B7A, 0xFFFF9BD8, 0xFFE3B5FF, 0xFF9DE36B, 0xFFF5F2FA)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: Nav) {
    val graph = LocalGraph.current
    val c = Lily.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by graph.settings.value.collectAsStateWithLifecycle()
    val metas by graph.conversations.conversations.collectAsStateWithLifecycle()
    val memories by graph.memory.items.collectAsStateWithLifecycle()
    val engine by graph.engines.status.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<String?>(null) }
    var licenses by remember { mutableStateOf(false) }
    var cacheBytes by remember { mutableLongStateOf(0L) }
    var notice by remember { mutableStateOf<String?>(null) }
    val onDevice = remember { SpeechInput.onDevice(context) }
    LaunchedEffect(confirm) { cacheBytes = withContext(Dispatchers.IO) { context.cacheDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() } } }

    fun installed(pkg: String) = runCatching { context.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    Column(Modifier.fillMaxSize()) {
        TopBar("Configurações", onBack = { nav.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionHeader("Roteador de IA")
            GlassCard(Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Profile.entries.forEach { p -> Chip(p.label, settings.profile == p, { graph.settings.update { it.copy(profile = p) } }) }
                }
                Spacer(Modifier.height(8.dp))
                Text(settings.profile.description, style = Type.footnote, color = c.secondary)
                Spacer(Modifier.height(6.dp))
                Text("Para fixar um modelo numa conversa, toque no nome do modelo no topo dela.", style = Type.caption, color = c.tertiary)
            }

            SectionHeader("Online e avatar")
            GlassCard(Modifier.fillMaxWidth()) {
                val gateway by graph.gatewayStore.config.collectAsStateWithLifecycle()
                SettingRow(
                    "Serviço online",
                    if (gateway == null) "Conecte o L.I.L.Y Gateway para o Codex, o Vídeo e modelos maiores" else "Conectado · ${settings.online.label.lowercase()}",
                    Icons.Rounded.Cloud,
                    onClick = { nav.go(Route.Online) },
                )
                SettingRow("Avatar", if (settings.avatar.enabled) "${settings.avatar.style.label} · ${settings.avatar.presentation.label.lowercase()}" else "Desligada (esfera)", Icons.Rounded.Face, onClick = { nav.go(Route.Avatar) })
            }

            SectionHeader("Aparência")
            GlassCard(Modifier.fillMaxWidth()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LilyTheme.entries.forEach { t -> Chip(t.label, settings.theme == t, { graph.settings.update { it.copy(theme = t) } }) }
                }
                if (settings.theme == LilyTheme.CUSTOM) {
                    Spacer(Modifier.height(12.dp))
                    Text("Cor de destaque", style = Type.caption, color = c.tertiary)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Accents.forEach { a ->
                            val selected = settings.accent == a
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(a.toInt()))
                                    .border(if (selected) 3.dp else 1.dp, if (selected) c.content else c.border, CircleShape)
                                    .clickable { graph.settings.update { it.copy(accent = a) } },
                            )
                        }
                    }
                }
            }

            SectionHeader("Memória")
            GlassCard(Modifier.fillMaxWidth()) {
                SettingRow("Usar memórias", "A L.I.L.Y só usa o que você mandou guardar", Icons.Rounded.Psychology, trailing = {
                    Toggle(settings.memoryEnabled, { on -> graph.settings.update { it.copy(memoryEnabled = on) } })
                })
                SettingRow("Gerenciar memórias", "${memories.size} ${if (memories.size == 1) "guardada" else "guardadas"} · ver, editar e apagar", Icons.Rounded.Memory, onClick = { nav.go(Route.Memory) })
            }

            SectionHeader("Voz")
            GlassCard(Modifier.fillMaxWidth()) {
                SettingRow("Responder em voz alta", "Quando a pergunta foi falada", Icons.Rounded.RecordVoiceOver, trailing = {
                    Toggle(settings.speakVoiceAnswers, { on -> graph.settings.update { it.copy(speakVoiceAnswers = on) } })
                })
                Text(String.format(Locale.forLanguageTag("pt-BR"), "Velocidade da leitura: %.1f×", settings.voiceRate), style = Type.footnote, color = c.secondary, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
                Slider(
                    value = settings.voiceRate,
                    onValueChange = { v -> graph.settings.update { it.copy(voiceRate = (v * 10).toInt() / 10f) } },
                    valueRange = 0.6f..1.6f,
                    colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.content.copy(alpha = 0.15f)),
                )
                SettingRow("Ouvir um exemplo", null, Icons.AutoMirrored.Rounded.VolumeUp, onClick = { graph.speak("Olá! Esta é a velocidade de leitura escolhida.") })
                SettingRow(
                    "Reconhecimento de fala",
                    if (onDevice) "No aparelho (o pacote de idioma está instalado)" else "Pode usar a rede do serviço de voz do Android",
                    Icons.Rounded.Mic,
                    onClick = { nav.go(Route.Model(Catalog.androidStt.id)) },
                )
            }

            SectionHeader("Desempenho")
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Liberar a memória depois de ${settings.unloadMinutes} min sem uso", style = Type.callout, color = c.content, modifier = Modifier.padding(start = 4.dp))
                Text("O modelo de conversa sai da memória sozinho (no perfil Eco, depois de 1 min).", style = Type.caption, color = c.tertiary, modifier = Modifier.padding(start = 4.dp))
                Slider(
                    value = settings.unloadMinutes.toFloat(),
                    onValueChange = { v -> graph.settings.update { it.copy(unloadMinutes = v.toInt().coerceIn(1, 10)) } },
                    valueRange = 1f..10f,
                    steps = 8,
                    colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.content.copy(alpha = 0.15f)),
                )
                val loaded = (engine as? EngineStatus.Ready)?.model?.name
                SettingRow(
                    "Liberar a memória agora",
                    loaded?.let { "$it está carregado" } ?: "Nenhum modelo carregado",
                    Icons.Rounded.Memory,
                    onClick = if (loaded != null) ({ graph.engines.unload() }) else null,
                )
            }

            SectionHeader("Teste do aparelho")
            GlassCard(Modifier.fillMaxWidth()) {
                SettingRow(
                    "Testar este aparelho",
                    "Roda os modelos instalados e mede velocidade, memória e temperatura; gera um relatório para copiar",
                    Icons.Rounded.Speed,
                    onClick = { nav.go(Route.Diagnostics) },
                )
            }

            SectionHeader("Downloads")
            GlassCard(Modifier.fillMaxWidth()) {
                SettingRow("Baixar só no Wi-Fi", "Modelos e pacotes de idioma (com dados móveis, a L.I.L.Y pergunta antes)", Icons.Rounded.Wifi, trailing = {
                    Toggle(settings.wifiOnly, { on -> graph.settings.update { it.copy(wifiOnly = on) } })
                })
            }

            SectionHeader("Privacidade")
            GlassCard(Modifier.fillMaxWidth()) {
                Text(
                    "Tudo é processado no aparelho. A internet só é usada para baixar modelos e pacotes de idioma, quando você pede. " +
                        "O que você escreve, fala ou envia não sai do aparelho. O microfone só liga quando você toca nele.",
                    style = Type.footnote,
                    color = c.secondary,
                    modifier = Modifier.padding(4.dp),
                )
                SettingRow("Apagar todas as conversas", "${metas.size} ${if (metas.size == 1) "conversa" else "conversas"} e os anexos", Icons.Rounded.Delete, onClick = { confirm = "conversas" })
                SettingRow("Apagar todas as memórias", "${memories.size} guardadas", Icons.Rounded.Delete, onClick = { confirm = "memorias" })
                SettingRow("Apagar medições de desempenho", "Tempos medidos neste aparelho", Icons.Rounded.Delete, onClick = { confirm = "medicoes" })
                SettingRow("Limpar arquivos temporários", "${formatBytes(cacheBytes)} · o próximo carregamento de modelo pode demorar mais", Icons.Rounded.Delete, onClick = { confirm = "cache" })
                notice?.let { Text(it, style = Type.caption, color = c.success, modifier = Modifier.padding(4.dp)) }
            }

            SectionHeader("Integrações")
            GlassCard(Modifier.fillMaxWidth()) {
                SettingRow("L.I.L.Y Keyboard", if (installed("app.lily.keyboard")) "Instalado · Perguntar, Corrigir, Reescrever e Traduzir no teclado" else "Não instalado", Icons.Rounded.Apps)
                SettingRow("Cristal Cover", if (CristalBridge.available(context)) "Lembretes com confirmação e Liquid Island" else "Não instalado ou sem a ponte do Info Brief", Icons.Rounded.Apps)
                SettingRow("L.I.L.Y Gallery", if (installed("app.lily.gallery")) "Instalada · editar, melhorar e analisar fotos" else "Não instalada", Icons.Rounded.Apps)
            }

            SectionHeader("Sobre")
            GlassCard(Modifier.fillMaxWidth()) {
                SettingRow("L.I.L.Y AI", "Versão ${BuildConfig.VERSION_NAME}", Icons.Rounded.Info)
                SettingRow("Licenças de terceiros", "Bibliotecas, modelos e fontes", Icons.Rounded.Description, onClick = { licenses = true })
                SettingRow("Termos de Uso", null, Icons.Rounded.Description, onClick = { nav.go(Route.Legal(LegalDoc.TERMS.name)) })
                SettingRow("Política de Privacidade", "O que fica no aparelho e o que pode sair", Icons.Rounded.Description, onClick = { nav.go(Route.Legal(LegalDoc.PRIVACY.name)) })
                SettingRow("Créditos", "Quem fez e o que o projeto usa", Icons.Rounded.Description, onClick = { nav.go(Route.Legal(LegalDoc.CREDITS.name)) })
            }
            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }

    confirm?.let { what ->
        val (title, text) = when (what) {
            "conversas" -> "Apagar todas as conversas?" to "Todas as conversas e anexos saem do aparelho."
            "memorias" -> "Apagar todas as memórias?" to "A L.I.L.Y deixa de lembrar o que você mandou guardar."
            "medicoes" -> "Apagar as medições?" to "Os tempos de carregamento e de resposta medidos neste aparelho serão zerados."
            else -> "Limpar arquivos temporários?" to "Imagens preparadas para os modelos, gravações e o cache do motor saem do aparelho."
        }
        ConfirmDialog(title, text, "Apagar", onConfirm = {
            confirm = null
            scope.launch {
                when (what) {
                    "conversas" -> graph.conversations.deleteAll()
                    "memorias" -> graph.memory.clear()
                    "medicoes" -> graph.perf.clear()
                    else -> withContext(Dispatchers.IO) { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
                }
                notice = "Pronto."
                cacheBytes = withContext(Dispatchers.IO) { context.cacheDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() } }
            }
        }, onDismiss = { confirm = null }, destructive = true)
    }

    if (licenses) {
        ModalBottomSheet(onDismissRequest = { licenses = false }, containerColor = sheetColor()) {
            val texts = remember {
                listOf("licenses/iris-3b/NOTICE", "licenses/iris-3b/LICENSE", "licenses/fonts-OFL.txt").associateWith { path ->
                    runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }.getOrDefault("")
                }
            }
            Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Text("Licenças de terceiros", style = Type.title, color = c.content)
                Spacer(Modifier.height(10.dp))
                listOf(
                    "LiteRT-LM e LiteRT (Google)" to "Apache 2.0",
                    "ML Kit (Google)" to "Termos de serviço do ML Kit",
                    "Jetpack Compose, AndroidX, Kotlin e kotlinx" to "Apache 2.0",
                    "Motor Kotlin do iris-3b (litert-community)" to "Apache 2.0 (texto abaixo)",
                    "Fonte Inter" to "SIL Open Font License 1.1 (texto abaixo)",
                ).forEach { (name, lic) -> LicenseRow(name, lic) }
                Text("Modelos", style = Type.headline, color = c.content, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                Catalog.all.filter { it.repo != null }.forEach { m -> LicenseRow("${m.name} · ${m.developer}", m.license) }
                texts.forEach { (path, text) ->
                    if (text.isNotBlank()) {
                        Text(path.substringAfter("licenses/"), style = Type.headline, color = c.content, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                        Text(text, style = Type.caption, color = c.secondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun LicenseRow(name: String, license: String) {
    val c = Lily.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(name, style = Type.callout, color = c.content)
        Text(license, style = Type.caption, color = c.secondary)
    }
}

