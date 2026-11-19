package app.lily.ai

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.lily.ai.codex.CodeRole
import app.lily.ai.codex.CodeRouter
import app.lily.ai.codex.CodexAgent
import app.lily.ai.codex.CodexEvent
import app.lily.ai.codex.CodexMode
import app.lily.ai.codex.CodexSession
import app.lily.ai.codex.Decision
import app.lily.ai.codex.Diff
import app.lily.ai.codex.ErrorParser
import app.lily.ai.codex.FileWorkspace
import app.lily.ai.codex.Languages
import app.lily.ai.codex.Phase
import app.lily.ai.codex.RemoteCodeLlm
import app.lily.ai.codex.RepoWorkspace
import app.lily.ai.codex.RestorePoints
import app.lily.ai.codex.TestPlan
import app.lily.ai.codex.Verification
import app.lily.ai.core.EngineChoice
import app.lily.ai.core.OnlineMode
import app.lily.ai.core.Profile
import app.lily.ai.core.Router
import app.lily.ai.core.RouterInput
import app.lily.ai.core.TaskKind
import app.lily.ai.data.Role
import app.lily.ai.remote.GatewayClient
import app.lily.ai.remote.GatewayException
import app.lily.ai.remote.GatewayStore
import app.lily.ai.remote.RemoteChunk
import app.lily.ai.remote.RemoteMessage
import app.lily.ai.remote.RemoteModel
import app.lily.ai.ui.Route
import app.lily.ai.presence.LilyPresence
import app.lily.ai.ui.screens.ChatSession
import app.lily.ai.video.Motion
import app.lily.ai.video.VideoJobs
import app.lily.ai.video.VideoMode
import app.lily.ai.video.VideoQuality
import app.lily.ai.video.VideoRequest
import app.lily.ai.video.VideoRouter
import java.io.File
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Os módulos novos com um gateway de mentira de verdade (HTTP em 127.0.0.1): modelos online no
 * roteador e na conversa, o Video AI do pedido ao arquivo, e o Codex do diff à validação no CI, com
 * auto-reparo e ponto de restauração.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NewModulesTest {
    private val app: LilyAiApp get() = ApplicationProvider.getApplicationContext()
    private lateinit var fake: FakeGateway
    private lateinit var client: GatewayClient
    private lateinit var store: GatewayStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before
    fun start() {
        fake = FakeGateway()
        val (s, c) = fake.connect(app)
        store = s
        client = c
    }

    @After
    fun stop() {
        fake.close()
        LilyPresence.reset()
    }

    private fun waitFor(timeoutMs: Long = 20_000, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10))
            if (System.currentTimeMillis() > end) fail("Tempo esgotado")
            Thread.sleep(10)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    // --- Gateway e roteador --------------------------------------------------------------------

    @Test
    fun `gateway só aceita https (http só no próprio aparelho) e guarda a chave cifrada`() {
        assertNull(GatewayStore.normalizeUrl("http://exemplo.com"))
        assertNull(GatewayStore.normalizeUrl("https://x.dev/?key=1"))
        assertEquals("https://lily.exemplo.workers.dev", GatewayStore.normalizeUrl("https://lily.exemplo.workers.dev/"))
        assertNotNull(store.save("https://ok.dev", "curta"))
        // A preferência guarda só o texto cifrado (aqui pela caixa de teste), nunca a chave pura.
        val raw = app.getSharedPreferences("lily_gateway", 0).getString("key", null)
        assertEquals("plain:${fake.key}", raw)
        assertEquals(fake.key, store.key())
    }

    @Test
    fun `status, catálogo e conversa em pedaços com o custo informado`() = runBlocking {
        val st = client.status()
        assertTrue(st.features.code && st.features.video)
        assertEquals(0.12, st.budget.spentTodayUsd!!, 1e-9)
        assertEquals(5, client.codeModels(refresh = true).size)
        fake.replies += "Olá! Tudo certo por aqui."
        val chunks = client.chat("deepseek/deepseek-v4-pro", listOf(RemoteMessage("user", "oi"))).toList()
        val texts = chunks.filterIsInstance<RemoteChunk.Text>()
        assertTrue(texts.size >= 2)
        assertEquals("Olá! Tudo certo por aqui.", texts.last().total)
        val done = chunks.last() as RemoteChunk.Done
        assertEquals(0.0015, done.usage!!.costUsd!!, 1e-9)
    }

    @Test
    fun `chave errada vira uma mensagem clara`() = runBlocking {
        store.save(fake.url, "errada-errada-errada-123")
        try {
            client.status()
            fail("devia recusar")
        } catch (e: GatewayException) {
            assertEquals(401, e.status)
            assertEquals("Chave de acesso inválida.", e.message)
        }
    }

    @Test
    fun `roteador usa modelo online só quando permitido, e nunca para imagem`() = runBlocking {
        val models = client.codeModels(refresh = true)
        val device = razr()
        fun plan(mode: OnlineMode, connected: Boolean = true, profile: Profile = Profile.BALANCED, image: Boolean = false) =
            Router.plan(TaskKind.CHAT, RouterInput(profile, emptySet(), remote = models, online = mode, connected = connected), device, needsImage = image)
        assertTrue(plan(OnlineMode.OFF).engine is EngineChoice.Unavailable)
        assertTrue(plan(OnlineMode.FALLBACK, connected = false).engine is EngineChoice.Unavailable)
        val remote = plan(OnlineMode.FALLBACK).engine as EngineChoice.Remote
        assertEquals("strong", remote.model.tier)
        assertFalse(plan(OnlineMode.FALLBACK).local)
        assertEquals("fast", (plan(OnlineMode.FALLBACK, profile = Profile.ECO).engine as EngineChoice.Remote).model.tier)
        assertEquals("frontier", (plan(OnlineMode.PREFER, profile = Profile.QUALITY).engine as EngineChoice.Remote).model.tier)
        assertTrue(plan(OnlineMode.PREFER, image = true).engine is EngineChoice.Unavailable)
    }

    @Test
    fun `conversa responde com o modelo online, marcado e com o custo`() {
        val graph = app.graph
        graph.gatewayStore = store
        graph.gateway = client
        runBlocking { client.codeModels(refresh = true) }
        graph.settings.update { it.copy(onlineConsent = true, online = OnlineMode.FALLBACK) }
        fake.replies += "Oi! Sou a L.I.L.Y, respondendo online."
        val s = ChatSession(graph, app, Route.Chat(draft = "Olá", send = true))
        waitFor { s.messages.count { it.role == Role.ASSISTANT } >= 1 && s.streaming == null }
        val answer = s.messages.last { it.role == Role.ASSISTANT }
        assertEquals("Oi! Sou a L.I.L.Y, respondendo online.", answer.text)
        assertTrue(answer.model!!, answer.model!!.contains("online"))
        assertTrue(answer.model!!, answer.model!!.contains("US$"))
        assertTrue(s.lastPlan?.local == false)
        // O sistema de memórias e a mensagem foram para o gateway, sem chave de provedor nenhuma.
        val sent = fake.chatRequests.last()
        assertTrue(sent.toString().contains("Olá"))
        s.dispose()
    }

    // --- Video AI ------------------------------------------------------------------------------

    @Test
    fun `video escolhe o modelo pela tarefa e pelo nível`() = runBlocking {
        val models = client.videoModels(refresh = true)
        assertEquals("LTX-2", VideoRouter.pick(models, needsImage = true, wantsAudio = false, VideoQuality.FAST)!!.model.name)
        assertEquals("Veo 3.1 Fast", VideoRouter.pick(models, needsImage = false, wantsAudio = false, VideoQuality.BEST)!!.model.name)
        // Com som: só modelos que geram som.
        assertTrue(VideoRouter.pick(models, needsImage = false, wantsAudio = true, VideoQuality.BALANCED)!!.model.audio)
    }

    @Test
    fun `video do pedido ao arquivo, com a fila acompanhada`() = runBlocking {
        val jobs = VideoJobs(app) { client }
        val model = client.videoModels(refresh = true).first { it.id == "ltx-2" }
        val bmp = android.graphics.Bitmap.createBitmap(64, 36, android.graphics.Bitmap.Config.ARGB_8888)
        val job = jobs.submit(VideoRequest(model, "a flor gira devagar", VideoMode.IMAGE, 6, "1080p", "16:9", false, Motion.GENTLE, bmp))
        val sent = fake.videoRequests.last()
        assertTrue(sent["image"].toString().contains("data:image/jpeg;base64,"))
        assertTrue(sent["prompt"].toString().contains("slow, gentle camera movement"))
        assertEquals("queued", jobs.job(job.id)!!.status)
        jobs.pollOnce(autoSave = false)
        assertEquals("queued", jobs.job(job.id)!!.status)
        jobs.pollOnce(autoSave = false)
        assertEquals("running", jobs.job(job.id)!!.status)
        assertTrue(jobs.job(job.id)!!.progress in 0.1f..0.93f)
        val more = jobs.pollOnce(autoSave = false)
        assertFalse(more)
        val done = jobs.job(job.id)!!
        assertEquals("done", done.status)
        assertEquals(4096L, File(done.file!!).length())
        // O histórico sobrevive (outra instância lê o mesmo arquivo).
        assertEquals("done", VideoJobs(app) { client }.job(job.id)!!.status)
    }

    // --- Codex: peças ----------------------------------------------------------------------------

    @Test
    fun `verificadores pegam erros reais e não reclamam de código certo`() {
        assertTrue(Languages.check("A.kt", "fun a() { val s = \"\${map[\"k\"]} }\" ; if (x) { y() } }").problems.isEmpty())
        assertTrue(Languages.check("A.kt", "fun a() {\n  if (x) {\n    y()\n}\n").problems.any { it.message.contains("nunca fechado") })
        assertTrue(Languages.check("A.kt", "val c = 'a'\n/* coment /* aninhado */ */\nval r = \"\"\"{ ( \"\"\"").problems.isEmpty())
        assertTrue(Languages.check("a.js", "const r = /\\{\"/; const t = `a \${b} {`; f(r)").problems.isEmpty())
        assertTrue(Languages.check("a.json", "{\"a\": [1, 2,]}").problems.isNotEmpty())
        assertTrue(Languages.check("a.json", "{\"a\": [1, 2]}").problems.isEmpty())
        assertTrue(Languages.check("a.xml", "<a><b></a>").problems.isNotEmpty())
        assertTrue(Languages.check("i.html", "<html><body><div><p>oi<br><img src=x></div></body></html>").problems.isEmpty())
        assertTrue(Languages.check("i.html", "<div><span></div>").problems.isNotEmpty())
        assertTrue(Languages.check("a.yml", "a:\n\tb: 1").problems.isNotEmpty())
        assertTrue(Languages.check("a.py", "def f():\nreturn 1\n").problems.isNotEmpty())
        assertTrue(Languages.check("a.py", "def f():\n    return {'a': (1, 2)}  # ok\n").problems.isEmpty())
        assertTrue(Languages.check("b.gradle", "id 'com.android.application'\nandroid { }").problems.isEmpty())
        assertFalse(Languages.check("x.bin", "...").checked)
    }

    @Test
    fun `diff unificado com contexto e números de linha`() {
        val before = (1..20).joinToString("\n") { "linha $it" }
        val after = before.replace("linha 10", "linha DEZ") + "\nlinha 21"
        val d = Diff.of("a.txt", before, after)
        assertEquals(2, d.added)
        assertEquals(1, d.removed)
        assertEquals(2, d.hunks.size)
        assertEquals("@@ -7,7 +7,7 @@", d.hunks[0].header)
        assertTrue(d.unified().contains("-linha 10\n+linha DEZ"))
        assertTrue(Diff.of("n.kt", null, "a\nb").created)
    }

    @Test
    fun `erros viram arquivos e linhas do projeto`() {
        val files = listOf("app/src/main/java/app/demo/Router.kt", "app/src/main/java/app/demo/Ui.kt", "tools/x.py")
        val kotlin = ErrorParser.parse("e: file:///home/runner/work/app/app/src/main/java/app/demo/Router.kt:42:5 Unresolved reference 'foo'.", files)
        assertEquals("app/src/main/java/app/demo/Router.kt" to 42, kotlin.locations.first())
        val stack = ErrorParser.parse("java.lang.IllegalStateException: quebrou\n\tat app.demo.Ui.render(Ui.kt:17)\n\tat android.os.Looper.loop(Looper.java:1)", files)
        assertEquals("IllegalStateException", stack.type)
        assertEquals("app/src/main/java/app/demo/Ui.kt" to 17, stack.locations.first())
        val py = ErrorParser.parse("Traceback:\n  File \"/w/tools/x.py\", line 3, in <module>\nNameError: name 'y' is not defined", files)
        assertEquals("tools/x.py" to 3, py.locations.first())
        // A mesma falha em outra linha tem a mesma assinatura (não conta como erro novo).
        assertEquals(kotlin.signature, ErrorParser.parse("e: file:///w/app/src/main/java/app/demo/Router.kt:57:5 Unresolved reference 'foo'.", files).signature)
    }

    @Test
    fun `roteador de programação escolhe pelo papel, custo e revisão independente`() = runBlocking {
        val models = client.codeModels(refresh = true)
        assertEquals(CodeRole.DEBUG, CodeRouter.classify("o app dá crash ao abrir"))
        assertEquals(CodeRole.ARCHITECTURE, CodeRouter.classify("Como devo projetar a arquitetura dos módulos?"))
        assertEquals(CodeRole.SECURITY, CodeRouter.classify("procure vulnerabilidades de injection"))
        assertEquals(CodeRole.DOCS, CodeRouter.classify("escreva a documentação do README"))
        assertEquals("deepseek/deepseek-v4-pro", CodeRouter.pick(CodeRole.GENERATE, models, Profile.BALANCED)!!.model.id)
        assertEquals("google/gemini-3-flash-preview", CodeRouter.pick(CodeRole.QUICK_FIX, models, Profile.BALANCED)!!.model.id)
        assertEquals("frontier", CodeRouter.pick(CodeRole.ARCHITECTURE, models, Profile.ECO)!!.model.tier)
        assertEquals("frontier", CodeRouter.pick(CodeRole.DEBUG, models, Profile.BALANCED, escalate = true)!!.model.tier)
        val review = CodeRouter.pick(CodeRole.REVIEW, models, Profile.BALANCED, avoidVendor = "Google")!!
        assertEquals("Anthropic", review.model.vendor)
        assertEquals("anthropic/claude-sonnet-4.6", CodeRouter.pick(CodeRole.GENERATE, models, Profile.BALANCED, manual = "anthropic/claude-sonnet-4.6")!!.model.id)
    }

    // --- Codex: agente e sessão ----------------------------------------------------------------

    private fun project(): File = File(app.cacheDir, "proj-${System.nanoTime()}").apply {
        File(this, "src/app/Calc.kt").apply { parentFile!!.mkdirs() }.writeText("package app\n\nobject Calc {\n    fun soma(a: Int, b: Int): Int = a - b\n}\n")
        File(this, "README.md").writeText("# Calc\n")
        File(this, "build/lixo.txt").apply { parentFile!!.mkdirs() }.writeText("não deve aparecer")
    }

    private fun scriptFix(review: Boolean = true) {
        fake.replies += FakeGateway.action("search", "query" to "fun soma", "thought" to "Procurar a função")
        fake.replies += "```json\n" + FakeGateway.action("read", "path" to "src/app/Calc.kt") + "\n```"
        fake.replies += FakeGateway.action("edit", "path" to "src/app/Calc.kt", "find" to "a - b", "replace" to "a + b", "thought" to "A soma estava subtraindo")
        fake.replies += FakeGateway.action("check")
        fake.replies += FakeGateway.action("finish", "summary" to "Corrigi a soma.", "changes" to "Troquei - por + em Calc.soma.", "verified" to listOf("Calc.kt linha 4 subtraía"), "hypotheses" to listOf("Não há testes para soma"), "risks" to listOf<String>(), "sources" to listOf<String>())
        if (review) fake.replies += """{"ok": true, "issues": []}"""
    }

    private fun session(ws: app.lily.ai.codex.Workspace, mode: CodexMode, task: String, error: String = "", review: Boolean = true, auto: Int? = null) = CodexSession(
        scope = scope,
        workspace = ws,
        mode = mode,
        task = task,
        errorText = error,
        models = { client.codeModels(refresh = true) },
        llmFor = { m: RemoteModel -> RemoteCodeLlm(client, m) },
        gateway = client,
        restore = RestorePoints(File(app.cacheDir, "restauro-${System.nanoTime()}")),
        profile = Profile.BALANCED,
        manualModel = null,
        webAllowed = true,
        reviewEnabled = review,
        autoApplyLines = auto,
        checkPollMs = 5,
        checkTimeoutMs = 10_000,
    )

    @Test
    fun `agente lê, edita e verifica sem gravar antes da aprovação`() = runBlocking {
        val dir = project()
        val ws = FileWorkspace(dir)
        assertFalse("pastas geradas ficam de fora", ws.files().any { it.path.startsWith("build/") })
        scriptFix(review = false)
        val events = ArrayList<CodexEvent>()
        val model = client.codeModels(refresh = true).first()
        val agent = CodexAgent(ws, RemoteCodeLlm(client, model), client, webAllowed = true, emit = { events += it })
        val report = agent.run("A soma está errada", "", maxSteps = 10)!!
        assertEquals("Corrigi a soma.", report.summary)
        assertEquals(listOf("Não há testes para soma"), report.hypotheses)
        assertEquals(1, agent.staged.size)
        assertTrue(File(dir, "src/app/Calc.kt").readText().contains("a - b"))
        assertTrue(events.any { it is CodexEvent.Staged })
        assertTrue(events.any { it is CodexEvent.Checks && it.ok })
        assertTrue(events.any { it is CodexEvent.Thought && it.text == "A soma estava subtraindo" })
        // O resultado de cada ação volta para o modelo (o arquivo lido com números de linha).
        assertTrue(fake.chatRequests.toList()[2].toString().contains("4: "))
    }

    @Test
    fun `sessão com aprovação, ponto de restauração e desfazer`() {
        val dir = project()
        scriptFix()
        val s = session(FileWorkspace(dir), CodexMode.TASK, "A soma está errada")
        s.start()
        waitFor { s.phase == Phase.AWAITING_APPROVAL }
        assertEquals(1, s.diffs.size)
        assertEquals(1, s.diffs[0].added)
        assertTrue(s.review!!.ok)
        assertTrue("revisão por outro fornecedor", s.review!!.reviewer.contains("Gemini") || s.review!!.reviewer.contains("Claude"))
        assertTrue(File(dir, "src/app/Calc.kt").readText().contains("a - b"))
        s.decide(Decision.Approve)
        waitFor { s.phase == Phase.DONE }
        assertTrue(File(dir, "src/app/Calc.kt").readText().contains("a + b"))
        assertEquals(Verification.SYNTAX_ONLY, s.verification)
        assertNotNull(s.restoreId)
        assertTrue(s.cost > 0)
        s.undo()
        waitFor { s.restoreId == null }
        assertTrue(File(dir, "src/app/Calc.kt").readText().contains("a - b"))
    }

    @Test
    fun `descartar não grava nada`() {
        val dir = project()
        scriptFix()
        val s = session(FileWorkspace(dir), CodexMode.TASK, "A soma está errada")
        s.start()
        waitFor { s.phase == Phase.AWAITING_APPROVAL }
        s.decide(Decision.Reject)
        waitFor { s.phase == Phase.DONE }
        assertTrue(File(dir, "src/app/Calc.kt").readText().contains("a - b"))
        assertNull(s.applied)
    }

    @Test
    fun `correção simples aplica sozinha no modo automático`() {
        val dir = project()
        scriptFix(review = false)
        val s = session(FileWorkspace(dir), CodexMode.TASK, "A soma está errada", review = false, auto = 12)
        s.start()
        waitFor { s.phase == Phase.DONE }
        assertTrue(s.autoApplied)
        assertTrue(File(dir, "src/app/Calc.kt").readText().contains("a + b"))
    }

    private fun repo() {
        fake.base.clear()
        fake.base["app/src/main/java/app/demo/Router.kt"] = "package app.demo\n\nfun rota() = foo()\n"
        fake.base["app/build.gradle.kts"] = "plugins { }\n"
    }

    private fun scriptRepoAttempt(from: String, to: String) {
        fake.replies += FakeGateway.action("read", "path" to "app/src/main/java/app/demo/Router.kt")
        fake.replies += FakeGateway.action("edit", "path" to "app/src/main/java/app/demo/Router.kt", "find" to from, "replace" to to)
        fake.replies += FakeGateway.action("finish", "summary" to "Troquei $from por $to.", "changes" to "$from → $to", "verified" to listOf("o erro aponta Router.kt:3"), "hypotheses" to listOf<String>(), "risks" to listOf("só o CI confirma"), "sources" to listOf<String>())
        fake.replies += """{"ok": true, "issues": []}"""
    }

    @Test
    fun `auto-reparo num repositório corrige, testa no CI, tenta de novo e confirma`() {
        repo()
        fake.ciResults += false to "e: file:///w/app/src/main/java/app/demo/Router.kt:3:14 Unresolved reference 'bar'.\nBUILD FAILED"
        fake.ciResults += true to "BUILD SUCCESSFUL"
        scriptRepoAttempt("foo()", "bar()")
        scriptRepoAttempt("bar()", "rotaPadrao()")
        val ws = RepoWorkspace(client, "dono/app", "main", CodexSession.branchName(), TestPlan("gradle", ":app:testDebugUnitTest", "."))
        val s = session(ws, CodexMode.REPAIR, "", error = "e: file:///w/app/src/main/java/app/demo/Router.kt:3:14 Unresolved reference 'foo'.")
        s.start()
        waitFor { s.phase == Phase.AWAITING_APPROVAL }
        s.decide(Decision.Approve)
        waitFor { s.phase == Phase.AWAITING_APPROVAL && s.attempt == 2 }
        s.decide(Decision.Approve)
        waitFor { s.phase == Phase.DONE }
        assertEquals(Verification.ERROR_GONE, s.verification)
        assertEquals(2, s.attempt)
        assertEquals(2, fake.commits.size)
        assertEquals(2, fake.checks.size)
        assertTrue(fake.branchName!!.startsWith("lily-codex/"))
        assertEquals("package app.demo\n\nfun rota() = foo()\n", fake.base["app/src/main/java/app/demo/Router.kt"])
        assertTrue(fake.branch["app/src/main/java/app/demo/Router.kt"]!!.contains("rotaPadrao()"))
        assertTrue(s.events.any { it is CodexEvent.Plan && it.text.startsWith("Tentativa 2") })
    }

    @Test
    fun `auto-reparo para quando o mesmo erro volta (sem ciclo infinito)`() {
        repo()
        val error = "e: file:///w/app/src/main/java/app/demo/Router.kt:3:14 Unresolved reference 'foo'."
        fake.ciResults += false to "$error\nBUILD FAILED"
        scriptRepoAttempt("foo()", "foo( )")
        val ws = RepoWorkspace(client, "dono/app", "main", CodexSession.branchName(), TestPlan("gradle", ":app:testDebugUnitTest", "."))
        val s = session(ws, CodexMode.REPAIR, "", error = error)
        s.start()
        waitFor { s.phase == Phase.AWAITING_APPROVAL }
        s.decide(Decision.Approve)
        waitFor { s.phase == Phase.DONE }
        assertEquals(Verification.NOT_FIXED, s.verification)
        assertEquals(1, s.attempt)
        assertTrue(s.events.any { it is CodexEvent.Failure && it.text.contains("mesmo erro") })
    }
}
