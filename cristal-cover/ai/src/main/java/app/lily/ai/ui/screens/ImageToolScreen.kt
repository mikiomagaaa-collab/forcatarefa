package app.lily.ai.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.lily.ai.AiGraph
import app.lily.ai.core.EngineChoice
import app.lily.ai.core.TaskKind
import app.lily.ai.data.MediaSaver
import app.lily.ai.engine.Attachments
import app.lily.ai.engine.Prompts
import app.lily.ai.engine.Vision
import app.lily.ai.models.Category
import app.lily.ai.tools.ImageResult
import app.lily.ai.ui.LocalGraph
import app.lily.ai.ui.Nav
import app.lily.ai.ui.Route
import app.lily.ai.ui.theme.ButtonKind
import app.lily.ai.ui.theme.Chip
import app.lily.ai.ui.theme.GlassCard
import app.lily.ai.ui.theme.IconButton
import app.lily.ai.ui.theme.Lily
import app.lily.ai.ui.theme.PillButton
import app.lily.ai.ui.theme.Progress
import app.lily.ai.ui.theme.SectionHeader
import app.lily.ai.ui.theme.TopBar
import app.lily.ai.ui.theme.Type
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** O que uma ferramenta de imagem produziu. */
sealed interface ImageOutcome {
    val engine: String
    val millis: Long

    data class Picture(val full: Bitmap, val preview: Bitmap, override val engine: String, override val millis: Long) : ImageOutcome
    data class Text(val text: String, override val engine: String, override val millis: Long, val install: String? = null) : ImageOutcome
    data class Labels(val labels: List<app.lily.ai.engine.Label>, override val engine: String, override val millis: Long) : ImageOutcome
}

/** As ferramentas de imagem que a tela oferece (a geração tem a sua própria tela). */
val ImageTasks = listOf(
    TaskKind.DESCRIBE_IMAGE, TaskKind.OCR, TaskKind.LABELS, TaskKind.ENHANCE,
    TaskKind.DENOISE, TaskKind.UPSCALE, TaskKind.REMOVE_BACKGROUND,
)

@Stable
class ImageToolState(private val graph: AiGraph, private val ctx: Context, start: Route.ImageTool) {
    var uri by mutableStateOf<Uri?>(null)
        private set
    var source by mutableStateOf<Bitmap?>(null)
        private set
    var task by mutableStateOf(start.task?.takeIf { it in ImageTasks })
    var running by mutableStateOf<TaskKind?>(null)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var outcome by mutableStateOf<ImageOutcome?>(null)
        private set
    var streaming by mutableStateOf("")
        private set
    var error by mutableStateOf<String?>(null)
    var install by mutableStateOf<String?>(null)
        private set
    var saved by mutableStateOf<Uri?>(null)
        private set
    private var job: Job? = null
    @Volatile private var cancelled = false

    init {
        start.image?.let { load(it) }
    }

