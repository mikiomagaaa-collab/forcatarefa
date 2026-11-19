package app.lily.ai

import android.content.Context
import app.lily.ai.remote.GatewayClient
import app.lily.ai.remote.GatewayStore
import app.lily.ai.remote.SecretBox
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.util.concurrent.ConcurrentLinkedDeque
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Guarda a chave sem Keystore (o Robolectric não tem o Android Keystore). Só nos testes. */
object PlainBox : SecretBox {
    override fun seal(plain: String): String = "plain:$plain"
    override fun open(sealed: String): String? = sealed.removePrefix("plain:")
}

/**
 * Um L.I.L.Y Gateway de mentira, rodando de verdade em 127.0.0.1, com o MESMO protocolo do gateway
 * (cristal-cover/gateway). Os testes conferem o caminho inteiro do app (HTTP, stream, filas, CI),
 * sem chamar nenhum provedor real.
 */
class FakeGateway : AutoCloseable {
    val key = "chave-de-teste-1234567890"
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val url: String get() = "http://127.0.0.1:${server.address.port}"
    private val json = Json { ignoreUnknownKeys = true }

    /** Respostas do modelo, em ordem (o agente do Codex recebe uma por passo). */
    val replies = ConcurrentLinkedDeque<String>()
    val chatRequests = ConcurrentLinkedDeque<JsonObject>()
    val videoRequests = ConcurrentLinkedDeque<JsonObject>()
    val searches = ConcurrentLinkedDeque<String>()

    /** O repositório: arquivos do ramo principal e do ramo do Codex. */
    val base = linkedMapOf<String, String>()
    val branch = linkedMapOf<String, String>()
    var branchName: String? = null
    val commits = ConcurrentLinkedDeque<JsonObject>()
    val checks = ConcurrentLinkedDeque<JsonObject>()

    /** Resultados do CI, em ordem: (passou, log). */
    val ciResults = ConcurrentLinkedDeque<Pair<Boolean, String>>()
    /** Quando não é nulo, o pedido de testes é recusado com esta mensagem (como o gateway real faz com 409). */
    @Volatile var checkRefusal: String? = null
    private var lastCi: Pair<Boolean, String>? = null

    private var videoPolls = 0
    /** Inclui a conversa grátis (como um gateway 1.2). */
    @Volatile var freeChat = false

    /** Inclui o modelo de vídeo grátis (como um gateway 1.1). */
    @Volatile var freeVideo = false
    val imagePrompts = ConcurrentLinkedDeque<String>()

    val models = """
        {"provider":"openrouter","models":[
          {"id":"anthropic/claude-opus-4.7","name":"Anthropic: Claude Opus 4.7","vendor":"Anthropic","tier":"frontier","speed":"slow","roles":["architecture","debug","review","security","refactor"],"context":200000,"priceIn":5,"priceOut":25},
          {"id":"anthropic/claude-sonnet-4.6","name":"Anthropic: Claude Sonnet 4.6","vendor":"Anthropic","tier":"strong","speed":"medium","roles":["generate","refactor","debug","review","security","chat"],"context":1000000,"priceIn":3,"priceOut":15},
          {"id":"deepseek/deepseek-v4-pro","name":"DeepSeek: V4 Pro","vendor":"DeepSeek","tier":"strong","speed":"medium","roles":["generate","quick","refactor","debug","chat"],"context":160000,"priceIn":0.46,"priceOut":0.92},
          {"id":"google/gemini-3.1-pro-preview","name":"Google: Gemini 3.1 Pro","vendor":"Google","tier":"frontier","speed":"medium","roles":["architecture","review","refactor","chat"],"context":1000000,"priceIn":2,"priceOut":12},
          {"id":"google/gemini-3-flash-preview","name":"Google: Gemini 3 Flash","vendor":"Google","tier":"fast","speed":"fast","roles":["quick","docs","explain","chat"],"context":1000000,"priceIn":0.3,"priceOut":2.5}
        ]}
    """.trimIndent()

    init {
        server.createContext("/") { ex ->
            try {
                handle(ex)
            } catch (e: Exception) {
                send(ex, 500, """{"error":"${e.message}"}""")
            }
        }
        server.start()
    }

