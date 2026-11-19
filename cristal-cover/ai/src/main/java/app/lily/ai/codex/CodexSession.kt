package app.lily.ai.codex

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.lily.ai.core.Profile
import app.lily.ai.presence.LilyPresence
import app.lily.ai.remote.GatewayClient
import app.lily.ai.remote.RemoteMessage
import app.lily.ai.remote.RemoteModel
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class CodexMode(val label: String, val detail: String) {
    TASK("Tarefa", "Escrever, alterar ou refatorar código"),
    REPAIR("Consertar erro", "Cole o erro ou o log: o Codex acha a causa, corrige e confere"),
    EXPLAIN("Explicar", "Entender um arquivo, uma parte ou o projeto"),
    SECURITY("Revisar segurança", "Procura vulnerabilidades e segredos expostos"),
}

enum class Phase(val label: String) {
    IDLE("Pronto"),
    PREPARING("Escolhendo o modelo"),
    WORKING("Trabalhando"),
    REVIEWING("Revisão independente"),
    AWAITING_APPROVAL("Esperando sua aprovação"),
    APPLYING("Aplicando"),
    VALIDATING("Validando"),
    DONE("Concluído"),
    FAILED("Não concluído"),
    CANCELLED("Cancelado"),
}

/** O quanto a solução foi conferida de verdade (compilar não basta). */
enum class Verification(val label: String) {
    NONE("Nada aplicado"),
    SYNTAX_ONLY("Só sintaxe: os testes do projeto não rodam no aparelho"),
    TESTS_PASSED("Testes do projeto passaram"),
    TESTS_FAILED("Testes do projeto falharam"),
    ERROR_GONE("O erro não aparece mais e os testes passaram"),
    NOT_FIXED("O erro continua"),
}

data class ReviewResult(val reviewer: String, val ok: Boolean, val issues: List<String>)

/** O que a pessoa decidiu no diff. */
sealed interface Decision {
    data object Approve : Decision
    data object Reject : Decision
    data class Adjust(val request: String) : Decision
}

/** Guarda o conteúdo de antes de cada mudança aplicada (para desfazer). */
class RestorePoints(private val dir: File) {
    private val json = Json { encodeDefaults = true }

    fun save(changes: List<FileChange>): String {
        val id = "rp-" + System.currentTimeMillis()
        dir.mkdirs()
        File(dir, "$id.json").writeText(json.encodeToString(ListSerializer(FileChange.serializer()), changes))
        return id
    }

    fun load(id: String): List<FileChange>? = runCatching {
        json.decodeFromString(ListSerializer(FileChange.serializer()), File(dir, "$id.json").readText())
    }.getOrNull()

    /** As mudanças que desfazem o ponto [id] (o "depois" volta a ser o "antes"). */
    fun inverse(id: String): List<FileChange>? = load(id)?.map { FileChange(it.path, it.after, it.before) }
}

/**
 * Uma sessão do Codex: escolhe o modelo, roda o agente, pede uma revisão independente, espera a
 * aprovação do diff, cria o ponto de restauração, aplica e valida (testes do projeto no GitHub
 * Actions quando é um repositório). No modo "Consertar erro", repete com outra abordagem se o erro
 * continuar — no máximo [MAX_ATTEMPTS] vezes, e para se o mesmo erro voltar.
 */