    fun load(newUri: Uri) {
        job?.cancel()
        outcome = null
        saved = null
        error = null
        job = graph.scope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) { MediaSaver.load(ctx, newUri, 2048) }
                uri = newUri
                source = bitmap
                task?.let { run(it) }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                error = "Não consegui abrir esta imagem."
            }
        }
    }

    /** Usa o resultado como nova entrada (por exemplo, remover o ruído e depois ampliar). */
    fun useResult() {
        val pic = outcome as? ImageOutcome.Picture ?: return
        source = pic.full
        uri = saved
        outcome = null
        saved = null
    }

    fun run(next: TaskKind) {
        val bitmap = source ?: return
        task = next
        job?.cancel()
        cancelled = false
        outcome = null
        saved = null
        error = null
        install = null
        streaming = ""
        progress = 0f
        running = next
        val started = SystemClock.elapsedRealtime()
        job = graph.scope.launch {
            try {
                outcome = when (next) {
                    TaskKind.DESCRIBE_IMAGE -> describe(bitmap, started)
                    TaskKind.ENHANCE -> picture(graph.tools.enhancePhoto(bitmap, { progress = it }, { cancelled }), started)
                    else -> when (val r = graph.tools.image(next, bitmap, { progress = it }, { cancelled })) {
                        is ImageResult.Picture -> picture(r, started)
                        is ImageResult.Text -> ImageOutcome.Text(r.text, r.engine, SystemClock.elapsedRealtime() - started)
                        is ImageResult.Labels -> ImageOutcome.Labels(r.labels, r.engine, SystemClock.elapsedRealtime() - started)
                    }
                }
            } catch (c: CancellationException) {
                // Parado por quem usa.
            } catch (t: Throwable) {
                error = if (cancelled) null else t.message ?: "A ferramenta falhou."
            } finally {
                running = null
            }
        }
    }

    private suspend fun picture(r: ImageResult.Picture, started: Long): ImageOutcome.Picture {
        val preview = withContext(Dispatchers.Default) { scaled(r.bitmap, 2048) }
        return ImageOutcome.Picture(r.bitmap, preview, r.engine, SystemClock.elapsedRealtime() - started)
    }

    /** Descrição com um modelo de visão; sem ele, o que o ML Kit reconhece (dito com honestidade). */
    private suspend fun describe(bitmap: Bitmap, started: Long): ImageOutcome {
        val plan = graph.tools.plan(TaskKind.DESCRIBE_IMAGE)
        val e = plan.engine
        if (e !is EngineChoice.Llm) {
            val labels = Vision.labels(bitmap)
            val text = Vision.ocr(bitmap)
            install = Category.VISION.name
            return ImageOutcome.Text(ChatSession.fallbackText(labels, text), "ML Kit (análise básica)", SystemClock.elapsedRealtime() - started, Category.VISION.name)
        }
        val file = withContext(Dispatchers.IO) { MediaSaver.cacheForModel(ctx, bitmap, "descrever") }
        graph.tools.text(plan, Prompts.tool(TaskKind.DESCRIBE_IMAGE, ""), attachments = Attachments(imageFile = file)).collect { streaming = it }
        return ImageOutcome.Text(streaming, engineLabel(plan).first, SystemClock.elapsedRealtime() - started)
    }

    fun stop() {
        cancelled = true
        job?.cancel()
    }

    /** Salva o resultado na galeria (Pictures/L.I.L.Y). */
    fun save() {
        val pic = outcome as? ImageOutcome.Picture ?: return
        graph.scope.launch {
            try {
                val s = withContext(Dispatchers.IO) {
                    MediaSaver.savePng(ctx, pic.full, "lily-${task?.name?.lowercase() ?: "imagem"}-${System.currentTimeMillis()}", "L.I.L.Y AI · ${task?.label} · ${pic.engine}")
                }
                saved = s.uri
            } catch (t: Throwable) {
                error = "Não consegui salvar: ${t.message}"
            }
        }
    }

    fun dispose() {
        stop()
    }

    companion object {
        fun scaled(bitmap: Bitmap, side: Int): Bitmap {
            val longest = max(bitmap.width, bitmap.height)
            if (longest <= side) return bitmap
            val s = side.toFloat() / longest
            return Bitmap.createScaledBitmap(bitmap, (bitmap.width * s).roundToInt(), (bitmap.height * s).roundToInt(), true)
        }
    }
}

