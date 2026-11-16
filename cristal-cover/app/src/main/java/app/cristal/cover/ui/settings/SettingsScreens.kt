package app.cristal.cover.ui.settings

import androidx.compose.material.icons.rounded.Lens
import app.cristal.cover.data.prefs.LockPlacement
import app.cristal.cover.data.prefs.LockWidget
import app.cristal.cover.data.prefs.ClockLayout
import androidx.compose.runtime.mutableIntStateOf
import android.Manifest
import android.content.Intent
import android.view.Display
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cristal.cover.BuildConfig
import app.cristal.cover.data.backup.BackupManager
import app.cristal.cover.data.display.CoverState
import app.cristal.cover.data.prefs.CoverClockPosition
import app.cristal.cover.data.prefs.CoverClockStyle
import app.cristal.cover.data.prefs.DrawerSort
import app.cristal.cover.data.prefs.GestureAction
import app.cristal.cover.data.prefs.GlassQuality
import app.cristal.cover.data.prefs.IconShape
import app.cristal.cover.data.prefs.IconStyle
import app.cristal.cover.data.prefs.LockMode
import app.cristal.cover.data.prefs.PrefKeys
import app.cristal.cover.data.prefs.RefreshMode
import app.cristal.cover.data.prefs.Settings
import app.cristal.cover.data.prefs.SettingsLimits
import app.cristal.cover.data.prefs.TempUnit
import app.cristal.cover.data.prefs.ThemeMode
import app.cristal.cover.data.prefs.WallpaperKind
import app.cristal.cover.data.shizuku.ShizukuStatus
import app.cristal.cover.data.system.PermissionState
import app.cristal.cover.ui.components.AppIconImage
import app.cristal.cover.ui.components.GlassTextField
import app.cristal.cover.ui.components.WallpaperLayer
import app.cristal.cover.ui.components.WallpaperPresets
import app.cristal.cover.ui.launcher.GlassButton
import app.cristal.cover.ui.launcher.SwitchRow
import app.cristal.cover.ui.launcher.startSafely
import app.cristal.cover.ui.lock.LockActivity
import app.cristal.cover.ui.widgets.formatTemp
import app.cristal.cover.ui.widgets.weatherLabel
import kotlin.math.roundToInt

enum class SettingsPage(val title: String) {
    MAIN("Configurações"),
    APPEARANCE("Aparência"),
    DESIGN("Design System"),
    WALLPAPER("Wallpaper Studio"),
    COLLECTIONS("Coleções"),
    HOME("Tela inicial e ícones"),
    GESTURES("Gestos"),
    LOCK("Tela de bloqueio"),
    ISLAND("Liquid Island"),
    COVER("Tela externa"),
    WEATHER("Clima"),
    PERMISSIONS("Permissões"),
    BACKUP("Backup"),
    HIDDEN("Apps ocultos"),
    ABOUT("Sobre"),
}

fun gestureLabel(action: GestureAction): String = when (action) {
    GestureAction.NONE -> "Nenhuma"
    GestureAction.APP_DRAWER -> "Gaveta de apps"
    GestureAction.QUICK_PANEL -> "Central de Controle"
    GestureAction.SYSTEM_NOTIFICATIONS -> "Notificações do sistema"
    GestureAction.SYSTEM_QUICK_SETTINGS -> "Ajustes rápidos do sistema"
    GestureAction.LOCK_SCREEN -> "Bloquear tela"
    GestureAction.RECENTS -> "Apps recentes"
    GestureAction.SETTINGS -> "Configurações do Cristal"
    GestureAction.TORCH -> "Lanterna"
}

@Composable
fun SettingsRoot(viewModel: SettingsViewModel, initialPage: SettingsPage, onExit: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val wallpaper by viewModel.wallpaper.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val cover by viewModel.cover.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(initialPage) }

    // Quem abriu direto em uma subpágina (ex.: "Papel de parede" pelo menu) sai ao voltar.
    val back: () -> Unit = {
        when {
            page == SettingsPage.MAIN || page == initialPage -> onExit()
            page == SettingsPage.DESIGN -> page = SettingsPage.APPEARANCE
            page == SettingsPage.COLLECTIONS -> page = SettingsPage.WALLPAPER
            else -> page = SettingsPage.MAIN
        }
    }
    androidx.activity.compose.BackHandler(onBack = back)

    when (page) {
        SettingsPage.MAIN -> MainPage(settings, wallpaper, permissions, back) { page = it }
        SettingsPage.APPEARANCE -> AppearancePage(viewModel, settings, wallpaper, back) { page = SettingsPage.DESIGN }
        SettingsPage.DESIGN -> DesignSystemPage(viewModel, settings, wallpaper, back)
        SettingsPage.WALLPAPER -> WallpaperStudioPage(viewModel, settings, wallpaper, back) { page = SettingsPage.COLLECTIONS }
        SettingsPage.COLLECTIONS -> CollectionsPage(viewModel, back)
        SettingsPage.HOME -> HomePage(viewModel, settings, wallpaper, back)
        SettingsPage.GESTURES -> GesturesPage(viewModel, settings, wallpaper, permissions, back)
        SettingsPage.LOCK -> LockPage(viewModel, settings, wallpaper, permissions, back) { page = SettingsPage.PERMISSIONS }
        SettingsPage.COVER -> CoverPage(viewModel, settings, wallpaper, permissions, cover, back)
        SettingsPage.ISLAND -> IslandPage(viewModel, settings, wallpaper, permissions, back) { page = SettingsPage.PERMISSIONS }
        SettingsPage.WEATHER -> WeatherPage(viewModel, settings, wallpaper, permissions, back)
        SettingsPage.PERMISSIONS -> PermissionsPage(viewModel, settings, wallpaper, permissions, back, onExit)
        SettingsPage.BACKUP -> BackupPage(viewModel, settings, wallpaper, back)
        SettingsPage.HIDDEN -> HiddenAppsPage(viewModel, settings, wallpaper, back)
        SettingsPage.ABOUT -> AboutPage(settings, wallpaper, back)
    }
}

