package app.lily.ai.avatar

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lily.ai.presence.LilyPresence
import app.lily.ai.presence.PresenceMode
import app.lily.ai.ui.LocalGraph
import app.lily.ai.ui.theme.Lily
import app.lily.ai.ui.theme.LilyOrb

/**
 * A L.I.L.Y como ela aparece nas telas do app: a avatar (com o estado de todos os módulos) ou, se a
 * pessoa desligou a avatar, a esfera de sempre. [presentation] força uma forma (a tela externa usa o
 * rosto, que cabe melhor).
 */
@Composable
fun PresenceAvatar(size: Dp, modifier: Modifier = Modifier, presentation: AvatarPresentation? = null, listeningLevel: Float? = null) {
    val graph = LocalGraph.current
    val settings by graph.settings.value.collectAsStateWithLifecycle()
    val presence by LilyPresence.state.collectAsStateWithLifecycle()
    val look = settings.avatar
    if (!look.enabled) {
        val busy = presence.mode != PresenceMode.IDLE
        LilyOrb(size, modifier, energy = if (busy) 1f else 0f, level = listeningLevel ?: presence.level)
        return
    }
    LilyAvatar(
        presence = presence,
        look = look,
        modifier = modifier.size(size),
        themeAccent = Lily.colors.accent,
        presentation = presentation ?: look.presentation,
    )
}
