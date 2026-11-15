package app.cristal.cover

import org.junit.Assert.assertTrue
import app.cristal.cover.data.prefs.LockPlacement
import app.cristal.cover.data.prefs.LockWidget
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import app.cristal.cover.ui.components.AdaptiveClock
import app.cristal.cover.data.prefs.CoverClockPosition
import app.cristal.cover.data.prefs.ClockLayout
import app.cristal.cover.data.prefs.ClockFont
import android.app.Notification
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.os.Process
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import app.cristal.cover.data.apps.AppInfo
import app.cristal.cover.data.battery.BatteryState
import app.cristal.cover.data.calendar.AgendaEvent
import app.cristal.cover.data.calendar.AgendaState
import app.cristal.cover.data.home.HomeItem
import app.cristal.cover.data.home.HomeLayout
import app.cristal.cover.data.home.WidgetKind
import app.cristal.cover.data.launch.CompatLevel
import app.cristal.cover.data.launch.CompatReason
import app.cristal.cover.data.launch.CompatReport
import app.cristal.cover.data.media.MediaState
import app.cristal.cover.data.notifications.NotificationItem
import app.cristal.cover.data.prefs.GlassQuality
import app.cristal.cover.data.prefs.IconShape
import app.cristal.cover.data.prefs.IconStyle
import app.cristal.cover.data.prefs.Settings
import app.cristal.cover.data.prefs.ThemeMode
import app.cristal.cover.data.system.QuickState
import app.cristal.cover.data.weather.DayForecast
import app.cristal.cover.data.weather.WeatherData
import app.cristal.cover.data.weather.WeatherState
import app.cristal.cover.data.weather.WeatherStatus
import app.cristal.cover.ui.components.IconLook
import app.cristal.cover.ui.components.LocalIconLook
import app.cristal.cover.ui.components.SheetState
import app.cristal.cover.ui.components.WallpaperLayer
import app.cristal.cover.ui.glass.LocalGlassBackdrop
import app.cristal.cover.ui.glass.glassBackdropSource
import app.cristal.cover.ui.glass.rememberGlassBackdrop
import app.cristal.cover.ui.launcher.AppDrawer
import app.cristal.cover.ui.launcher.AppOptionsDialog
import app.cristal.cover.ui.launcher.CompatDialog
import app.cristal.cover.ui.launcher.ContextMenu
import app.cristal.cover.ui.launcher.DragController
import app.cristal.cover.ui.launcher.FolderOverlay
import app.cristal.cover.ui.launcher.HomeCallbacks
import app.cristal.cover.ui.launcher.HomeContent
import app.cristal.cover.ui.launcher.MenuAction
import app.cristal.cover.ui.launcher.MenuState
import app.cristal.cover.ui.launcher.MenuTarget
import app.cristal.cover.ui.launcher.ControlCenter
import app.cristal.cover.ui.launcher.ControlCenterActions
import app.cristal.cover.data.control.ControlLayout
import app.cristal.cover.data.control.ControlPreset
import app.cristal.cover.data.prefs.WallpaperKind
import android.graphics.LinearGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import app.cristal.cover.ui.launcher.SafeLayout
import app.cristal.cover.ui.launcher.TodayPanel
import app.cristal.cover.ui.launcher.WidgetPicker
import app.cristal.cover.ui.lock.LockActions
import app.cristal.cover.ui.lock.LockScreen
import app.cristal.cover.ui.lock.LockUiState
import app.cristal.cover.ui.theme.CristalTheme
import app.cristal.cover.ui.widgets.WidgetActions
import app.cristal.cover.ui.widgets.WidgetData
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Teste de fumaça: monta as telas de verdade, com dados falsos, em uma tela do tamanho da tela
 * externa do Razr (cerca de 422×426 dp), e desenha tudo pelo pipeline de hardware.
 *
 * Serve para pegar erros que só aparecem em execução (layout impossível, estado inválido, shader
 * quebrado) sem precisar de aparelho. As imagens ficam em build/outputs/telas.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w422dp-h426dp-400dpi")