/** Abre uma imagem na L.I.L.Y Gallery (ou, sem ela, no visualizador do aparelho). */
fun openInGallery(context: Context, uri: Uri) {
    val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    val gallery = Intent(view).setPackage("app.lily.gallery")
    runCatching { context.startActivity(gallery) }.recoverCatching { context.startActivity(Intent.createChooser(view, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImageToolScreen(nav: Nav, key: Int, route: Route.ImageTool) {
    val graph = LocalGraph.current
    val c = Lily.colors
    val context = LocalContext.current
    val state = remember(key) { nav.retain(key, { ImageToolState(graph, context.applicationContext, route) }, ImageToolState::dispose) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(state::load) }
    val source = state.source

    Column(Modifier.fillMaxSize()) {
        TopBar(state.task?.label ?: "Analisar imagem", onBack = { nav.back() }, subtitle = state.outcome?.let { "${it.engine} · ${seconds(it.millis)} · no aparelho" } ?: "Tudo no aparelho")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            val pic = state.outcome as? ImageOutcome.Picture
            when {
                source == null -> GlassCard(Modifier.fillMaxWidth().padding(top = 8.dp), onClick = {
                    pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, tint = c.accent, modifier = Modifier.padding(bottom = 8.dp))
                        Text("Escolher uma foto", style = Type.headline, color = c.content)
                        Text("O seletor do Android mostra só o que você escolher.", style = Type.footnote, color = c.secondary)
                    }
                }
                pic != null -> BeforeAfter(scaledForUi(source), pic.preview, Modifier.fillMaxWidth().padding(top = 8.dp))
                else -> Image(
                    source.asImageBitmap(),
                    contentDescription = "Imagem escolhida",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(20.dp)),
                )
            }
            if (source != null) {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${source.width} × ${source.height}" + (pic?.let { " → ${it.full.width} × ${it.full.height}" } ?: ""),
                        style = Type.caption,
                        color = c.tertiary,
                        modifier = Modifier.weight(1f),
                    )
                    Text("Trocar foto", style = Type.caption, color = c.accent, modifier = Modifier.clip(CircleShape).padding(4.dp).pointerInput(Unit) {
                        detectTapGestures { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    })
                }
                SectionHeader("Ferramentas")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImageTasks.forEach { t -> Chip(t.label, state.task == t, { state.run(t) }, enabled = state.running == null) }
                    Chip("Perguntar no chat", false, { state.uri?.let { nav.go(Route.Chat(image = it)) } }, icon = Icons.Rounded.Forum, enabled = state.uri != null)
                }
            }

            state.running?.let { t ->
                Spacer(Modifier.height(16.dp))
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("${t.label}…", style = Type.headline, color = c.content)
                    Spacer(Modifier.height(8.dp))
                    if (t == TaskKind.DESCRIBE_IMAGE) {
                        if (state.streaming.isEmpty()) TypingDots() else RichText(state.streaming)
                    } else {
                        Progress(state.progress, indeterminate = state.progress <= 0f)
                        Text("${(state.progress * 100).toInt()}%", style = Type.caption, color = c.secondary, modifier = Modifier.padding(top = 4.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    PillButton("Parar", state::stop, kind = ButtonKind.GLASS, height = 38.dp)
                }
            }

            state.error?.let { e ->
                Spacer(Modifier.height(14.dp))
                GlassCard(Modifier.fillMaxWidth()) { Text(e, style = Type.callout, color = c.content) }
            }

            when (val o = state.outcome) {
                is ImageOutcome.Picture -> {
                    Spacer(Modifier.height(14.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val saved = state.saved
                        if (saved == null) {
                            PillButton("Salvar na galeria", state::save, height = 42.dp)
                        } else {
                            PillButton("Abrir na Gallery", { openInGallery(context, saved) }, height = 42.dp)
                            PillButton("Compartilhar", { shareImage(context, saved) }, kind = ButtonKind.GLASS, height = 42.dp)
                        }
                        PillButton("Usar como entrada", state::useResult, kind = ButtonKind.GLASS, height = 42.dp)
                    }
                    if (state.saved != null) Text("Salva em Pictures/L.I.L.Y.", style = Type.caption, color = c.success, modifier = Modifier.padding(top = 6.dp))
                }
                is ImageOutcome.Text -> {
                    SectionHeader(if (state.task == TaskKind.OCR) "Texto encontrado" else "Resultado")
                    GlassCard(Modifier.fillMaxWidth(), strong = true) {
                        SelectionContainer { RichText(o.text) }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(Icons.Rounded.ContentCopy, "Copiar", { copyToClipboard(context, o.text) }, size = 38.dp, filled = false, tint = c.secondary)
                            IconButton(Icons.Rounded.Share, "Compartilhar", { shareText(context, o.text) }, size = 38.dp, filled = false, tint = c.secondary)
                            IconButton(Icons.Rounded.Forum, "Continuar no chat", { nav.go(Route.Chat(draft = o.text)) }, size = 38.dp, filled = false, tint = c.secondary)
                        }
                        if (state.task == TaskKind.OCR && o.text.length > 120) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PillButton("Resumir", { nav.go(Route.TextTool(TaskKind.SUMMARIZE, o.text)) }, kind = ButtonKind.GLASS, height = 36.dp)
                                PillButton("Traduzir", { nav.go(Route.TextTool(TaskKind.TRANSLATE, o.text)) }, kind = ButtonKind.GLASS, height = 36.dp)
                            }
                        }
                        o.install?.let { cat ->
                            Spacer(Modifier.height(8.dp))
                            PillButton("Abrir o Model Hub", { nav.go(Route.Hub(cat)) }, kind = ButtonKind.TINTED, height = 38.dp)
                        }
                    }
                }
                is ImageOutcome.Labels -> {
                    SectionHeader("O que aparece")
                    if (o.labels.isEmpty()) {
                        Text("Nada reconhecido com segurança.", style = Type.callout, color = c.secondary)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            o.labels.forEach { l -> Tag("${l.text} · ${(l.confidence * 100).roundToInt()}%", c.accent) }
                        }
                    }
                }
                null -> Unit
            }
            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }
}