@Composable
private fun MainPage(
    settings: Settings,
    wallpaper: ImageBitmap?,
    permissions: PermissionState,
    onBack: () -> Unit,
    open: (SettingsPage) -> Unit,
) {
    val pending = listOf(permissions.notificationAccess, permissions.accessibility).count { !it }
    SettingsScaffold(SettingsPage.MAIN.title, settings, wallpaper, onBack) {
        if (pending > 0) {
            item {
                SettingsCard {
                    NavRow(
                        Icons.Rounded.Security,
                        if (pending == 1) "1 acesso pendente" else "$pending acessos pendentes",
                        "Toque para ativar notificações e acessibilidade.",
                    ) { open(SettingsPage.PERMISSIONS) }
                }
            }
        }
        item {
            SettingsCard("Visual") {
                NavRow(Icons.Rounded.Palette, "Aparência", "Tema, vidro, desempenho e taxa de atualização") { open(SettingsPage.APPEARANCE) }
                NavRow(Icons.Rounded.Wallpaper, "Wallpaper Studio", "Um papel de parede para cada tela e para cada bloqueio") { open(SettingsPage.WALLPAPER) }
                NavRow(Icons.Rounded.GridView, "Tela inicial e ícones", "Grade, dock, forma, tamanho e pacotes de ícones") { open(SettingsPage.HOME) }
            }
        }
        item {
            SettingsCard("Comportamento") {
                NavRow(Icons.Rounded.TouchApp, "Gestos", "O que cada gesto faz") { open(SettingsPage.GESTURES) }
                NavRow(Icons.Rounded.Lock, "Tela de bloqueio", "Relógio, notificações e atalhos sobre o bloqueio do sistema") { open(SettingsPage.LOCK) }
                NavRow(Icons.Rounded.Lens, "Liquid Island", "Ilha dinâmica em volta da câmera: música, chamadas e avisos") { open(SettingsPage.ISLAND) }
                NavRow(Icons.Rounded.Smartphone, "Tela externa", "Assumir a tela externa, liberar apps e compatibilidade") { open(SettingsPage.COVER) }
                NavRow(Icons.Rounded.WbSunny, "Clima", "Cidade e unidade de temperatura") { open(SettingsPage.WEATHER) }
            }
        }
        item {
            SettingsCard("Sistema") {
                NavRow(Icons.Rounded.Security, "Permissões", "Notificações, acessibilidade, Shizuku e launcher padrão") { open(SettingsPage.PERMISSIONS) }
                NavRow(Icons.Rounded.Backup, "Backup", "Exportar e restaurar toda a configuração") { open(SettingsPage.BACKUP) }
                NavRow(Icons.Rounded.VisibilityOff, "Apps ocultos") { open(SettingsPage.HIDDEN) }
                NavRow(Icons.Rounded.Info, "Sobre", "Versão, privacidade e limitações") { open(SettingsPage.ABOUT) }
            }
        }
    }
}

@Composable
private fun AppearancePage(vm: SettingsViewModel, settings: Settings, wallpaper: ImageBitmap?, onBack: () -> Unit, openDesign: () -> Unit) {
    SettingsScaffold(SettingsPage.APPEARANCE.title, settings, wallpaper, onBack) {
        item {
            SettingsCard("Design System Liquid Flip") {
                NavRow(Icons.Rounded.Palette, "Temas e materiais", "${settings.liquidTheme.label} · prévia dos componentes") { openDesign() }
            }
        }
        item {
            SettingsCard("Cor do vidro e do texto") {
                ChoiceRow(
                    "Modo",
                    listOf(ThemeMode.AUTO to "Pelo papel de parede", ThemeMode.LIGHT to "Vidro claro", ThemeMode.DARK to "Vidro escuro"),
                    settings.themeMode,
                ) { vm.setEnum(PrefKeys.THEME_MODE, it) }
                InfoText("No automático, um papel de parede escuro usa vidro escuro com texto branco, e um claro usa vidro claro com texto escuro.")
            }
        }
        item {
            SettingsCard("Vidro") {
                ChoiceRow(
                    "Qualidade",
                    listOf(GlassQuality.LIGHT to "Leve", GlassQuality.BLUR to "Blur", GlassQuality.LIQUID to "Líquido"),
                    settings.glassQuality,
                ) { vm.setEnum(PrefKeys.GLASS_QUALITY, it) }
                InfoText(
                    when (settings.glassQuality) {
                        GlassQuality.LIGHT -> "Sem blur nem shader: o mais econômico. Ideal para poupar bateria."
                        GlassQuality.BLUR -> "Desfoque real do fundo, sem refração."
                        GlassQuality.LIQUID -> "Desfoque, refração, dispersão de cor e brilho nas bordas (shader AGSL)."
                    },
                )
                ChoiceRow(
                    "Transparência",
                    listOf(0.12f to "Mais transparente", 0.45f to "Padrão", 0.85f to "Mais fosco"),
                    listOf(0.12f, 0.45f, 0.85f).minByOrNull { kotlin.math.abs(it - settings.glassIntensity) } ?: 0.45f,
                ) { vm.set(PrefKeys.GLASS_INTENSITY, it) }
                SliderRow(
                    "Ajuste fino",
                    settings.glassIntensity,
                    0f..1f,
                    label = {
                        when {
                            it < 0.3f -> "transparente"
                            it < 0.65f -> "padrão"
                            else -> "fosco"
                        }
                    },
                ) { vm.set(PrefKeys.GLASS_INTENSITY, it) }
                InfoText(
                    "O Liquid Glass é um vidro claro: o destaque vem da lente nas bordas e do brilho, não do desfoque. " +
                        "Se algum texto ficar difícil de ler sobre o seu papel de parede, puxe para \"fosco\".",
                )
                SwitchRow(
                    title = "Reflexo acompanha o movimento",
                    subtitle = "Usa o sensor de gravidade para o brilho da borda se mover ao inclinar o aparelho.",
                    checked = settings.dynamicLight,
                    onChange = { vm.set(PrefKeys.DYNAMIC_LIGHT, it) },
                )
            }
        }
        item {
            SettingsCard("Desempenho") {
                SwitchRow(
                    title = "Desempenho adaptativo",
                    subtitle = "Se os quadros começarem a atrasar, reduz sozinho a qualidade do vidro até a tela ser recriada.",
                    checked = settings.adaptivePerformance,
                    onChange = { vm.set(PrefKeys.ADAPTIVE_PERFORMANCE, it) },
                )
                ChoiceRow(
                    "Taxa de atualização",
                    listOf(RefreshMode.AUTO to "Automática", RefreshMode.MAX to "Máxima", RefreshMode.SAVER to "60 Hz"),
                    settings.refreshMode,
                ) { vm.setEnum(PrefKeys.REFRESH_MODE, it) }
                InfoText(
                    "Automática deixa o sistema decidir. Máxima pede a maior taxa da tela (144 Hz na tela externa) " +
                        "e volta para automática se os quadros não couberem nesse ritmo. No modo de economia de bateria " +
                        "o app usa 60 Hz e vidro leve.",
                )
                SwitchRow(
                    title = "Flip Transition",
                    subtitle = "A tela inicial chega com uma mola ao abrir ou fechar o aparelho e recolhe conforme a dobradiça fecha.",
                    checked = settings.flipTransition,
                    onChange = { vm.set(PrefKeys.FLIP_TRANSITION, it) },
                )
                SwitchRow(
                    title = "Vibração ao tocar",
                    checked = settings.haptics,
                    onChange = { vm.set(PrefKeys.HAPTICS, it) },
                )
            }
        }
    }
}

private fun <T> rangeOptions(range: IntRange, map: (Int) -> T): List<Pair<T, String>> = range.map { map(it) to it.toString() }