class ScreensSmokeTest {

    @get:Rule
    val rule = createComposeRule()

    // --- Dados falsos ------------------------------------------------------------------------------

    private val palette = listOf(0xFF34C759, 0xFF0A84FF, 0xFFFF9F0A, 0xFFFF375F, 0xFFBF5AF2, 0xFF64D2FF, 0xFFFFD60A, 0xFF30B0C7)

    private fun icon(index: Int, letter: String): Bitmap {
        val bmp = Bitmap.createBitmap(144, 144, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(palette[index % palette.size].toInt())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            textSize = 76f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        canvas.drawText(letter, 72f, 98f, paint)
        return bmp
    }

    private val names = listOf(
        "WhatsApp", "Câmera", "Chrome", "Mapas", "Spotify", "YouTube", "Gmail", "Agenda",
        "Calculadora", "Fotos", "Configurações", "Play Store", "Telefone", "Mensagens", "Relógio", "Banco",
    )

    private val apps: List<AppInfo> = names.mapIndexed { index, name ->
        val component = ComponentName("com.exemplo.app$index", "com.exemplo.app$index.Main")
        AppInfo(
            key = AppInfo.keyOf(component, 0),
            component = component,
            user = Process.myUserHandle(),
            label = name,
            icon = icon(index, name.take(1)).asImageBitmap(),
            monoIcon = null,
            maskable = true,
            isSystem = false,
            isWorkProfile = false,
        )
    }

    private fun appItem(id: Long, appIndex: Int, x: Int, y: Int) =
        HomeItem.App(id, 0, x, y, apps[appIndex], apps[appIndex].label)

    private val folder = HomeItem.Folder(50, 0, 3, 3, "Social", listOf(appItem(51, 0, 0, 0), appItem(52, 5, 1, 0), appItem(53, 6, 2, 0)))

    private val layout = HomeLayout(
        pages = listOf(
            listOf(
                HomeItem.Widget(1, 0, 0, 0, 4, 2, WidgetKind.CLOCK),
                appItem(2, 0, 0, 2), appItem(3, 1, 1, 2), appItem(4, 3, 2, 2), appItem(5, 4, 3, 2),
                appItem(6, 5, 0, 3), appItem(7, 6, 1, 3), appItem(8, 9, 2, 3), folder,
            ),
            listOf(
                HomeItem.Widget(20, 1, 0, 0, 2, 2, WidgetKind.WEATHER),
                HomeItem.Widget(21, 1, 2, 0, 2, 2, WidgetKind.BATTERY),
                HomeItem.Widget(22, 1, 0, 2, 4, 2, WidgetKind.MUSIC),
            ),
            listOf(
                HomeItem.Widget(40, 2, 0, 0, 2, 2, WidgetKind.ANALOG_CLOCK),
                HomeItem.Widget(41, 2, 2, 0, 2, 2, WidgetKind.DATE),
                HomeItem.Widget(42, 2, 0, 2, 4, 1, WidgetKind.SHORTCUTS),
                HomeItem.Widget(43, 2, 0, 3, 4, 1, WidgetKind.SEARCH),
            ),
        ),
        dock = listOf(appItem(30, 12, 0, 0), appItem(31, 13, 1, 0), appItem(32, 2, 2, 0), appItem(33, 1, 3, 0)),
    )

    private val now = System.currentTimeMillis()

    private val widgetData = WidgetData(
        battery = BatteryState(percent = 76, charging = true),
        weather = WeatherState(
            WeatherStatus.READY,
            WeatherData(
                place = "São Paulo, SP · BR", lat = -23.5, lon = -46.6, tempC = 24.3, feelsLikeC = 25.0, code = 2,
                isDay = true, humidity = 60, windKmh = 9.0,
                days = listOf(
                    DayForecast("2026-11-15", 2, 18.0, 27.0), DayForecast("2026-11-16", 61, 17.0, 23.0),
                    DayForecast("2026-11-17", 0, 16.0, 28.0), DayForecast("2026-11-18", 95, 19.0, 26.0),
                ),
                fetchedAt = now,
            ),
        ),
        media = MediaState("com.exemplo.musica", "Garota de Ipanema", "Tom Jobim", icon(4, "♪").asImageBitmap(), true, true, true),
        agenda = AgendaState(
            hasPermission = true,
            events = listOf(
                AgendaEvent(1, "Reunião do grêmio", now + 3_600_000, now + 7_200_000, false, 0xFF0A84FF.toInt(), "Sala 12"),
                AgendaEvent(2, "Prova de matemática", now + 90_000_000, now + 93_600_000, false, 0xFFFF375F.toInt(), ""),
            ),
        ),
        notificationAccess = true,
    )

    private val notifications = listOf(
        NotificationItem(
            "n1", "com.exemplo.app0", "WhatsApp", "Ana", "Oi! Você vem hoje?", now - 120_000, 0, true, true,
            Notification.VISIBILITY_PRIVATE, null, apps[0].icon, null,
        ),
        NotificationItem(
            "n2", "com.exemplo.app6", "Gmail", "Boletim escolar", "As notas do bimestre já estão disponíveis no portal.",
            now - 3_700_000, 0, true, true, Notification.VISIBILITY_PUBLIC, null, apps[6].icon, null,
        ),
    )

    private val quick = QuickState(true, false, true, false, true, false, 0.6f)

    private val callbacks = HomeCallbacks({}, {}, { _, _, _ -> }, {}, {}, {}, {})
    private val widgetActions = WidgetActions()
    private val controlActions = ControlCenterActions(
        onControl = {}, onControlSettings = {}, onBrightness = {}, onBrightnessEnd = {}, onAutoBrightness = {},
        onVolume = {}, onSaveLayout = {}, onPreset = {}, onSystemPanel = {}, onOpenNotification = {},
        onDismissNotification = {}, onClearAll = {}, onRequestNotificationAccess = {},
    )

    /** Foto de teste (gradiente) e o "objeto" dela: um círculo no meio, com o resto transparente. */
    private fun depthImages(): Pair<ImageBitmap, ImageBitmap> {
        val w = 600
        val h = 1200
        val photo = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val subject = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c1 = Canvas(photo)
        c1.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF203A8F.toInt(), 0xFF8E3B9E.toInt(), Shader.TileMode.CLAMP) })
        val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFB347.toInt() }
        c1.drawCircle(w / 2f, h * 0.36f, w * 0.3f, disc)
        Canvas(subject).drawCircle(w / 2f, h * 0.36f, w * 0.3f, disc)
        return photo.asImageBitmap() to subject.asImageBitmap()
    }

    // --- Infra -------------------------------------------------------------------------------------

    private fun shot(name: String) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/outputs/telas").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Reproduz a estrutura da tela inicial: cena (papel de parede + páginas + dock) e sobreposições. */
    @Composable
    private fun Scene(
        settings: Settings,
        initialPage: Int = 1,
        sceneLayout: HomeLayout = layout,
        overlay: @Composable () -> Unit = {},
    ) {
        CristalTheme(settings = settings) {
            CompositionLocalProvider(LocalIconLook provides IconLook(settings.iconShape, settings.iconStyle)) {
                val sceneBackdrop = rememberGlassBackdrop()
                val overlayBackdrop = rememberGlassBackdrop()
                val drag = remember { DragController() }
                val pagerState = rememberPagerState(initialPage = initialPage) { 1 + sceneLayout.pageCount }
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    Box(Modifier.fillMaxSize().glassBackdropSource(overlayBackdrop)) {
                        WallpaperLayer(
                            image = null,
                            presetIndex = settings.wallpaperPreset,
                            dim = settings.wallpaperDim,
                            modifier = Modifier.glassBackdropSource(sceneBackdrop),
                        )
                        CompositionLocalProvider(LocalGlassBackdrop provides sceneBackdrop) {
                            HomeContent(
                                settings = settings,
                                layout = sceneLayout,
                                pagerState = pagerState,
                                safe = SafeLayout(),
                                drag = drag,
                                hover = null,
                                widgetData = widgetData,
                                widgetActions = widgetActions,
                                callbacks = callbacks,
                                todayPanel = { TodayPanel(widgetData, widgetActions) },
                            )
                        }
                    }
                    CompositionLocalProvider(LocalGlassBackdrop provides overlayBackdrop) { overlay() }
                }
            }
        }
    }

    @Composable
    private fun openSheet(): SheetState {
        val state = remember { SheetState() }
        LaunchedEffect(Unit) { state.progress.snapTo(1f) }
        return state
    }

    // --- Testes ------------------------------------------------------------------------------------

    @Test
    fun `tela inicial com vidro liquido`() {
        rule.setContent { Scene(Settings(glassQuality = GlassQuality.LIQUID)) }
        shot("01-inicial-liquido")
        // Troca de página para os dois lados sem quebrar.
        rule.onRoot().performTouchInput { swipeLeft() }
        shot("02-pagina-de-widgets")
        rule.onRoot().performTouchInput { swipeLeft() }
        shot("02b-widgets-novos")
        rule.onRoot().performTouchInput { swipeRight() }
        rule.onRoot().performTouchInput { swipeRight() }
        rule.onRoot().performTouchInput { swipeRight() }
        shot("03-painel-hoje")
    }

    @Test
    fun `tela inicial nos modos blur e leve e no tema claro`() {
        rule.setContent { Scene(Settings(glassQuality = GlassQuality.BLUR, wallpaperPreset = 2, iconShape = IconShape.CIRCLE)) }
        shot("04-inicial-blur")
    }

    @Test
    fun `tela inicial no modo leve com tema claro e grade 5x5`() {
        rule.setContent {
            Scene(
                Settings(
                    glassQuality = GlassQuality.LIGHT, themeMode = ThemeMode.LIGHT, wallpaperPreset = 7,
                    gridColumns = 5, gridRows = 5, iconShape = IconShape.HEXAGON, iconStyle = IconStyle.MONO_GLASS,
                ),
            )
        }
        shot("05-inicial-leve-claro")
    }

    @Test
    fun `gaveta de apps com busca`() {
        rule.setContent {
            Scene(Settings()) {
                AppDrawer(state = openSheet(), apps = apps, settings = Settings(), safe = SafeLayout(), onAppClick = {}, onAppHold = { _, _ -> })
            }
        }
        shot("06-gaveta")
        rule.onAllNodesWithText("Buscar apps").onFirst().assertExists()
        rule.onNodeWithText("Calculadora").assertExists()
    }

    @Test
    fun `central de controle na tela externa`() {
        rule.setContent {
            Scene(Settings()) {
                ControlCenter(
                    state = openSheet(), safe = SafeLayout(), quick = quick, layout = ControlPreset.LIQUID_FLIP.build(onCover = true),
                    widgetData = widgetData, widgetActions = widgetActions, notifications = notifications,
                    notificationAccess = true, onCover = true, actions = controlActions,
                )
            }
        }
        shot("07-central-externa")
        rule.onNodeWithContentDescription("Wi-Fi").assertExists()
        rule.onNodeWithContentDescription("Brilho").assertExists()
        // Modo de edição: tocar num controle mostra as ações dele.
        rule.onNodeWithContentDescription("Editar a central").performClick()
        rule.onNodeWithText("Concluir").assertExists()
        rule.onNodeWithContentDescription("Brilho").performClick()
        rule.onNodeWithContentDescription("Mudar tamanho").assertExists()
        shot("07b-central-edicao")
    }

    @Test
    @Config(sdk = [36], qualifiers = "w411dp-h1004dp-420dpi")
    fun `central de controle na tela interna e notificacoes`() {
        val single = ControlLayout(pages = listOf(ControlPreset.IOS.build(onCover = false).pages.first()))
        rule.setContent {
            Scene(Settings().forScreen(onCover = false)) {
                ControlCenter(
                    state = openSheet(), safe = SafeLayout(), quick = quick.copy(dndOn = true, autoRotate = true),
                    layout = single, widgetData = widgetData, widgetActions = widgetActions, notifications = notifications,
                    notificationAccess = true, onCover = false, actions = controlActions,
                )
            }
        }
        shot("08-central-interna")
        rule.onAllNodesWithText("Garota de Ipanema").onFirst().assertExists()
        rule.onRoot().performTouchInput { swipeLeft() }
        rule.waitForIdle()
        shot("08b-central-notificacoes")
        rule.onNodeWithText("Ana").assertExists()
    }

    @Test
    fun `pasta aberta e menu de contexto`() {
        rule.setContent {
            Scene(Settings()) {
                FolderOverlay(folder = folder, iconScale = 1f, onAppClick = {}, onChildHold = { _, _ -> }, onRename = {}, onDismiss = {})
            }
        }
        shot("09-pasta")
        rule.onAllNodesWithText("Social").onFirst().assertExists()
    }

    @Test
    fun `menu de contexto perto do dedo`() {
        rule.setContent {
            Scene(Settings()) {
                ContextMenu(
                    state = MenuState(MenuTarget.DrawerApp(apps[0]), Offset(900f, 980f)),
                    title = apps[0].label,
                    actions = listOf(
                        MenuAction(Icons.Rounded.Tune, "Opções na tela externa") {},
                        MenuAction(Icons.Rounded.Info, "Informações do app") {},
                        MenuAction(Icons.Rounded.Delete, "Desinstalar", destructive = true) {},
                    ),
                    onDismiss = {},
                )
            }
        }
        shot("10-menu")
        rule.onNodeWithText("Desinstalar").performClick()
    }

    @Test
    fun `dialogos de compatibilidade opcoes e widgets`() {
        rule.setContent {
            Scene(Settings()) {
                CompatDialog(
                    app = apps[15],
                    report = CompatReport(CompatLevel.CAUTION, listOf(CompatReason.FIXED_LANDSCAPE, CompatReason.GAME)),
                    shizukuReady = false, onOpen = {}, onOptions = {}, onDismiss = {},
                )
            }
        }
        shot("11-aviso-compatibilidade")
        rule.onNodeWithText("Abrir").performClick()
    }

    @Test
    fun `dialogo de opcoes do app`() {
        rule.setContent {
            Scene(Settings()) {
                AppOptionsDialog(
                    app = apps[4], pref = null, shizukuReady = true, onScale = {}, onForceResizable = {},
                    onSkipWarning = {}, onHidden = {}, onDismiss = {},
                )
            }
        }
        shot("12-opcoes-do-app")
    }

    @Test
    fun `seletor de widgets`() {
        rule.setContent { Scene(Settings()) { WidgetPicker(onPick = {}, onDismiss = {}) } }
        shot("13-widgets")
        rule.onNodeWithText("Clima").performClick()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w411dp-h1004dp-420dpi")
    fun `tela de bloqueio com notificacoes ocultas`() {
        val state = LockUiState(
            settings = Settings(), battery = BatteryState(percent = 76, charging = true), media = widgetData.media,
            notifications = notifications, torchAvailable = true, torchOn = false,
        )
        rule.setContent {
            CristalTheme(settings = state.settings) {
                LockScreen(state = state, secure = true, resetKey = 0, actions = LockActions({}, {}, {}, {}, {}, {}, {}, {}))
            }
        }
        shot("14-bloqueio")
        rule.onNodeWithContentDescription("Deslize para desbloquear").assertExists()
        // A notificação privada fica oculta; a pública aparece.
        rule.onNodeWithText("Conteúdo oculto").assertExists()
        rule.onNodeWithText("Oi! Você vem hoje?").assertDoesNotExist()
        rule.onNodeWithText("Boletim escolar").assertExists()
    }

    @Test
    fun `cover clock mostra so relogio e notificacoes`() {
        val state = LockUiState(
            settings = Settings(), battery = BatteryState(percent = 76, charging = true), media = widgetData.media,
            notifications = notifications, torchAvailable = true, torchOn = false,
        )
        rule.setContent {
            CristalTheme(settings = state.settings) {
                LockScreen(
                    state = state, secure = true, resetKey = 0,
                    actions = LockActions({}, {}, {}, {}, {}, {}, {}, {}), coverMinimal = true,
                )
            }
        }
        shot("15-cover-clock")
        // Minimalista de propósito: nada de música, atalhos ou dica de deslizar.
        rule.onNodeWithText("Garota de Ipanema").assertDoesNotExist()
        rule.onNodeWithContentDescription("Deslize para desbloquear").assertDoesNotExist()
        rule.onNodeWithText("Boletim escolar").assertExists()
        // A notificação privada mostra só o nome do app; tocar expande.
        rule.onNodeWithText("Oi! Você vem hoje?").assertDoesNotExist()
        rule.onNodeWithText("Boletim escolar").performClick()
        rule.onNodeWithText("Abrir").assertExists()
        shot("15b-cover-clock-expandido")
    }

    @Test
    @Config(sdk = [36], qualifiers = "w411dp-h1004dp-420dpi")
    fun `tela interna tem grade propria e mais alta`() {
        val inner = HomeLayout(
            pages = listOf(
                listOf(
                    HomeItem.Widget(1, 0, 0, 0, 4, 2, WidgetKind.CLOCK),
                    HomeItem.Widget(2, 0, 0, 2, 2, 2, WidgetKind.WEATHER),
                    HomeItem.Widget(3, 0, 2, 2, 2, 2, WidgetKind.DATE),
                    appItem(4, 0, 0, 4), appItem(5, 1, 1, 4), appItem(6, 3, 2, 4), appItem(7, 4, 3, 4),
                    appItem(8, 5, 0, 5), appItem(9, 6, 1, 5), appItem(10, 9, 2, 5), folder.copy(cellX = 3, cellY = 5),
                    appItem(11, 7, 0, 6), appItem(12, 8, 1, 6), appItem(13, 10, 2, 6), appItem(14, 11, 3, 6),
                    appItem(15, 14, 0, 7), appItem(16, 15, 1, 7),
                ),
            ),
            dock = layout.dock,
        )
        rule.setContent { Scene(Settings().forScreen(onCover = false), sceneLayout = inner) }
        shot("16-tela-interna")
        rule.onAllNodesWithText("Parcialmente nublado").onFirst().assertExists()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w411dp-h1004dp-420dpi")
    fun `bloqueio interno com efeito de profundidade`() {
        val (photo, subject) = depthImages()
        val settings = Settings().copy(wallpaperKind = WallpaperKind.IMAGE)
        val state = LockUiState(
            settings = settings, battery = BatteryState(percent = 76), notifications = notifications.take(1),
            wallpaper = photo, subject = subject,
        )
        rule.setContent {
            CristalTheme(settings = settings) {
                LockScreen(state = state, secure = false, resetKey = 0, actions = LockActions({}, {}, {}, {}, {}, {}, {}, {}))
            }
        }
        shot("17-bloqueio-profundidade")
        rule.onNodeWithContentDescription("Deslize para desbloquear").assertExists()
    }

    @Test
    fun `cover clock com efeito de profundidade`() {
        val (photo, subject) = depthImages()
        val settings = Settings().copy(wallpaperKind = WallpaperKind.IMAGE)
        val state = LockUiState(settings = settings, notifications = notifications, wallpaper = photo, subject = subject)
        rule.setContent {
            CristalTheme(settings = settings) {
                LockScreen(
                    state = state, secure = false, resetKey = 0,
                    actions = LockActions({}, {}, {}, {}, {}, {}, {}, {}), coverMinimal = true,
                )
            }
        }
        shot("18-cover-clock-profundidade")
        // As duas camadas medem igual: tocar expande a notificação da camada de cima.
        rule.onAllNodesWithText("Boletim escolar").onLast().performClick()
        rule.onAllNodesWithText("Abrir").onLast().assertExists()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w411dp-h1004dp-420dpi")
    fun `galeria com todas as fontes de relogio`() {
        rule.setContent {
            Column(Modifier.fillMaxSize().background(Color(0xFF15121F)).padding(8.dp)) {
                for (row in ClockFont.entries.chunked(3)) {
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        for (font in row) {
                            Column(Modifier.weight(1f).fillMaxHeight().padding(4.dp)) {
                                AdaptiveClock(
                                    time = "10:42", font = font, layout = ClockLayout.AUTO, adaptive = true,
                                    modifier = Modifier.fillMaxWidth().weight(1f),
                                )
                                Text(font.label, color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
        shot("19-fontes-do-relogio")
        rule.onNodeWithText("Manuscrita").assertExists()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w411dp-h1004dp-420dpi")
    fun `bloqueio interno sem cartao com relogio alto`() {
        val settings = Settings().copy(lockClockCard = false, lockClockFont = ClockFont.TALL)
        val state = LockUiState(settings = settings, battery = BatteryState(percent = 76), notifications = notifications.take(1))
        rule.setContent {
            CristalTheme(settings = settings) {
                LockScreen(state = state, secure = false, resetKey = 0, actions = LockActions({}, {}, {}, {}, {}, {}, {}, {}))
            }
        }
        shot("20-bloqueio-relogio-adaptativo")
    }

    @Test
    fun `cover clock empilhado com fonte impacto`() {
        val settings = Settings().copy(coverClockFont = ClockFont.IMPACT, coverClockPosition = CoverClockPosition.CENTER)
        val state = LockUiState(settings = settings, notifications = emptyList())
        rule.setContent {
            CristalTheme(settings = settings) {
                LockScreen(state = state, secure = false, resetKey = 0, actions = LockActions({}, {}, {}, {}, {}, {}, {}, {}), coverMinimal = true)
            }
        }
        shot("21-cover-clock-empilhado")
    }

    @Test
    @Config(sdk = [36], qualifiers = "w411dp-h1004dp-420dpi")
    fun `bloqueio com widgets acima e musica embaixo na pre-visualizacao`() {
        val settings = Settings().copy(
            lockClockCard = false,
            lockClockScale = 1.3f,
            lockWidgets = listOf(LockWidget.BATTERY, LockWidget.WEATHER, LockWidget.ALARM),
            lockWidgetsPosition = LockPlacement.ABOVE_CLOCK,
            lockMediaPosition = LockPlacement.BOTTOM,
        )
        val state = LockUiState(
            settings = settings, battery = BatteryState(percent = 76, charging = true), media = widgetData.media,
            notifications = notifications.take(1), weather = widgetData.weather,
        )
        var saved = -1f
        rule.setContent {
            CristalTheme(settings = settings) {
                LockScreen(
                    state = state, secure = false, resetKey = 0,
                    actions = LockActions({}, {}, {}, {}, {}, {}, {}, {}),
                    onClockScale = { saved = it },
                )
            }
        }
        shot("22-bloqueio-widgets-e-alca")
        rule.onNodeWithText("76").assertExists()
        rule.onNodeWithText("Arraste o canto para esticar").assertExists()
        rule.onAllNodesWithText("Garota de Ipanema").onFirst().assertExists()
        // Arrastar o canto para cima encolhe o relógio e salva o novo tamanho.
        rule.onNodeWithContentDescription("Esticar o relógio").performTouchInput {
            down(center)
            moveBy(Offset(0f, -70f))
            moveBy(Offset(0f, -70f))
            up()
        }
        rule.waitForIdle()
        assertTrue("tamanho salvo: $saved", saved in 0.6f..1.29f)
        shot("22b-bloqueio-relogio-menor")
    }
}
