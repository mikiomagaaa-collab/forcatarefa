package app.lily.keyboard.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import app.lily.keyboard.brief.BriefCandidate
import app.lily.keyboard.brief.BriefPhase
import app.lily.keyboard.brief.BriefUi
import app.lily.keyboard.core.KeyboardTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Ações do Info Brief na barra do teclado. */
@Stable
class BriefActions(
    val onLaunch: (Long, Offset?) -> Unit = { _, _ -> },
    val onAccept: (Long) -> Unit = {},
    val onDismiss: (Long) -> Unit = {},
)

private val BriefBlue = Color(0xFF0A84FF)
private val BriefCyan = Color(0xFF64D2FF)

/** "Show · Hoje 19:00?" para o chip. */
internal fun briefSummary(c: BriefCandidate, today: LocalDate = LocalDate.now()): String {
    val at = c.at ?: return c.title
    val pt = Locale.forLanguageTag("pt-BR")
    val day = when (at.toLocalDate()) {
        today -> "Hoje"
        today.plusDays(1) -> "Amanhã"
        else -> DateTimeFormatter.ofPattern("EEE d/M", pt).format(at).replaceFirstChar { it.uppercase() }
    }
    val time = if (c.timeConfirmed || c.kind.wire == "event") " " + DateTimeFormatter.ofPattern("HH:mm", pt).format(at) else ""
    return "${c.title} · $day$time" + if (c.dateConfirmed) "" else "?"
}

/**
 * O Info Brief na barra de sugestões. Na fase de brilho, a expressão reconhecida aparece num chip
 * com um brilho azul que a percorre; depois o chip se fecha numa esfera, que sai do teclado rumo à
 * Liquid Island (o Cristal continua o voo do mesmo ponto). Sem a ilha, vira um chip com a ação.
 */
@Composable
fun BriefOverlay(ui: BriefUi, theme: KeyboardTheme, actions: BriefActions, modifier: Modifier = Modifier) {
    val cover = theme.background.asColor().copy(alpha = 0.94f * theme.backgroundAlpha.coerceIn(0.4f, 1f))
    Box(modifier.background(cover), contentAlignment = Alignment.CenterStart) {
        when (ui.phase) {
            BriefPhase.GLOW -> BriefGlow(ui, theme, actions)
            BriefPhase.LAUNCHED -> Unit
            BriefPhase.CHIP -> BriefChip(ui, theme, actions)
        }
    }
}

@Composable
private fun BriefGlow(ui: BriefUi, theme: KeyboardTheme, actions: BriefActions) {
    val appear = remember(ui.id) { Animatable(0f) }
    val sweep = remember(ui.id) { Animatable(0f) }
    val morph = remember(ui.id) { Animatable(0f) }
    val fade = remember(ui.id) { Animatable(1f) }
    val launch by rememberUpdatedState(actions.onLaunch)
    var coords by remember(ui.id) { androidx.compose.runtime.mutableStateOf<LayoutCoordinates?>(null) }
    var fullWidth by remember(ui.id) { mutableIntStateOf(0) }
    LaunchedEffect(ui.id) {
        appear.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
        sweep.animateTo(1f, tween(650, easing = LinearEasing))
        if (!ui.fly) {
            launch(ui.id, null)
            return@LaunchedEffect
        }
        morph.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
        // A esfera passa para o Cristal exatamente onde está.
        val origin = coords?.let { c -> c.positionOnScreen() + Offset(c.size.width / 2f, c.size.height / 2f) }
        launch(ui.id, origin)
        fade.animateTo(0f, tween(140))
    }
    val density = LocalDensity.current
    val m = morph.value
    val sphere = 22.dp
    val chipHeight = 30.dp
    val width = if (fullWidth > 0 && m > 0f) lerp(with(density) { fullWidth.toDp() }, sphere, m) else null
    Row(
        Modifier
            .padding(start = 10.dp)
            .graphicsLayer {
                alpha = appear.value * fade.value
                val s = 0.92f + 0.08f * appear.value
                scaleX = s
                scaleY = s
            }
            .then(if (width != null) Modifier.width(width) else Modifier.widthIn(max = 280.dp))
            .height(lerp(chipHeight, sphere, m))
            .onSizeChanged { if (m == 0f) fullWidth = it.width }
            .onGloballyPositioned { coords = it }
            .clip(RoundedCornerShape(50))
            .drawBehind {
                // Fundo azul suave; o brilho corre da esquerda para a direita; ao fechar, a esfera.
                drawRect(BriefBlue.copy(alpha = 0.18f + 0.82f * m))
                val x = size.width * (sweep.value * 1.4f - 0.2f)
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, BriefCyan.copy(alpha = 0.55f * (1f - m)), Color.Transparent),
                        startX = x - size.width * 0.35f,
                        endX = x + size.width * 0.35f,
                    ),
                )
                if (m > 0f) {
                    drawCircle(
                        Brush.radialGradient(listOf(Color.White.copy(alpha = m), BriefCyan.copy(alpha = m), BriefBlue.copy(alpha = m)), center = Offset(size.width * 0.38f, size.height * 0.35f), radius = size.height),
                        radius = size.minDimension / 2f,
                        center = center,
                    )
                }
            }
            .semantics { contentDescription = "Info Brief: ${ui.candidate.expression}" }
            .padding(horizontal = lerp(10.dp, 0.dp, m)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (m < 0.6f) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = BriefCyan.copy(alpha = 1f - m / 0.6f),
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                ui.candidate.expression,
                color = theme.textColor.asColor().copy(alpha = (1f - m / 0.6f).coerceIn(0f, 1f)),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BriefChip(ui: BriefUi, theme: KeyboardTheme, actions: BriefActions) {
    val text = theme.textColor.asColor()
    val appear = remember(ui.id) { Animatable(0f) }
    LaunchedEffect(ui.id) { appear.animateTo(1f, tween(220, easing = FastOutSlowInEasing)) }
    Row(
        Modifier
            .fillMaxHeight()
            .padding(horizontal = 8.dp)
            .graphicsLayer { alpha = appear.value },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(BriefBlue), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Event, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
        }
        Text(
            briefSummary(ui.candidate),
            color = text,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp).weight(1f),
        )
        Text(
            if (ui.candidate.at == null) "Adicionar" else "Criar lembrete",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier
                .padding(start = 6.dp)
                .clip(CircleShape)
                .background(BriefBlue)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { actions.onAccept(ui.id) }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        Box(
            Modifier
                .padding(start = 4.dp)
                .size(32.dp)
                .clip(CircleShape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { actions.onDismiss(ui.id) }
                .semantics { contentDescription = "Agora não" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Close, contentDescription = null, tint = text.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
        }
    }
}