@Composable
private fun HomePage(vm: SettingsViewModel, settings: Settings, wallpaper: ImageBitmap?, onBack: () -> Unit) {
    val packs by vm.iconPacks.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadIconPacks() }
    SettingsScaffold(SettingsPage.HOME.title, settings, wallpaper, onBack) {
        item {
            SettingsCard("Grade da tela interna") {
                ChoiceRow("Colunas", rangeOptions(SettingsLimits.gridColumns) { it }, settings.innerGridColumns) {
                    vm.set(PrefKeys.INNER_GRID_COLUMNS, it)
                }
                ChoiceRow("Linhas", rangeOptions(SettingsLimits.innerGridRows) { it }, settings.innerGridRows) {
                    vm.set(PrefKeys.INNER_GRID_ROWS, it)
                }
                InfoText("A tela interna e a externa têm telas iniciais separadas: ícones, widgets e dock de uma não mexem na outra.")
            }
        }
        item {
            SettingsCard("Grade da tela externa") {
                ChoiceRow("Colunas", rangeOptions(SettingsLimits.gridColumns) { it }, settings.gridColumns) {
                    vm.set(PrefKeys.GRID_COLUMNS, it)
                }
                ChoiceRow("Linhas", rangeOptions(SettingsLimits.gridRows) { it }, settings.gridRows) {
                    vm.set(PrefKeys.GRID_ROWS, it)
                }
                InfoText("Ao diminuir a grade, os itens que não couberem vão para a primeira posição livre.")
                SwitchRow(title = "Mostrar nomes dos apps", checked = settings.showLabels, onChange = { vm.set(PrefKeys.SHOW_LABELS, it) })
            }
        }
        item {
            SettingsCard("Dock") {
                SwitchRow(title = "Mostrar dock", checked = settings.dockEnabled, onChange = { vm.set(PrefKeys.DOCK_ENABLED, it) })
                ChoiceRow("Itens no dock", rangeOptions(SettingsLimits.dockCount) { it }, settings.dockCount) {
                    vm.set(PrefKeys.DOCK_COUNT, it)
                }
                SwitchRow(
                    title = "Aproveitar a área ao lado das câmeras",
                    subtitle = "Na tela externa, coloca o dock na faixa livre ao lado das lentes, se ela existir.",
                    checked = settings.useCutoutArea,
                    onChange = { vm.set(PrefKeys.USE_CUTOUT_AREA, it) },
                )
            }
        }
        item {
            SettingsCard("Ícones") {
                SliderRow(
                    "Tamanho",
                    settings.iconScale,
                    SettingsLimits.ICON_SCALE_MIN..SettingsLimits.ICON_SCALE_MAX,
                    label = { "${(it * 100).roundToInt()}%" },
                ) { vm.set(PrefKeys.ICON_SCALE, it) }
                ChoiceRow(
                    "Forma",
                    listOf(
                        IconShape.SQUIRCLE to "Squircle",
                        IconShape.CIRCLE to "Círculo",
                        IconShape.ROUNDED to "Arredondado",
                        IconShape.SQUARE to "Quadrado",
                        IconShape.TEARDROP to "Gota",
                        IconShape.HEXAGON to "Hexágono",
                    ),
                    settings.iconShape,
                ) { vm.setEnum(PrefKeys.ICON_SHAPE, it) }
                ChoiceRow(
                    "Estilo",
                    listOf(IconStyle.ORIGINAL to "Original", IconStyle.MONO_GLASS to "Vidro monocromático"),
                    settings.iconStyle,
                ) { vm.setEnum(PrefKeys.ICON_STYLE, it) }
                InfoText("O estilo monocromático vale para apps que oferecem ícone temático; os demais ficam como estão.")
                ChoiceRow(
                    "Pacote de ícones",
                    listOf("" to "Nenhum") + packs.map { it.packageName to it.label },
                    settings.iconPack,
                ) { vm.set(PrefKeys.ICON_PACK, it) }
                if (packs.isEmpty()) InfoText("Nenhum pacote de ícones instalado. Pacotes no formato ADW/Nova da Play Store funcionam.")
            }
        }
        item {
            SettingsCard("Gaveta de apps") {
                ChoiceRow("Colunas", rangeOptions(SettingsLimits.drawerColumns) { it }, settings.drawerColumns) {
                    vm.set(PrefKeys.DRAWER_COLUMNS, it)
                }
                ChoiceRow(
                    "Ordem",
                    listOf(DrawerSort.NAME to "Alfabética", DrawerSort.USAGE to "Mais usados"),
                    settings.drawerSort,
                ) { vm.setEnum(PrefKeys.DRAWER_SORT, it) }
                SwitchRow(
                    title = "Abrir já com o teclado",
                    subtitle = "Foca a busca assim que a gaveta abre.",
                    checked = settings.drawerSearchOnOpen,
                    onChange = { vm.set(PrefKeys.DRAWER_SEARCH_ON_OPEN, it) },
                )
            }
        }
    }
}

@Composable
private fun GesturesPage(
    vm: SettingsViewModel,
    settings: Settings,
    wallpaper: ImageBitmap?,
    permissions: PermissionState,
    onBack: () -> Unit,
) {
    val options = GestureAction.entries.map { it to gestureLabel(it) }
    SettingsScaffold(SettingsPage.GESTURES.title, settings, wallpaper, onBack) {
        item {
            SettingsCard {
                InfoText("Deslizar para os lados sempre troca de página; a primeira página à esquerda é o painel \"Hoje\".")
                if (!permissions.accessibility) {
                    InfoText("As ações \"do sistema\", \"Bloquear tela\" e \"Apps recentes\" precisam do serviço de acessibilidade ativo.")
                }
            }
        }
        item {
            SettingsCard("Deslizar para cima") {
                ChoiceRow("Ação", options, settings.gestureSwipeUp) { vm.setEnum(PrefKeys.GESTURE_SWIPE_UP, it) }
            }
        }
        item {
            SettingsCard("Deslizar para baixo") {
                ChoiceRow("Ação", options, settings.gestureSwipeDown) { vm.setEnum(PrefKeys.GESTURE_SWIPE_DOWN, it) }
            }
        }
        item { ControlCenterCard(permissions) }
        item {
            SettingsCard("Dois toques no fundo") {
                ChoiceRow("Ação", options, settings.gestureDoubleTap) { vm.setEnum(PrefKeys.GESTURE_DOUBLE_TAP, it) }
            }
        }
        item {
            SettingsCard("Sempre ativos") {
                InfoText("Toque longo em um ícone: menu e arrastar. Toque longo no fundo: widgets, papel de parede e configurações.")
            }
        }
    }
}

