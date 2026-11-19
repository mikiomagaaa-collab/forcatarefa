package app.lily.ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lily.ai.core.OnlineMode
import app.lily.ai.remote.GatewayException
import app.lily.ai.remote.GatewayStatus
import app.lily.ai.remote.RemoteModel
import app.lily.ai.ui.LocalGraph
import app.lily.ai.ui.Nav
import app.lily.ai.ui.theme.ButtonKind
import app.lily.ai.ui.theme.Chip
import app.lily.ai.ui.theme.ConfirmDialog
import app.lily.ai.ui.theme.GlassCard
import app.lily.ai.ui.theme.Lily
import app.lily.ai.ui.theme.PillButton
import app.lily.ai.ui.theme.SectionHeader
import app.lily.ai.ui.theme.SettingRow
import app.lily.ai.ui.theme.Toggle
import app.lily.ai.ui.theme.TopBar
import app.lily.ai.ui.theme.Type
import app.lily.ai.ui.theme.glass
import java.util.Locale
import kotlinx.coroutines.launch

/** Um campo de texto no vidro, com rótulo (senhas ficam ocultas). */
@Composable
fun GlassField(label: String, value: String, onValue: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "", secret: Boolean = false, minLines: Int = 1) {
    val c = Lily.colors
    Column(modifier) {
        Text(label, style = Type.caption, color = c.secondary, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        Box(Modifier.fillMaxWidth().glass(c, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
            if (value.isEmpty()) Text(placeholder, style = Type.callout, color = c.tertiary)
            BasicTextField(
                value,
                onValue,
                singleLine = minLines == 1,
                minLines = minLines,
                textStyle = Type.callout.copy(color = c.content),
                cursorBrush = SolidColor(c.accent),
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = if (secret) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Valor em dólar no formato brasileiro (US$ 0,0105). */
fun usd(value: Double): String = "US$ " + String.format(Locale.forLanguageTag("pt-BR"), if (value < 0.1) "%.4f" else "%.2f", value)

/**
 * Serviço online: a conexão com o L.I.L.Y Gateway (endereço e chave de acesso), o aviso de
 * processamento externo, quando usar modelos online e o que o gateway oferece agora (com o limite
 * de gasto do dia).
 */
@Composable
fun OnlineScreen(nav: Nav) {
    val graph = LocalGraph.current
    val c = Lily.colors
    val scope = rememberCoroutineScope()
    val settings by graph.settings.value.collectAsStateWithLifecycle()
    val config by graph.gatewayStore.config.collectAsStateWithLifecycle()
    val status by graph.gateway.lastStatus.collectAsStateWithLifecycle()
    var url by remember { mutableStateOf(config?.url.orEmpty()) }
    var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var models by remember { mutableStateOf<List<RemoteModel>>(graph.gateway.cachedModels()) }
    var confirmRemove by remember { mutableStateOf(false) }

    fun test() {
        busy = true
        message = null
        scope.launch {
            try {
                val s = graph.gateway.status()
                models = if (s.features.code) graph.gateway.codeModels(refresh = true) else emptyList()
                message = "Conectado ao gateway ${s.version}."
            } catch (e: GatewayException) {
                message = e.message
            } catch (e: Exception) {
                message = "Não consegui testar: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(config?.url) { if (config?.hasKey == true && status == null) test() }

    Column(Modifier.fillMaxSize()) {
        TopBar("Serviço online", onBack = { nav.back() }, subtitle = "L.I.L.Y Gateway")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Cloud, null, tint = c.accent)
                    Spacer(Modifier.padding(4.dp))
                    Text("Para o Codex, o Vídeo e modelos maiores", style = Type.headline, color = c.content)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "A L.I.L.Y funciona no aparelho. Os recursos online passam pelo L.I.L.Y Gateway, um serviço seu (na sua conta Cloudflare) " +
                        "que guarda as chaves dos provedores. O app só conhece o endereço dele e uma chave de acesso, cifrada pelo Android.",
                    style = Type.footnote,
                    color = c.secondary,
                )
                Spacer(Modifier.height(6.dp))
                Text("Como criar o seu gateway: docs/GATEWAY.md no projeto.", style = Type.caption, color = c.tertiary)
            }

            SectionHeader("Conexão")
            GlassCard(Modifier.fillMaxWidth()) {
                GlassField("Endereço do gateway", url, { url = it }, placeholder = "https://lily-gateway.seu-nome.workers.dev")
                Spacer(Modifier.height(10.dp))
                GlassField(
                    "Chave de acesso",
                    key,
                    { key = it },
                    placeholder = if (config?.hasKey == true) "Guardada (deixe vazio para manter)" else "A LILY_ACCESS_KEY do seu gateway",
                    secret = true,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(
                        if (busy) "Testando…" else "Salvar e testar",
                        {
                            val error = graph.gatewayStore.save(url, key)
                            key = ""
                            if (error != null) message = error else test()
                        },
                        enabled = !busy && url.isNotBlank(),
                        height = 42.dp,
                    )
                    if (config != null) PillButton("Remover", { confirmRemove = true }, kind = ButtonKind.GLASS, icon = Icons.Rounded.Delete, height = 42.dp)
                }
                message?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, style = Type.footnote, color = if (it.startsWith("Conectado")) c.success else c.warning)
                }
            }

            SectionHeader("Privacidade")
            GlassCard(Modifier.fillMaxWidth()) {
                SettingRow(
                    "Permitir processamento externo",
                    "O que você mandar para um recurso online (texto, código, imagem de referência) sai do aparelho e passa pelo gateway e pelo provedor (OpenRouter, fal.ai, Tavily ou GitHub). Cada tela avisa quando algo é online.",
                    Icons.Rounded.Shield,
                    trailing = { Toggle(settings.onlineConsent, { on -> graph.settings.update { it.copy(onlineConsent = on) } }) },
                )
            }

            SectionHeader("Na conversa")
            GlassCard(Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OnlineMode.entries.forEach { m ->
                        Chip(m.label, settings.online == m, { graph.settings.update { it.copy(online = m) } }, enabled = settings.onlineConsent)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(if (settings.onlineConsent) settings.online.description else "Ligue \"Permitir processamento externo\" para usar modelos online.", style = Type.footnote, color = c.secondary)
                Text("O Codex e o Vídeo sempre são online e pedem o mesmo consentimento.", style = Type.caption, color = c.tertiary)
            }

            status?.let { StatusCard(it) }

            if (models.isNotEmpty()) {
                SectionHeader("Modelos online (lidos agora no provedor)")
                GlassCard(Modifier.fillMaxWidth()) {
                    models.sortedWith(compareBy({ listOf("frontier", "strong", "fast").indexOf(it.tier) }, { it.typicalUsd })).forEach { m ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(m.label, style = Type.callout, color = c.content)
                                Text("${m.vendor} · ${tierLabel(m.tier)} · ${m.context / 1000}k de contexto", style = Type.caption, color = c.secondary)
                            }
                            Text("${usd(m.priceIn)} / ${usd(m.priceOut)}", style = Type.caption, color = c.tertiary)
                        }
                    }
                    Text("Preço por milhão de tokens (entrada / saída), informado pelo provedor.", style = Type.caption, color = c.tertiary)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (confirmRemove) {
        ConfirmDialog(
            "Remover a conexão?",
            "A chave de acesso e o endereço saem do aparelho. Os recursos online param até você conectar de novo.",
            "Remover",
            onConfirm = {
                graph.gatewayStore.clear()
                url = ""
                models = emptyList()
                confirmRemove = false
            },
            onDismiss = { confirmRemove = false },
            destructive = true,
        )
    }
}

fun tierLabel(tier: String): String = when (tier) {
    "frontier" -> "raciocínio máximo"
    "strong" -> "forte"
    "fast" -> "rápido"
    else -> tier
}

@Composable
private fun StatusCard(s: GatewayStatus) {
    val c = Lily.colors
    SectionHeader("O que o seu gateway oferece")
    GlassCard(Modifier.fillMaxWidth()) {
        @Composable
        fun line(ok: Boolean, title: String, detail: String) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (ok) "✓" else "—", style = Type.headline, color = if (ok) c.success else c.tertiary, modifier = Modifier.padding(end = 10.dp))
                Column {
                    Text(title, style = Type.callout, color = c.content)
                    Text(detail, style = Type.caption, color = c.secondary)
                }
            }
        }
        line(s.features.code, "Modelos de programação e conversa", if (s.features.code) "${s.codeModels} modelos (OpenRouter)" else s.codeError ?: "Falta OPENROUTER_API_KEY no gateway")
        line(s.features.video, "Geração de vídeo", if (s.features.video) "fal.ai" else "Falta FAL_KEY no gateway")
        line(s.features.search, "Pesquisa na internet (Codex)", s.searchProvider?.let { if (it == "tavily") "Tavily" else "Brave Search" } ?: "Falta TAVILY_API_KEY ou BRAVE_API_KEY")
        line(s.features.repos, "Repositórios do Codex", if (s.repos.isEmpty()) "Falta GITHUB_TOKEN e GITHUB_REPOS" else s.repos.joinToString())
        Spacer(Modifier.height(6.dp))
        val b = s.budget
        Text(
            if (b.enforced) "Gasto hoje: ${usd(b.spentTodayUsd ?: 0.0)} de ${usd(b.dailyUsd)} (limite diário do gateway)"
            else "Limite diário de ${usd(b.dailyUsd)} configurado, mas não garantido: ligue o KV LILY_USAGE no gateway.",
            style = Type.footnote,
            color = if (b.enforced) c.secondary else c.warning,
        )
    }
}
