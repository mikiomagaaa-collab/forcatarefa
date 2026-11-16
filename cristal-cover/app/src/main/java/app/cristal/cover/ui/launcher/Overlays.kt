package app.cristal.cover.ui.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cristal.cover.data.apps.AppInfo
import app.cristal.cover.data.db.AppPrefEntity
import app.cristal.cover.data.home.HomeItem
import app.cristal.cover.data.home.WidgetKind
import app.cristal.cover.data.launch.CompatLevel
import app.cristal.cover.data.launch.CompatReason
import app.cristal.cover.data.launch.CompatReport
import app.cristal.cover.data.widgets.WidgetProviderEntry
import app.cristal.cover.ui.components.AppIconImage
import app.cristal.cover.ui.glass.GlassSurface
import app.cristal.cover.ui.glass.LocalGlassSpec
import app.cristal.cover.ui.glass.Motion
import app.cristal.cover.ui.glass.rememberHaptic
import kotlin.math.roundToInt
import androidx.compose.material.icons.rounded.AutoAwesome

// --- Base de diálogo --------------------------------------------------------------------------------

/** Diálogo de vidro centralizado. Tocar fora fecha. O conteúdo rola se não couber na tela. */
@Composable
fun GlassDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 360.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, Motion.pop) }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            .padding(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        val limit = this.maxHeight
        GlassSurface(
            modifier = modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .heightIn(max = limit)
                .graphicsLayer {
                    val p = appear.value
                    alpha = p.coerceIn(0f, 1f)
                    scaleX = 0.9f + 0.1f * p
                    scaleY = 0.9f + 0.1f * p
                }
                // Toques dentro do diálogo não devem fechá-lo.
                .pointerInput(Unit) { detectTapGestures { } },
            cornerRadius = 30.dp,
            elevation = 20.dp,
            reactToTouch = false,
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(18.dp),
                content = content,
            )
        }
    }
}

@Composable
fun DialogTitle(text: String) {
    Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
fun DialogText(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 13.sp, lineHeight = 18.sp, color = LocalGlassSpec.current.secondaryContentColor, modifier = modifier)
}

/** Botão de texto em pílula; [primary] destaca a ação principal. */
@Composable
fun GlassButton(text: String, modifier: Modifier = Modifier, primary: Boolean = false, onClick: () -> Unit) {
    GlassSurface(
        modifier = modifier.height(42.dp),
        cornerRadius = 21.dp,
        tint = if (primary) Color.White.copy(alpha = 0.86f) else Color.Unspecified,
        elevation = 4.dp,
        onClick = onClick,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (primary) Color(0xFF14161A) else LocalContentColor.current,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null, enabled: Boolean = true) {
    val spec = LocalGlassSpec.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onChange(!checked) }
            .padding(vertical = 6.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, lineHeight = 16.sp, color = spec.secondaryContentColor)
        }
        Switch(checked = checked, onCheckedChange = if (enabled) onChange else null, enabled = enabled)
    }
}

// --- Menu de contexto -------------------------------------------------------------------------------

