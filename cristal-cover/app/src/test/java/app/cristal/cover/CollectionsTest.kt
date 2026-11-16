package app.cristal.cover

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import app.cristal.cover.data.collections.AppearanceKeys
import app.cristal.cover.data.collections.CollectionRepository
import app.cristal.cover.data.prefs.ClockFont
import app.cristal.cover.data.prefs.LiquidThemeId
import app.cristal.cover.data.prefs.PrefKeys
import app.cristal.cover.data.prefs.Settings
import app.cristal.cover.data.prefs.SettingsRepository
import app.cristal.cover.ui.settings.CollectionActions
import app.cristal.cover.ui.settings.CollectionsContent
import app.cristal.cover.ui.theme.CristalTheme
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h1004dp-420dpi")
class CollectionsTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun repositories(): Pair<SettingsRepository, CollectionRepository> {
        val file = File(context.filesDir, "test-${System.nanoTime()}.preferences_pb")
        val store = PreferenceDataStoreFactory.create(produceFile = { file })
        val settings = SettingsRepository(store, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        return settings to CollectionRepository(context, settings)
    }

    @Test
    fun `chaves de aparencia`() {
        assertTrue(AppearanceKeys.belongs("wallpaper_lock_inner_zoom"))
        assertTrue(AppearanceKeys.belongs("lock_clock_x"))
        assertTrue(AppearanceKeys.belongs("cover_clock_font"))
        assertTrue(AppearanceKeys.belongs("liquid_theme"))
        assertFalse("versões das imagens não são guardadas", AppearanceKeys.belongs("wallpaper_lock_inner_version"))
        assertFalse("o resto do aparelho fica fora", AppearanceKeys.belongs("island_enabled"))
        assertFalse(AppearanceKeys.belongs("grid_columns"))
    }

    @Test
    fun `guardar, mudar e aplicar devolve a aparencia e as imagens`() = runBlocking {
        val (settings, collections) = repositories()
        settings.setEnum(PrefKeys.LOCK_CLOCK_FONT, ClockFont.IMPACT)
        settings.set(PrefKeys.LOCK_CLOCK_X, 0.2f)
        settings.setEnum(PrefKeys.LIQUID_THEME, LiquidThemeId.LAVENDER)
        settings.set(PrefKeys.ISLAND_ENABLED, true)
        val image = File(context.filesDir, CollectionRepository.imageName(app.cristal.cover.data.prefs.WallpaperSlot.LOCK_INNER))
        image.writeBytes(byteArrayOf(1, 2, 3))

        val saved = collections.saveCurrent("Praia")
        assertEquals(listOf("Praia"), collections.collections.value.map { it.name })
        assertTrue(CollectionRepository.imageName(app.cristal.cover.data.prefs.WallpaperSlot.LOCK_INNER) in saved.images)

        // Muda tudo, inclusive algo que não é aparência.
        settings.setEnum(PrefKeys.LOCK_CLOCK_FONT, ClockFont.SCRIPT)
        settings.set(PrefKeys.LOCK_CLOCK_X, -0.3f)
        settings.setEnum(PrefKeys.LIQUID_THEME, LiquidThemeId.AMOLED)
        settings.set(PrefKeys.ISLAND_ENABLED, false)
        image.delete()

        assertTrue(collections.apply(saved.id))
        val now = settings.current()
        assertEquals(ClockFont.IMPACT, now.lockClockFont)
        assertEquals(0.2f, now.lockClockX)
        assertEquals(LiquidThemeId.LAVENDER, now.liquidTheme)
        assertFalse("configuração fora da aparência não volta", now.islandEnabled)
        assertTrue("a imagem voltou", image.exists())
        assertEquals(saved.id, collections.active.value)
    }

    @Test
    fun `renomear, duplicar e excluir`() = runBlocking {
        val (_, collections) = repositories()
        val a = collections.saveCurrent("Noite")
        collections.rename(a.id, "Noite estrelada")
        val copy = collections.duplicate(a.id)!!
        assertEquals(listOf("Noite estrelada", "Noite estrelada (cópia)"), collections.collections.value.map { it.name })
        collections.delete(a.id)
        assertEquals(listOf(copy.id), collections.collections.value.map { it.id })
        val preview = collections.preview(copy)
        assertEquals(Settings().lockClockFont, preview.settings.lockClockFont)
    }

    @Test
    fun `galeria mostra as colecoes`() {
        val (_, collections) = repositories()
        val items = runBlocking {
            collections.saveCurrent("Praia")
            collections.saveCurrent("Cidade")
            collections.collections.value
        }
        val previews = runBlocking { items.associate { it.id to collections.preview(it) } }
        rule.setContent {
            CristalTheme(settings = Settings()) {
                CollectionsContent(
                    collections = items,
                    active = items.last().id,
                    previews = previews,
                    onBack = {},
                    actions = CollectionActions({}, { _, _ -> }, { _, _ -> }, {}, {}, {}),
                )
            }
        }
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File("build/outputs/telas").apply { mkdirs() }.resolve("46-colecoes.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // O selo no cartão e o botão "Em uso" no lugar de "Aplicar".
        rule.onAllNodesWithText("Em uso").assertCountEquals(2)
        rule.onNodeWithText("Cidade").assertExists()
    }
}
