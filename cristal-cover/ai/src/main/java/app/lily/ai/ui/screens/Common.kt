package app.lily.ai.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.lily.ai.core.DeviceSnapshot
import app.lily.ai.data.MediaSaver
import app.lily.ai.models.Compat
import app.lily.ai.ui.LocalGraph
import app.lily.ai.ui.theme.IconButton
import app.lily.ai.ui.theme.Lily
import app.lily.ai.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** A leitura do aparelho, atualizada a cada 4 s enquanto a tela está visível. */
@Composable
fun rememberDevice(): DeviceSnapshot {
    val graph = LocalGraph.current
    val state by produceState(graph.device.snapshot()) {
        while (true) {
            delay(4_000)
            value = withContext(Dispatchers.Default) { graph.device.snapshot() }
        }
    }
    return state
}

/** Miniatura de uma imagem (lida fora da thread da interface). */
@Composable
fun rememberThumb(uri: Uri?, maxSide: Int = 512): Bitmap? {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, uri) {
        value = uri?.let { u -> withContext(Dispatchers.IO) { runCatching { MediaSaver.load(context, u, maxSide) }.getOrNull() } }
    }
    return bitmap
}

fun compatColor(level: Compat, colors: app.lily.ai.ui.theme.LilyColors): Color = when (level) {
    Compat.COMPATIBLE -> colors.success
    Compat.LIMITED -> colors.warning
    Compat.EXPERIMENTAL -> colors.accent2
    Compat.INCOMPATIBLE -> colors.danger
}

fun greeting(): String {
    val h = java.time.LocalTime.now().hour
    return when (h) {
        in 5..11 -> "Bom dia"
        in 12..17 -> "Boa tarde"
        else -> "Boa noite"
    }
}

fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(android.content.ClipboardManager::class.java)
    cm?.setPrimaryClip(android.content.ClipData.newPlainText("L.I.L.Y", text))
}

/**
 * O compositor: texto, foto, voz. Com uma resposta em andamento, o botão vira "parar".
 * [listening]: nível da voz (null quando não está ouvindo).
 */