/** Menu de vidro posicionado perto do dedo, sem sair da tela. */
@Composable
fun ContextMenu(state: MenuState, title: String, actions: List<MenuAction>, onDismiss: () -> Unit) {
    val appear = remember(state) { Animatable(0f) }
    LaunchedEffect(state) { appear.animateTo(1f, Motion.pop) }
    val haptic = rememberHaptic()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) {
        GlassSurface(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val margin = 10.dp.roundToPx()
                    val gap = 44.dp.roundToPx()
                    val placeable = measurable.measure(
                        constraints.copy(
                            minWidth = 0,
                            minHeight = 0,
                            maxWidth = minOf(constraints.maxWidth - 2 * margin, 236.dp.roundToPx()).coerceAtLeast(0),
                            maxHeight = (constraints.maxHeight - 2 * margin).coerceAtLeast(0),
                        ),
                    )
                    val x = (state.anchor.x.roundToInt() - placeable.width / 2)
                        .coerceIn(margin, (constraints.maxWidth - placeable.width - margin).coerceAtLeast(margin))
                    val above = state.anchor.y.roundToInt() - gap - placeable.height
                    val y = if (above >= margin) {
                        above
                    } else {
                        (state.anchor.y.roundToInt() + gap)
                            .coerceIn(margin, (constraints.maxHeight - placeable.height - margin).coerceAtLeast(margin))
                    }
                    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(x, y) }
                }
                .graphicsLayer {
                    val p = appear.value
                    alpha = p.coerceIn(0f, 1f)
                    scaleX = 0.85f + 0.15f * p
                    scaleY = 0.85f + 0.15f * p
                }
                .pointerInput(Unit) { detectTapGestures { } },
            cornerRadius = 22.dp,
            elevation = 18.dp,
            reactToTouch = false,
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                Text(
                    title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalGlassSpec.current.secondaryContentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                for (action in actions) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                haptic()
                                action.onClick()
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val color = if (action.destructive) Color(0xFFFF6B60) else LocalContentColor.current
                        Icon(action.icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(action.label, fontSize = 14.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

// --- Pasta ------------------------------------------------------------------------------------------

@Composable
fun FolderOverlay(
    folder: HomeItem.Folder,
    iconScale: Float,
    onAppClick: (AppInfo) -> Unit,
    onChildHold: (HomeItem.App, androidx.compose.ui.geometry.Offset) -> Unit,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val appear = remember(folder.id) { Animatable(0f) }
    LaunchedEffect(folder.id) { appear.animateTo(1f, Motion.pop) }
    val focusManager = LocalFocusManager.current
    var title by remember(folder.id) { mutableStateOf(folder.title) }
    val spec = LocalGlassSpec.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            .padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        val limit = this.maxHeight
        GlassSurface(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .fillMaxWidth()
                .heightIn(max = limit)
                .graphicsLayer {
                    val p = appear.value
                    alpha = p.coerceIn(0f, 1f)
                    scaleX = 0.7f + 0.3f * p
                    scaleY = 0.7f + 0.3f * p
                }
                .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } },
            cornerRadius = 34.dp,
            elevation = 22.dp,
            reactToTouch = false,
        ) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().padding(bottom = 10.dp), contentAlignment = Alignment.Center) {
                    if (title.isEmpty()) {
                        Text("Nome da pasta", fontSize = 16.sp, color = spec.secondaryContentColor)
                    }
                    BasicTextField(
                        value = title,
                        onValueChange = {
                            title = it.take(30)
                            onRename(title)
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = spec.contentColor,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        ),
                        cursorBrush = SolidColor(spec.contentColor),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(folder.children, key = { it.id }) { child ->
                        DrawerIcon(
                            app = child.app,
                            iconScale = iconScale,
                            showLabel = true,
                            label = child.label,
                            onClick = { onAppClick(child.app) },
                            onHold = { at -> onChildHold(child, at) },
                        )
                    }
                }
            }
        }
    }
}

// --- Menu da tela inicial e widgets -----------------------------------------------------------------

