package app.cristal.cover.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cristal.cover.data.prefs.GlassQuality
import app.cristal.cover.ui.components.IconLook
import app.cristal.cover.ui.components.LocalIconLook
import app.cristal.cover.ui.glass.LocalGlassLight
import app.cristal.cover.ui.glass.rememberGlassLight
import app.cristal.cover.ui.onboarding.OnboardingScreen
import app.cristal.cover.ui.theme.CristalTheme
import app.cristal.cover.ui.widgets.LocalWidgetHost

/** Raiz da activity do launcher: aplica tema e qualidade do vidro e decide entre onboarding e tela inicial. */
@Composable
fun LauncherRoot(viewModel: LauncherViewModel, governor: PerformanceGovernor) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val loaded by viewModel.settingsLoaded.collectAsStateWithLifecycle()
    val widgetData by viewModel.widgetData.collectAsStateWithLifecycle()
    val powerSave = widgetData.battery.powerSave
    val quality = governor.effectiveQuality(settings, powerSave)
    val highRateOk = governor.highRateSustainable

    LaunchedEffect(settings.refreshMode, powerSave, highRateOk) {
        governor.applyRefreshMode(settings.refreshMode, powerSave)
    }

    // A cor do vidro e do texto segue o papel de parede da tela em que o launcher está.
    val displayId = rememberDisplayId()
    val cover by viewModel.cover.collectAsStateWithLifecycle()
    val themed = settings.forScreen(cover.cover?.id == displayId)

    CristalTheme(settings = themed, qualityOverride = quality) {
        val light = rememberGlassLight(enabled = settings.dynamicLight && quality == GlassQuality.LIQUID)
        CompositionLocalProvider(
            LocalGlassLight provides light,
            LocalIconLook provides IconLook(settings.iconShape, settings.iconStyle, androidx.compose.ui.graphics.Color(settings.iconTintColor)),
            LocalWidgetHost provides viewModel.widgetHost,
        ) {
            when {
                !loaded -> Box(Modifier.fillMaxSize().background(Color.Black))
                !settings.onboardingDone -> OnboardingScreen(onFinish = viewModel::finishOnboarding)
                else -> LauncherScreen(viewModel = viewModel, quality = quality)
            }
        }
    }
}