    fun connect(context: Context): Pair<GatewayStore, GatewayClient> {
        val store = GatewayStore(context, PlainBox)
        require(store.save(url, key) == null)
        return store to GatewayClient(store) { true }
    }

    private fun send(ex: HttpExchange, code: Int, body: String, type: String = "application/json") {
        val bytes = body.toByteArray()
        ex.responseHeaders.add("content-type", type)
        ex.sendResponseHeaders(code, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    private fun query(ex: HttpExchange): Map<String, String> = ex.requestURI.rawQuery.orEmpty().split('&').filter { '=' in it }
        .associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8") }

    private fun handle(ex: HttpExchange) {
        val path = ex.requestURI.path
        val method = ex.requestMethod
        if (path.startsWith("/files/")) {
            val bytes = ByteArray(4096) { (it % 251).toByte() }
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
            return
        }
        if (ex.requestHeaders.getFirst("Authorization") != "Bearer $key") return send(ex, 401, """{"error":"Chave de acesso inválida."}""")
        val body = if (method == "POST") ex.requestBody.readBytes().decodeToString() else ""
        val obj = if (body.isNotBlank()) json.parseToJsonElement(body).jsonObject else JsonObject(emptyMap())
        when {
            path == "/v1/status" -> send(ex, 200, """{"ok":true,"version":"1.0.0","features":{"code":true,"video":true,"search":true,"repos":true},"codeModels":5,"searchProvider":"tavily","repos":["dono/app"],"budget":{"dailyUsd":3,"spentTodayUsd":0.12,"enforced":true}}""")
            path == "/v1/models" -> send(ex, 200, if (freeChat) models.replaceFirst("\"models\":[", "\"models\":[$FREE_CHAT_MODEL,") else models)
            path == "/v1/chat" -> {
                chatRequests += obj
                val text = replies.pollFirst() ?: """{"action":"finish","summary":"Nada a fazer.","changes":"","verified":[],"hypotheses":[],"risks":[],"sources":[]}"""
                val half = text.length / 2
                val chunk = { s: String -> "data: " + buildJsonObject { put("choices", buildJsonArray { add(buildJsonObject { put("delta", buildJsonObject { put("content", s) }) }) }) } + "\n\n" }
                val sse = ": OPENROUTER PROCESSING\n\n" + chunk(text.substring(0, half)) + chunk(text.substring(half)) +
                    "data: {\"choices\":[{\"delta\":{}}],\"usage\":{\"prompt_tokens\":100,\"completion_tokens\":20,\"cost\":0.0015}}\n\ndata: [DONE]\n\n"
                ex.responseHeaders.add("x-lily-model", obj["model"]?.jsonPrimitive?.content ?: "")
                send(ex, 200, sse, "text/event-stream")
            }
            path == "/v1/search" -> {
                searches += obj["query"]?.jsonPrimitive?.content.orEmpty()
                send(ex, 200, """{"provider":"tavily","query":"x","results":[{"title":"Room release notes","url":"https://developer.android.com/jetpack/androidx/releases/room","snippet":"Version 2.8.5"}]}""")
            }
            path == "/v1/video/models" -> send(
                ex,
                200,
                """{"provider":"fal","models":[${if (freeVideo) FREE_MODEL + "," else ""}
                  {"id":"ltx-2","name":"LTX-2","family":"LTX (Lightricks)","summary":"Rápido","modes":["text","image"],"durations":[6,8,10],"resolutions":["1080p"],"aspects":["16:9","9:16"],"audio":true,"quality":3,"speed":"fast","typicalSeconds":45,"fromUsd":0.36,"priceNote":"por segundo"},
                  {"id":"kling-2.5","name":"Kling 2.5 Turbo Pro","family":"Kling","summary":"Coerente","modes":["text","image"],"durations":[5,10],"resolutions":["1080p"],"aspects":["16:9","9:16","1:1"],"audio":false,"quality":4,"speed":"medium","typicalSeconds":100,"fromUsd":0.35,"priceNote":"por segundo"},
                  {"id":"veo-3.1-fast","name":"Veo 3.1 Fast","family":"Veo","summary":"Melhor","modes":["text","image"],"durations":[8],"resolutions":["720p","1080p"],"aspects":["16:9","9:16"],"audio":true,"quality":5,"speed":"medium","typicalSeconds":70,"fromUsd":0.8,"priceNote":"por segundo"}
                ]}""",
            )
            path == "/v1/video/quote" -> send(ex, 200, """{"model":"ltx-2","estimatedUsd":0.36,"duration":6,"resolution":"1080p"}""")
            path == "/v1/image" && method == "POST" -> {
                imagePrompts += obj["prompt"]?.jsonPrimitive?.content.orEmpty()
                send(ex, 200, """{"image":"data:image/png;base64,${pngBase64()}","model":"FLUX.1 [schnell]","width":1024,"height":1024,"costUsd":0,"free":true,"usedToday":3,"perDay":150}""")
            }
            path == "/v1/video" && method == "POST" && obj["model"]?.jsonPrimitive?.content == "ltx-free" -> {
                videoRequests += obj
                send(ex, 200, """{"jobId":"free-0123456789abcdef","model":"ltx-free","mode":"text","estimatedUsd":0,"typicalSeconds":90,"free":true,"stream":true,"duration":4,"resolution":"480p","aspect":"9:16"}""")
            }
            path == "/v1/video/free-0123456789abcdef" && method == "GET" -> {
                // Como o gateway: uma resposta longa em NDJSON que só termina com o vídeo pronto.
                ex.responseHeaders.add("content-type", "application/x-ndjson; charset=utf-8")
                ex.sendResponseHeaders(200, 0)
                ex.responseBody.use { out ->
                    for (line in listOf(
                        """{"status":"running","elapsed":1,"typicalSeconds":90,"free":true}""",
                        """{"status":"running","elapsed":30,"typicalSeconds":90,"free":true}""",
                        """{"status":"done","elapsed":61,"free":true,"video":{"url":"$url/files/out.mp4","contentType":"video/mp4","fileSize":4096}}""",
                    )) {
                        out.write((line + "\n").toByteArray())
                        out.flush()
                        Thread.sleep(40)
                    }
                }
            }
            path == "/v1/video" && method == "POST" -> {
                videoRequests += obj
                videoPolls = 0
                send(ex, 200, """{"jobId":"job-0123456789abcdef","model":"${obj["model"]?.jsonPrimitive?.content}","mode":"${if (obj["image"] != null) "image" else "text"}","estimatedUsd":0.36,"typicalSeconds":45,"simplified":false,"duration":6,"resolution":"1080p","aspect":"16:9"}""")
            }
            path.startsWith("/v1/video/") && method == "GET" -> {
                videoPolls++
                send(
                    ex,
                    200,
                    when (videoPolls) {
                        1 -> """{"status":"queued","position":1,"elapsed":2,"typicalSeconds":45}"""
                        2 -> """{"status":"running","elapsed":20,"typicalSeconds":45}"""
                        else -> """{"status":"done","elapsed":44,"video":{"url":"$url/files/out.mp4","contentType":"video/mp4","fileSize":4096}}"""
                    },
                )
            }
            path.startsWith("/v1/video/") && method == "DELETE" -> send(ex, 200, """{"cancelled":true}""")
            path == "/v1/repos" -> send(ex, 200, """{"repos":[{"repo":"dono/app","available":true,"defaultBranch":"main","private":true}],"workflow":"lily-codex-check.yml"}""")
            path == "/v1/repo/tree" -> {
                val ref = query(ex)["ref"]
                val files = if (ref == branchName && branchName != null) branch else base
                if (ref != "main" && ref != branchName) return send(ex, 404, """{"error":"GitHub não encontrou o modelo ou o recurso pedido."}""")
                send(ex, 200, buildJsonObject {
                    put("repo", "dono/app")
                    put("ref", ref)
                    put("files", buildJsonArray { files.forEach { (p, c) -> add(buildJsonObject { put("path", p); put("size", c.length) }) } })
                }.toString())
            }
            path == "/v1/repo/file" -> {
                val q = query(ex)
                val files = if (q["ref"] == branchName && branchName != null) branch else base
                val content = files[q["path"]]
                send(ex, 200, buildJsonObject {
                    put("path", q["path"])
                    put("exists", content != null)
                    if (content != null) put("content", content)
                }.toString())
            }
            path == "/v1/repo/commit" -> {
                commits += obj
                val name = obj["branch"]!!.jsonPrimitive.content
                require(name.startsWith("lily-codex/")) { "ramo inválido" }
                if (branchName != name) {
                    branchName = name
                    branch.clear()
                    branch.putAll(base)
                }
                for (f in obj["files"]!!.jsonArray) {
                    val o = f.jsonObject
                    val p = o["path"]!!.jsonPrimitive.content
                    val c = o["content"]
                    if (c == null || c is JsonNull) branch.remove(p) else branch[p] = c.jsonPrimitive.content
                }
                send(ex, 200, """{"repo":"dono/app","branch":"$name","commit":"c${commits.size}abcdef","parent":"p","created":${commits.size == 1},"url":"https://github.com/dono/app/tree/$name"}""")
            }
            path == "/v1/repo/check" && method == "POST" && checkRefusal != null -> {
                send(ex, 409, buildJsonObject { put("error", checkRefusal) }.toString())
            }
            path == "/v1/repo/check" && method == "POST" -> {
                checks += obj
                lastCi = ciResults.pollFirst() ?: (true to "BUILD SUCCESSFUL")
                send(ex, 200, """{"repo":"dono/app","branch":"${obj["branch"]?.jsonPrimitive?.content}","workflow":"lily-codex-check.yml","runner":"gradle","startedAt":"2026-11-19T10:00:00Z"}""")
            }
            path == "/v1/repo/check" -> {
                val r = lastCi ?: return send(ex, 200, """{"status":"waiting"}""")
                send(ex, 200, buildJsonObject {
                    put("status", "completed")
                    put("conclusion", if (r.first) "success" else "failure")
                    put("url", "https://github.com/dono/app/actions/runs/1")
                    put("failures", if (r.first) JsonArray(emptyList()) else buildJsonArray { add(buildJsonObject { put("job", "Testes"); put("log", r.second) }) })
                }.toString())
            }
            else -> send(ex, 404, """{"error":"Rota desconhecida."}""")
        }
    }

    override fun close() {
        server.stop(0)
    }

    companion object {
        const val FREE_CHAT_MODEL = """{"id":"free/gpt-oss-20b","name":"L.I.L.Y Online (grátis, beta)","vendor":"OpenAI gpt-oss-20b · Workers AI","family":"gpt-oss","tier":"fast","speed":"medium","roles":["chat","quick","explain","docs"],"context":128000,"priceIn":0,"priceOut":0,"created":0,"free":true}"""

        const val FREE_MODEL = """{"id":"ltx-free","name":"LTX-Video (grátis)","family":"LTX (Lightricks)","summary":"Grátis","modes":["text","image"],"durations":[2,4,6,8],"resolutions":["480p"],"aspects":["16:9","9:16","1:1"],"audio":false,"quality":2,"speed":"medium","typicalSeconds":90,"fromUsd":0,"priceNote":"grátis","free":true,"provider":"hf"}"""

        /** Uma imagem PNG de verdade (8×8, azul) para a imagem online. */
        fun pngBase64(): String {
            val img = java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB)
            for (x in 0 until 8) for (y in 0 until 8) img.setRGB(x, y, 0x3366FF)
            val out = java.io.ByteArrayOutputStream()
            javax.imageio.ImageIO.write(img, "png", out)
            return java.util.Base64.getEncoder().encodeToString(out.toByteArray())
        }

        /** Uma ação do agente em JSON (como o modelo responderia). */
        fun action(name: String, vararg pairs: Pair<String, Any>): String = buildJsonObject {
            put("action", name)
            for ((k, v) in pairs) when (v) {
                is String -> put(k, v)
                is Int -> put(k, v)
                is List<*> -> put(k, JsonArray(v.map { JsonPrimitive(it.toString()) }))
                else -> put(k, v.toString())
            }
        }.toString()
    }
}