@Stable
class CodexSession(
    private val scope: CoroutineScope,
    val workspace: Workspace,
    val mode: CodexMode,
    val task: String,
    val errorText: String,
    private val models: suspend () -> List<RemoteModel>,
    private val llmFor: (RemoteModel) -> CodeLlm,
    private val gateway: GatewayClient?,
    private val restore: RestorePoints,
    private val profile: Profile,
    private val manualModel: String?,
    private val webAllowed: Boolean,
    private val reviewEnabled: Boolean,
    private val autoApplyLines: Int?,
    private val checkPollMs: Long = 10_000,
    private val checkTimeoutMs: Long = 25 * 60_000,
) {
    val id: String = UUID.randomUUID().toString().take(8)
    val events = mutableStateListOf<CodexEvent>()
    var phase by mutableStateOf(Phase.IDLE)
        private set
    var pick by mutableStateOf<CodePick?>(null)
        private set
    var report by mutableStateOf<FinishReport?>(null)
        private set
    var diffs by mutableStateOf<List<FileDiff>>(emptyList())
        private set
    var review by mutableStateOf<ReviewResult?>(null)
        private set
    var verification by mutableStateOf(Verification.NONE)
        private set
    var applied by mutableStateOf<ApplyResult?>(null)
        private set
    var restoreId by mutableStateOf<String?>(null)
        private set
    var attempt by mutableStateOf(0)
        private set
    var cost by mutableStateOf(0.0)
        private set
    var checkUrl by mutableStateOf<String?>(null)
        private set
    var autoApplied by mutableStateOf(false)
        private set

    private var job: Job? = null
    private var decision: CompletableDeferred<Decision>? = null
    private var lastChanges: List<FileChange> = emptyList()

    val busy: Boolean get() = phase in setOf(Phase.PREPARING, Phase.WORKING, Phase.REVIEWING, Phase.APPLYING, Phase.VALIDATING)

    private fun emit(e: CodexEvent) {
        scope.launch(Dispatchers.Main.immediate) { events += e }
    }

    private suspend fun set(p: Phase) = withContext(Dispatchers.Main.immediate) {
        phase = p
        when (p) {
            Phase.PREPARING, Phase.WORKING, Phase.REVIEWING, Phase.APPLYING, Phase.VALIDATING -> LilyPresence.working(PRESENCE, "Codex: ${p.label.lowercase()}")
            Phase.AWAITING_APPROVAL -> LilyPresence.clear(PRESENCE)
            Phase.DONE -> LilyPresence.finished(PRESENCE, verification != Verification.TESTS_FAILED && verification != Verification.NOT_FIXED, "Codex concluiu")
            Phase.FAILED -> LilyPresence.finished(PRESENCE, false, "Codex não concluiu")
            else -> LilyPresence.clear(PRESENCE)
        }
    }

    fun start() {
        if (job != null) return
        job = scope.launch(Dispatchers.Default) {
            try {
                run()
            } catch (c: CancellationException) {
                withContext(Dispatchers.Main.immediate + kotlinx.coroutines.NonCancellable) {
                    phase = Phase.CANCELLED
                    LilyPresence.clear(PRESENCE)
                }
                throw c
            } catch (e: Exception) {
                emit(CodexEvent.Failure(e.message ?: e.javaClass.simpleName))
                set(Phase.FAILED)
            }
        }
    }

    fun decide(d: Decision) {
        decision?.complete(d)
    }

    fun cancel() {
        job?.cancel()
    }

    private suspend fun run() {
        set(Phase.PREPARING)
        val catalog = models()
        if (catalog.isEmpty()) {
            emit(CodexEvent.Failure("O Codex precisa do serviço online (modelos de programação). Conecte o L.I.L.Y Gateway em Configurações > Serviço online."))
            set(Phase.FAILED)
            return
        }
        val role = when (mode) {
            CodexMode.REPAIR -> CodeRole.DEBUG
            CodexMode.EXPLAIN -> CodeRole.EXPLAIN
            CodexMode.SECURITY -> CodeRole.SECURITY
            CodexMode.TASK -> CodeRouter.classify(task)
        }
        var error: ErrorReport? = if (mode == CodexMode.REPAIR && errorText.isNotBlank()) ErrorParser.parse(errorText, workspace.files().map { it.path }) else null
        val seen = LinkedHashMap<String, Int>()
        error?.let { seen[it.signature] = 1 }
        val maxAttempts = if (mode == CodexMode.REPAIR) MAX_ATTEMPTS else 1
        var extra = ""
        while (attempt < maxAttempts) {
            attempt++
            val chosen = CodeRouter.pick(role, catalog, profile, manualModel, escalate = attempt > 1) ?: run {
                emit(CodexEvent.Failure("Nenhum modelo de programação disponível no gateway."))
                set(Phase.FAILED)
                return
            }
            withContext(Dispatchers.Main.immediate) { pick = chosen }
            emit(CodexEvent.Plan((if (attempt > 1) "Tentativa $attempt · " else "") + "Modelo: ${chosen.model.label} (${chosen.model.vendor}) · " + chosen.reasons.joinToString(" ")))
            set(Phase.WORKING)
            val agent = CodexAgent(workspace, llmFor(chosen.model), gateway, webAllowed, ::emit)
            val ctx = buildString {
                if (error != null) {
                    append("ERROR TO FIX (type ${error.type}): ${error.message}\n")
                    if (error.locations.isNotEmpty()) append("Locations found in the project: ").append(error.locations.joinToString { "${it.first}:${it.second}" }).append('\n')
                    append("Raw log (tail):\n").append(error.raw.takeLast(6_000)).append('\n')
                    append("Steps: 1) understand the error and the logs 2) open the files and lines above 3) investigate related files 4) find the most probable cause 5) make the smallest correct fix 6) run check.\n")
                }
                if (extra.isNotEmpty()) append(extra)
            }
            val taskText = when (mode) {
                CodexMode.REPAIR -> task.ifBlank { "Corrija o erro abaixo." }
                CodexMode.EXPLAIN -> "Explique (não altere arquivos): ${task.ifBlank { "o projeto" }}"
                CodexMode.SECURITY -> "Revise a segurança (vulnerabilidades, segredos expostos, permissões excessivas). Só proponha mudanças se forem seguras e pequenas. ${task}"
                CodexMode.TASK -> task
            }
            val finish = agent.run(taskText, ctx, maxSteps = if (role == CodeRole.QUICK_FIX || role == CodeRole.EXPLAIN) 14 else 28)
            withContext(Dispatchers.Main.immediate) {
                cost += agent.totalCost
                report = finish
            }
            val changes = agent.staged.changes()
            lastChanges = changes
            withContext(Dispatchers.Main.immediate) { diffs = changes.map { Diff.of(it.path, it.before, it.after) } }
            if (finish == null && changes.isEmpty()) {
                set(Phase.FAILED)
                return
            }
            if (changes.isEmpty()) {
                emit(CodexEvent.Note(if (mode == CodexMode.REPAIR) "O Codex não propôs mudanças: veja o relatório." else "Nenhum arquivo alterado."))
                set(Phase.DONE)
                return
            }
            val (syntaxOk, _) = agent.runChecks()
            // Revisão independente: outro fornecedor olha o diff antes de você.
            if (reviewEnabled) {
                set(Phase.REVIEWING)
                CodeRouter.pick(CodeRole.REVIEW, catalog, profile, avoidVendor = chosen.model.vendor)?.takeIf { it.model.id != chosen.model.id }?.let { reviewer ->
                    val r = runCatching { review(llmFor(reviewer.model), reviewer.model.label, taskText, finish, changes) }.getOrNull()
                    withContext(Dispatchers.Main.immediate) { review = r }
                    r?.let { emit(CodexEvent.Note(if (it.ok) "Revisão (${it.reviewer}): sem problemas" else "Revisão (${it.reviewer}): ${it.issues.size} ponto(s) de atenção")) }
                }
            }
            val totalLines = diffs.sumOf { it.added + it.removed }
            val canAuto = autoApplyLines != null && changes.size == 1 && totalLines <= autoApplyLines && syntaxOk && review?.ok != false && mode != CodexMode.SECURITY
            val d: Decision = if (canAuto) {
                withContext(Dispatchers.Main.immediate) { autoApplied = true }
                emit(CodexEvent.Note("Correção simples aplicada sozinha (modo automático, $totalLines linhas). Dá para desfazer."))
                Decision.Approve
            } else {
                val wait = CompletableDeferred<Decision>()
                decision = wait
                set(Phase.AWAITING_APPROVAL)
                wait.await()
            }
            when (d) {
                Decision.Reject -> {
                    emit(CodexEvent.Note("Mudanças descartadas. Nada foi gravado."))
                    withContext(Dispatchers.Main.immediate) { diffs = emptyList() }
                    set(Phase.DONE)
                    return
                }
                is Decision.Adjust -> {
                    extra = "The user reviewed your previous proposal and asked: ${d.request}\nPrevious diff:\n" + diffs.joinToString("\n") { it.unified() }.take(8_000)
                    attempt--
                    continue
                }
                Decision.Approve -> Unit
            }
            set(Phase.APPLYING)
            val rp = restore.save(changes)
            val result = workspace.apply(changes, "${mode.label}: ${task.take(72).ifBlank { error?.message?.take(72) ?: "Codex" }}")
            withContext(Dispatchers.Main.immediate) {
                restoreId = rp
                applied = result
            }
            emit(CodexEvent.Note("Aplicado ${result.where}. Ponto de restauração criado."))
            set(Phase.VALIDATING)
            val tests = workspace.tests
            if (workspace is RepoWorkspace && tests != null && gateway != null) {
                ciProblem = null
                val outcome = runCi(workspace, tests)
                if (outcome == null) {
                    withContext(Dispatchers.Main.immediate) { verification = if (syntaxOk) Verification.SYNTAX_ONLY else Verification.TESTS_FAILED }
                    emit(CodexEvent.Note(ciProblem?.let { "Não consegui pedir os testes do projeto: $it A verificação ficou só na sintaxe." } ?: "Os testes do projeto não terminaram a tempo: a verificação ficou só na sintaxe."))
                    break
                }
                if (outcome.first) {
                    val stillThere = error != null && outcome.second.contains(error.message.take(60))
                    withContext(Dispatchers.Main.immediate) {
                        verification = when {
                            error == null -> Verification.TESTS_PASSED
                            stillThere -> Verification.NOT_FIXED
                            else -> Verification.ERROR_GONE
                        }
                    }
                    if (!stillThere) break
                } else {
                    val newError = ErrorParser.parse(outcome.second, workspace.files().map { it.path })
                    val count = (seen[newError.signature] ?: 0) + 1
                    seen[newError.signature] = count
                    withContext(Dispatchers.Main.immediate) { verification = if (mode == CodexMode.REPAIR) Verification.NOT_FIXED else Verification.TESTS_FAILED }
                    if (mode != CodexMode.REPAIR) break
                    if (count >= 2) {
                        emit(CodexEvent.Failure("O mesmo erro voltou depois de duas tentativas diferentes. Parei para não ficar em ciclo: veja o relatório e o diff."))
                        break
                    }
                    emit(CodexEvent.Note("Os testes ainda falham (${newError.type}). Nova tentativa com outra abordagem."))
                    extra = "Your previous fix was applied but the project tests still fail. Do NOT repeat the same approach (${finish?.changes?.take(300)}). New failure:\n${outcome.second.takeLast(5_000)}"
                    error = newError
                    continue
                }
            } else {
                withContext(Dispatchers.Main.immediate) { verification = if (syntaxOk) Verification.SYNTAX_ONLY else Verification.TESTS_FAILED }
                emit(
                    CodexEvent.Note(
                        if (syntaxOk) "Sintaxe conferida no aparelho. Os testes do projeto precisam do repositório com o GitHub Actions (ou do seu computador)."
                        else "A verificação de sintaxe encontrou problemas nos arquivos aplicados: veja o diff ou desfaça.",
                    ),
                )
                break
            }
        }
        set(Phase.DONE)
    }

    /** Por que os testes não puderam ser pedidos (ex.: o ramo não tem o workflow do Codex). */
    private var ciProblem: String? = null

    /** Roda os testes no GitHub Actions do ramo e espera o resultado. (passou, log) ou null se não terminou. */
    private suspend fun runCi(ws: RepoWorkspace, tests: TestPlan): Pair<Boolean, String>? {
        val g = gateway ?: return null
        val started = try {
            g.checkStart(ws.repo, ws.branch, tests.runner, tests.tasks, tests.workdir)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ciProblem = e.message ?: e.javaClass.simpleName
            return null
        }
        emit(CodexEvent.Action("Rodando os testes do projeto no GitHub Actions", "${tests.runner} ${tests.tasks}"))
        val deadline = System.currentTimeMillis() + checkTimeoutMs
        while (System.currentTimeMillis() < deadline) {
            delay(checkPollMs)
            val s = runCatching { g.checkStatus(ws.repo, ws.branch, started.startedAt.ifBlank { Instant.now().toString() }) }.getOrNull() ?: continue
            s.url?.let { u -> withContext(Dispatchers.Main.immediate) { checkUrl = u } }
            if (s.status == "completed") {
                val ok = s.conclusion == "success"
                emit(CodexEvent.Checks(if (ok) "Testes do projeto passaram" else "Testes do projeto falharam (${s.conclusion})", ok))
                return ok to s.failures.joinToString("\n\n") { "[${it.job}]\n${it.log}" }
            }
        }
        return null
    }

    private suspend fun review(llm: CodeLlm, label: String, task: String, finish: FinishReport?, changes: List<FileChange>): ReviewResult {
        val diff = changes.joinToString("\n") { Diff.of(it.path, it.before, it.after).unified() }.take(30_000)
        val reply = llm.complete(
            listOf(
                RemoteMessage(
                    "system",
                    "You are an independent senior code reviewer. Check the proposed diff for bugs, regressions, security problems and invented APIs. " +
                        "Reply ONLY with JSON: {\"ok\": true|false, \"issues\": [\"short issue in Brazilian Portuguese\", ...]}. ok=false only for real problems.",
                ),
                RemoteMessage("user", "Task: $task\nAuthor summary: ${finish?.summary.orEmpty()}\n\nDiff:\n$diff"),
            ),
            maxTokens = 1500,
        )
        reply.costUsd?.let { c -> withContext(Dispatchers.Main.immediate) { cost += c } }
        val text = reply.text
        val obj = runCatching {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            Json.parseToJsonElement(text.substring(start, end + 1)).jsonObject
        }.getOrNull() ?: return ReviewResult(label, true, listOf("Revisão sem formato: ${text.take(200)}"))
        val ok = obj["ok"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true
        val issues = runCatching { obj["issues"]!!.jsonArray.map { it.jsonPrimitive.content } }.getOrDefault(emptyList())
        return ReviewResult(label, ok, issues)
    }

    /** Desfaz as mudanças aplicadas (pelo ponto de restauração). */
    fun undo() {
        val rp = restoreId ?: return
        scope.launch(Dispatchers.Default) {
            val inverse = restore.inverse(rp) ?: return@launch emit(CodexEvent.Failure("Ponto de restauração não encontrado."))
            try {
                val r = workspace.apply(inverse, "Desfaz a mudança do Codex")
                emit(CodexEvent.Note("Desfeito ${r.where}. Os arquivos voltaram ao que eram antes."))
                withContext(Dispatchers.Main.immediate) {
                    restoreId = null
                    verification = Verification.NONE
                }
            } catch (e: Exception) {
                emit(CodexEvent.Failure("Não consegui desfazer: ${e.message}"))
            }
        }
    }

    companion object {
        const val MAX_ATTEMPTS = 3
        const val PRESENCE = "codex"

        fun branchName(): String = "lily-codex/" + java.time.LocalDate.now() + "-" + UUID.randomUUID().toString().take(6)
    }
}