@Composable
private fun scaledForUi(bitmap: Bitmap): Bitmap = remember(bitmap) { ImageToolState.scaled(bitmap, 2048) }

/** Antes e depois: arraste a divisória (ou toque) para comparar. Fundo xadrez mostra a transparência. */
@Composable
fun BeforeAfter(before: Bitmap, after: Bitmap, modifier: Modifier = Modifier) {
    val c = Lily.colors
    var split by remember { mutableFloatStateOf(0.5f) }
    val ratio = after.width.toFloat() / after.height.coerceAtLeast(1)
    Box(
        modifier
            .aspectRatio(ratio.coerceIn(0.4f, 2.5f))
            .clip(RoundedCornerShape(20.dp))
            .pointerInput(Unit) { detectTapGestures { split = (it.x / size.width).coerceIn(0f, 1f) } }
            .pointerInput(Unit) { detectHorizontalDragGestures { change, dx -> change.consume(); split = (split + dx / size.width).coerceIn(0f, 1f) } },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cell = 12.dp.toPx()
            var y = 0f
            var row = 0
            while (y < size.height) {
                var x = if (row % 2 == 0) 0f else cell
                while (x < size.width) {
                    drawRect(Color(0x22888888), Offset(x, y), androidx.compose.ui.geometry.Size(cell, cell))
                    x += cell * 2
                }
                y += cell
                row++
            }
        }
        Image(after.asImageBitmap(), contentDescription = "Depois", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        Image(
            before.asImageBitmap(),
            contentDescription = "Antes",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().drawWithContent { clipRect(right = size.width * split) { this@drawWithContent.drawContent() } },
        )
        Canvas(Modifier.fillMaxSize()) {
            val x = size.width * split
            drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
            drawCircle(Color.White, 13.dp.toPx(), Offset(x, size.height / 2))
            drawCircle(c.accent, 9.dp.toPx(), Offset(x, size.height / 2))
        }
        Text("Antes", style = Type.caption, color = Color.White, modifier = Modifier.align(Alignment.TopStart).padding(10.dp).background(Color(0x66000000), CircleShape).padding(horizontal = 8.dp, vertical = 3.dp))
        Text("Depois", style = Type.caption, color = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).background(Color(0x66000000), CircleShape).padding(horizontal = 8.dp, vertical = 3.dp))
    }
}
