package app.cristal.cover.ui.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cristal.cover.data.home.WidgetKind
import app.cristal.cover.ui.components.WallpaperTextShadow
import app.cristal.cover.ui.widgets.WidgetActions
import app.cristal.cover.ui.widgets.WidgetCard
import app.cristal.cover.ui.widgets.WidgetData
import app.cristal.cover.ui.widgets.currentLocale
import app.cristal.cover.ui.widgets.formatDate
import app.cristal.cover.ui.widgets.rememberMinuteTicker
import java.time.Instant
import java.time.ZoneId

/** Painel "Hoje", à esquerda da primeira página: todos os widgets em uma lista rolável. */
@Composable
fun TodayPanel(widgetData: WidgetData, widgetActions: WidgetActions) {
    val now = rememberMinuteTicker().value
    val locale = currentLocale()
    val hour = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).hour
    val greeting = when (hour) {
        in 5..11 -> "Bom dia"
        in 12..17 -> "Boa tarde"
        else -> "Boa noite"
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") {
            Column(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 2.dp)) {
                Text(
                    greeting,
                    color = Color.White,
                    style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, shadow = WallpaperTextShadow),
                )
                Text(
                    formatDate(now).replaceFirstChar { it.titlecase(locale) },
                    color = Color.White.copy(alpha = 0.85f),
                    style = TextStyle(fontSize = 13.sp, shadow = WallpaperTextShadow),
                )
            }
        }
        item(key = "weather") {
            WidgetCard(WidgetKind.WEATHER, widgetData, widgetActions, Modifier.fillMaxWidth().height(132.dp))
        }
        item(key = "music") {
            WidgetCard(WidgetKind.MUSIC, widgetData, widgetActions, Modifier.fillMaxWidth().height(108.dp))
        }
        item(key = "agenda") {
            WidgetCard(WidgetKind.AGENDA, widgetData, widgetActions, Modifier.fillMaxWidth().height(148.dp))
        }
        item(key = "battery") {
            WidgetCard(WidgetKind.BATTERY, widgetData, widgetActions, Modifier.fillMaxWidth().height(92.dp))
        }
    }
}
