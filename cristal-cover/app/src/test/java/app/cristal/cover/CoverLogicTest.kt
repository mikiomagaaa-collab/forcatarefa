package app.cristal.cover

import android.content.pm.ActivityInfo
import android.view.Display
import app.cristal.cover.data.backup.BackupFile
import app.cristal.cover.data.backup.BackupItem
import app.cristal.cover.data.backup.BackupManager
import app.cristal.cover.data.display.CoverDisplayRepository
import app.cristal.cover.data.display.DisplayDesc
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
}