@Composable
fun HomeMenuBar(actions: List<MenuAction>, onDismiss: () -> Unit, bottomPadding: Dp) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, Motion.pop) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        GlassSurface(
            modifier = Modifier
                .padding(start = 14.dp, end = 14.dp, bottom = bottomPadding + 14.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    val p = appear.value
                    alpha = p.coerceIn(0f, 1f)
                    translationY = (1f - p) * 60f
                }
                .pointerInput(Unit) { detectTapGestures { } },
            cornerRadius = 28.dp,
            elevation = 18.dp,
            reactToTouch = false,
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (action in actions) {
                    val haptic = rememberHaptic()
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                haptic()
                                action.onClick()
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(action.icon, contentDescription = null, modifier = Modifier.size(24.dp))
                        Text(
                            action.label,
                            fontSize = 11.sp,
                            maxLines = 2,
                            textAlign = TextAlign.Center,
                            lineHeight = 13.sp,
                            modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun widgetInfo(kind: WidgetKind): Triple<ImageVector, String, String> = when (kind) {
    WidgetKind.CLOCK -> Triple(Icons.Rounded.Schedule, "Relógio", "Hora, data e bateria")
    WidgetKind.WEATHER -> Triple(Icons.Rounded.WbSunny, "Clima", "Temperatura e previsão")
    WidgetKind.MUSIC -> Triple(Icons.Rounded.MusicNote, "Música", "Controle do player ativo")
    WidgetKind.BATTERY -> Triple(Icons.Rounded.BatteryFull, "Bateria", "Nível e estado de carga")
    WidgetKind.AGENDA -> Triple(Icons.Rounded.Event, "Agenda", "Próximos compromissos")
    WidgetKind.ANALOG_CLOCK -> Triple(Icons.Rounded.Schedule, "Relógio de ponteiros", "Mostrador analógico")
    WidgetKind.DATE -> Triple(Icons.Rounded.CalendarToday, "Data", "Dia do mês em destaque")
    WidgetKind.SHORTCUTS -> Triple(Icons.Rounded.TouchApp, "Atalhos", "Lanterna, câmera, busca e ajustes")
    WidgetKind.SEARCH -> Triple(Icons.Rounded.Search, "Busca", "Abre a gaveta de apps")
    WidgetKind.PHOTO -> Triple(Icons.Rounded.Photo, "Foto", "Uma ou várias fotos suas, em sequência")
    WidgetKind.LILY -> Triple(Icons.Rounded.AutoAwesome, "L.I.L.Y", "Pergunte por texto ou por voz à IA do aparelho")
    WidgetKind.APP_WIDGET -> Triple(Icons.Rounded.Widgets, "Widget de app", "De outro aplicativo")
}

@Composable
fun WidgetPicker(
    onPick: (WidgetKind) -> Unit,
    onDismiss: () -> Unit,
    providers: List<WidgetProviderEntry> = emptyList(),
    onPickProvider: (WidgetProviderEntry) -> Unit = {},
) {
    GlassDialog(onDismiss = onDismiss) {
        DialogTitle("Adicionar widget")
        for (kind in WidgetKind.entries.filter { it.builtIn }) {
            val (icon, name, description) = widgetInfo(kind)
            PickerRow(icon, name, "$description · ${kind.spanX}×${kind.spanY}") { onPick(kind) }
        }
        if (providers.isNotEmpty()) {
            Text(
                "DE OUTROS APPS",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = LocalGlassSpec.current.secondaryContentColor,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            for (entry in providers) {
                PickerRow(Icons.Rounded.Widgets, entry.label, "${entry.appLabel} · ${entry.spanX}×${entry.spanY}") { onPickProvider(entry) }
            }
        }
    }
}

@Composable
private fun PickerRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val haptic = rememberHaptic()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                haptic()
                onClick()
            }
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 12.sp, color = LocalGlassSpec.current.secondaryContentColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// --- Compatibilidade --------------------------------------------------------------------------------

fun compatReasonText(reason: CompatReason): String = when (reason) {
    CompatReason.FIXED_LANDSCAPE -> "Só funciona deitado: na tela externa pode aparecer girado ou com faixas."
    CompatReason.OLD_TARGET -> "É um app antigo, que não sabe se adaptar a outros tamanhos de tela."
    CompatReason.NEEDS_LARGER_SCREEN -> "O próprio app declara que precisa de uma tela maior."
    CompatReason.MIN_WINDOW_TOO_BIG -> "A janela mínima do app é maior do que a tela externa."
    CompatReason.NO_SMALL_SCREENS -> "O app declara que não suporta telas pequenas."
    CompatReason.GAME -> "É um jogo: controles e textos podem ficar pequenos ou cortados."
}

@Composable
fun CompatDialog(
    app: AppInfo,
    report: CompatReport,
    shizukuReady: Boolean,
    onOpen: (dontAskAgain: Boolean) -> Unit,
    onOptions: () -> Unit,
    onDismiss: () -> Unit,
) {
    var dontAsk by remember { mutableStateOf(false) }
    GlassDialog(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
            AppIconImage(app = app, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                if (report.level == CompatLevel.PROBLEM) "${app.label} provavelmente não cabe na tela externa"
                else "${app.label} pode não funcionar bem na tela externa",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 19.sp,
            )
        }
        for (reason in report.reasons) DialogText("•  ${compatReasonText(reason)}", Modifier.padding(bottom = 4.dp))
        DialogText(
            if (shizukuReady) {
                "Dica: em \"Opções na tela externa\" você pode reduzir a escala ou forçar o redimensionamento deste app."
            } else {
                "Dica: com o Shizuku ativo, o Cristal consegue reduzir a escala e forçar o redimensionamento de apps teimosos."
            },
            Modifier.padding(top = 6.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { dontAsk = !dontAsk }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = dontAsk,
                onCheckedChange = { dontAsk = it },
                colors = CheckboxDefaults.colors(uncheckedColor = LocalContentColor.current.copy(alpha = 0.6f)),
            )
            Text("Não avisar mais para este app", fontSize = 13.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton("Opções", Modifier.weight(1f), onClick = onOptions)
            GlassButton("Abrir", Modifier.weight(1f), primary = true) { onOpen(dontAsk) }
        }
    }
}

/** Ajustes por app para a tela externa. Escala e redimensionamento dependem do Shizuku. */
@Composable
fun AppOptionsDialog(
    app: AppInfo,
    pref: AppPrefEntity?,
    shizukuReady: Boolean,
    onScale: (Int) -> Unit,
    onForceResizable: (Boolean) -> Unit,
    onSkipWarning: (Boolean) -> Unit,
    onHidden: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val current = pref ?: AppPrefEntity(componentKey = app.key)
    var scale by remember(app.key) { mutableFloatStateOf(current.scalePercent.toFloat()) }
    GlassDialog(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            AppIconImage(app = app, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(app.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                DialogText("Opções na tela externa")
            }
        }
        Text("Escala: ${scale.roundToInt()}%", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Slider(
            value = scale,
            onValueChange = { scale = (it / 5f).roundToInt() * 5f },
            onValueChangeFinished = { onScale(scale.roundToInt()) },
            valueRange = 50f..100f,
            enabled = shizukuReady,
            colors = SliderDefaults.colors(
                thumbColor = LocalContentColor.current,
                activeTrackColor = LocalContentColor.current,
                inactiveTrackColor = LocalContentColor.current.copy(alpha = 0.25f),
            ),
        )
        DialogText(
            if (shizukuReady) {
                "Valores menores fazem o app \"enxergar\" uma tela maior: tudo fica menor e cabe mais conteúdo. " +
                    "A escala vale só enquanto o app está aberto e é desfeita ao voltar para o Cristal."
            } else {
                "A escala muda a densidade da tela externa enquanto o app está aberto. Isso exige o Shizuku ativo."
            },
        )
        Spacer(Modifier.height(6.dp))
        SwitchRow(
            title = "Forçar redimensionamento",
            subtitle = "Para apps que se recusam a abrir fora da tela principal (via Shizuku).",
            checked = current.forceResizable,
            onChange = onForceResizable,
            enabled = shizukuReady,
        )
        SwitchRow(
            title = "Não avisar sobre compatibilidade",
            checked = current.skipWarning,
            onChange = onSkipWarning,
        )
        SwitchRow(
            title = "Ocultar da gaveta de apps",
            checked = current.hidden,
            onChange = onHidden,
        )
        GlassButton("Concluir", Modifier.fillMaxWidth().padding(top = 8.dp), primary = true, onClick = onDismiss)
    }
}

/** Explica por que um app não abriu na tela externa e como liberar. */
@Composable
fun BlockedHelpDialog(
    app: AppInfo,
    shizukuReady: Boolean,
    onOpenSystemSettings: () -> Unit,
    onOpenCristalSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassDialog(onDismiss = onDismiss) {
        DialogTitle("${app.label} não abriu aqui?")
        DialogText(
            "A Motorola só mostra na tela externa os apps que você liberou. Quando um app não está liberado, " +
                "o sistema o abre na tela de dentro ou mostra o aviso \"abra o telefone para continuar\".",
        )
        Spacer(Modifier.height(8.dp))
        DialogText("1. Abra Configurações > Tela externa > Apps e ative ${app.label}.")
        DialogText(
            if (shizukuReady) {
                "2. Ou ative \"Liberar apps automaticamente\" nas configurações do Cristal (Tela externa): " +
                    "ele pede a liberação ao sistema antes de abrir cada app."
            } else {
                "2. Ou ative o Shizuku: com ele o Cristal libera os apps sozinho, sem você entrar nas configurações."
            },
            Modifier.padding(top = 4.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton("Cristal", Modifier.weight(1f), onClick = onOpenCristalSettings)
            GlassButton("Sistema", Modifier.weight(1f), primary = true, onClick = onOpenSystemSettings)
        }
    }
}

// --- Mensagens --------------------------------------------------------------------------------------

@Composable
fun SnackbarHost(controller: SnackbarController, bottomPadding: Dp) {
    val message = controller.message ?: return
    val appear = remember(message) { Animatable(0f) }
    LaunchedEffect(message) { appear.animateTo(1f, Motion.pop) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        GlassSurface(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, bottom = bottomPadding + 92.dp)
                .widthIn(max = 380.dp)
                .graphicsLayer {
                    val p = appear.value
                    alpha = p.coerceIn(0f, 1f)
                    translationY = (1f - p) * 40f
                },
            cornerRadius = 22.dp,
            elevation = 16.dp,
            reactToTouch = false,
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(message, fontSize = 13.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f, fill = false))
                val label = controller.actionLabel
                val action = controller.action
                if (label != null && action != null) {
                    Text(
                        label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(start = 14.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                controller.clear()
                                action()
                            },
                    )
                }
            }
        }
    }
}
