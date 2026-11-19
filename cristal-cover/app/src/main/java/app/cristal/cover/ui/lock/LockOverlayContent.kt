package app.cristal.cover.ui.lock

import android.app.ActivityOptions
import android.app.KeyguardManager
import android.content.Intent
import android.provider.MediaStore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cristal.cover.core.safely
import app.cristal.cover.ui.theme.CristalTheme

/**
 * Conteúdo do modo "Sobreposição": a mesma tela de bloqueio, desenhada em uma janela de
 * acessibilidade. Aqui não há activity, então abrir notificações e a câmera passa pelo desbloqueio.
 */
@Composable
fun LockOverlayContent(presenter: LockPresenter, onUnlock: () -> Unit, onHide: () -> Unit) {
    val state by presenter.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val secure = safely("ler segurança do aparelho") {
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure
    } ?: true
    // O overlay normalmente está na tela externa; lá vale o Cover Clock e o papel de parede dela.
    val onCover = safely("ler tela do overlay") { context.display.displayId != android.view.Display.DEFAULT_DISPLAY } ?: false
    val view = state.forDisplay(onCover)
    CristalTheme(settings = view.settings) {
        LockScreen(
            coverMinimal = onCover,
            state = view,
            secure = secure,
            resetKey = 0,
            actions = LockActions(
                onUnlock = onUnlock,
                // Sem activity não dá para encadear a ação ao desbloqueio; destrava e o usuário abre em seguida.
                onOpenNotification = { onUnlock() },
                onDismissNotification = presenter::dismiss,
                onTorch = presenter::toggleTorch,
                onCamera = {
                    safely("abrir câmera segura pelo overlay") {
                        val displayId = context.display.displayId
                        context.startActivity(
                            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle(),
                        )
                    }
                    // A câmera segura aparece sobre o bloqueio; só é preciso tirar o overlay da frente.
                    onHide()
                },
                onMediaPlayPause = presenter::mediaPlayPause,
                onMediaNext = presenter::mediaNext,
                onMediaPrevious = presenter::mediaPrevious,
            ),
        )
    }
}
