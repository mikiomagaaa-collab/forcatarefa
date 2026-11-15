package app.cristal.cover.ui.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cristal.cover.ui.glass.LocalGlassSpec
import app.cristal.cover.ui.glass.rememberHaptic
import app.cristal.cover.ui.theme.ClockTextStyle
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle as JavaTextStyle
import kotlin.math.cos
import kotlin.math.sin

private val Accent = Color(0xFFFF453A)

/** Relógio de ponteiros. */
@Composable
internal fun AnalogClockContent() {
    val now = rememberMinuteTicker().value
    val ink = LocalGlassSpec.current.contentColor
    val time = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
    val hourAngle = (time.hour % 12 + time.minute / 60f) * 30f
    val minuteAngle = time.minute * 6f
    Canvas(Modifier.fillMaxSize().padding(12.dp)) {
        val radius = size.minDimension / 2f
        val c = center
        fun point(angleDeg: Float, length: Float): Offset {
            val a = Math.toRadians(angleDeg.toDouble())
            return Offset(c.x + sin(a).toFloat() * radius * length, c.y - cos(a).toFloat() * radius * length)
        }
        for (i in 0 until 12) {
            val major = i % 3 == 0
            drawLine(
                color = ink.copy(alpha = if (major) 0.9f else 0.42f),
                start = point(i * 30f, if (major) 0.82f else 0.90f),
                end = point(i * 30f, 1f),
                strokeWidth = radius * 0.035f,
                cap = StrokeCap.Round,
            )
        }
        drawLine(ink, c, point(hourAngle, 0.50f), strokeWidth = radius * 0.08f, cap = StrokeCap.Round)
        drawLine(ink, c, point(minuteAngle, 0.78f), strokeWidth = radius * 0.05f, cap = StrokeCap.Round)
        drawCircle(Accent, radius * 0.07f, c)
    }
}

/** Dia do mês em destaque, no estilo do calendário. */
@Composable
internal fun DateContent() {
    val now = rememberMinuteTicker().value
    val spec = LocalGlassSpec.current
    val locale = currentLocale()
    val date = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
    Column(
        modifier = Modifier.fillMaxSize().padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            date.dayOfWeek.getDisplayName(JavaTextStyle.FULL, locale).uppercase(locale),
            color = Accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(date.dayOfMonth.toString(), style = ClockTextStyle, fontSize = 54.sp, maxLines = 1)
        Text(
            date.month.getDisplayName(JavaTextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) },
            color = spec.secondaryContentColor,
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}

/** Quatro atalhos de um toque. */
@Composable
internal fun ShortcutsContent(data: WidgetData, actions: WidgetActions) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShortcutButton(
            if (data.torchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
            "Lanterna",
            data.torchOn,
            actions.onTorch,
        )
        ShortcutButton(Icons.Rounded.PhotoCamera, "Câmera", false, actions.onCamera)
        ShortcutButton(Icons.Rounded.Search, "Buscar apps", false, actions.onSearch)
        ShortcutButton(Icons.Rounded.Settings, "Configurações", false, actions.onSettings)
    }
}

@Composable
private fun ShortcutButton(icon: ImageVector, description: String, active: Boolean, onClick: () -> Unit) {
    val haptic = rememberHaptic()
    val ink = LocalGlassSpec.current.contentColor
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(if (active) Color.White.copy(alpha = 0.9f) else ink.copy(alpha = 0.16f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                haptic()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = if (active) Color(0xFF14161A) else ink, modifier = Modifier.size(24.dp))
    }
}

/** Barra de busca que abre a gaveta de apps. */
@Composable
internal fun SearchContent() {
    val spec = LocalGlassSpec.current
    Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = spec.secondaryContentColor, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text("Buscar apps", color = spec.secondaryContentColor, fontSize = 15.sp, maxLines = 1)
    }
}