@Composable
private fun LockPage(
    vm: SettingsViewModel,
    settings: Settings,
    wallpaper: ImageBitmap?,
    permissions: PermissionState,
    onBack: () -> Unit,
    openPermissions: () -> Unit,
) {
    val context = LocalContext.current
    SettingsScaffold(SettingsPage.LOCK.title, settings, wallpaper, onBack) {
        item {
            SettingsCard("Modo") {
                ChoiceRow(
                    "Tela de bloqueio do Cristal",
                    listOf(LockMode.OFF to "Desligada", LockMode.ACTIVITY to "Activity (recomendado)", LockMode.OVERLAY to "Sobreposição"),
                    settings.lockMode,
                ) { vm.setEnum(PrefKeys.LOCK_MODE, it) }
                InfoText(
                    when (settings.lockMode) {
                        LockMode.OFF -> "O aparelho usa só a tela de bloqueio original."
                        LockMode.ACTIVITY -> "Uma tela do Cristal aparece sobre o bloqueio do sistema, pelo mecanismo oficial do Android (o mesmo de alarmes e chamadas)."
                        LockMode.OVERLAY -> "Experimental: uma janela de acessibilidade cobre o bloqueio. Use se o modo Activity não aparecer na tela externa do seu aparelho."
                    },
                )
                if (settings.lockMode != LockMode.OFF && !permissions.accessibility) {
                    NavRow(Icons.Rounded.Security, "Falta ativar a acessibilidade", "Sem ela o Android não deixa abrir a tela com o aparelho apagado.", openPermissions)
                }
                GlassButton("Pré-visualizar", Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    context.startSafely(Intent(context, LockActivity::class.java).putExtra(LockActivity.EXTRA_PREVIEW, true))
                }
            }
        }
        item {
            SettingsCard("Conteúdo") {
                SwitchRow(
                    title = "Só na tela externa",
                    subtitle = "Com o aparelho aberto, fica a tela de bloqueio original.",
                    checked = settings.lockCoverOnly,
                    onChange = { vm.set(PrefKeys.LOCK_COVER_ONLY, it) },
                )
                SwitchRow(title = "Mostrar notificações", checked = settings.lockShowNotifications, onChange = { vm.set(PrefKeys.LOCK_SHOW_NOTIFICATIONS, it) })
                SwitchRow(
                    title = "Ocultar conteúdo sensível",
                    subtitle = "Mostra só o nome do app até você desbloquear. Notificações marcadas como secretas nunca aparecem.",
                    checked = settings.lockHideSensitive,
                    onChange = { vm.set(PrefKeys.LOCK_HIDE_SENSITIVE, it) },
                    enabled = settings.lockShowNotifications,
                )
                SwitchRow(title = "Atalhos de lanterna e câmera", checked = settings.lockShortcuts, onChange = { vm.set(PrefKeys.LOCK_SHORTCUTS, it) })
                SwitchRow(title = "Controle de música", checked = settings.lockMusic, onChange = { vm.set(PrefKeys.LOCK_MUSIC, it) })
            }
        }
        item {
            SettingsCard("Relógio (bloqueio da tela interna)") {
                Text("Fonte do relógio", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                ClockFontPicker(settings.lockClockFont, settings.lockClockLayout) { vm.setEnum(PrefKeys.LOCK_CLOCK_FONT, it) }
                InfoText("A mesma fonte vale para o widget de relógio da tela inicial.")
                SwitchRow(
                    title = "Relógio em cartão de vidro",
                    checked = settings.lockClockCard,
                    onChange = { vm.set(PrefKeys.LOCK_CLOCK_CARD, it) },
                    subtitle = "Desligado, os números ficam grandes direto sobre a foto (com o efeito de profundidade isso é automático).",
                )
                ChoiceRow("Forma", ClockLayout.entries.map { it to it.label }, settings.lockClockLayout) { vm.setEnum(PrefKeys.LOCK_CLOCK_LAYOUT, it) }
                SliderRow("Tamanho", settings.lockClockScale, 0.6f..1.8f, label = { "${(it * 100).roundToInt()}%" }) {
                    vm.set(PrefKeys.LOCK_CLOCK_SCALE, it)
                }
                GlassButton("Pré-visualizar e ajustar o tamanho", Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    context.startSafely(Intent(context, LockActivity::class.java).putExtra(LockActivity.EXTRA_PREVIEW, true))
                }
                InfoText("Na pré-visualização, arraste a alça embaixo do relógio para aumentar ou diminuir, como no iPhone.")
                SwitchRow(
                    title = "Formato adaptativo",
                    checked = settings.lockClockAdaptive,
                    onChange = { vm.set(PrefKeys.LOCK_CLOCK_ADAPTIVE, it) },
                    subtitle = "Ajusta tamanho e forma ao espaço livre; com profundidade, cresce até passar por trás do objeto da foto.",
                )
            }
        }
        item {
            SettingsCard("Widgets e música no bloqueio") {
                InfoText("Escolha até 4 widgets pequenos para a tela de bloqueio interna.")
                for (w in LockWidget.entries) {
                    val on = w in settings.lockWidgets
                    SwitchRow(
                        title = w.label,
                        checked = on,
                        enabled = on || settings.lockWidgets.size < 4,
                        onChange = { checked ->
                            val next = if (checked) settings.lockWidgets + w else settings.lockWidgets - w
                            vm.set(PrefKeys.LOCK_WIDGETS, next.joinToString(",") { it.name })
                        },
                    )
                }
                if (settings.lockWidgets.isNotEmpty()) {
                    ChoiceRow("Posição dos widgets", LockPlacement.entries.map { it to it.label }, settings.lockWidgetsPosition) {
                        vm.setEnum(PrefKeys.LOCK_WIDGETS_POSITION, it)
                    }
                }
                ChoiceRow(
                    "Player de música",
                    listOf<Pair<LockPlacement?, String>>(LockPlacement.BELOW_CLOCK to "Abaixo do relógio", LockPlacement.BOTTOM to "Embaixo", null to "Esconder"),
                    if (settings.lockMusic) settings.lockMediaPosition else null,
                ) { choice ->
                    if (choice == null) {
                        vm.set(PrefKeys.LOCK_MUSIC, false)
                    } else {
                        vm.set(PrefKeys.LOCK_MUSIC, true)
                        vm.setEnum(PrefKeys.LOCK_MEDIA_POSITION, choice)
                    }
                }
            }
        }
        item {
            SettingsCard("Cover Clock (bloqueio da tela externa)") {
                InfoText("Na tela externa o bloqueio mostra só papel de parede, relógio e notificações.")
                Text("Fonte do relógio", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                ClockFontPicker(settings.coverClockFont, settings.coverClockLayout) { vm.setEnum(PrefKeys.COVER_CLOCK_FONT, it) }
                ChoiceRow(
                    "Forma",
                    ClockLayout.entries.map { it to it.label },
                    settings.coverClockLayout,
                ) { vm.setEnum(PrefKeys.COVER_CLOCK_LAYOUT, it) }
                SwitchRow(
                    title = "Formato adaptativo",
                    checked = settings.coverClockAdaptive,
                    onChange = { vm.set(PrefKeys.COVER_CLOCK_ADAPTIVE, it) },
                    subtitle = "O relógio cresce, estica e empilha as horas sobre os minutos para ocupar o espaço livre. Com o efeito de profundidade, passa por trás da pessoa da foto.",
                )
                SliderRow(
                    "Tamanho",
                    settings.coverClockScale,
                    0.6f..1.8f,
                    label = { "${(it * 100).roundToInt()}%" },
                ) { vm.set(PrefKeys.COVER_CLOCK_SCALE, it) }
                ChoiceRow(
                    "Posição",
                    listOf(CoverClockPosition.TOP to "Em cima", CoverClockPosition.CENTER to "No meio", CoverClockPosition.BOTTOM to "Embaixo"),
                    settings.coverClockPosition,
                ) { vm.setEnum(PrefKeys.COVER_CLOCK_POSITION, it) }
                ChoiceRow(
                    "Notificações exibidas",
                    listOf(0 to "Nenhuma", 1 to "1", 2 to "2", 3 to "3", 4 to "4"),
                    settings.coverLockMaxNotifications,
                ) { vm.set(PrefKeys.COVER_LOCK_MAX_NOTIFICATIONS, it) }
                GlassButton("Pré-visualizar o Cover Clock", Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    context.startSafely(
                        Intent(context, LockActivity::class.java)
                            .putExtra(LockActivity.EXTRA_PREVIEW, true)
                            .putExtra(LockActivity.EXTRA_PREVIEW_COVER, true),
                    )
                }
                InfoText("O papel de parede dele é escolhido no Wallpaper Studio > Tela externa > Bloqueio.")
            }
        }
        item {
            SettingsCard("Segurança") {
                InfoText(
                    "O Cristal não substitui nem desliga o bloqueio do Android: ele só desenha por cima. Para destravar, " +
                        "deslize para cima e o próprio sistema pede o PIN, o padrão ou a digital. O app nunca vê sua credencial. " +
                        "Se o Cristal fechar por qualquer motivo, a tela de bloqueio original está logo atrás.",
                )
                InfoText(
                    "Limitação conhecida: com uma tela sobre o bloqueio, o leitor de digital lateral só é ativado depois que " +
                        "você desliza para desbloquear.",
                )
            }
        }
    }
}

@Composable
private fun CoverPage(
    vm: SettingsViewModel,
    settings: Settings,
    wallpaper: ImageBitmap?,
    permissions: PermissionState,
    cover: CoverState,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val shizukuReady = permissions.shizuku == ShizukuStatus.READY
    SettingsScaffold(SettingsPage.COVER.title, settings, wallpaper, onBack) {
        item {
            SettingsCard("Usar o Cristal na tela externa") {
                InfoText(
                    "Há três caminhos. Use o que funcionar no seu aparelho; eles não se atrapalham.",
                )
                Text("1. Abrir agora", fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
                InfoText("Envia o Cristal para a tela externa. Depois é só fechar o aparelho.")
                GlassButton("Abrir na tela externa", Modifier.fillMaxWidth(), primary = true) { vm.openOnCover() }

                Text("2. Bloco nos Ajustes rápidos", fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp))
                InfoText("Adiciona um botão \"Cristal Cover\" aos Ajustes rápidos. Na tela externa, puxe os ajustes para baixo e toque nele.")
                GlassButton("Adicionar o bloco", Modifier.fillMaxWidth()) {
                    val ok = runCatching {
                        val manager = context.getSystemService(android.app.StatusBarManager::class.java)
                        manager.requestAddTileService(
                            android.content.ComponentName(context, app.cristal.cover.service.CoverTileService::class.java),
                            "Cristal Cover",
                            android.graphics.drawable.Icon.createWithResource(context, app.cristal.cover.R.drawable.ic_tile_cristal),
                            context.mainExecutor,
                        ) { }
                    }.isSuccess
                    if (!ok) vm.message("Adicione manualmente: edite os Ajustes rápidos e arraste \"Cristal Cover\".")
                }

                Text("3. Painel de apps da Motorola", fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp))
                InfoText("Em Configurações > Tela externa > Painéis, edite o painel Apps e adicione o Cristal Cover.")
            }
        }
        item {
            SettingsCard("Telas detectadas") {
                if (cover.displays.isEmpty()) InfoText("Nenhuma tela informada pelo sistema.")
                for (d in cover.displays) {
                    val isCover = cover.cover?.id == d.id
                    Text(
                        "Tela ${d.id}${if (isCover) " · externa" else ""}${if (d.isDefault) " · principal" else ""}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    InfoText("${d.name} · ${d.widthPx}×${d.heightPx} · ${d.densityDpi} dpi · até ${d.maxRefreshRate.roundToInt()} Hz")
                }
                val folded = when (cover.folded) {
                    true -> "fechado"
                    false -> "aberto"
                    null -> "não informado pelo aparelho"
                }
                InfoText("Estado do aparelho: $folded.")
                ChoiceRow(
                    "Qual é a tela externa",
                    listOf(-1 to "Detectar") + cover.displays.filter { it.id != Display.DEFAULT_DISPLAY }.map { it.id to "Tela ${it.id}" },
                    settings.coverDisplayOverride,
                ) { vm.set(PrefKeys.COVER_DISPLAY_OVERRIDE, it) }
            }
        }
        item {
            SettingsCard("Launcher na tela externa") {
                SwitchRow(
                    title = "Assumir a tela externa",
                    subtitle = "Abre o Cristal sozinho ao fechar o aparelho e sempre que a tela inicial da Motorola aparecer na " +
                        "tela externa. Precisa da acessibilidade. Para ver a tela original, toque longo no fundo > Tela Motorola.",
                    checked = settings.coverTakeover,
                    onChange = { vm.set(PrefKeys.COVER_TAKEOVER, it) },
                    enabled = permissions.accessibility || settings.coverTakeover,
                )
                GlassButton("Configurações da tela externa (sistema)", Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    val opened = vm.center.externalDisplaySettingsIntents().any { context.startSafely(it) }
                    if (!opened) vm.message("Não foi possível abrir as configurações do sistema.")
                }
                InfoText("Na Motorola, os apps da tela externa são escolhidos em Configurações > Tela externa > Painéis > Apps.")
            }
        }
        item {
            SettingsCard("Apps de terceiros") {
                SwitchRow(
                    title = "Avisar sobre apps incompatíveis",
                    subtitle = "Antes de abrir um app que tende a não caber na tela externa.",
                    checked = settings.warnIncompatible,
                    onChange = { vm.set(PrefKeys.WARN_INCOMPATIBLE, it) },
                )
                SwitchRow(
                    title = "Liberar apps automaticamente",
                    subtitle = "Antes de abrir, pede ao serviço da Motorola para permitir o app na tela externa (via Shizuku). " +
                        "A liberação vale até reiniciar o aparelho.",
                    checked = settings.autoAllowOnCover,
                    onChange = { vm.set(PrefKeys.AUTO_ALLOW_ON_COVER, it) },
                )
                SwitchRow(
                    title = "Ignorar orientação pedida pelos apps",
                    subtitle = "Faz apps \"só deitados\" ou \"só em pé\" ocuparem a tela externa inteira (via Shizuku).",
                    checked = settings.ignoreOrientationOnCover,
                    onChange = { vm.setIgnoreOrientation(it) },
                    enabled = shizukuReady,
                )
                InfoText(
                    if (shizukuReady) {
                        "Shizuku ativo. Escala e redimensionamento por app ficam em: toque longo no ícone > Opções na tela externa."
                    } else {
                        "Sem Shizuku, o Cristal abre os apps pelo caminho normal do Android e avisa quando o sistema bloquear."
                    },
                )
            }
        }
    }
}

@Composable
private fun WeatherPage(
    vm: SettingsViewModel,
    settings: Settings,
    wallpaper: ImageBitmap?,
    permissions: PermissionState,
    onBack: () -> Unit,
) {
    val cities by vm.cities.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.refreshPermissions()
        if (granted) vm.setUseLocation(true) else vm.message("Sem a permissão, escolha a cidade pelo nome.")
    }
    SettingsScaffold(SettingsPage.WEATHER.title, settings, wallpaper, onBack) {
        item {
            SettingsCard("Agora") {
                val data = weather.data
                if (data != null) {
                    Text(
                        "${formatTemp(data.tempC, settings.tempUnit)} · ${weatherLabel(data.code)}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    InfoText(data.place.ifBlank { "Local atual (aproximado)" })
                } else {
                    InfoText("Escolha uma cidade abaixo para ver o clima nos widgets.")
                }
                GlassButton("Atualizar agora", Modifier.fillMaxWidth().padding(top = 4.dp)) { vm.refreshWeather() }
            }
        }
        item {
            SettingsCard("Lugar") {
                SwitchRow(
                    title = "Usar minha localização aproximada",
                    subtitle = "Usa a última posição conhecida do aparelho, arredondada para cerca de 1 km.",
                    checked = settings.weatherUseLocation && permissions.location,
                    onChange = { enabled ->
                        if (enabled && !permissions.location) {
                            runCatching { locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
                        } else {
                            vm.setUseLocation(enabled)
                        }
                    },
                )
                if (settings.weatherCity.isNotBlank()) InfoText("Cidade escolhida: ${settings.weatherCity}")
                GlassTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        vm.searchCity(it)
                    },
                    placeholder = "Buscar cidade",
                    leadingIcon = Icons.Rounded.Search,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
                for (city in cities) {
                    Text(
                        city.display,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                query = ""
                                vm.pickCity(city)
                            }
                            .padding(vertical = 10.dp),
                    )
                }
            }
        }
        item {
            SettingsCard("Unidade") {
                ChoiceRow(
                    "Temperatura",
                    listOf(TempUnit.CELSIUS to "Celsius (°C)", TempUnit.FAHRENHEIT to "Fahrenheit (°F)"),
                    settings.tempUnit,
                ) { vm.setEnum(PrefKeys.TEMP_UNIT, it) }
                InfoText("Dados do Open-Meteo (open-meteo.com). O app envia apenas latitude e longitude arredondadas.")
            }
        }
    }
}

@Composable
private fun PermissionsPage(
    vm: SettingsViewModel,
    settings: Settings,
    wallpaper: ImageBitmap?,
    permissions: PermissionState,
    onBack: () -> Unit,
    onExit: () -> Unit,
) {
    SettingsScaffold(SettingsPage.PERMISSIONS.title, settings, wallpaper, onBack) {
        item { SettingsCard("Acessos") { PermissionList(vm, permissions) } }
        item { SettingsCard("Shizuku (opcional)") { ShizukuSection(vm, permissions) } }
        item {
            SettingsCard {
                GlassButton("Refazer a configuração guiada", Modifier.fillMaxWidth()) {
                    vm.redoOnboarding()
                    vm.message("A configuração guiada abrirá na próxima vez que você abrir o launcher.")
                    onExit()
                }
            }
        }
    }
}

/** Lista completa de acessos; usada nas configurações. */
@Composable
fun PermissionList(vm: SettingsViewModel, permissions: PermissionState) {
    CorePermissions(vm, permissions)
    OptionalPermissions(vm, permissions)
}

/** Os dois acessos que destravam a maior parte do app. */
@Composable
fun CorePermissions(vm: SettingsViewModel, permissions: PermissionState) {
    val context = LocalContext.current
    PermissionRow(
        title = "Acesso às notificações",
        description = "Para mostrar notificações no painel e na tela de bloqueio e controlar a música. Nada sai do aparelho.",
        granted = permissions.notificationAccess,
        actionLabel = "Ativar",
        onAction = {
            if (!context.startSafely(vm.center.notificationAccessIntent())) {
                context.startSafely(vm.center.notificationAccessFallbackIntent())
            }
        },
    )
    PermissionRow(
        title = "Serviço de acessibilidade",
        description = "Para bloquear a tela por gesto, abrir os painéis do sistema, assumir a tela externa e mostrar a " +
            "tela de bloqueio. O serviço não lê o conteúdo das telas: só sabe qual app está em primeiro plano.",
        granted = permissions.accessibility,
        actionLabel = "Abrir acessibilidade",
        onAction = { context.startSafely(vm.center.accessibilityIntent()) },
    )
    if (!permissions.notificationAccess || !permissions.accessibility) {
        InfoText(
            "Apareceu \"Configuração restrita\"? É a proteção do Android para apps instalados por APK. Abra os " +
                "detalhes do app, toque nos três pontinhos no canto e escolha \"Permitir configurações restritas\". Depois volte aqui.",
        )
        Chip("Abrir detalhes do app", selected = false) { context.startSafely(vm.center.appDetailsIntent()) }
    }
}

@Composable
fun OptionalPermissions(vm: SettingsViewModel, permissions: PermissionState) {
    val context = LocalContext.current
    val calendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshPermissions() }
    val location = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshPermissions() }
    PermissionRow(
        title = "Agenda",
        description = "Para o widget de próximos compromissos (somente leitura).",
        granted = permissions.calendar,
        actionLabel = "Permitir",
        onAction = { runCatching { calendar.launch(Manifest.permission.READ_CALENDAR) } },
        optional = true,
    )
    PermissionRow(
        title = "Localização aproximada",
        description = "Para o clima do lugar onde você está. Você também pode escolher a cidade pelo nome.",
        granted = permissions.location,
        actionLabel = "Permitir",
        onAction = { runCatching { location.launch(Manifest.permission.ACCESS_COARSE_LOCATION) } },
        optional = true,
    )
    PermissionRow(
        title = "Launcher padrão",
        description = "Só se quiser o Cristal também como tela inicial do aparelho aberto. Não é necessário para a tela externa.",
        granted = permissions.defaultHome,
        actionLabel = "Escolher launcher",
        onAction = { context.startSafely(vm.center.homeSettingsIntent()) },
        optional = true,
    )
}

