package app.lily.keyboard.clipboard

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
enum class ClipKind { TEXT, LINK, EMAIL, PHONE, NUMBER }

@Immutable
@Serializable
data class ClipItem(val id: Long, val text: String, val time: Long, val pinned: Boolean = false, val kind: ClipKind = ClipKind.TEXT)

/**
 * Histórico da área de transferência do teclado.
 *
 * O Android só deixa o teclado ativo ler o que foi copiado. Itens marcados como sensíveis pelo app
 * de origem (senhas, por exemplo), códigos numéricos de verificação e textos que parecem senhas não
 * são guardados. Itens não fixados somem depois de uma hora. Tudo fica num arquivo privado, fora de
 * backups, e o histórico pode ser desligado.
 */
class ClipboardHistory(private val file: File?, private val clock: () -> Long = System::currentTimeMillis) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<ClipItem>> = _items.asStateFlow()

    private var manager: ClipboardManager? = null
    private var enabled: () -> Boolean = { true }
    private val listener = ClipboardManager.OnPrimaryClipChangedListener { onClipChanged() }

    fun attach(context: Context, isEnabled: () -> Boolean) {
        if (manager != null) return
        enabled = isEnabled
        manager = context.getSystemService(ClipboardManager::class.java)
        runCatching { manager?.addPrimaryClipChangedListener(listener) }
    }

    fun detach() {
        runCatching { manager?.removePrimaryClipChangedListener(listener) }
        manager = null
    }

    private fun onClipChanged() {
        if (!enabled()) return
        val clip = runCatching { manager?.primaryClip }.getOrNull() ?: return
        val description = clip.description
        if (description?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true) return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).text?.toString() ?: return
        add(text)
    }

    /** Guarda um texto copiado (se não for sensível). Devolve `false` se foi descartado. */
    fun add(text: String): Boolean {
        val clean = text.take(MAX_LENGTH)
        if (clean.isBlank() || looksSensitive(clean)) return false
        val now = clock()
        val existing = _items.value.firstOrNull { it.text == clean }
        // Identificador único mesmo para duas cópias no mesmo milissegundo.
        val id = existing?.id ?: maxOf(now, (_items.value.maxOfOrNull { it.id } ?: 0L) + 1)
        val item = ClipItem(id, clean, now, existing?.pinned ?: false, classify(clean))
        _items.value = (listOf(item) + _items.value.filter { it.text != clean }).let(::prune)
        save()
        return true
    }

    fun togglePin(id: Long) {
        _items.value = _items.value.map { if (it.id == id) it.copy(pinned = !it.pinned) else it }
        save()
    }

    fun remove(id: Long) {
        _items.value = _items.value.filter { it.id != id }
        save()
    }

    /** Apaga o histórico. Com [keepPinned], os fixados ficam. */
    fun clear(keepPinned: Boolean = true) {
        _items.value = if (keepPinned) _items.value.filter { it.pinned } else emptyList()
        save()
    }

    /** Remove os itens vencidos; chamado quando o painel abre. */
    fun prune() {
        val next = prune(_items.value)
        if (next != _items.value) {
            _items.value = next
            save()
        }
    }

    private fun prune(list: List<ClipItem>): List<ClipItem> {
        val now = clock()
        val fresh = list.filter { it.pinned || now - it.time < EXPIRY_MS }
        val pinned = fresh.filter { it.pinned }
        val others = fresh.filter { !it.pinned }.take((MAX_ITEMS - pinned.size).coerceAtLeast(0))
        return fresh.filter { it in pinned || it in others }
    }

    private fun load(): List<ClipItem> {
        val f = file ?: return emptyList()
        if (!f.exists()) return emptyList()
        return runCatching { json.decodeFromString(ListSerializer(ClipItem.serializer()), f.readText()) }
            .onFailure { Log.w("LilyKeyboard", "Histórico da área de transferência ilegível", it) }
            .getOrDefault(emptyList())
    }

    private fun save() {
        val f = file ?: return
        runCatching { f.writeText(json.encodeToString(ListSerializer(ClipItem.serializer()), _items.value)) }
    }

    companion object {
        private const val MAX_ITEMS = 30
        private const val MAX_LENGTH = 5_000
        const val EXPIRY_MS = 60 * 60 * 1000L

        private val URL = Regex("^(https?://|www\\.)\\S+$", RegexOption.IGNORE_CASE)
        private val EMAIL = Regex("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$")
        private val PHONE = Regex("^\\+?[\\d ()-]{8,20}$")
        private val NUMBER = Regex("^-?[\\d.,]+$")
        /** Códigos de verificação (4 a 8 dígitos, às vezes separados por espaço ou hífen). */
        private val OTP = Regex("^\\s*\\d{3,4}[ -]?\\d{0,4}\\s*$")
        /** Uma "palavra" só, longa, misturando maiúsculas, minúsculas, números e símbolos: provável senha ou chave. */
        private val SECRET = Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^\\w\\s])\\S{8,64}$")
        private val TOKEN = Regex("^[A-Za-z0-9_\\-]{32,}$")

        fun looksSensitive(text: String): Boolean {
            val t = text.trim()
            if (OTP.matches(t) && t.count { it.isDigit() } in 4..8) return true
            if (SECRET.matches(t)) return true
            if (TOKEN.matches(t) && t.any { it.isDigit() } && t.any { it.isLetter() }) return true
            return false
        }

        fun classify(text: String): ClipKind {
            val t = text.trim()
            return when {
                URL.matches(t) -> ClipKind.LINK
                EMAIL.matches(t) -> ClipKind.EMAIL
                PHONE.matches(t) && t.count { it.isDigit() } >= 8 -> ClipKind.PHONE
                NUMBER.matches(t) -> ClipKind.NUMBER
                else -> ClipKind.TEXT
            }
        }
    }
}
