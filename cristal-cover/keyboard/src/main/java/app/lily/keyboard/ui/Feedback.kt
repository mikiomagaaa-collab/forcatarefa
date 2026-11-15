package app.lily.keyboard.ui

import android.content.Context
import android.media.AudioManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import app.lily.keyboard.core.KeyCode
import app.lily.keyboard.core.KeyboardSettings

/** Vibração e som das teclas, conforme as preferências. */
class Feedback(context: Context) {
    private val vibrator: Vibrator? = runCatching { context.getSystemService(VibratorManager::class.java)?.defaultVibrator }.getOrNull()
    private val audio: AudioManager? = context.getSystemService(AudioManager::class.java)
    private val primitives = runCatching {
        vibrator?.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK) == true
    }.getOrDefault(false)

    fun key(code: KeyCode, settings: KeyboardSettings) {
        if (settings.vibration) vibrate(settings.vibrationStrength, light = code == KeyCode.SPACE)
        if (settings.sound) {
            val fx = when (code) {
                KeyCode.DELETE -> AudioManager.FX_KEYPRESS_DELETE
                KeyCode.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                KeyCode.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            runCatching { audio?.playSoundEffect(fx, settings.soundVolume.coerceIn(0.05f, 1f)) }
        }
    }

    private fun vibrate(strength: Float, light: Boolean) {
        val v = vibrator ?: return
        val s = (strength.coerceIn(0f, 1f) * if (light) 0.6f else 1f).coerceAtLeast(0.08f)
        runCatching {
            if (primitives) {
                v.vibrate(VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, s).compose())
            } else {
                v.vibrate(VibrationEffect.createOneShot((6 + 14 * s).toLong(), (40 + 215 * s).toInt().coerceIn(1, 255)))
            }
        }
    }
}
