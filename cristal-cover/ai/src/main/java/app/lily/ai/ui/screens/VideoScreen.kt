package app.lily.ai.ui.screens

import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.util.Size
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lily.ai.data.MediaSaver
import app.lily.ai.remote.GatewayException
import app.lily.ai.remote.VideoModel
import app.lily.ai.ui.LocalGraph
import app.lily.ai.ui.Nav
import app.lily.ai.ui.Route
import app.lily.ai.ui.theme.ButtonKind
import app.lily.ai.ui.theme.Chip
import app.lily.ai.ui.theme.EmptyState
import app.lily.ai.ui.theme.GlassCard
import app.lily.ai.ui.theme.Lily
import app.lily.ai.ui.theme.PillButton
import app.lily.ai.ui.theme.Progress
import app.lily.ai.ui.theme.SectionHeader
import app.lily.ai.ui.theme.TopBar
import app.lily.ai.ui.theme.Toggle
import app.lily.ai.ui.theme.Type
import app.lily.ai.ui.theme.glass
import app.lily.ai.video.Motion
import app.lily.ai.video.VideoJob
import app.lily.ai.video.VideoMode
import app.lily.ai.video.VideoQuality
import app.lily.ai.video.VideoRequest
import app.lily.ai.video.VideoRouter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Video AI: descreva (e, se quiser, dê uma imagem), escolha formato e duração, veja quanto custa e gere.
 * O modelo roda no serviço online; o celular manda o pedido, acompanha, mostra e guarda.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoScreen(nav: Nav, start: Route.Video) {
    val graph = LocalGraph.current
    val c = Lily.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by graph.settings.value.collectAsStateWithLifecycle()
    val config by graph.gatewayStore.config.collectAsStateWithLifecycle()
    val jobs by graph.videos.jobs.collectAsStateWithLifecycle()

    var models by remember { mutableStateOf<List<VideoModel>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var prompt by remember { mutableStateOf(start.prompt) }
    var mode by remember { mutableStateOf(VideoMode.TEXT) }
    var reference by remember { mutableStateOf<Bitmap?>(null) }
    var parent by remember { mutableStateOf<String?>(null) }
    var quality by remember { mutableStateOf(VideoQuality.BALANCED) }
    var manual by remember { mutableStateOf<String?>(null) }
    var aspect by remember { mutableStateOf("16:9") }
    var duration by remember { mutableStateOf(0) }
    var resolution by remember { mutableStateOf("") }
    var audio by remember { mutableStateOf(false) }
    var motion by remember { mutableStateOf(Motion.NATURAL) }
    var estimate by remember { mutableStateOf<Double?>(null) }
    var sending by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf<String?>(null) }

    val ready = settings.onlineConsent && config?.hasKey == true
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        try {
            models = graph.gateway.videoModels()
            loadError = if (models.isEmpty()) "O seu gateway ainda não tem a geração de vídeo (falta FAL_KEY)." else null
        } catch (e: GatewayException) {
            loadError = e.message
        }
        if (graph.videos.jobs.value.any { it.active }) app.lily.ai.video.VideoService.start(context)
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            reference = withContext(Dispatchers.IO) { runCatching { MediaSaver.load(context, uri, 1600) }.getOrNull() }
            mode = VideoMode.IMAGE
            parent = null
        }
    }

    val needsImage = mode != VideoMode.TEXT
    val auto = VideoRouter.pick(models, needsImage, audio, quality)
    val model = manual?.let { id -> models.firstOrNull { it.id == id && (if (needsImage) it.fromImage else it.fromText) } } ?: auto?.model
    // Ajusta formato, duração e resolução ao que o modelo escolhido aceita.
    LaunchedEffect(model?.id) {
        model ?: return@LaunchedEffect
        if (aspect !in model.aspects) aspect = model.aspects.first()
        if (duration !in model.durations) duration = model.durations.first()
        if (resolution !in model.resolutions) resolution = model.resolutions.first()
        if (!model.audio) audio = false
    }
    // A estimativa vem do gateway (mesma conta do limite de gasto), um instante depois de mudar algo.
    LaunchedEffect(model?.id, duration, resolution, audio, needsImage) {
        estimate = null
        val m = model ?: return@LaunchedEffect
        delay(350)
        estimate = runCatching {
            graph.videos.quote(VideoRequest(m, "x", mode, duration, resolution, aspect, audio, motion, reference = if (needsImage) Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888) else null))
        }.getOrNull()
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Vídeo", onBack = { nav.back() }, subtitle = "Criação por IA · online")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            if (!ready) {
                EmptyState(
                    Icons.Rounded.Cloud,
                    "Conecte o serviço online",
                    "Os modelos de vídeo são grandes demais para o celular: eles rodam no seu L.I.L.Y Gateway (fal.ai). O celular envia o pedido, acompanha e guarda o resultado.",
                    action = "Configurar",
                    onAction = { nav.go(Route.Online) },
                )
                return@Column
            }
            loadError?.let {
                GlassCard(Modifier.fillMaxWidth()) { Text(it, style = Type.footnote, color = c.warning) }
                Spacer(Modifier.height(10.dp))
            }

            GlassCard(Modifier.fillMaxWidth(), strong = true) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Descrição", mode == VideoMode.TEXT, {
                        mode = VideoMode.TEXT
                        reference = null
                        parent = null
                    })
                    Chip("Animar imagem", mode == VideoMode.IMAGE, { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, icon = Icons.Rounded.AddPhotoAlternate)
                }
                reference?.let { bmp ->
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(bmp.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(mode.label, style = Type.callout, color = c.content)
                            Text("A imagem vai junto ao serviço online.", style = Type.caption, color = c.tertiary)
                        }
                        PillButton("Tirar", {
                            reference = null
                            parent = null
                            mode = VideoMode.TEXT
                        }, kind = ButtonKind.GLASS, height = 36.dp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                GlassField(
                    if (mode == VideoMode.TEXT) "O que deve acontecer no vídeo" else "Como a cena deve se mover",
                    prompt,
                    { prompt = it },
                    placeholder = if (mode == VideoMode.TEXT) "Ex.: uma onda de vidro líquido se formando ao pôr do sol, câmera lenta" else "Ex.: a câmera se aproxima devagar e as folhas balançam",
                    minLines = 3,
                )
            }

            SectionHeader("Ajustes")
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Qualidade", style = Type.callout, color = c.content)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoQuality.entries.forEach { q -> Chip(q.label, manual == null && quality == q, { quality = q; manual = null }) }
                }
                Text(auto?.reason ?: if (models.isEmpty()) "Carregando os modelos…" else "Nenhum modelo faz este pedido.", style = Type.caption, color = c.tertiary, modifier = Modifier.padding(top = 6.dp))
                model?.let { m ->
                    Spacer(Modifier.height(12.dp))
                    if (m.aspects.size > 1) {
                        Text("Formato", style = Type.callout, color = c.content)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { m.aspects.forEach { a -> Chip(aspectLabel(a), aspect == a, { aspect = a }) } }
                        Spacer(Modifier.height(10.dp))
                    }
                    if (m.durations.size > 1) {
                        Text("Duração", style = Type.callout, color = c.content)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { m.durations.forEach { d -> Chip("$d s", duration == d, { duration = d }) } }
                        Spacer(Modifier.height(10.dp))
                    }
                    if (m.resolutions.size > 1) {
                        Text("Resolução", style = Type.callout, color = c.content)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { m.resolutions.forEach { r -> Chip(r, resolution == r, { resolution = r }) } }
                        Spacer(Modifier.height(10.dp))
                    }
                    Text("Movimento", style = Type.callout, color = c.content)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Motion.entries.forEach { mo -> Chip(mo.label, motion == mo, { motion = mo }) } }
                    if (models.any { it.audio }) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Com som gerado", style = Type.callout, color = c.content, modifier = Modifier.weight(1f))
                            Toggle(audio, { audio = it })
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Escolher o modelo", style = Type.caption, color = c.secondary)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    models.filter { if (needsImage) it.fromImage else it.fromText }.forEach { m ->
                        Chip(m.name, manual == m.id, { manual = if (manual == m.id) null else m.id })
                    }
                }
                model?.let { Text("${it.family} · ${it.summary}", style = Type.caption, color = c.tertiary, modifier = Modifier.padding(top = 6.dp)) }
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().glass(c, RoundedCornerShape(18.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Cloud, null, tint = c.accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Processamento online (fal.ai)", style = Type.callout, color = c.content)
                    Text(
                        estimate?.let { "Estimativa: ${usd(it)} · cobrado na conta do serviço" } ?: "Calculando a estimativa…",
                        style = Type.caption,
                        color = c.secondary,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            PillButton(
                if (sending) "Enviando…" else "Gerar vídeo",
                {
                    val m = model ?: return@PillButton
                    sending = true
                    notice = null
                    scope.launch {
                        try {
                            graph.videos.submit(VideoRequest(m, prompt, mode, duration, resolution, aspect, audio, motion, reference, parent))
                            notice = "Enviado. Você pode sair: a Liquid Island mostra o andamento."
                        } catch (e: GatewayException) {
                            notice = e.message
                        } catch (e: Exception) {
                            notice = "Não consegui enviar: ${e.message}"
                        } finally {
                            sending = false
                        }
                    }
                },
                Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Movie,
                enabled = !sending && model != null && prompt.isNotBlank() && (!needsImage || reference != null),
            )
            notice?.let { Text(it, style = Type.footnote, color = c.secondary, modifier = Modifier.padding(top = 8.dp)) }

            if (jobs.isNotEmpty()) {
                SectionHeader("Seus vídeos")
                jobs.forEach { job ->
                    VideoJobCard(
                        job = job,
                        playing = playing == job.id,
                        onPlay = { playing = if (playing == job.id) null else job.id },
                        onSave = { scope.launch { notice = if (graph.videos.saveToGallery(job.id) != null) "Salvo em Filmes/L.I.L.Y (aparece na L.I.L.Y Gallery)." else "Não consegui salvar." } },
                        onShare = {
                            scope.launch {
                                graph.videos.saveToGallery(job.id)
                                graph.videos.shareIntent(job.id)?.let { context.startActivity(android.content.Intent.createChooser(it, null)) }
                            }
                        },
                        onContinue = {
                            scope.launch {
                                reference = graph.videos.frame(job.id, last = true)
                                parent = job.id
                                mode = VideoMode.CONTINUE
                                prompt = job.prompt
                            }
                        },
                        onTransform = {
                            scope.launch {
                                reference = graph.videos.frame(job.id, last = false)
                                parent = job.id
                                mode = VideoMode.TRANSFORM
                                prompt = ""
                            }
                        },
                        onCancel = { scope.launch { graph.videos.cancel(job.id) } },
                        onDelete = { graph.videos.delete(job.id) },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun aspectLabel(a: String): String = when (a) {
    "16:9" -> "Paisagem 16:9"
    "9:16" -> "Retrato 9:16"
    "1:1" -> "Quadrado 1:1"
    else -> a
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VideoJobCard(
    job: VideoJob,
    playing: Boolean,
    onPlay: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onContinue: () -> Unit,
    onTransform: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = Lily.colors
    val thumb by produceState<Bitmap?>(null, job.file, job.reference) {
        value = withContext(Dispatchers.IO) {
            job.file?.let { f -> runCatching { ThumbnailUtils.createVideoThumbnail(File(f), Size(480, 270), null) }.getOrNull() }
                ?: job.reference?.let { android.graphics.BitmapFactory.decodeFile(it) }
        }
    }
    GlassCard(Modifier.fillMaxWidth()) {
        if (playing && job.file != null) {
            val ratio = job.aspect.split(":").mapNotNull { it.toFloatOrNull() }.takeIf { it.size == 2 }?.let { it[0] / it[1] } ?: (16f / 9f)
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoPath(job.file)
                        setOnPreparedListener { mp -> mp.isLooping = true; start() }
                        setOnClickListener { if (isPlaying) pause() else start() }
                    }
                },
                modifier = Modifier.fillMaxWidth().aspectRatio(ratio.coerceIn(0.5f, 2f)).clip(RoundedCornerShape(16.dp)),
            )
            Spacer(Modifier.height(10.dp))
        }
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(84.dp, 56.dp).clip(RoundedCornerShape(12.dp)).background(c.glassStrong), contentAlignment = Alignment.Center) {
                thumb?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    ?: Icon(Icons.Rounded.Movie, null, tint = c.tertiary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(job.prompt, style = Type.callout, color = c.content, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${job.modelName} · ${job.duration} s · ${job.resolution} · ${job.aspect} · ~${usd(job.estimatedUsd)}", style = Type.caption, color = c.secondary)
                val status = when (job.status) {
                    "queued" -> job.position?.let { "Na fila (posição ${it + 1})" } ?: "Na fila"
                    "running" -> "Gerando · cerca de ${(job.progress * 100).toInt()}% (estimado)"
                    "downloading" -> "Baixando"
                    "done" -> if (job.galleryUri != null) "Pronto · salvo na galeria" else "Pronto"
                    "cancelled" -> "Cancelado"
                    else -> job.error ?: "Não foi gerado"
                }
                Text(status, style = Type.caption, color = when (job.status) { "failed" -> c.danger; "done" -> c.success; else -> c.tertiary })
                job.note?.let { Text(it, style = Type.caption, color = c.warning) }
            }
        }
        if (job.active) {
            Spacer(Modifier.height(8.dp))
            Progress(job.progress, indeterminate = job.status == "queued")
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (job.status == "done") {
                Chip(if (playing) "Fechar" else "Assistir", false, onPlay)
                if (job.galleryUri == null) Chip("Salvar na Gallery", false, onSave)
                Chip("Compartilhar", false, onShare)
                Chip("Continuar", false, onContinue)
                Chip("Transformar", false, onTransform)
            }
            if (job.status == "queued") Chip("Cancelar", false, onCancel)
            if (!job.active) Chip("Apagar", false, onDelete)
        }
    }
}