@Composable
fun ShizukuSection(vm: SettingsViewModel, permissions: PermissionState) {
    val context = LocalContext.current
    val busy by vm.busy.collectAsStateWithLifecycle()
    val status = when (permissions.shizuku) {
        ShizukuStatus.NOT_INSTALLED -> "Não instalado"
        ShizukuStatus.NOT_RUNNING -> "Instalado, mas parado"
        ShizukuStatus.NO_PERMISSION -> "Rodando; falta autorizar o Cristal"
        ShizukuStatus.READY -> "Pronto"
    }
    RowTitle("Estado", status)
    InfoText(
        "O Shizuku dá ao Cristal os mesmos poderes do ADB, sem root e sem computador. Com ele o app libera apps na " +
            "tela externa, ajusta a escala, alterna Wi-Fi/Bluetooth e concede os acessos acima em um toque.",
    )
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (permissions.shizuku) {
            ShizukuStatus.NOT_INSTALLED -> GlassButton("Baixar o Shizuku", Modifier.weight(1f), primary = true) {
                context.startSafely(vm.center.shizukuDownloadIntent())
            }

            ShizukuStatus.NOT_RUNNING -> {
                GlassButton("Abrir o Shizuku", Modifier.weight(1f), primary = true) {
                    vm.center.shizukuAppIntent()?.let { context.startSafely(it) }
                }
                GlassButton("Opções do desenvolvedor", Modifier.weight(1f)) {
                    context.startSafely(vm.center.developerSettingsIntent())
                }
            }

            ShizukuStatus.NO_PERMISSION -> GlassButton("Autorizar o Cristal", Modifier.weight(1f), primary = true) {
                vm.requestShizukuPermission()
            }

            ShizukuStatus.READY -> GlassButton(
                if (busy) "Concedendo…" else "Conceder acessos automaticamente",
                Modifier.weight(1f),
                primary = true,
            ) { if (!busy) vm.grantViaShizuku() }
        }
    }
    if (permissions.shizuku == ShizukuStatus.NOT_RUNNING) {
        InfoText(
            "Para iniciar sem computador: ative as Opções do desenvolvedor, ligue a \"Depuração por Wi-Fi\", e no " +
                "Shizuku toque em Parear e depois em Iniciar. É preciso repetir o Iniciar depois de reiniciar o aparelho.",
        )
    }
    if (permissions.secureSettings) {
        InfoText("O Cristal já consegue religar os próprios serviços sozinho depois de uma atualização.")
    }
}

