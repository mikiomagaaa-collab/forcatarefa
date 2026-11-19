package app.lily.ai.remote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.Immutable
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Guarda segredos cifrados (a chave de acesso do gateway nunca fica em texto no aparelho). */
interface SecretBox {
    fun seal(plain: String): String
    fun open(sealed: String): String?
}

/** AES-GCM com a chave no Android Keystore (não sai do hardware de segurança). */
class KeystoreBox(private val alias: String = "lily_gateway_key") : SecretBox {
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    override fun seal(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return "v1:" + Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP)
    }

    override fun open(sealed: String): String? = runCatching {
        val parts = sealed.split(':')
        require(parts.size == 3 && parts[0] == "v1")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[1], Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(parts[2], Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrNull()
}

@Immutable
data class GatewayConfig(val url: String, val hasKey: Boolean)

/** Um erro do gateway (ou da rede) com uma mensagem para a pessoa. */
class GatewayException(val status: Int, message: String) : Exception(message)

/**
 * Onde fica a conexão com o L.I.L.Y Gateway: o endereço (preferências) e a chave de acesso (cifrada
 * pelo Keystore). Também guarda o último catálogo de modelos para mostrar sem internet.
 */
class GatewayStore(context: Context, private val box: SecretBox = KeystoreBox()) {
    private val prefs = context.getSharedPreferences("lily_gateway", Context.MODE_PRIVATE)
    private val cacheDir = File(context.filesDir, "gateway").apply { mkdirs() }
    private val _config = MutableStateFlow(read())
    val config: StateFlow<GatewayConfig?> = _config.asStateFlow()

    private fun read(): GatewayConfig? {
        val url = prefs.getString(KEY_URL, null) ?: return null
        return GatewayConfig(url, prefs.contains(KEY_SECRET))
    }

    /** Salva endereço e chave; devolve a mensagem de erro, se houver. */
    fun save(url: String, key: String): String? {
        val clean = normalizeUrl(url) ?: return "Endereço inválido. Use o endereço https do seu gateway (ex.: https://lily-gateway.seu-nome.workers.dev)."
        if (key.isNotBlank() && key.trim().length < 16) return "A chave de acesso precisa ter pelo menos 16 caracteres."
        val sealed = if (key.isBlank()) prefs.getString(KEY_SECRET, null) else runCatching { box.seal(key.trim()) }.getOrElse {
            return "Não foi possível proteger a chave neste aparelho (Keystore): ${it.message}"
        }
        if (sealed == null) return "Informe a chave de acesso do gateway."
        prefs.edit().putString(KEY_URL, clean).putString(KEY_SECRET, sealed).apply()
        _config.value = read()
        return null
    }

    fun key(): String? = prefs.getString(KEY_SECRET, null)?.let(box::open)

    fun clear() {
        prefs.edit().clear().apply()
        cacheDir.listFiles()?.forEach { it.delete() }
        _config.value = null
    }

    fun <T> cache(name: String, serializer: KSerializer<T>, value: T) {
        runCatching { File(cacheDir, name).writeText(JSON.encodeToString(serializer, value)) }
    }

    fun <T> cached(name: String, serializer: KSerializer<T>): T? =
        runCatching { File(cacheDir, name).takeIf { it.isFile }?.readText()?.let { JSON.decodeFromString(serializer, it) } }.getOrNull()

    companion object {
        private const val KEY_URL = "url"
        private const val KEY_SECRET = "key"
        val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

        /** Só https (http só para o próprio aparelho, usado nos testes). */
        fun normalizeUrl(raw: String): String? {
            val t = raw.trim().trimEnd('/')
            val uri = runCatching { URI(t) }.getOrNull() ?: return null
            val host = uri.host ?: return null
            val local = host == "127.0.0.1" || host == "localhost"
            if (uri.scheme != "https" && !(uri.scheme == "http" && local)) return null
            if (uri.rawQuery != null || uri.rawFragment != null) return null
            return t
        }
    }
}

/** Uma mensagem para um modelo online. */
data class RemoteMessage(val role: String, val content: String)

/** Pedaços de uma resposta online: o texto acumulado e, no fim, o uso informado pelo provedor. */
sealed interface RemoteChunk {
    data class Text(val total: String) : RemoteChunk
    data class Done(val model: String, val usage: RemoteUsage?) : RemoteChunk
}

/**
 * O cliente do L.I.L.Y Gateway: conversa com modelos online (em pedaços), pesquisa, vídeo e o
 * repositório autorizado do Codex. Nada aqui tem chave de provedor: só a chave de acesso ao gateway.
 */
class GatewayClient(private val store: GatewayStore, private val isOnline: () -> Boolean = { true }) {
    private val json = GatewayStore.JSON

    @Volatile private var models: List<RemoteModel>? = null
    @Volatile private var videoModels: List<VideoModel>? = null
    private val _status = MutableStateFlow<GatewayStatus?>(null)

    /** O último status lido (null = ainda não conferido). */
    val lastStatus: StateFlow<GatewayStatus?> = _status.asStateFlow()

    val configured: Boolean get() = store.config.value?.hasKey == true

    /** Há internet agora (os testes respondem sim). */
    fun online(): Boolean = isOnline()

    private fun target(): Pair<String, String> {
        val cfg = store.config.value ?: throw GatewayException(0, "Conecte o L.I.L.Y Gateway em Configurações > Serviço online.")
        val key = store.key() ?: throw GatewayException(0, "A chave do gateway não pôde ser lida neste aparelho. Informe de novo em Configurações > Serviço online.")
        if (!isOnline()) throw GatewayException(0, "Sem internet agora: os modelos online precisam de conexão.")
        return cfg.url to key
    }

    private fun open(method: String, path: String, body: String?, readTimeout: Int = 60_000): HttpURLConnection {
        val (base, key) = target()
        val conn = URL(base + path).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15_000
        conn.readTimeout = readTimeout
        conn.setRequestProperty("Authorization", "Bearer $key")
        conn.setRequestProperty("Accept", "application/json, text/event-stream")
        conn.setRequestProperty("User-Agent", "LILY-AI-Android")
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        return conn
    }

    private fun errorOf(conn: HttpURLConnection, code: Int): GatewayException {
        val text = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
        val message = runCatching { json.parseToJsonElement(text).jsonObject["error"]?.jsonPrimitive?.contentOrNull }.getOrNull()
        return GatewayException(
            code,
            message ?: when (code) {
                401 -> "O gateway recusou a chave de acesso."
                404 -> "O gateway não conhece este pedido (versão antiga?)."
                else -> "O gateway respondeu com erro $code."
            },
        )
    }

    private suspend fun <T> call(method: String, path: String, body: String?, decode: (String) -> T): T = withContext(Dispatchers.IO) {
        val conn = try {
            open(method, path, body)
        } catch (e: GatewayException) {
            throw e
        } catch (e: java.io.IOException) {
            throw GatewayException(0, "Não consegui falar com o gateway: ${e.message ?: e.javaClass.simpleName}")
        }
        try {
            val code = try {
                conn.responseCode
            } catch (e: java.io.IOException) {
                throw GatewayException(0, "Não consegui falar com o gateway: ${e.message ?: e.javaClass.simpleName}")
            }
            if (code >= 400) throw errorOf(conn, code)
            decode(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    private fun <T> decoder(s: KSerializer<T>): (String) -> T = { text -> json.decodeFromString(s, text) }

    suspend fun status(): GatewayStatus = call("GET", "/v1/status", null, decoder(GatewayStatus.serializer())).also { _status.value = it }

    /** O catálogo de modelos online (o gateway lê ao vivo no provedor). Sem internet, o último conhecido. */
    suspend fun codeModels(refresh: Boolean = false): List<RemoteModel> {
        if (!refresh) models?.let { return it }
        return try {
            call("GET", "/v1/models", null, decoder(RemoteModelList.serializer())).models.also {
                models = it
                store.cache("modelos.json", ListSerializer(RemoteModel.serializer()), it)
            }
        } catch (e: GatewayException) {
            cachedModels().ifEmpty { throw e }
        }
    }

    /** O último catálogo conhecido, sem rede (para o roteador decidir na hora). */
    fun cachedModels(): List<RemoteModel> = models ?: store.cached("modelos.json", ListSerializer(RemoteModel.serializer())).orEmpty().also { if (it.isNotEmpty()) models = it }

    /**
     * Uma resposta online em pedaços. Cancelar a coleta fecha a conexão (o gateway para de cobrar
     * quando o provedor percebe).
     */
    fun chat(model: String, messages: List<RemoteMessage>, temperature: Double = 0.2, maxTokens: Int = 4096): Flow<RemoteChunk> = flow {
        val body = buildJsonObject {
            put("model", model)
            put("temperature", temperature)
            put("maxTokens", maxTokens)
            put(
                "messages",
                buildJsonArray {
                    messages.forEach { m -> add(buildJsonObject { put("role", m.role); put("content", m.content) }) }
                },
            )
        }.toString()
        val conn = try {
            open("POST", "/v1/chat", body, readTimeout = 120_000)
        } catch (e: java.io.IOException) {
            throw GatewayException(0, "Não consegui falar com o gateway: ${e.message ?: e.javaClass.simpleName}")
        }
        val job = currentCoroutineContext()[Job]
        val handle = job?.invokeOnCompletion { runCatching { conn.disconnect() } }
        try {
            val code = conn.responseCode
            if (code >= 400) throw errorOf(conn, code)
            val used = conn.getHeaderField("x-lily-model") ?: model
            val text = StringBuilder()
            var usage: RemoteUsage? = null
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = reader.readLine() ?: break
                    val event = parseSse(line) ?: continue
                    if (event == DONE) break
                    event.error?.let { throw GatewayException(502, "O modelo online parou com erro: $it") }
                    event.usage?.let { usage = it }
                    if (!event.delta.isNullOrEmpty()) {
                        text.append(event.delta)
                        emit(RemoteChunk.Text(text.toString()))
                    }
                }
            }
            emit(RemoteChunk.Done(used, usage))
        } finally {
            handle?.dispose()
            conn.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    suspend fun search(query: String, max: Int = 6): SearchResponse =
        call("POST", "/v1/search", buildJsonObject { put("query", query); put("max", max) }.toString(), decoder(SearchResponse.serializer()))

    suspend fun videoModels(refresh: Boolean = false): List<VideoModel> {
        if (!refresh) videoModels?.let { return it }
        return try {
            call("GET", "/v1/video/models", null, decoder(VideoModelList.serializer())).models.also {
                videoModels = it
                store.cache("video.json", ListSerializer(VideoModel.serializer()), it)
            }
        } catch (e: GatewayException) {
            store.cached("video.json", ListSerializer(VideoModel.serializer())).orEmpty().ifEmpty { throw e }
        }
    }

    suspend fun videoQuote(request: JsonObject): VideoQuote = call("POST", "/v1/video/quote", request.toString(), decoder(VideoQuote.serializer()))

    suspend fun videoSubmit(request: JsonObject): VideoSubmitted = call("POST", "/v1/video", request.toString(), decoder(VideoSubmitted.serializer()))

    suspend fun videoStatus(jobId: String): VideoStatus = call("GET", "/v1/video/$jobId", null, decoder(VideoStatus.serializer()))

    suspend fun videoCancel(jobId: String): Boolean = call("DELETE", "/v1/video/$jobId", null) { text ->
        runCatching { json.parseToJsonElement(text).jsonObject["cancelled"]?.jsonPrimitive?.contentOrNull == "true" }.getOrDefault(false)
    }

    suspend fun repos(): RepoList = call("GET", "/v1/repos", null, decoder(RepoList.serializer()))

    suspend fun tree(repo: String, ref: String): RepoTree = call("GET", "/v1/repo/tree?repo=${enc(repo)}&ref=${enc(ref)}", null, decoder(RepoTree.serializer()))

    suspend fun file(repo: String, ref: String, path: String): RepoFile =
        call("GET", "/v1/repo/file?repo=${enc(repo)}&ref=${enc(ref)}&path=${enc(path)}", null, decoder(RepoFile.serializer()))

    suspend fun commit(repo: String, base: String, branch: String, message: String, files: List<RepoChange>): RepoCommit {
        val body = buildJsonObject {
            put("repo", repo)
            put("base", base)
            put("branch", branch)
            put("message", message)
            put("files", json.encodeToJsonElement(ListSerializer(RepoChange.serializer()), files))
        }
        return call("POST", "/v1/repo/commit", body.toString(), decoder(RepoCommit.serializer()))
    }

    suspend fun checkStart(repo: String, branch: String, runner: String, tasks: String, workdir: String): CheckStarted {
        val body = buildJsonObject {
            put("repo", repo)
            put("branch", branch)
            put("runner", runner)
            put("tasks", tasks)
            put("workdir", workdir)
        }
        return call("POST", "/v1/repo/check", body.toString(), decoder(CheckStarted.serializer()))
    }

    suspend fun checkStatus(repo: String, branch: String, since: String): CheckStatus =
        call("GET", "/v1/repo/check?repo=${enc(repo)}&branch=${enc(branch)}&since=${enc(since)}", null, decoder(CheckStatus.serializer()))

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    /** Uma linha do stream (formato OpenAI/OpenRouter, repassado pelo gateway). */
    internal data class SseEvent(val delta: String?, val usage: RemoteUsage?, val error: String?)

    companion object {
        internal val DONE = SseEvent(null, null, null)

        internal fun parseSse(line: String): SseEvent? {
            val t = line.trim()
            if (!t.startsWith("data:")) return null
            val payload = t.removePrefix("data:").trim()
            if (payload == "[DONE]") return DONE
            val obj = runCatching { GatewayStore.JSON.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return null
            val error = obj["error"]?.let { e ->
                runCatching { e.jsonObject["message"]?.jsonPrimitive?.contentOrNull }.getOrNull() ?: runCatching { e.jsonPrimitive.contentOrNull }.getOrNull() ?: e.toString()
            }
            val delta = runCatching {
                obj["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("delta")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
            }.getOrNull()
            val usage = runCatching {
                obj["usage"]?.jsonObject?.let { u ->
                    RemoteUsage(
                        u["prompt_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
                        u["completion_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
                        u["cost"]?.jsonPrimitive?.doubleOrNull,
                    )
                }
            }.getOrNull()
            return SseEvent(delta, usage, error)
        }
    }
}
