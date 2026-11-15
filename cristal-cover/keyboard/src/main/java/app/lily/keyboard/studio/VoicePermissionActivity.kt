package app.lily.keyboard.studio

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Tela invisível que só pede a permissão do microfone. O teclado não pode mostrar o pedido de
 * permissão sozinho, então abre esta atividade e volta para onde você estava.
 */
class VoicePermissionActivity : ComponentActivity() {
    private val request = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Toast.makeText(
            this,
            if (granted) "Microfone liberado. Toque no microfone do teclado de novo." else "Sem o microfone, o ditado fica desligado.",
            Toast.LENGTH_SHORT,
        ).show()
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) request.launch(Manifest.permission.RECORD_AUDIO)
    }
}