@Composable
private fun BackupPage(vm: SettingsViewModel, settings: Settings, wallpaper: ImageBitmap?, onBack: () -> Unit) {
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportBackup(uri)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importBackup(uri)
    }
    val busy by vm.busy.collectAsStateWithLifecycle()
    SettingsScaffold(SettingsPage.BACKUP.title, settings, wallpaper, onBack) {
        item {
            SettingsCard {
                InfoText(
                    "O backup é um arquivo .json com todas as preferências, a tela inicial (páginas, dock, pastas e " +
                        "widgets) e os ajustes por app. A imagem do papel de parede não entra; escolha-a de novo depois de restaurar.",
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton(if (busy) "Aguarde…" else "Exportar", Modifier.weight(1f), primary = true) {
                        if (!busy) {
                            runCatching { exporter.launch(BackupManager.SUGGESTED_NAME) }
                                .onFailure { vm.message("Não foi possível abrir o seletor de arquivos nesta tela.") }
                        }
                    }
                    GlassButton("Restaurar", Modifier.weight(1f)) {
                        if (!busy) {
                            runCatching { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
                                .onFailure { vm.message("Não foi possível abrir o seletor de arquivos nesta tela.") }
                        }
                    }
                }
                InfoText("Restaurar substitui a configuração atual. Dica: faça isso com o aparelho aberto, na tela principal.")
            }
        }
    }
}

