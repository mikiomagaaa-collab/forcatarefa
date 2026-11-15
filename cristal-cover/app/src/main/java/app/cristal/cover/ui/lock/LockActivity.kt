package app.cristal.cover.ui.lock

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.MediaStore
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.cristal.cover.core.Logx
import app.cristal.cover.core.safely
import app.cristal.cover.data.display.CoverDisplayRepository
import app.cristal.cover.data.notifications.NotificationItem
import app.cristal.cover.data.prefs.SettingsRepository
import app.cristal.cover.ui.launcher.LauncherActivity
import app.cristal.cover.ui.theme.CristalTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Tela de bloqueio em forma de activity.
 *
 * Como funciona, e por que é seguro:
 * - `setShowWhenLocked(true)` faz a activity aparecer POR CIMA do keyguard, sem removê-lo. É o
 *   mesmo mecanismo usado por alarmes e chamadas.
 * - Para destravar, a activity chama [KeyguardManager.requestDismissKeyguard]: o sistema mostra o
 *   próprio PIN/padrão/digital. O Cristal nunca vê nem valida a credencial.
 * - Se a activity for fechada ou travar, o keyguard original está logo atrás.
 */
@AndroidEntryPoint
class LockActivity : ComponentActivity() {

    @Inject lateinit var presenter: LockPresenter
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var displayRepository: CoverDisplayRepository

    private val keyguard: KeyguardManager? by lazy { getSystemService(KeyguardManager::class.java) }
    private val powerManager: PowerManager? by lazy { getSystemService(PowerManager::class.java) }
    private var preview = false
    private var resetKey by mutableIntStateOf(0)
    private var receiverRegistered = false
    private var dismissing = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                // Destravou por fora (digital, por exemplo): não há mais o que mostrar.
                Intent.ACTION_USER_PRESENT -> if (!preview) finishQuietly()
                Intent.ACTION_SCREEN_ON -> lifecycleScope.launch {
                    delay(350)
                    closeIfNotLocked()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        safely("configurar janela da tela de bloqueio") {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        handleIntent(intent)

        setContent {
            val state by presenter.state.collectAsStateWithLifecycle()
            CristalTheme(settings = state.settings) {
                BackHandler { if (preview) finishQuietly() }
                LockScreen(
                    state = state,
                    secure = keyguard?.isDeviceSecure == true,
                    resetKey = resetKey,
                    actions = LockActions(
                        onUnlock = { requestUnlock(null) },
                        onOpenNotification = ::openNotification,
                        onDismissNotification = presenter::dismiss,
                        onTorch = presenter::toggleTorch,
                        onCamera = ::openCamera,
                        onMediaPlayPause = presenter::mediaPlayPause,
                        onMediaNext = presenter::mediaNext,
                        onMediaPrevious = presenter::mediaPrevious,
                    ),
                )
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        receiverRegistered = safely("registrar receiver da tela de bloqueio") {
            ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            true
        } ?: false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        preview = intent?.getBooleanExtra(EXTRA_PREVIEW, false) == true
        if (intent?.getBooleanExtra(EXTRA_AUTO_DISMISS, false) == true) {
            // Veio do modo sobreposição: o usuário já deslizou, então pede o desbloqueio direto.
            window.decorView.post { requestUnlock(null) }
        }
    }

    override fun onResume() {
        super.onResume()
        closeIfNotLocked()
    }

    /**
     * A activity é aberta com a tela apagada, às vezes antes de o aparelho travar de fato. Quando
     * a tela acende e não há bloqueio ativo, não faz sentido ficar na frente.
     */
    private fun closeIfNotLocked() {
        if (preview || dismissing) return
        val interactive = powerManager?.isInteractive ?: true
        if (interactive && keyguard?.isKeyguardLocked != true) finishQuietly()
    }

    private fun requestUnlock(afterUnlock: (() -> Unit)?) {
        if (preview) {
            finishQuietly()
            return
        }
        val km = keyguard
        if (km == null || !km.isKeyguardLocked) {
            afterUnlock?.invoke()
            onUnlocked()
            return
        }
        dismissing = true
        try {
            km.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() {
                        dismissing = false
                        afterUnlock?.invoke()
                        onUnlocked()
                    }

                    override fun onDismissCancelled() {
                        dismissing = false
                        resetKey++
                    }

                    override fun onDismissError() {
                        dismissing = false
                        resetKey++
                    }
                },
            )
        } catch (t: Throwable) {
            Logx.w("Falha ao pedir o desbloqueio ao sistema", t)
            dismissing = false
            resetKey++
        }
    }

    private fun onUnlocked() {
        val settings = settingsRepository.settings.value
        val displayId = currentDisplayId()
        val cover = displayRepository.currentCover(settings.coverDisplayOverride)
        // Com "assumir a tela externa" ligado, cai direto no launcher do Cristal.
        if (settings.coverTakeover && cover != null && cover.id == displayId) {
            safely("abrir launcher após desbloquear") {
                startActivity(
                    Intent(this, LauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle(),
                )
            }
        }
        finishQuietly()
    }

    private fun openNotification(item: NotificationItem) {
        val displayId = currentDisplayId()
        requestUnlock { presenter.open(item, displayId) }
    }

    private fun openCamera() {
        // A intent "segura" abre a câmera por cima do bloqueio, sem destravar o aparelho.
        val secure = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(currentDisplayId()).toBundle()
        try {
            startActivity(secure, options)
        } catch (t: Throwable) {
            Logx.w("Câmera segura indisponível; pedindo desbloqueio", t)
            requestUnlock {
                safely("abrir câmera") {
                    startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options)
                }
            }
        }
    }

    private fun currentDisplayId(): Int = safely("ler tela da activity") { display?.displayId } ?: Display.DEFAULT_DISPLAY

    @SuppressLint("InlinedApi")
    private fun finishQuietly() {
        if (isFinishing) return
        finish()
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, android.R.anim.fade_out)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, android.R.anim.fade_out)
        }
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            safely("remover receiver da tela de bloqueio") { unregisterReceiver(receiver) }
            receiverRegistered = false
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PREVIEW = "preview"
        const val EXTRA_AUTO_DISMISS = "auto_dismiss"
    }
}
