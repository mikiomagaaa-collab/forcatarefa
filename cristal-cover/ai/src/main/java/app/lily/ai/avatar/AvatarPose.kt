package app.lily.ai.avatar

import androidx.compose.runtime.Immutable
import app.lily.ai.presence.PresenceMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * A expressão da avatar (alvos; a tela anima de uma para outra). Valores normalizados:
 * [eyeOpen] 1 = normal; [smile] -1 triste a 1 sorriso; [browRaise] -1 baixa a 1 erguida;
 * [browTilt] 1 = pontas de dentro para cima (preocupada); [gazeX]/[gazeY] -1..1 para onde olha;
 * [tilt] inclinação da cabeça em graus; [happyEyes] 1 = olhos fechados em arco (alegria);
 * [squint] 1 = olhos apertados (concentrada).
 */
@Immutable
data class Pose(
    val eyeOpen: Float = 1f,
    val smile: Float = 0.35f,
    val browRaise: Float = 0f,
    val browTilt: Float = 0f,
    val gazeX: Float = 0f,
    val gazeY: Float = 0f,
    val tilt: Float = 0f,
    val happyEyes: Float = 0f,
    val squint: Float = 0f,
)

object AvatarPose {
    /** A expressão de cada estado. */
    fun target(mode: PresenceMode): Pose = when (mode) {
        PresenceMode.IDLE -> Pose(smile = 0.35f)
        PresenceMode.LISTENING -> Pose(eyeOpen = 1.12f, smile = 0.2f, browRaise = 0.35f, gazeX = 0f, gazeY = 0.05f, tilt = -5f)
        PresenceMode.THINKING -> Pose(eyeOpen = 0.95f, smile = 0.05f, browRaise = 0.45f, browTilt = 0.15f, gazeX = -0.65f, gazeY = -0.7f, tilt = 3f)
        PresenceMode.SPEAKING -> Pose(eyeOpen = 1.02f, smile = 0.4f, browRaise = 0.15f)
        PresenceMode.WORKING -> Pose(eyeOpen = 0.88f, smile = 0.1f, browRaise = -0.15f, gazeY = 0.35f, squint = 0.45f)
        PresenceMode.SUCCESS -> Pose(smile = 1f, browRaise = 0.4f, happyEyes = 1f, tilt = 4f)
        PresenceMode.ERROR -> Pose(eyeOpen = 0.9f, smile = -0.55f, browRaise = 0.2f, browTilt = 1f, gazeX = 0.15f, gazeY = 0.55f, tilt = -2f)
    }

    private fun hash(n: Long, seed: Int): Float {
        var x = n * 374761393L + seed * 668265263L
        x = (x xor (x ushr 13)) * 1274126177L
        return ((x xor (x ushr 16)) and 0xFFFF).toFloat() / 0xFFFF
    }

    /**
     * Quanto as pálpebras estão fechadas agora (0 aberta, 1 fechada). Pisca de 2,4 a 5,4 s, às vezes
     * duas vezes seguidas. Determinístico (a mesma hora dá a mesma piscada).
     */
    fun blink(timeMs: Long, seed: Int = 7): Float {
        val window = 4_200L
        val slot = timeMs / window
        val at = (hash(slot, seed) * 3_000f).toLong() + 600L
        val local = timeMs - slot * window
        fun shape(start: Long): Float {
            val d = local - start
            if (d < 0 || d > 170) return 0f
            return sin(PI * d / 170.0).toFloat()
        }
        val twice = hash(slot, seed + 1) > 0.78f
        return maxOf(shape(at), if (twice) shape(at + 260) else 0f)
    }

    /** Respiração: um ciclo de 4,8 s (−1..1). */
    fun breathing(timeMs: Long): Float = sin(2 * PI * timeMs / 4_800.0).toFloat()

    /**
     * A boca falando: o nível do áudio mais um vaivém de sílabas (~6 por segundo), para não parecer
     * só abrir e fechar. Fora da fala, fechada.
     */
    fun mouthOpen(mode: PresenceMode, level: Float, timeMs: Long): Float {
        if (mode != PresenceMode.SPEAKING) return 0f
        val syllables = abs(sin(2 * PI * timeMs / 165.0)).toFloat()
        val base = level.coerceIn(0f, 1f)
        return (0.18f + 0.82f * base) * (0.35f + 0.65f * syllables)
    }

    /** Olhar vagando devagar quando parada (não fica fixo como uma foto). */
    fun idleGaze(timeMs: Long): Pair<Float, Float> {
        val x = sin(2 * PI * timeMs / 9_300.0).toFloat() * 0.25f
        val y = sin(2 * PI * timeMs / 13_700.0 + 1.3).toFloat() * 0.12f
        return x to y
    }
}
