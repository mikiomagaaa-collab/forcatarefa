package app.lily.ai

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lily.ai.avatar.AvatarLook
import app.lily.ai.avatar.AvatarPose
import app.lily.ai.avatar.AvatarPresentation
import app.lily.ai.avatar.AvatarStyle
import app.lily.ai.avatar.LilyAvatar
import app.lily.ai.presence.LilyPresence
import app.lily.ai.presence.Presence
import app.lily.ai.presence.PresenceMode
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h1005dp-420dpi")
class AvatarTest {
    @get:Rule
    val rule = createComposeRule()

    @After
    fun clean() {
        LilyPresence.reset()
        LilyPresence.clock = { android.os.SystemClock.elapsedRealtime() }
    }

    private fun shot(name: String): Bitmap {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/outputs/telas").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }

    // --- Presença -----------------------------------------------------------------------------

    @Test
    fun `presença mostra o mais importante e volta depois do sucesso`() {
        var now = 1_000L
        LilyPresence.clock = { now }
        LilyPresence.working("video", "Gerando vídeo", 0.4f)
        assertEquals(PresenceMode.WORKING, LilyPresence.state.value.mode)
        assertEquals(0.4f, LilyPresence.state.value.progress)
        LilyPresence.thinking("codex", "Analisando o projeto")
        assertEquals(PresenceMode.THINKING, LilyPresence.state.value.mode)
        assertEquals(2, LilyPresence.state.value.active)
        LilyPresence.listening("microfone", 0.7f)
        assertEquals(PresenceMode.LISTENING, LilyPresence.state.value.mode)
        LilyPresence.clear("microfone")
        LilyPresence.finished("codex", true, "Correção verificada")
        assertEquals(PresenceMode.SUCCESS, LilyPresence.state.value.mode)
        now += LilyPresence.TRANSIENT_MS + 1
        LilyPresence.recompute()
        // O vídeo continua em andamento: volta para "trabalhando", com o progresso.
        assertEquals(PresenceMode.WORKING, LilyPresence.state.value.mode)
        LilyPresence.finished("video", false)
        assertEquals(PresenceMode.ERROR, LilyPresence.state.value.mode)
        now += LilyPresence.TRANSIENT_MS + 1
        LilyPresence.recompute()
        assertEquals(PresenceMode.IDLE, LilyPresence.state.value.mode)
    }

    @Test
    fun `expressões, piscadas e boca seguem o estado`() {
        assertTrue(AvatarPose.target(PresenceMode.SUCCESS).happyEyes > 0.5f)
        assertTrue(AvatarPose.target(PresenceMode.ERROR).smile < 0f)
        assertTrue(AvatarPose.target(PresenceMode.ERROR).browTilt > 0.5f)
        assertTrue(AvatarPose.target(PresenceMode.THINKING).gazeY < 0f)
        assertEquals(0f, AvatarPose.mouthOpen(PresenceMode.IDLE, 1f, 500))
        assertTrue((0 until 1000 step 20).any { AvatarPose.mouthOpen(PresenceMode.SPEAKING, 0.8f, it.toLong()) > 0.5f })
        // Pisca pelo menos uma vez a cada janela de alguns segundos, e fica aberta na maior parte do tempo.
        val samples = (0 until 20_000 step 10).map { AvatarPose.blink(it.toLong()) }
        assertTrue(samples.any { it > 0.9f })
        assertTrue(samples.count { it == 0f } > samples.size * 0.85)
    }

    // --- Telas --------------------------------------------------------------------------------

    @Test
    fun `avatar em todos os estados`() {
        val states = listOf(
            Presence(PresenceMode.IDLE, "Pronta"),
            Presence(PresenceMode.LISTENING, "Ouvindo", level = 0.8f),
            Presence(PresenceMode.THINKING, "Pensando"),
            Presence(PresenceMode.SPEAKING, "Falando", level = 0.9f),
            Presence(PresenceMode.WORKING, "Gerando vídeo", progress = 0.62f),
            Presence(PresenceMode.SUCCESS, "Concluído"),
            Presence(PresenceMode.ERROR, "Algo deu errado"),
        )
        rule.setContent {
            Column(Modifier.fillMaxSize().background(Color(0xFF0B0A14)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                states.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { p ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                LilyAvatar(p, AvatarLook(), Modifier.size(190.dp, 200.dp), timeMs = 1_200L)
                                Text(p.label.orEmpty(), color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
        shot("ai20-avatar-estados")
    }

    @Test
    fun `avatar estilos, formas e cores`() {
        val p = Presence(PresenceMode.SPEAKING, "Falando", level = 0.6f)
        rule.setContent {
            Column(Modifier.fillMaxSize().background(Color(0xFF0B0A14)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AvatarStyle.entries.forEach { style ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LilyAvatar(p, AvatarLook(style = style), Modifier.size(124.dp, 140.dp), timeMs = 2_000L)
                            Text(style.label, color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(AvatarPresentation.FACE, AvatarPresentation.ORB).forEach { pr ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            LilyAvatar(Presence(PresenceMode.IDLE), AvatarLook(presentation = pr), Modifier.size(150.dp), timeMs = 2_000L)
                            Text(pr.label, color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(20, 160, 330).forEach { hue ->
                        LilyAvatar(Presence(PresenceMode.IDLE), AvatarLook(hue = hue), Modifier.size(124.dp, 140.dp), timeMs = 2_000L)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(PresenceMode.WORKING, PresenceMode.LISTENING, PresenceMode.ERROR).forEach { m ->
                        LilyAvatar(Presence(m, progress = 0.4f, level = 0.7f), AvatarLook(presentation = AvatarPresentation.ORB), Modifier.size(110.dp), timeMs = 2_000L)
                    }
                }
            }
        }
        shot("ai21-avatar-estilos")
    }
}
