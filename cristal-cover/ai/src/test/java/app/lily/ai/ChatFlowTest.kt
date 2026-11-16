package app.lily.ai

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import androidx.test.core.app.ApplicationProvider
import app.lily.ai.bridge.AiBridgeService
import app.lily.ai.core.Accel
import app.lily.ai.core.EngineChoice
import app.lily.ai.diagnostics.DiagState
import app.lily.ai.diagnostics.Diagnostics
import app.lily.ai.diagnostics.Report
import app.lily.ai.data.MsgStatus
import app.lily.ai.data.Role
import app.lily.ai.engine.Attachments
import app.lily.ai.engine.EngineRefused
import app.lily.ai.engine.EngineStatus
import app.lily.ai.engine.LlmEngine
import app.lily.ai.engine.Turn
import app.lily.ai.models.Catalog
import app.lily.ai.models.Category
import app.lily.ai.models.ModelSpec
import app.lily.ai.ui.Route
import app.lily.ai.ui.screens.ChatSession
import app.lily.shared.bridge.AiBridge
import java.io.File
import java.io.RandomAccessFile
import java.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/**
 * Motor de teste: responde com pedaços fixos e guarda o que recebeu (sistema, histórico, pergunta).
 * Só existe nos testes; o app usa o LiteRT-LM.
 */
class FakeEngine(
    private val pieces: List<String> = listOf("Olá! ", "Tudo bem por aqui, ", "e com você? ", "Posso ajudar no que precisar."),
    private val stepMs: Long = 100,
    private val failAfter: Int? = null,
) : LlmEngine {
    val systems = mutableListOf<String?>()
    val histories = mutableListOf<List<Turn>>()
    val prompts = mutableListOf<String>()
    val models = mutableListOf<String>()
    override val status = MutableStateFlow<EngineStatus>(EngineStatus.Idle)
    override var idleMillis: Long = 0
    override var onLoaded: ((ModelSpec, Accel, Long) -> Unit)? = null

    override fun generate(choice: EngineChoice.Llm, system: String?, history: List<Turn>, prompt: String, attachments: Attachments, temperature: Double): Flow<String> = flow {
        systems += system
        histories += history
        prompts += prompt
        models += choice.model.id
        var acc = ""
        pieces.forEachIndexed { i, p ->
            if (failAfter != null && i == failAfter) throw EngineRefused("Faltou memória para o modelo. Feche outros apps ou use um modelo menor (perfil Eco).")
            delay(stepMs)
            acc += p
            emit(acc)
        }
        // Como o motor real: depois de responder, fica pronto com o modelo carregado.
        status.value = EngineStatus.Ready(choice.model, choice.accel, 1_500)
    }

    override fun unload() = Unit
    override suspend fun unloadNow() = Unit
}

