package app.lily.ai.ui

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.lily.ai.core.TaskKind

/** As telas da L.I.L.Y AI. */
sealed interface Route {
    data object Home : Route

    /**
     * Uma conversa. [draft]/[image]/[audio] chegam de outro app ou da tela inicial; [send] manda o
     * rascunho assim que abre; [voice] = a pergunta foi falada (a resposta pode ser lida em voz
     * alta); [model] = começar com um modelo escolhido à mão.
     */
    data class Chat(
        val id: String? = null,
        val draft: String? = null,
        val image: Uri? = null,
        val audio: Uri? = null,
        val send: Boolean = false,
        val voice: Boolean = false,
        val model: String? = null,
    ) : Route

    data object History : Route
    /** O Model Hub, opcionalmente já filtrado numa categoria (nome de [app.lily.ai.models.Category]). */
    data class Hub(val category: String? = null) : Route
    data class Model(val id: String) : Route
    data object Tools : Route
    data class TextTool(val task: TaskKind, val text: String = "") : Route
    data class ImageTool(val image: Uri? = null, val task: TaskKind? = null) : Route
    data class ImageGen(val prompt: String = "") : Route
    data object Settings : Route
    data object Memory : Route
    data object Diagnostics : Route

    /** Serviço online (conexão com o L.I.L.Y Gateway). */
    data object Online : Route

    /** Aparência e presença da avatar. */
    data object Avatar : Route

    /** Video AI (geração online). */
    data class Video(val prompt: String = "") : Route

    /** L.I.L.Y Codex: [task] já preenchida; [error] = um erro para consertar (do Diagnóstico ou do chat). */
    data class Codex(val task: String = "", val error: String = "") : Route

    /** Termos de Uso, Privacidade ou Créditos (nome de [app.lily.ai.ui.screens.LegalDoc]). */
    data class Legal(val doc: String) : Route
}

/** Uma tela na pilha. A chave não muda quando a rota é trocada no lugar (a conversa ganha um id). */
@Immutable
data class Entry(val key: Int, val route: Route)

/**
 * Pilha de telas simples. Cada entrada pode guardar um objeto que sobrevive enquanto ela estiver na
 * pilha (a conversa em andamento continua se a pessoa abrir o Model Hub e voltar) e é descartado
 * quando ela sai.
 */
@Stable
class Nav(start: Route = Route.Home) {
    private var nextKey = 0
    val stack = mutableStateListOf(Entry(nextKey++, start))
    val current: Entry get() = stack.last()

    /** A última mudança foi para frente (anima da direita) ou para trás. */
    var forward by mutableStateOf(true)
        private set

    private class Retained<T : Any>(val value: T, val dispose: (T) -> Unit) {
        fun close() = dispose(value)
    }

    private val retained = HashMap<Int, Retained<*>>()

    fun go(route: Route) {
        forward = true
        stack.add(Entry(nextKey++, route))
    }

    /** Abre [route] já com um objeto guardado (a conversa da tela externa continua na interna). */
    fun <T : Any> goWith(route: Route, value: T, dispose: (T) -> Unit) {
        val entry = Entry(nextKey++, route)
        retained[entry.key] = Retained(value, dispose)
        forward = true
        stack.add(entry)
    }

    /** Troca a rota da tela atual sem animar (mesma chave). */
    fun replace(route: Route) {
        val top = stack.last()
        if (top.route::class != route::class) drop(top.key)
        stack[stack.lastIndex] = top.copy(route = route)
    }

    /** Atualiza a rota de uma entrada (a conversa ganhou um id), sem animar. */
    fun update(key: Int, route: Route) {
        val i = stack.indexOfFirst { it.key == key }
        if (i >= 0) stack[i] = stack[i].copy(route = route)
    }

    /** Troca a tela atual por outra nova (estado novo, com animação): "Nova conversa". */
    fun reset(route: Route) {
        val top = stack.last()
        drop(top.key)
        forward = true
        stack[stack.lastIndex] = Entry(nextKey++, route)
    }

    fun home() {
        forward = false
        while (stack.size > 1) drop(stack.removeAt(stack.lastIndex).key)
        if (stack.last().route != Route.Home) {
            drop(stack.last().key)
            stack[0] = Entry(nextKey++, Route.Home)
        }
    }

    /** Volta uma tela; false se já está no início. */
    fun back(): Boolean {
        if (stack.size <= 1) return false
        forward = false
        drop(stack.removeAt(stack.lastIndex).key)
        return true
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> retain(key: Int, create: () -> T, dispose: (T) -> Unit = {}): T =
        (retained.getOrPut(key) { Retained(create(), dispose) } as Retained<T>).value

    /** Tira um objeto guardado sem descartá-lo (ele vai para outra entrada). */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> release(key: Int): T? = retained.remove(key)?.value as T?

    /** Descarta tudo (a atividade foi fechada). */
    fun clear() {
        retained.values.forEach { it.close() }
        retained.clear()
    }

    private fun drop(key: Int) {
        retained.remove(key)?.close()
    }

    companion object {
        /** Chave da conversa da tela externa (fora da pilha). */
        const val COVER_KEY = -1
    }
}
