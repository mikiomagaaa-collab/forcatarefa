package app.lily.ai.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lily.ai.avatar.AvatarLook
import app.lily.ai.avatar.AvatarMotion
import app.lily.ai.avatar.AvatarOverlay
import app.lily.ai.avatar.AvatarPresentation
import app.lily.ai.avatar.AvatarSize
import app.lily.ai.avatar.AvatarStyle
import app.lily.ai.avatar.LilyAvatar
import app.lily.ai.avatar.OverlayCorner
import app.lily.ai.avatar.OverlayMode
import app.lily.ai.presence.Presence
import app.lily.ai.presence.PresenceMode
import app.lily.ai.ui.LocalGraph
import app.lily.ai.ui.Nav
import app.lily.ai.ui.theme.Chip
import app.lily.ai.ui.theme.GlassCard
import app.lily.ai.ui.theme.Lily
import app.lily.ai.ui.theme.PillButton
import app.lily.ai.ui.theme.SectionHeader
import app.lily.ai.ui.theme.SettingRow
import app.lily.ai.ui.theme.Toggle
import app.lily.ai.ui.theme.TopBar
import app.lily.ai.ui.theme.Type
import kotlinx.coroutines.delay

/**
 * A avatar da L.I.L.Y: aparência (estilo, forma, cor, tamanho, transparência, animações) com prévia
 * que passa pelos estados, e a presença por cima de outros apps (com a permissão do Android).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AvatarScreen(nav: Nav) {
    val graph = LocalGraph.current
    val c = Lily.colors
    val context = LocalContext.current
    val settings by graph.settings.value.collectAsStateWithLifecycle()
    val look = settings.avatar
    fun set(change: (AvatarLook) -> AvatarLook) = graph.settings.update { it.copy(avatar = change(it.avatar)) }
    var canDraw by remember { mutableStateOf(AvatarOverlay.canDraw(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canDraw = AvatarOverlay.canDraw(context) }
    // A prévia mostra cada estado por alguns segundos (é só a prévia: nada é ouvido nem gerado).
    val demo = listOf(
        Presence(PresenceMode.IDLE, "Pronta"),
        Presence(PresenceMode.LISTENING, "Ouvindo", level = 0.7f),
        Presence(PresenceMode.THINKING, "Pensando"),
        Presence(PresenceMode.SPEAKING, "Falando", level = 0.8f),
        Presence(PresenceMode.WORKING, "Trabalhando", progress = 0.6f),
        Presence(PresenceMode.SUCCESS, "Concluído"),
        Presence(PresenceMode.ERROR, "Algo deu errado"),
    )
    val elapsed by androidx.compose.runtime.produceState(0L) {
        val start = androidx.compose.animation.core.withInfiniteAnimationFrameNanos { it }
        while (true) androidx.compose.animation.core.withInfiniteAnimationFrameNanos { value = (it - start) / 1_000_000L }
    }
    val step = ((elapsed / 2_400L) % demo.size).toInt()

    Column(Modifier.fillMaxSize()) {
        TopBar("Avatar", onBack = { nav.back() }, subtitle = "A presença da L.I.L.Y")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            GlassCard(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    LilyAvatar(demo[step], look.copy(enabled = true), Modifier.size(200.dp), themeAccent = c.accent)
                }
                Text("Prévia: ${demo[step].label}", style = Type.footnote, color = c.secondary, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                SettingRow("Mostrar a avatar", "Desligada, a L.I.L.Y aparece como a esfera", trailing = { Toggle(look.enabled, { on -> set { it.copy(enabled = on) } }) })
            }

            SectionHeader("Estilo")
            GlassCard(Modifier.fillMaxWidth()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AvatarStyle.entries.forEach { s -> Chip(s.label, look.style == s, { set { it.copy(style = s) } }) }
                }
                Text(look.style.detail, style = Type.caption, color = c.tertiary, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AvatarPresentation.entries.forEach { p -> Chip(p.label, look.presentation == p, { set { it.copy(presentation = p) } }) }
                }
                Text(look.presentation.detail, style = Type.caption, color = c.tertiary, modifier = Modifier.padding(top = 6.dp))
            }

            SectionHeader("Cor")
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Seguir o tema", style = Type.callout, color = c.content, modifier = Modifier.weight(1f))
                    Toggle(look.hue < 0, { on -> set { it.copy(hue = if (on) -1 else app.lily.ai.avatar.AvatarPalette.hueOf(c.accent).toInt()) } })
                }
                if (look.hue >= 0) {
                    Slider(
                        value = look.hue.toFloat(),
                        onValueChange = { v -> set { it.copy(hue = v.toInt().coerceIn(0, 359)) } },
                        valueRange = 0f..359f,
                        colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent),
                    )
                    Text("Matiz ${look.hue}°", style = Type.caption, color = c.tertiary)
                }
            }

            SectionHeader("Tamanho, transparência e movimento")
            GlassCard(Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AvatarSize.entries.forEach { s -> Chip(s.label, look.size == s, { set { it.copy(size = s) } }) }
                }
                Spacer(Modifier.height(10.dp))
                Text("Opacidade ${(look.opacity * 100).toInt()}%", style = Type.callout, color = c.content)
                Slider(look.opacity, { v -> set { it.copy(opacity = v) } }, valueRange = 0.4f..1f, colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent))
                Text("Animações", style = Type.callout, color = c.content)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AvatarMotion.entries.forEach { m -> Chip(m.label, look.motion == m, { set { it.copy(motion = m) } }) }
                }
                Text("Com \"Remover animações\" ligado no Android, ela fica parada.", style = Type.caption, color = c.tertiary, modifier = Modifier.padding(top = 6.dp))
            }

            SectionHeader("Por cima de outros apps")
            GlassCard(Modifier.fillMaxWidth()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OverlayMode.entries.forEach { m -> Chip(m.label, look.overlay == m, { set { it.copy(overlay = m) } }) }
                }
                Text(look.overlay.detail, style = Type.footnote, color = c.secondary, modifier = Modifier.padding(top = 6.dp))
                if (look.overlay != OverlayMode.OFF) {
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OverlayCorner.entries.forEach { k -> Chip(k.label, look.corner == k, { set { it.copy(corner = k) } }) }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (canDraw) {
                        SettingRow("Permissão concedida", "Tocar abre a L.I.L.Y · segurar esconde até a próxima tarefa · arrastar muda de lugar", Icons.Rounded.Visibility)
                    } else {
                        Text(
                            "O Android precisa permitir \"Exibir sobre outros apps\" para a L.I.L.Y. Sem a permissão, ela aparece só dentro do app e na Liquid Island (avisos ao vivo).",
                            style = Type.footnote,
                            color = c.warning,
                        )
                        Spacer(Modifier.height(8.dp))
                        PillButton("Permitir sobreposição", { context.startActivity(AvatarOverlay.permissionIntent(context)) }, icon = Icons.Rounded.Layers, height = 42.dp)
                    }
                    Text("Na tela externa do Razr ela aparece na forma compacta (rosto ou orbe).", style = Type.caption, color = c.tertiary, modifier = Modifier.padding(top = 8.dp))
                }
            }
            GlassCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text(
                    "A avatar só mostra o que a L.I.L.Y já está fazendo por pedido seu. Ela nunca liga a câmera nem o microfone, não fala sozinha e não manda notificações.",
                    style = Type.footnote,
                    color = c.secondary,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