/** A conversa inteira (sessão, roteador, histórico, memória, documentos, gravação) e a ponte com o teclado. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChatFlowTest {
    private val app: LilyAiApp get() = ApplicationProvider.getApplicationContext()
    private val graph get() = app.graph

    @Before
    fun device() {
        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        shadowOf(am).setMemoryInfo(ActivityManager.MemoryInfo().apply {
            totalMem = 7_600L * 1024 * 1024
            availMem = 3_100L * 1024 * 1024
        })
        ReflectionHelpers.setStaticField(Build::class.java, "SUPPORTED_64_BIT_ABIS", arrayOf("arm64-v8a"))
    }

    private fun install(spec: ModelSpec) {
        spec.files.forEach { f ->
            val file = graph.store.file(spec, f)
            file.parentFile!!.mkdirs()
            RandomAccessFile(file, "rw").use { it.setLength(f.bytes) }
        }
        graph.store.markInstalled(spec, update = false)
    }

    private fun waitUntil(timeoutMs: Long = 15_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(25))
            if (System.currentTimeMillis() > end) throw AssertionError("Tempo esgotado")
            Thread.sleep(3)
        }
    }

    private fun session(route: Route.Chat = Route.Chat()) = ChatSession(graph, app, route).also { s -> waitUntil { !s.loading } }

    private fun answered(s: ChatSession, count: Int) = waitUntil { s.messages.count { it.role == Role.ASSISTANT } >= count && s.streaming == null }

    @Test
    fun `pergunta e resposta em pedacos ficam gravadas no historico`() {
        install(Catalog.qwen3Small)
        val fake = FakeEngine()
        graph.engines = fake
        val s = session()
        s.send("Oi, tudo bem com você hoje?")
        answered(s, 1)
        val answer = s.messages.last()
        assertEquals(Role.ASSISTANT, answer.role)
        assertEquals(MsgStatus.DONE, answer.status)
        assertEquals("Olá! Tudo bem por aqui, e com você? Posso ajudar no que precisar.", answer.text)
        assertTrue(answer.model!!.startsWith("Qwen3 0.6B"))
        assertEquals(listOf(Catalog.qwen3Small.id), fake.models)
        val id = s.conversationId!!
        assertEquals("Oi, tudo bem com você hoje?", graph.conversations.meta(id)!!.title)
        val saved = runBlocking { graph.conversations.messages(id) }
        assertEquals(2, saved.size)
        assertTrue("velocidade medida", (graph.perf.items.value[Catalog.qwen3Small.id]?.answers ?: 0) >= 1)
    }

    @Test
    fun `historico vai alternado e sem os erros`() {
        install(Catalog.qwen3Small)
        val ok = FakeEngine(stepMs = 10)
        graph.engines = ok
        val s = session()
        s.send("Primeira pergunta")
        answered(s, 1)
        graph.engines = FakeEngine(stepMs = 10, failAfter = 1)
        s.send("Segunda pergunta")
        answered(s, 2)
        assertEquals(MsgStatus.ERROR, s.messages.last().status)
        assertTrue(s.messages.last().text.contains("Faltou memória"))
        val third = FakeEngine(stepMs = 10)
        graph.engines = third
        s.send("Terceira pergunta")
        answered(s, 3)
        val history = third.histories.single()
        assertEquals(listOf(true, false), history.map { it.fromUser })
        assertEquals("Primeira pergunta", history.first().text)
        assertEquals("Terceira pergunta", third.prompts.single())
    }

    @Test
    fun `parar guarda o que ja chegou`() {
        install(Catalog.qwen3Small)
        graph.engines = FakeEngine(pieces = List(20) { "palavra$it " }, stepMs = 200)
        val s = session()
        s.send("Conte uma história longa")
        waitUntil { s.streaming?.text?.isNotEmpty() == true }
        s.stop()
        answered(s, 1)
        val answer = s.messages.last()
        assertEquals(MsgStatus.STOPPED, answer.status)
        assertTrue(answer.text.startsWith("palavra0"))
        assertTrue(answer.text.length < 20 * 9)
    }

    @Test
    fun `sem modelo a conversa explica e aponta o model hub`() {
        graph.engines = FakeEngine()
        val s = session()
        s.send("Oi")
        answered(s, 1)
        val answer = s.messages.last()
        assertEquals(MsgStatus.ERROR, answer.status)
        assertEquals(Category.CHAT.name, answer.install)
    }

    @Test
    fun `memorias entram no sistema so quando ligadas`() {
        install(Catalog.qwen3Small)
        val fake = FakeEngine(stepMs = 5)
        graph.engines = fake
        graph.memory.add("Prefiro respostas curtas", "preferencia")
        val s = session()
        s.send("Me explica fotossíntese")
        answered(s, 1)
        assertTrue(fake.systems.last()!!.contains("Prefiro respostas curtas"))
        graph.settings.update { it.copy(memoryEnabled = false) }
        s.send("E a respiração celular?")
        answered(s, 2)
        assertFalse(fake.systems.last()!!.contains("Prefiro respostas curtas"))
    }

    @Test
    fun `documento vai junto da pergunta e das perguntas seguintes`() {
        install(Catalog.qwen3)
        val fake = FakeEngine(stepMs = 5)
        graph.engines = fake
        val file = File(app.cacheDir, "notas.txt").apply { writeText("Reunião na sexta às 15h. Levar o relatório de vendas.") }
        val s = session()
        s.attachDocument(Uri.fromFile(file))
        waitUntil { s.document != null }
        s.send("Quando é a reunião?")
        answered(s, 1)
        val first = fake.prompts.last()
        assertTrue(first.contains("Documento “notas.txt”"))
        assertTrue(first.contains("Levar o relatório de vendas"))
        assertTrue(first.endsWith("Quando é a reunião?"))
        assertEquals("document", s.messages.first().attachments.single().kind)
        s.send("E o que eu preciso levar?")
        answered(s, 2)
        assertTrue("pergunta de acompanhamento leva o documento", fake.prompts.last().contains("Levar o relatório de vendas"))
    }

    @Test
    fun `apagar a conversa apaga o documento anexado`() {
        install(Catalog.qwen3Small)
        graph.engines = FakeEngine(stepMs = 5)
        val file = File(app.cacheDir, "lista.txt").apply { writeText("Comprar pão e leite") }
        val s = session()
        s.attachDocument(Uri.fromFile(file))
        waitUntil { s.document != null }
        val copy = s.document!!.file
        s.send("Resuma")
        answered(s, 1)
        assertTrue(copy.exists())
        runBlocking { graph.conversations.delete(s.conversationId!!) }
        assertFalse(copy.exists())
    }

    @Test
    fun `teste do aparelho mede os modelos e mostra as falhas`() {
        install(Catalog.qwen3Small)
        install(Catalog.qwen3)
        graph.engines = FakeEngine(stepMs = 50)
        val diagnostics = Diagnostics(graph, app)
        var report: Report? = null
        graph.scope.launch { report = diagnostics.run(includeGpu = false) }
        waitUntil(60_000) { report != null }
        val r = report!!
        assertEquals(listOf("Qwen3 0.6B", "Qwen3 1.7B"), r.runs.map { it.model })
        assertTrue(r.runs.all { it.error == null && it.used == Accel.CPU && it.loadMillis == 1_500L })
        assertEquals("Olá! Tudo bem por aqui, e com você? Posso ajudar no que precisar.", r.runs.first().answer)
        val text = r.text()
        assertTrue(text, text.contains("Qwen3 0.6B · CPU pedida, CPU usada · carregou em 1,5 s"))
        assertTrue(text, text.contains("Ampliação (Real-ESRGAN): — · não instalado"))
        assertTrue(diagnostics.state.value is DiagState.Done)
    }

    @Test
    fun `painel de voz fechado no meio vira live update e depois aviso`() {
        install(Catalog.qwen3Small)
        graph.engines = FakeEngine(stepMs = 150)
        val nm = app.getSystemService(android.app.NotificationManager::class.java)
        val assist = graph.assist
        val s = assist.session()
        assist.attach()
        s.send("Qual é a capital do Brasil?")
        waitUntil { s.streaming?.text?.isNotEmpty() == true }
        assist.detach()
        waitUntil { nm.activeNotifications.any { it.id == 7201 } }
        val working = nm.activeNotifications.first { it.id == 7201 }.notification
        assertTrue("Live Update contínuo", working.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals("L.I.L.Y respondendo", working.extras.getString(android.app.Notification.EXTRA_TITLE))
        answered(s, 1)
        waitUntil { nm.activeNotifications.any { it.id == 7202 } }
        assertFalse("o aviso de trabalhando some", nm.activeNotifications.any { it.id == 7201 })
        val done = nm.activeNotifications.first { it.id == 7202 }.notification
        assertTrue(done.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString().startsWith("Olá!"))
        // Reabrir o painel tira os avisos e continua a mesma conversa (acompanhamento).
        assist.attach()
        assertFalse(nm.activeNotifications.any { it.id == 7202 })
        assertTrue(assist.session() === s)
    }

    // --- Ponte com o teclado ------------------------------------------------------------------

    private fun bridge(action: String, text: String, style: String = "NATURAL"): List<Message> {
        val service = Robolectric.buildService(AiBridgeService::class.java).create().get()
        val messenger = Messenger(service.onBind(Intent()))
        val received = mutableListOf<Message>()
        val reply = Messenger(Handler(Looper.getMainLooper()) { m -> received += Message.obtain(m); true })
        messenger.send(Message.obtain(null, AiBridge.MSG_REQUEST).apply {
            replyTo = reply
            data = Bundle().apply {
                putLong(AiBridge.KEY_ID, 42L)
                putString(AiBridge.KEY_ACTION, action)
                putString(AiBridge.KEY_TEXT, text)
                putString(AiBridge.KEY_STYLE, style)
            }
        })
        waitUntil { received.any { it.what == AiBridge.MSG_DONE || it.what == AiBridge.MSG_ERROR } }
        return received
    }

    @Test
    fun `teclado pede correcao informal pela ponte`() {
        install(Catalog.qwen3Small)
        val fake = FakeEngine(pieces = listOf("vc viu que a prova ", "mudou pra sexta?"), stepMs = 10)
        graph.engines = fake
        val received = bridge("CORRECT", "vc viu q a prova mudou pra sexta?", style = "INFORMAL")
        val done = received.last()
        assertEquals(AiBridge.MSG_DONE, done.what)
        assertEquals(42L, done.data.getLong(AiBridge.KEY_ID))
        assertEquals("vc viu que a prova mudou pra sexta?", done.data.getString(AiBridge.KEY_TEXT))
        assertEquals("Qwen3 0.6B", done.data.getString(AiBridge.KEY_MODEL))
        assertTrue(fake.prompts.single().contains("NÃO troque abreviações"))
        assertTrue(fake.prompts.single().contains("vc viu q a prova mudou pra sexta?"))
    }

    @Test
    fun `ponte sem modelo responde no_model`() {
        graph.engines = FakeEngine()
        val received = bridge("REWRITE", "texto qualquer")
        assertEquals(AiBridge.MSG_ERROR, received.last().what)
        assertEquals(AiBridge.CODE_NO_MODEL, received.last().data.getString(AiBridge.KEY_CODE))
    }

    @Test
    fun `ponte recusa acao desconhecida e texto vazio`() {
        install(Catalog.qwen3Small)
        graph.engines = FakeEngine()
        assertEquals(AiBridge.MSG_ERROR, bridge("APAGAR_TUDO", "x").last().what)
        assertEquals(AiBridge.MSG_ERROR, bridge("CORRECT", "   ").last().what)
    }
}
