package app.lily.ai

import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import app.lily.ai.ui.AssistActivity
import app.lily.ai.ui.MainActivity
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Abre as atividades de verdade (onCreate, setContent e dois segundos de quadros), como o Android
 * faz ao tocar no ícone ou chamar a L.I.L.Y pela ilha. As telas sozinhas já são testadas em
 * [AiScreensTest]; aqui o que importa é o caminho completo de abertura. O relógio dos quadros é
 * controlado: as animações infinitas (o orbe) andam só o tempo pedido.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h1005dp-420dpi")
class LaunchTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val app: LilyAiApp get() = ApplicationProvider.getApplicationContext()

    private fun <A : android.app.Activity> opens(intent: Intent) {
        rule.mainClock.autoAdvance = false
        ActivityScenario.launch<A>(intent).use { scenario ->
            rule.mainClock.advanceTimeBy(2_000)
            rule.onRoot().assertExists()
            scenario.onActivity { assertFalse("a atividade fechou sozinha", it.isFinishing) }
        }
    }

    @Test
    fun `o app abre pelo ícone`() {
        opens<MainActivity>(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(app, MainActivity::class.java.name),
        )
    }

    @Test
    fun `o painel de voz abre (ilha, assistente do Android)`() {
        opens<AssistActivity>(
            Intent(Intent.ACTION_ASSIST).putExtra(AssistActivity.EXTRA_SILENT, true).setClassName(app, AssistActivity::class.java.name),
        )
    }

    /** Na tela externa do Razr (422 × 426 dp) o app abre na versão compacta, de voz. */
    @Test
    @Config(qualifiers = "w422dp-h426dp-420dpi")
    fun `o app abre na tela externa`() {
        opens<MainActivity>(Intent(Intent.ACTION_MAIN).setClassName(app, MainActivity::class.java.name).putExtra(MainActivity.EXTRA_ROUTE, "chat"))
    }

    @Test
    @Config(qualifiers = "w422dp-h426dp-420dpi")
    fun `o painel de voz abre na tela externa`() {
        opens<AssistActivity>(
            Intent(AiVoiceAction).putExtra(AssistActivity.EXTRA_SILENT, true).setClassName(app, AssistActivity::class.java.name),
        )
    }

    /**
     * O erro visto no Razr com a 1.2.0: o WorkManager (vem com o LiteRT) iniciava na partida, antes de
     * qualquer código da L.I.L.Y, e fechava o app. Agora ele não está entre as inicializações da partida
     * e, se alguma biblioteca pedir, inicia sob demanda pelo LilyAiApp.
     */
    @Test
    fun `o WorkManager não inicia na partida, só sob demanda`() {
        val pm = app.packageManager
        val provider = pm.getProviderInfo(
            android.content.ComponentName(app, androidx.startup.InitializationProvider::class.java),
            android.content.pm.PackageManager.GET_META_DATA,
        )
        val initializers = provider.metaData?.keySet().orEmpty()
        assertFalse("o WorkManager ainda inicia na partida", "androidx.work.WorkManagerInitializer" in initializers)
        org.junit.Assert.assertTrue("as outras inicializações sumiram", "androidx.lifecycle.ProcessLifecycleInitializer" in initializers)
        val work = androidx.work.WorkManager.getInstance(app)
        org.junit.Assert.assertNotNull(work)
    }

    private companion object {
        const val AiVoiceAction = "app.lily.ai.action.VOICE"
    }
}
