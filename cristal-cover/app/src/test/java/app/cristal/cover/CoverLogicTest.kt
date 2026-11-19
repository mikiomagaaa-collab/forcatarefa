package app.cristal.cover

import android.content.pm.ActivityInfo
import android.view.Display
import app.cristal.cover.data.backup.BackupFile
import app.cristal.cover.data.backup.BackupItem
import app.cristal.cover.data.backup.BackupManager
import app.cristal.cover.data.display.CoverDisplayRepository
import app.cristal.cover.data.display.DisplayDesc
import app.cristal.cover.data.home.HomeProfile
import app.cristal.cover.data.prefs.Settings
import app.cristal.cover.data.prefs.WallpaperConfig
import app.cristal.cover.data.prefs.WallpaperKind
import app.cristal.cover.data.prefs.WallpaperSlot
import app.cristal.cover.data.launch.AppLauncher
import app.cristal.cover.data.launch.CompatAdvisor
import app.cristal.cover.data.launch.CompatLevel
import app.cristal.cover.data.launch.CompatReason
import app.cristal.cover.ui.launcher.normalizeForSearch
import app.cristal.cover.ui.launcher.relativeTime
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverLogicTest {

    private fun display(id: Int, w: Int, h: Int, flags: Int = 0) = DisplayDesc(
        id = id, name = "Tela $id", widthPx = w, heightPx = h, densityDpi = 400,
        refreshRate = 60f, maxRefreshRate = 144f, state = Display.STATE_ON, flags = flags,
    )

    private val inner = display(0, 1080, 2640)
    private val razrCover = display(1, 1056, 1066)

    // --- Detecção da tela externa ------------------------------------------------------------------

    @Test
    fun `razr - a tela 1 quase quadrada e a externa`() {
        assertEquals(razrCover, CoverDisplayRepository.pickCover(listOf(inner, razrCover), overrideId = -1))
    }

    @Test
    fun `aparelho comum nao tem tela externa`() {
        assertNull(CoverDisplayRepository.pickCover(listOf(inner), overrideId = -1))
    }

    @Test
    fun `monitor externo grande nao e confundido com tela externa`() {
        assertNull(CoverDisplayRepository.pickCover(listOf(inner, display(2, 1920, 1080)), overrideId = -1))
    }

    @Test
    fun `tela privada e ignorada`() {
        val private = display(3, 1000, 1000, flags = Display.FLAG_PRIVATE)
        assertNull(CoverDisplayRepository.pickCover(listOf(inner, private), overrideId = -1))
    }

    @Test
    fun `a escolha manual vence a deteccao e some se a tela nao existir`() {
        val other = display(4, 1920, 1080)
        assertEquals(other, CoverDisplayRepository.pickCover(listOf(inner, razrCover, other), overrideId = 4))
        assertEquals(razrCover, CoverDisplayRepository.pickCover(listOf(inner, razrCover), overrideId = 9))
    }

    @Test
    fun `aparelho que troca a tela logica - a propria tela padrao vira a externa`() {
        val swapped = display(0, 1056, 1066)
        assertEquals(swapped, CoverDisplayRepository.pickCover(listOf(swapped), overrideId = -1))
    }

    // --- Compatibilidade ---------------------------------------------------------------------------

    private fun evaluate(
        orientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
        targetSdk: Int = 36,
        requiresSw: Int = 0,
        minW: Int = 0,
        minH: Int = 0,
        small: Boolean = true,
        game: Boolean = false,
    ) = CompatAdvisor.evaluate(orientation, targetSdk, requiresSw, minW, minH, small, game, razrCover)

    @Test
    fun `app moderno e redimensionavel nao gera aviso`() {
        val report = evaluate()
        assertEquals(CompatLevel.OK, report.level)
        assertTrue(report.reasons.isEmpty())
    }

    @Test
    fun `paisagem fixa e jogo geram cautela`() {
        val report = evaluate(orientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, game = true)
        assertEquals(CompatLevel.CAUTION, report.level)
        assertEquals(listOf(CompatReason.FIXED_LANDSCAPE, CompatReason.GAME), report.reasons)
    }

    @Test
    fun `exigir tela maior que a externa e problema`() {
        // 1056 px a 400 dpi = 422 dp de menor largura.
        assertEquals(CompatLevel.OK, evaluate(requiresSw = 400).level)
        val report = evaluate(requiresSw = 600)
        assertEquals(CompatLevel.PROBLEM, report.level)
        assertTrue(CompatReason.NEEDS_LARGER_SCREEN in report.reasons)
    }

    @Test
    fun `janela minima maior que a tela e problema`() {
        assertEquals(CompatLevel.PROBLEM, evaluate(minW = 1200).level)
        assertEquals(CompatLevel.OK, evaluate(minW = 1000, minH = 1000).level)
    }

    // --- Segurança dos comandos de shell -----------------------------------------------------------

    @Test
    fun `nomes de pacote e componente suspeitos nao entram em comandos`() {
        assertTrue(AppLauncher.isSafePackage("com.whatsapp"))
        assertTrue(AppLauncher.isSafeComponent("com.foo/.Main\$Inner"))
        assertFalse(AppLauncher.isSafePackage("com.foo; rm -rf /"))
        assertFalse(AppLauncher.isSafePackage("com.foo\$(id)"))
        assertFalse(AppLauncher.isSafeComponent("com.foo/.Main' ; id ; '"))
        assertFalse(AppLauncher.isSafeComponent("com.foo"))
        assertFalse(AppLauncher.isSafePackage(""))
    }

    // --- Utilidades --------------------------------------------------------------------------------

    @Test
    fun `busca ignora acentos e maiusculas`() {
        assertEquals("camera", normalizeForSearch("Câmera"))
        assertEquals("configuracoes", normalizeForSearch("CONFIGURAÇÕES"))
    }

    @Test
    fun `tempo relativo das notificacoes`() {
        val now = 10_000_000_000L
        assertEquals("agora", relativeTime(now - 20_000, now))
        assertEquals("5 min", relativeTime(now - 5 * 60_000, now))
        assertEquals("2 h", relativeTime(now - 2 * 3_600_000, now))
        assertEquals("3 d", relativeTime(now - 3 * 86_400_000L, now))
        assertEquals("agora", relativeTime(now + 60_000, now))
    }

    @Test
    fun `backup sobrevive a ida e volta e tolera campos desconhecidos`() {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        val original = BackupFile(
            createdAt = 42,
            settings = mapOf("grid_columns" to "i:5", "theme_mode" to "s:DARK"),
            home = listOf(BackupItem(id = 1, type = 0, container = -100, componentKey = "com.foo/.Main#0")),
        )
        val text = json.encodeToString(BackupFile.serializer(), original)
        assertEquals(original, json.decodeFromString(BackupFile.serializer(), text))
        val future = text.replaceFirst("{", "{\"campoNovo\":123,")
        assertEquals(original, json.decodeFromString(BackupFile.serializer(), future))
        assertEquals(BackupManager.APP_MARKER, original.app)
    }

    // --- Papéis de parede por área e telas iniciais separadas ----------------------------------------

    @Test
    fun `por padrao as outras areas acompanham a tela inicial interna`() {
        val settings = Settings()
        for (slot in WallpaperSlot.entries) assertEquals(WallpaperSlot.HOME_INNER, settings.resolvedSlot(slot))
    }

    @Test
    fun `area com papel de parede proprio nao muda as outras`() {
        val own = WallpaperConfig(kind = WallpaperKind.PRESET, preset = 5, dim = 0.3f, follows = null)
        val settings = Settings().let { it.copy(wallpapers = it.wallpapers + (WallpaperSlot.LOCK_COVER to own)) }
        val lockCover = settings.forSlot(WallpaperSlot.LOCK_COVER)
        assertEquals(WallpaperSlot.LOCK_COVER, lockCover.wallpaperSlot)
        assertEquals(5, lockCover.wallpaperPreset)
        assertEquals(0.3f, lockCover.wallpaperDim, 0.0001f)
        // As demais continuam com o padrão da tela inicial interna.
        assertEquals(0, settings.forSlot(WallpaperSlot.HOME_COVER).wallpaperPreset)
        assertEquals(WallpaperSlot.HOME_INNER, settings.forSlot(WallpaperSlot.LOCK_INNER).wallpaperSlot)
    }

    @Test
    fun `sincronizacao em cadeia e em circulo nao trava`() {
        val base = Settings()
        val chained = base.copy(
            wallpapers = base.wallpapers +
                (WallpaperSlot.HOME_COVER to WallpaperConfig(preset = 9, follows = null)) +
                (WallpaperSlot.LOCK_INNER to WallpaperConfig(follows = WallpaperSlot.LOCK_COVER)) +
                (WallpaperSlot.LOCK_COVER to WallpaperConfig(follows = WallpaperSlot.HOME_COVER)),
        )
        assertEquals(WallpaperSlot.HOME_COVER, chained.resolvedSlot(WallpaperSlot.LOCK_INNER))
        assertEquals(9, chained.forSlot(WallpaperSlot.LOCK_INNER).wallpaperPreset)
        val circular = base.copy(
            wallpapers = base.wallpapers +
                (WallpaperSlot.HOME_INNER to WallpaperConfig(follows = WallpaperSlot.LOCK_INNER)) +
                (WallpaperSlot.LOCK_INNER to WallpaperConfig(follows = WallpaperSlot.HOME_INNER)),
        )
        // Não importa onde para; importa que para.
        assertTrue(circular.resolvedSlot(WallpaperSlot.HOME_INNER) in WallpaperSlot.entries)
    }

    @Test
    fun `cada tela tem grade e conteineres proprios`() {
        val settings = Settings(gridColumns = 5, gridRows = 4, innerGridColumns = 4, innerGridRows = 7)
        assertEquals(5 to 4, settings.columns(true) to settings.rows(true))
        assertEquals(4 to 7, settings.columns(false) to settings.rows(false))
        val inner = settings.forScreen(onCover = false)
        assertEquals(4 to 7, inner.gridColumns to inner.gridRows)
        assertEquals(5 to 4, settings.forScreen(onCover = true).let { it.gridColumns to it.gridRows })
        assertTrue(HomeProfile.INNER.desktop != HomeProfile.COVER.desktop && HomeProfile.INNER.dock != HomeProfile.COVER.dock)
        assertTrue(HomeProfile.isDock(HomeProfile.COVER.dock) && !HomeProfile.isDock(HomeProfile.COVER.desktop))
    }
}
