package app.lily.ai.diagnostics

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import app.lily.ai.BuildConfig
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Os erros que fecharam a L.I.L.Y AI, guardados no aparelho (os últimos [KEEP]) para a pessoa ver,
 * copiar e exportar sem computador. A captura liga no começo do processo (antes dos providers e do
 * resto do app). Com o app na tela, o relatório abre na hora em [DiagnosticActivity], que roda num
 * processo separado e não depende do resto do app; ele também abre pelo ícone "L.I.L.Y
 * Diagnóstico". Nada é enviado para fora do aparelho sozinho.
 */
object CrashReport {
    private const val MAX_CHARS = 24_000
    private const val KEEP = 10

    /** Processo da tela de diagnóstico (ver o manifesto). */
    const val PROCESS_SUFFIX = ":relatorio"

    @Volatile
    private var started = 0

    @Volatile
    private var handler: Thread.UncaughtExceptionHandler? = null

    /** O app de agora (o tratador grava nele). */
    @Volatile
    private var target: Application? = null

    /** Liga a captura: guarda o erro e, com o app na tela, mostra o relatório. Depois o Android fecha o app como sempre. */
    fun install(app: Application) {
        target = app
        app.registerActivityLifecycleCallbacks(Counter)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous != null && previous === handler) return
        handler = Thread.UncaughtExceptionHandler { thread, error ->
            val app = target ?: return@UncaughtExceptionHandler previous?.uncaughtException(thread, error) ?: Unit
            runCatching { Log.e(DiagLog.TAG, "Erro não tratado na thread ${thread.name}", error) }
            runCatching { save(app, thread.name, error) }.onFailure { runCatching { Log.e(DiagLog.TAG, "Não deu para guardar o erro", it) } }
            runCatching { DiagLog.line("✗ ERRO FATAL (${module(error)}): ${error.javaClass.name}: ${error.message}", Log.ERROR) }
            // Na tela (ou abrindo pelo ícone, ainda na thread principal): mostra o relatório já.
            if (started > 0 || thread === Looper.getMainLooper().thread) runCatching { app.startActivity(intent(app, crashed = true)) }
            // No Android sempre há o tratador do sistema (ele fecha o app). Sem ele (testes na JVM),
            // só a thread morre.
            previous?.uncaughtException(thread, error)
        }
        Thread.setDefaultUncaughtExceptionHandler(handler)
    }

    fun intent(context: Context, crashed: Boolean = false): Intent = Intent(context, DiagnosticActivity::class.java)
        .putExtra(DiagnosticActivity.EXTRA_CRASHED, crashed)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    /** Monta o relatório: versão, aparelho, hora, processo, módulo provável e a pilha completa (com as causas). */
    fun format(
        thread: String,
        error: Throwable,
        process: String = Application.getProcessName(),
        now: Instant = Instant.now(),
        fatal: Boolean = true,
    ): String {
        val time = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.forLanguageTag("pt-BR")).format(now.atZone(ZoneId.systemDefault()))
        val head = buildString {
            appendLine("L.I.L.Y AI ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) " + if (fatal) "fechou por um erro" else "teve um erro (o app seguiu)")
            appendLine("Aparelho: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Quando: $time · processo $process · thread $thread")
            appendLine("Módulo provável: ${module(error)}")
            appendLine()
        }
        val stack = error.stackTraceToString()
        return head + if (stack.length > MAX_CHARS) stack.take(MAX_CHARS) + "\n…(cortado)" else stack
    }

    /** Guarda um erro no histórico (o mais novo primeiro; os mais antigos saem). */
    fun save(context: Context, thread: String, error: Throwable, fatal: Boolean = true) {
        val dir = dir(context).apply { mkdirs() }
        File(dir, "erro-${System.currentTimeMillis()}.txt").writeText(format(thread, error, fatal = fatal))
        all(dir).drop(KEEP).forEach { it.delete() }
    }

    /** O último relatório guardado, se houver. */
    fun last(context: Context): String? = history(context).firstOrNull()

    /** Quando o erro guardado mais novo aconteceu (null se não há nenhum). */
    fun lastTime(context: Context): Long? = all(dir(context)).firstOrNull()?.name?.removePrefix("erro-")?.removeSuffix(".txt")?.toLongOrNull()

    /** Todos os relatórios guardados, do mais novo para o mais antigo. */
    fun history(context: Context): List<String> = all(dir(context)).mapNotNull { runCatching { it.readText() }.getOrNull()?.takeIf(String::isNotBlank) }

    fun clear(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
        File(context.filesDir, "ultimo-erro.txt").delete()
    }

    private fun dir(context: Context) = File(DiagLog.dir(context), "erros")

    private fun all(dir: File): List<File> = (dir.listFiles { f -> f.name.startsWith("erro-") } ?: emptyArray())
        .sortedByDescending { it.name.removePrefix("erro-").removeSuffix(".txt").toLongOrNull() ?: 0L }

    /**
     * Onde o erro nasceu: a biblioteca ou parte do app no ponto mais fundo da pilha e, se for
     * diferente, a parte do app que a chamou.
     */
    fun module(error: Throwable): String {
        val chain = generateSequence(error) { t -> t.cause?.takeIf { it !== t } }.take(20).toList()
        val root = chain.last()
        // Erro ao instalar um provider no começo do processo: é a partida de uma biblioteca, antes de
        // qualquer código da L.I.L.Y (o R8 às vezes junta o código dela numa classe nossa, o que
        // apontaria o módulo errado).
        val atStartup = chain.any { t -> t.stackTrace.any { it.className == "android.app.ActivityThread" && it.methodName == "installProvider" } }
        if (atStartup) {
            val text = chain.joinToString(" ") { it.message.orEmpty() }
            val lib = libraryIn(text) ?: chain.asReversed().firstNotNullOfOrNull { t -> t.stackTrace.firstNotNullOfOrNull { libraryIn(it.className) } }
            return "Partida das bibliotecas, antes do código da L.I.L.Y" + (lib?.let { " ($it)" } ?: "")
        }
        val frames = root.stackTrace.map { it.className }
        val first = frames.firstNotNullOfOrNull(::moduleOf)
        val caller = frames.firstOrNull { it.startsWith("app.lily.ai.") }?.let(::moduleOf)
        return when {
            first == null -> "Sistema Android (${frames.firstOrNull() ?: root.javaClass.name})"
            caller == null || caller == first -> first
            else -> "$first · chamado por: $caller"
        }
    }

    /** A biblioteca citada num nome de classe ou numa mensagem de erro. */
    private fun libraryIn(text: String): String? = when {
        "androidx.work" in text -> "WorkManager, que vem com o LiteRT"
        "androidx.room" in text -> "banco de dados Room"
        "mlkit" in text || "firebase.components" in text -> "ML Kit"
        "androidx.emoji2" in text -> "emojis"
        "androidx.lifecycle" in text -> "ciclo de vida"
        "androidx.profileinstaller" in text -> "perfil de desempenho"
        "litert" in text -> "LiteRT"
        else -> null
    }

    private fun moduleOf(cls: String): String? = when {
        cls.startsWith("app.lily.ai.engine.EngineHost") || cls.startsWith("app.lily.ai.engine.Llm") -> "Motor de linguagem (LiteRT-LM)"
        cls.startsWith("app.lily.ai.engine.Vision") || cls.startsWith("app.lily.ai.engine.Translator") || cls.startsWith("app.lily.ai.engine.MlKit") -> "Visão e tradução (ML Kit)"
        cls.startsWith("app.lily.ai.engine.SuperRes") || cls.startsWith("app.lily.ai.engine.Enhance") -> "Ampliação de imagem (LiteRT)"
        cls.startsWith("app.lily.ai.engine.Audio") || cls.startsWith("app.lily.ai.engine.Voice") || cls.startsWith("app.lily.ai.engine.SpeechInput") -> "Voz"
        cls.startsWith("app.lily.ai.engine.Documents") -> "Documentos"
        cls.startsWith("app.lily.ai.engine.") -> "Motores"
        cls.startsWith("app.lily.ai.models.") -> "Model Hub e downloads"
        cls.startsWith("app.lily.ai.data.") -> "Dados salvos (conversas, memória, configurações)"
        cls.startsWith("app.lily.ai.assist.") -> "Voz pela ilha (painel da L.I.L.Y)"
        cls.startsWith("app.lily.ai.bridge.") -> "Ponte com o teclado e a Gallery"
        cls.startsWith("app.lily.ai.imagegen.") -> "Geração de imagem"
        cls.startsWith("app.lily.ai.tools.") -> "Ferramentas"
        cls.startsWith("app.lily.ai.core.") -> "Núcleo (roteador e aparelho)"
        cls.startsWith("app.lily.ai.diagnostics.") -> "Diagnóstico"
        cls.startsWith("app.lily.ai.ui.") -> "Interface"
        cls.startsWith("app.lily.ai.") -> "Inicialização do app"
        cls.startsWith("com.google.mlkit") || cls.startsWith("com.google.android.gms.internal.mlkit") -> "ML Kit (Google)"
        cls.startsWith("com.google.ai.edge.litertlm") -> "LiteRT-LM (Google)"
        cls.startsWith("com.google.ai.edge.litert") -> "LiteRT (Google)"
        cls.startsWith("com.google.firebase.components") -> "ML Kit (componentes)"
        cls.startsWith("androidx.work") -> "WorkManager (vem com o LiteRT)"
        cls.startsWith("androidx.room") -> "Banco de dados (Room)"
        cls.startsWith("androidx.compose") -> "Interface (Jetpack Compose)"
        cls.startsWith("kotlinx.serialization") -> "Leitura de dados salvos"
        cls.startsWith("kotlinx.coroutines") -> null
        else -> null
    }

    private object Counter : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            started++
        }

        override fun onActivityStopped(activity: Activity) {
            started = (started - 1).coerceAtLeast(0)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