@Composable
fun Composer(
    text: String,
    onText: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Pergunte qualquer coisa",
    image: Bitmap? = null,
    audioLabel: String? = null,
    fileLabel: String? = null,
    onRemoveAttachment: () -> Unit = {},
    onAttach: (() -> Unit)? = null,
    onMic: (() -> Unit)? = null,
    listening: Float? = null,
    busy: Boolean = false,
    onStop: () -> Unit = {},
) {
    val c = Lily.colors
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.glassStrong).border(1.dp, c.border, RoundedCornerShape(28.dp))) {
        androidx.compose.foundation.layout.Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            if (image != null || audioLabel != null || fileLabel != null) {
                Row(Modifier.padding(start = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (image != null) {
                        Image(image.asImageBitmap(), contentDescription = "Imagem anexada", contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)))
                    }
                    if (audioLabel != null) {
                        Row(Modifier.clip(CircleShape).background(c.accent.copy(alpha = 0.16f)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = c.accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(audioLabel, style = Type.footnote, color = c.content)
                        }
                    }
                    if (fileLabel != null) {
                        Row(Modifier.clip(CircleShape).background(c.accent2.copy(alpha = 0.16f)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Description, contentDescription = null, tint = c.accent2, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(fileLabel, style = Type.footnote, color = c.content, maxLines = 1)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(Icons.Rounded.Close, "Remover anexo", onRemoveAttachment, size = 30.dp)
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (onAttach != null) IconButton(Icons.Rounded.AddPhotoAlternate, "Anexar foto", onAttach, size = 40.dp, filled = false)
                Box(Modifier.weight(1f).heightIn(min = 40.dp).padding(vertical = 9.dp, horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) Text(if (listening != null) "Ouvindo…" else placeholder, style = Type.body, color = c.tertiary)
                    BasicTextField(
                        value = text,
                        onValueChange = onText,
                        textStyle = Type.body.copy(color = c.content),
                        cursorBrush = SolidColor(c.accent),
                        maxLines = 6,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (onMic != null && text.isBlank() && !busy) {
                    IconButton(Icons.Rounded.Mic, if (listening != null) "Parar de ouvir" else "Falar", onMic, size = 40.dp, tint = if (listening != null) c.onAccent else c.content, filled = listening == null, modifier = if (listening != null) Modifier.background(c.accent, CircleShape) else Modifier)
                }
                when {
                    busy -> IconButton(Icons.Rounded.Stop, "Parar a resposta", onStop, size = 40.dp, tint = c.onAccent, modifier = Modifier.background(c.accent, CircleShape), filled = false)
                    text.isNotBlank() || image != null || audioLabel != null || fileLabel != null ->
                        IconButton(Icons.AutoMirrored.Rounded.Send, "Enviar", onSend, size = 40.dp, tint = c.onAccent, modifier = Modifier.background(c.accent, CircleShape), filled = false)
                }
            }
        }
    }
}

/** Escuta a voz (com a permissão pedida no toque) e devolve o texto final. */
class VoiceInputState {
    var level by mutableStateOf<Float?>(null)
    var partial by mutableStateOf("")
}

@Composable
fun rememberVoiceInput(): VoiceInputState = remember { VoiceInputState() }

/**
 * Ouve enquanto [active] (o toque já pediu a permissão do microfone). Para sozinho no fim da fala;
 * [onEnd] avisa que acabou (com ou sem texto).
 */
@Composable
fun ListenEffect(state: VoiceInputState, active: Boolean, onFinal: (String) -> Unit, onError: (String) -> Unit, onEnd: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        state.level = 0f
        state.partial = ""
        // A avatar mostra que está ouvindo só enquanto o microfone (ligado pelo toque) estiver aberto.
        try {
            app.lily.ai.engine.SpeechInput.listen(context).collect { e ->
                when (e) {
                    is app.lily.ai.engine.SpeechEvent.Level -> {
                        state.level = e.rms
                        app.lily.ai.presence.LilyPresence.listening(MIC, e.rms)
                    }
                    is app.lily.ai.engine.SpeechEvent.Partial -> state.partial = e.text
                    is app.lily.ai.engine.SpeechEvent.Final -> onFinal(e.text)
                    is app.lily.ai.engine.SpeechEvent.Failed -> onError(e.message)
                    app.lily.ai.engine.SpeechEvent.Ready -> app.lily.ai.presence.LilyPresence.listening(MIC, 0f)
                }
            }
        } finally {
            state.level = null
            app.lily.ai.presence.LilyPresence.clear(MIC)
            onEnd()
        }
    }
}

private const val MIC = "microfone"

/**
 * Conversa contínua por voz: quando a resposta de uma pergunta falada fica pronta (e a leitura em voz
 * alta termina), chama [onReady] para voltar a ouvir. Só reage a respostas novas (abrir a tela de novo
 * não liga o microfone) e só com a tela em primeiro plano.
 */
@Composable
fun FollowUpEffect(session: ChatSession, onReady: () -> Unit) {
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val seen = remember(session) { androidx.compose.runtime.mutableIntStateOf(session.followUps) }
    LaunchedEffect(session, session.followUps) {
        if (session.followUps <= seen.intValue) return@LaunchedEffect
        seen.intValue = session.followUps
        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) onReady()
    }
}

fun shareText(context: Context, text: String) {
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text)
    context.startActivity(android.content.Intent.createChooser(send, null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun shareImage(context: Context, uri: Uri) {
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("image/*").putExtra(android.content.Intent.EXTRA_STREAM, uri)
        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(android.content.Intent.createChooser(send, null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Uma imagem de um arquivo do app, reduzida para a tela. */
@Composable
fun rememberFileBitmap(path: String?, maxSide: Int = 720): Bitmap? {
    val bitmap by produceState<Bitmap?>(null, path) {
        value = path?.let { p ->
            withContext(Dispatchers.IO) {
                runCatching {
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(p, bounds)
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
                    android.graphics.BitmapFactory.decodeFile(p, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
                }.getOrNull()
            }
        }
    }
    return bitmap
}

/** Pede um texto (renomear, guardar na memória, novo projeto). */
@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    supporting: String? = null,
    singleLine: Boolean = true,
) {
    val c = Lily.colors
    var value by remember { mutableStateOf(initial) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (c.dark) Color(0xFF1B1826) else Color.White,
        title = { Text(title, style = Type.headline, color = c.content) },
        text = {
            androidx.compose.foundation.layout.Column {
                if (supporting != null) {
                    Text(supporting, style = Type.footnote, color = c.secondary)
                    Spacer(Modifier.size(10.dp))
                }
                androidx.compose.material3.OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = singleLine,
                    maxLines = if (singleLine) 1 else 6,
                    textStyle = Type.body.copy(color = c.content),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.accent,
                        unfocusedBorderColor = c.border,
                        cursorColor = c.accent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Text(
                confirm,
                style = Type.callout.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                color = if (value.isBlank()) c.tertiary else c.accent,
                modifier = Modifier.clip(CircleShape).clickableIf(value.isNotBlank()) { onConfirm(value.trim()) }.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        },
        dismissButton = {
            Text("Cancelar", style = Type.callout, color = c.secondary, modifier = Modifier.clip(CircleShape).clickableIf(true) { onDismiss() }.padding(horizontal = 14.dp, vertical = 8.dp))
        },
    )
}

private fun Modifier.clickableIf(enabled: Boolean, onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(enabled = enabled, onClick = onClick))