@Composable
private fun HiddenAppsPage(vm: SettingsViewModel, settings: Settings, wallpaper: ImageBitmap?, onBack: () -> Unit) {
    val hidden by vm.hiddenApps.collectAsStateWithLifecycle()
    SettingsScaffold(SettingsPage.HIDDEN.title, settings, wallpaper, onBack) {
        item {
            SettingsCard {
                if (hidden.isEmpty()) {
                    InfoText("Nenhum app oculto. Para ocultar, toque longo em um app na gaveta e escolha \"Ocultar da gaveta\".")
                }
                for (app in hidden) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIconImage(app = app, size = 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(app.label, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
                        Chip("Reexibir", selected = false) { vm.unhide(app) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutPage(settings: Settings, wallpaper: ImageBitmap?, onBack: () -> Unit) {
    SettingsScaffold(SettingsPage.ABOUT.title, settings, wallpaper, onBack) {
        item {
            SettingsCard {
                Text("Cristal Cover", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                InfoText("Versão ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                InfoText("Launcher e tela de bloqueio em vidro líquido para a tela externa de dobráveis flip.")
            }
        }
        item {
            SettingsCard("Privacidade") {
                InfoText(
                    "O app não tem anúncios, contas nem telemetria. As notificações são lidas e mostradas apenas no " +
                        "aparelho. A única conexão de rede é a consulta de clima ao Open-Meteo.",
                )
            }
        }
        item {
            SettingsCard("Limitações do Android e da Motorola") {
                InfoText("• Nenhum app pode substituir a tela de bloqueio do sistema; o Cristal desenha por cima dela.")
                InfoText("• O gesto de início na tela externa sempre leva ao launcher da Motorola; \"Assumir a tela externa\" reabre o Cristal em seguida.")
                InfoText("• A Motorola decide quais apps podem aparecer na tela externa; a liberação via Shizuku dura até o próximo reinício.")
                InfoText("• Apps comuns não podem alternar Wi-Fi, Bluetooth ou dados; sem Shizuku o Cristal abre o painel do sistema.")
                InfoText("• O vidro refrata o papel de parede do próprio Cristal; o Android não permite ler o papel de parede do sistema.")
            }
        }
    }
}

/** Explica a Liquid Control Center e libera o que ela precisa para mexer no sistema. */
@Composable
private fun ControlCenterCard(permissions: PermissionState) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    val canWrite = remember(tick) { runCatching { android.provider.Settings.System.canWrite(context) }.getOrDefault(false) }
    val dndAccess = remember(tick) {
        runCatching {
            context.getSystemService(android.app.NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true
        }.getOrDefault(false)
    }
    fun open(intent: android.content.Intent) {
        runCatching { context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    SettingsCard("Liquid Control Center") {
        InfoText(
            "Deslize para baixo na tela inicial para abrir. Toque no lápis para editar: adicionar e remover controles, " +
                "mudar o tamanho, criar páginas e escolher uma predefinição (Liquid Flip, iOS ou One UI). " +
                "A tela interna e a externa têm centrais separadas.",
        )
        InfoText(
            "Brilho, rotação e Não perturbe funcionam depois de liberar abaixo. Wi-Fi, Bluetooth, dados, modo avião, " +
                "localização, NFC, modo escuro e economia de bateria só podem ser ligados direto com o Shizuku; sem ele, " +
                "o botão abre o ajuste do sistema. Toque longo em qualquer controle abre o ajuste dele.",
        )
        SwitchRow(
            "Alterar configurações do sistema",
            canWrite,
            {
                open(
                    android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        android.net.Uri.parse("package:${context.packageName}"),
                    ),
                )
            },
            subtitle = if (canWrite) "Liberado: brilho e rotação pela central" else "Toque para liberar brilho e rotação",
        )
        SwitchRow(
            "Acesso ao Não perturbe",
            dndAccess,
            { open(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) },
            subtitle = if (dndAccess) "Liberado" else "Toque para liberar o botão Não perturbe",
        )
        if (permissions.shizuku != app.cristal.cover.data.shizuku.ShizukuStatus.READY) {
            InfoText("Shizuku desligado: os botões de rádio abrem o ajuste do sistema em vez de ligar direto.")
        }
    }
}

@Composable
private fun IslandPage(
    vm: SettingsViewModel,
    settings: Settings,
    wallpaper: ImageBitmap?,
    permissions: PermissionState,
    onBack: () -> Unit,
    openPermissions: () -> Unit,
) {
    SettingsScaffold(SettingsPage.ISLAND.title, settings, wallpaper, onBack) {
        item {
            SettingsCard {
                InfoText(
                    "Uma cápsula preta em volta da câmera da tela interna, por cima de qualquer app, como a Dynamic Island do " +
                        "iPhone. Mostra a música (capa e ondinha), chamadas e timers em andamento, o carregamento, o modo " +
                        "silencioso e as notificações que chegam. Toque para abrir; segure ou puxe para baixo para expandir a " +
                        "música; deslize para cima para recolher.",
                )
                SwitchRow(
                    title = "Ativar a Liquid Island",
                    checked = settings.islandEnabled,
                    onChange = { vm.set(PrefKeys.ISLAND_ENABLED, it) },
                    subtitle = if (permissions.accessibility) null else "Precisa do serviço de acessibilidade do Cristal ligado.",
                )
                if (!permissions.accessibility) {
                    GlassButton("Ligar a acessibilidade", Modifier.fillMaxWidth().padding(top = 4.dp), primary = true, onClick = openPermissions)
                }
                SwitchRow(
                    title = "Mostrar sempre",
                    checked = settings.islandAlwaysVisible,
                    onChange = { vm.set(PrefKeys.ISLAND_ALWAYS, it) },
                    subtitle = "A cápsula fica visível mesmo sem nada acontecendo.",
                )
                GlassButton("Ver demonstração", Modifier.fillMaxWidth().padding(top = 6.dp)) { vm.islandDemo() }
            }
        }
        item { DuplicateAlertsCard(vm, permissions) }
        item {
            SettingsCard("O que aparece") {
                SwitchRow(title = "Música", checked = settings.islandMedia, onChange = { vm.set(PrefKeys.ISLAND_MEDIA, it) })
                SwitchRow(title = "Notificações que chegam", checked = settings.islandAlerts, onChange = { vm.set(PrefKeys.ISLAND_ALERTS, it) })
                SwitchRow(title = "Chamadas e timers", checked = settings.islandCalls, onChange = { vm.set(PrefKeys.ISLAND_CALLS, it) })
                SwitchRow(title = "Carregando", checked = settings.islandCharging, onChange = { vm.set(PrefKeys.ISLAND_CHARGING, it) })
                SwitchRow(title = "Som e silencioso", checked = settings.islandRinger, onChange = { vm.set(PrefKeys.ISLAND_RINGER, it) })
                InfoText("Música e notificações precisam do acesso às notificações. Com o aparelho bloqueado, as notificações mostram só o nome do app.")
            }
        }
        item {
            SettingsCard("Alinhamento") {
                SliderRow("Altura", settings.islandOffsetY, -16f..24f, label = { "${it.roundToInt()} dp" }) { vm.set(PrefKeys.ISLAND_OFFSET_Y, it) }
                InfoText("Use para centralizar a câmera dentro da cápsula, se precisar.")
            }
        }
    }
}

/**
 * Notificação aparecendo duas vezes (pop-up do Android e ilha): explica e resolve. Com o Shizuku,
 * desliga os pop-ups do sistema em um toque; sem ele, abre o ajuste exato de cada app que avisou.
 */
@Composable
private fun DuplicateAlertsCard(vm: SettingsViewModel, permissions: PermissionState) {
    val context = LocalContext.current
    val popups by vm.systemPopups.collectAsStateWithLifecycle()
    val apps by vm.popupApps.collectAsStateWithLifecycle()
    LaunchedEffect(permissions.shizuku) { if (permissions.shizuku == ShizukuStatus.READY) vm.refreshSystemPopups() }
    SettingsCard("Notificação aparecendo duas vezes?") {
        InfoText(
            "Quando chega um aviso importante, o Android mostra o pop-up dele no alto da tela e a ilha mostra o mesmo " +
                "aviso. Deixe só um dos dois.",
        )
        when (permissions.shizuku) {
            ShizukuStatus.READY -> SwitchRow(
                title = "Pop-ups do sistema",
                checked = popups ?: true,
                onChange = { vm.setSystemPopups(it) },
                subtitle = "Desligado, só a ilha avisa. As notificações continuam na barra, na central e no bloqueio. " +
                    "Vale para todos os apps e dá para religar aqui.",
            )
            ShizukuStatus.NO_PERMISSION -> {
                InfoText("Com o Shizuku, dá para desligar os pop-ups de todos os apps em um toque.")
                GlassButton("Permitir o Shizuku", Modifier.fillMaxWidth().padding(top = 4.dp)) { vm.requestShizukuPermission() }
            }
            else -> InfoText(
                "Sem o Shizuku, desligue por app: Configurações do Android > Notificações > Notificações de apps > " +
                    "escolha o app > toque no tipo de aviso > desligue a opção de pop-up (aparecer na tela).",
            )
        }
        if (apps.isNotEmpty()) {
            RowTitle("Avisando com pop-up agora")
            apps.forEach { item ->
                NavRow(Icons.Rounded.NotificationsActive, item.appLabel, "Abrir a opção de pop-up deste aviso") {
                    context.openNotificationChannel(item.packageName, item.channelId)
                }
            }
        } else {
            GlassButton("Abrir as notificações dos apps", Modifier.fillMaxWidth().padding(top = 6.dp)) {
                context.openNotificationChannel(null, null)
            }
        }
        InfoText("Prefere o pop-up do sistema? Desligue \"Notificações que chegam\" logo abaixo.")
    }
}

/** Abre o ajuste de um canal de notificação; sem canal, o do app; sem app, a lista de todos. */
private fun android.content.Context.openNotificationChannel(pkg: String?, channel: String?) {
    val candidates = buildList {
        if (pkg != null && channel != null) {
            add(
                Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, pkg)
                    .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, channel),
            )
        }
        if (pkg != null) add(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, pkg))
        add(Intent(android.provider.Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS))
    }
    for (intent in candidates) {
        if (runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
    }
}
