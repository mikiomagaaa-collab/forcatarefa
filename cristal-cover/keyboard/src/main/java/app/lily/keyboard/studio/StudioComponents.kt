package app.lily.keyboard.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Cores do Keyboard Studio (identidade do Liquid Flip: preto profundo, roxo e lavanda). */
object StudioColors {
    val background = Brush.verticalGradient(listOf(Color(0xFF0B0A14), Color(0xFF1A1233), Color(0xFF0B0A14)))
    val card = Color(0x1FFFFFFF)
    val cardBorder = Color(0x26FFFFFF)
    val text = Color(0xFFF4F1FF)
    val secondary = Color(0xB3E6E0FF)
    val accent = Color(0xFFB79CFF)
    val accentDark = Color(0xFF6C4BD6)
}

@Composable
fun StudioScaffold(title: String, onBack: (() -> Unit)?, content: LazyListScope.() -> Unit) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    Box(Modifier.fillMaxSize().background(StudioColors.background)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = insets.calculateTopPadding() + 8.dp, bottom = insets.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "titulo") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    if (onBack != null) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(StudioColors.card).clickable(onClick = onBack),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar", tint = StudioColors.text)
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(title, color = StudioColors.text, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
                }
            }
            content()
        }
    }
}

@Composable
fun Section(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(StudioColors.card)
            .border(1.dp, StudioColors.cardBorder, RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (title != null) Text(title.uppercase(), color = StudioColors.secondary, fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
fun Info(text: String) {
    Text(text, color = StudioColors.secondary, fontSize = 13.sp, lineHeight = 18.sp)
}

@Composable
fun SwitchItem(title: String, checked: Boolean, subtitle: String? = null, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = StudioColors.text.copy(alpha = if (enabled) 1f else 0.5f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, color = StudioColors.secondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = StudioColors.accentDark, checkedThumbColor = Color.White),
        )
    }
}

@Composable
fun SliderItem(title: String, value: Float, range: ClosedFloatingPointRange<Float>, label: (Float) -> String, onChange: (Float) -> Unit) {
    Column {
        Row {
            Text(title, color = StudioColors.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(label(value), color = StudioColors.secondary, fontSize = 13.sp)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = StudioColors.accent, inactiveTrackColor = Color(0x33FFFFFF)),
        )
    }
}

@Composable
fun <T> ChoiceItem(title: String?, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (title != null) Text(title, color = StudioColors.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            for ((value, label) in options) {
                val on = value == selected
                Box(
                    Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(if (on) StudioColors.accent else StudioColors.card)
                        .clickable { onSelect(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (on) Color(0xFF17112B) else StudioColors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun NavItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(StudioColors.card)
            .border(1.dp, StudioColors.cardBorder, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(StudioColors.accent.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = StudioColors.accent)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = StudioColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = StudioColors.secondary, fontSize = 12.sp, maxLines = 2)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = StudioColors.secondary)
    }
}

@Composable
fun StudioButton(text: String, modifier: Modifier = Modifier, primary: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (primary) StudioColors.accent else StudioColors.card)
            .border(1.dp, if (primary) Color.Transparent else StudioColors.cardBorder, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (primary) Color(0xFF17112B) else StudioColors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
