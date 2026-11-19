package app.cristal.cover.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cristal.cover.data.home.WidgetKind
import app.cristal.cover.data.prefs.PrefKeys
import app.cristal.cover.data.prefs.Settings
import app.cristal.cover.data.prefs.WidgetDensity
import app.cristal.cover.data.prefs.WidgetLook
import app.cristal.cover.ui.design.LiquidQuickColors
import app.cristal.cover.ui.glass.LocalGlassSpec
import app.cristal.cover.ui.widgets.LocalWidgetStyle
import app.cristal.cover.ui.widgets.WidgetActions
import app.cristal.cover.ui.widgets.WidgetCard
import app.cristal.cover.ui.widgets.WidgetSamples
import app.cristal.cover.ui.widgets.widgetStyle
import kotlin.math.roundToInt

/**
 * Personalização dos widgets: aparência (Liquid Glass, claro, escuro, colorido, monocromático),
 * transparência, desfoque, cor de destaque, cantos e densidade. A prévia no alto usa os próprios
 * widgets, então mostra cada mudança na hora. Vale para todos os widgets e entra nas coleções.
 */
@Composable
internal fun WidgetsPage(vm: SettingsViewModel, settings: Settings, wallpaper: ImageBitmap?, onBack: () -> Unit) {
    SettingsScaffold(SettingsPage.WIDGETS.title, settings, wallpaper, onBack) {
        item { WidgetPreview(settings) }
        item {
            SettingsCard("Aparência") {
                ChoiceRow("Estilo", WidgetLook.entries.map { it to it.label }, settings.widgetLook) { vm.set(PrefKeys.WIDGET_STYLE_LOOK, it.name) }
                InfoText(settings.widgetLook.detail)
            }
        }
        item {
            SettingsCard("Vidro") {
                SliderRow("Transparência", settings.widgetTransparency, 0f..1f, { "${(it * 100).roundToInt()}%" }) {
                    vm.set(PrefKeys.WIDGET_STYLE_TRANSPARENCY, it)
                }
                SliderRow("Intensidade do desfoque", settings.widgetBlur, 0f..1f, { "${(it * 100).roundToInt()}%" }) {
                    vm.set(PrefKeys.WIDGET_STYLE_BLUR, it)
                }
            }
        }
        item {
            SettingsCard("Forma e espaço") {
                SliderRow("Arredondamento", settings.widgetCorner, 0.6f..1.4f, { "${(it * 26).roundToInt()} dp" }) {
                    vm.set(PrefKeys.WIDGET_STYLE_CORNER, it)
                }
                ChoiceRow("Densidade", WidgetDensity.entries.map { it to it.label }, settings.widgetDensity) { vm.set(PrefKeys.WIDGET_STYLE_DENSITY, it.name) }
            }
        }
        item {
            SettingsCard("Cor de destaque") {
                AccentRow(settings.widgetAccent) { vm.set(PrefKeys.WIDGET_STYLE_ACCENT, it) }
                InfoText("A primeira bola usa a cor do tema. No estilo Colorido, cada widget tem a cor dele e o destaque fica branco.")
            }
        }
    }
}

/** Os próprios widgets com a aparência escolhida (dados de exemplo). */
@Composable
private fun WidgetPreview(settings: Settings) {
    val data = remember { WidgetSamples.data() }
    val actions = remember { WidgetActions() }
    CompositionLocalProvider(LocalWidgetStyle provides settings.widgetStyle()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WidgetCard(WidgetKind.CLOCK, data, actions, Modifier.weight(1f).height(164.dp))
                WidgetCard(WidgetKind.WEATHER, data, actions, Modifier.weight(1f).height(164.dp))
            }
            WidgetCard(WidgetKind.AGENDA, data, actions, Modifier.fillMaxWidth().height(164.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WidgetCard(WidgetKind.BATTERY, data, actions, Modifier.weight(1f).height(164.dp))
                WidgetCard(WidgetKind.BRIEF, data, actions, Modifier.weight(1f).height(164.dp))
            }
            WidgetCard(WidgetKind.LILY, data, actions, Modifier.fillMaxWidth().height(84.dp))
            Text("Prévia com dados de exemplo", fontSize = 12.sp, color = LocalGlassSpec.current.secondaryContentColor, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun AccentRow(selected: Int, onSelect: (Int) -> Unit) {
    val options = listOf(0) + LiquidQuickColors.map { it.toArgb() }.distinct()
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { argb ->
            val isSelected = argb == selected
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        if (argb == 0) {
                            Brush.sweepGradient(listOf(Color(0xFFFF7A2E), Color(0xFF9E52FF), Color(0xFF338CFF), Color(0xFF40EBF2), Color(0xFFFF7A2E)))
                        } else {
                            Brush.linearGradient(listOf(Color(argb), Color(argb)))
                        },
                    )
                    .border(if (isSelected) 3.dp else 1.dp, Color.White.copy(alpha = if (isSelected) 0.95f else 0.3f), CircleShape)
                    .clickable { onSelect(argb) },
                contentAlignment = Alignment.Center,
            ) {}
        }
    }
}
