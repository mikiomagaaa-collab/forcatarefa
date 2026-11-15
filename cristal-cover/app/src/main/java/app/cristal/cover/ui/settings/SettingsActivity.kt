package app.cristal.cover.ui.settings

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cristal.cover.core.safely
import app.cristal.cover.data.prefs.GlassQuality
import app.cristal.cover.ui.components.IconLook
import app.cristal.cover.ui.components.LocalIconLook
import app.cristal.cover.ui.theme.CristalTheme
import dagger.hilt.android.AndroidEntryPoint

/** Configurações do Cristal. Pode abrir direto em uma subpágina com [EXTRA_PAGE]. */
@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        safely("configurar recorte de tela") {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        val initial = SettingsPage.entries.firstOrNull { it.name == intent?.getStringExtra(EXTRA_PAGE) } ?: SettingsPage.MAIN

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) {
                viewModel.messages.collect { text ->
                    safely("mostrar aviso") { Toast.makeText(this@SettingsActivity, text, Toast.LENGTH_LONG).show() }
                }
            }
            // As listas de configurações têm muitos cards; o vidro "Blur" já fica ótimo e pesa menos.
            val quality = if (settings.glassQuality == GlassQuality.LIGHT) GlassQuality.LIGHT else GlassQuality.BLUR
            CristalTheme(settings = settings, qualityOverride = quality) {
                CompositionLocalProvider(LocalIconLook provides IconLook(settings.iconShape, settings.iconStyle)) {
                    SettingsRoot(viewModel = viewModel, initialPage = initial, onExit = { finish() })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
    }

    companion object {
        const val EXTRA_PAGE = "page"
        val PAGE_WALLPAPER: String = SettingsPage.WALLPAPER.name
        val PAGE_WEATHER: String = SettingsPage.WEATHER.name
        val PAGE_COVER: String = SettingsPage.COVER.name
    }
}
