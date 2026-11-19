package app.cristal.cover.ui.lock

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.cristal.cover.core.safely
import app.cristal.cover.data.prefs.PrefKeys
import app.cristal.cover.data.prefs.Settings
import app.cristal.cover.data.prefs.SettingsRepository
import app.cristal.cover.ui.settings.SettingsActivity
import app.cristal.cover.ui.theme.CristalTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Editor da tela de bloqueio. Só abre depois de o aparelho ser desbloqueado (o bloqueio pede a
 * autenticação ao sistema antes), e roda na mesma tela em que a pessoa segurou o bloqueio.
 */
@AndroidEntryPoint
class LockEditorActivity : ComponentActivity() {

    @Inject lateinit var presenter: LockPresenter
    @Inject lateinit var settingsRepository: SettingsRepository

    private var initial by mutableStateOf<Settings?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val startOnCover = intent?.getBooleanExtra(EXTRA_COVER, false) == true
        // O rascunho parte das configurações gravadas (não do estado inicial, que pode ainda estar vazio).
        lifecycleScope.launch { initial = settingsRepository.current() }

        setContent {
            val state by presenter.state.collectAsStateWithLifecycle()
            val start = initial
            CristalTheme(settings = state.settings) {
                BackHandler { finish() }
                if (start == null) {
                    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black))
                } else {
                    LockEditorScreen(
                        state = state,
                        initial = start,
                        startOnCover = startOnCover,
                        actions = LockEditorActions(
                            onCancel = { finish() },
                            onDone = ::save,
                            onOpenCollections = { openSettings("COLLECTIONS") },
                            onOpenWallpaper = { openSettings("WALLPAPER") },
                        ),
                    )
                }
            }
        }
    }

    private fun save(draft: EditorDraft) {
        lifecycleScope.launch {
            settingsRepository.edit { p ->
                p[PrefKeys.LOCK_CLOCK_X] = draft.inner.x
                p[PrefKeys.LOCK_CLOCK_Y] = draft.inner.y
                p[PrefKeys.LOCK_CLOCK_Z] = draft.inner.z
                p[PrefKeys.LOCK_CLOCK_SCALE] = draft.inner.scale
                p[PrefKeys.LOCK_CLOCK_FONT] = draft.inner.font.name
                p[PrefKeys.LOCK_CLOCK_COLOR] = draft.inner.color
                p[PrefKeys.LOCK_CLOCK_GLASS] = draft.inner.glass
                p[PrefKeys.LOCK_CLOCK_FILL] = draft.inner.fill.name
                p[PrefKeys.LOCK_CLOCK_COLOR2] = draft.inner.color2
                p[PrefKeys.LOCK_CLOCK_FILL_OPACITY] = draft.inner.fillOpacity
                p[PrefKeys.LOCK_CLOCK_SPLIT] = draft.inner.split
                p[PrefKeys.LOCK_CLOCK_HARMONY] = draft.inner.harmony
                p[PrefKeys.COVER_CLOCK_X] = draft.cover.x
                p[PrefKeys.COVER_CLOCK_Y] = draft.cover.y
                p[PrefKeys.COVER_CLOCK_Z] = draft.cover.z
                p[PrefKeys.COVER_CLOCK_SCALE] = draft.cover.scale
                p[PrefKeys.COVER_CLOCK_FONT] = draft.cover.font.name
                p[PrefKeys.COVER_CLOCK_COLOR] = draft.cover.color
                p[PrefKeys.COVER_CLOCK_GLASS] = draft.cover.glass
                p[PrefKeys.COVER_CLOCK_FILL] = draft.cover.fill.name
                p[PrefKeys.COVER_CLOCK_COLOR2] = draft.cover.color2
                p[PrefKeys.COVER_CLOCK_FILL_OPACITY] = draft.cover.fillOpacity
                p[PrefKeys.COVER_CLOCK_SPLIT] = draft.cover.split
                p[PrefKeys.COVER_CLOCK_HARMONY] = draft.cover.harmony
            }
            finish()
        }
    }

    private fun openSettings(page: String) {
        val displayId = safely("ler tela do editor") { display?.displayId } ?: Display.DEFAULT_DISPLAY
        safely("abrir configurações pelo editor") {
            startActivity(
                Intent(this, SettingsActivity::class.java)
                    .putExtra(SettingsActivity.EXTRA_PAGE, page)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle(),
            )
        }
    }

    companion object {
        const val EXTRA_COVER = "cover"

        /** Abre o editor na tela [displayId] (o aparelho já precisa estar desbloqueado). */
        fun open(context: Context, cover: Boolean, displayId: Int) {
            safely("abrir o editor do bloqueio") {
                context.startActivity(
                    Intent(context, LockEditorActivity::class.java)
                        .putExtra(EXTRA_COVER, cover)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle(),
                )
            }
        }
    }
}
