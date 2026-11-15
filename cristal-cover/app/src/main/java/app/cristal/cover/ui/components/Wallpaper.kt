package app.cristal.cover.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.roundToInt

@Immutable
class WallpaperBlob(val x: Float, val y: Float, val radius: Float, val color: Color, val depth: Float = 1f)

@Immutable
class WallpaperPreset(val name: String, val base: List<Color>, val blobs: List<WallpaperBlob>)

/** Papéis de parede embutidos: gradientes "mesh" desenhados por código (não ocupam espaço no APK). */
object WallpaperPresets {
    val all: List<WallpaperPreset> = listOf(
        WallpaperPreset(
            "Aurora",
            listOf(Color(0xFF0B1026), Color(0xFF1B1464), Color(0xFF3A1C71)),
            listOf(
                WallpaperBlob(0.15f, 0.20f, 0.55f, Color(0xFF00D4FF), 1.4f),
                WallpaperBlob(0.85f, 0.30f, 0.50f, Color(0xFFFF4ECD), 0.8f),
                WallpaperBlob(0.55f, 0.90f, 0.60f, Color(0xFF7CFFB2), 1.1f),
            ),
        ),
        WallpaperPreset(
            "Oceano",
            listOf(Color(0xFF021B3A), Color(0xFF03396C), Color(0xFF005B96)),
            listOf(
                WallpaperBlob(0.80f, 0.15f, 0.55f, Color(0xFF4FC3F7), 1.2f),
                WallpaperBlob(0.20f, 0.75f, 0.60f, Color(0xFF00E5C3), 0.9f),
                WallpaperBlob(0.60f, 0.55f, 0.35f, Color(0xFF7B8CFF), 1.5f),
            ),
        ),
        WallpaperPreset(
            "Pôr do sol",
            listOf(Color(0xFF2B1055), Color(0xFF7597DE), Color(0xFFFF9A8B)),
            listOf(
                WallpaperBlob(0.75f, 0.80f, 0.60f, Color(0xFFFF6A3D), 1.2f),
                WallpaperBlob(0.20f, 0.25f, 0.50f, Color(0xFFB65CFF), 0.9f),
                WallpaperBlob(0.50f, 0.55f, 0.40f, Color(0xFFFFC371), 1.4f),
            ),
        ),
        WallpaperPreset(
            "Floresta",
            listOf(Color(0xFF071A14), Color(0xFF0B3D2E), Color(0xFF145A45)),
            listOf(
                WallpaperBlob(0.20f, 0.20f, 0.55f, Color(0xFF7CFF6B), 1.1f),
                WallpaperBlob(0.85f, 0.70f, 0.55f, Color(0xFF00C9A7), 0.8f),
                WallpaperBlob(0.55f, 0.40f, 0.35f, Color(0xFFFFE98A), 1.5f),
            ),
        ),
        WallpaperPreset(
            "Grafite",
            listOf(Color(0xFF0C0D10), Color(0xFF1C1E24), Color(0xFF2B2E36)),
            listOf(
                WallpaperBlob(0.80f, 0.20f, 0.55f, Color(0xFF6B7385), 1.2f),
                WallpaperBlob(0.15f, 0.80f, 0.55f, Color(0xFF3D4760), 0.9f),
                WallpaperBlob(0.50f, 0.50f, 0.30f, Color(0xFF9AA4B8), 1.4f),
            ),
        ),
        WallpaperPreset(
            "Lavanda",
            listOf(Color(0xFF2E1F5E), Color(0xFF6A4FB3), Color(0xFFB79CED)),
            listOf(
                WallpaperBlob(0.20f, 0.75f, 0.55f, Color(0xFFFF9DE2), 1.1f),
                WallpaperBlob(0.80f, 0.20f, 0.55f, Color(0xFF8EC5FF), 0.9f),
                WallpaperBlob(0.55f, 0.50f, 0.35f, Color(0xFFFFFFFF), 1.5f),
            ),
        ),
        WallpaperPreset(
            "Brasa",
            listOf(Color(0xFF1A0508), Color(0xFF5C0A1A), Color(0xFFB3261E)),
            listOf(
                WallpaperBlob(0.75f, 0.25f, 0.55f, Color(0xFFFF8A00), 1.2f),
                WallpaperBlob(0.20f, 0.80f, 0.55f, Color(0xFFFF3D71), 0.9f),
                WallpaperBlob(0.45f, 0.45f, 0.30f, Color(0xFFFFD166), 1.5f),
            ),
        ),
        WallpaperPreset(
            "Gelo",
            listOf(Color(0xFFDDE7F5), Color(0xFFB8CCE8), Color(0xFF9DB7DD)),
            listOf(
                WallpaperBlob(0.20f, 0.20f, 0.55f, Color(0xFFFFFFFF), 1.1f),
                WallpaperBlob(0.85f, 0.75f, 0.55f, Color(0xFF7FB2FF), 0.9f),
                WallpaperBlob(0.55f, 0.45f, 0.35f, Color(0xFFC9B6FF), 1.5f),
            ),
        ),
    )

    fun get(index: Int): WallpaperPreset = all[index.mod(all.size)]
}

/**
 * Desenha o papel de parede do launcher (imagem do usuário ou preset).
 *
 * @param parallax deslocamento horizontal de -1 a 1, lido só na fase de desenho, para que trocar
 * de página não provoque recomposição.
 */
@Composable
fun WallpaperLayer(
    image: ImageBitmap?,
    presetIndex: Int,
    dim: Float,
    modifier: Modifier = Modifier,
    parallax: () -> Float = { 0f },
) {
    val preset = WallpaperPresets.get(presetIndex)
    Spacer(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val shift = parallax().coerceIn(-1f, 1f)
                if (image != null) drawCroppedImage(image, shift) else drawPreset(preset, shift)
                if (dim > 0.001f) drawRect(Color.Black.copy(alpha = dim.coerceIn(0f, 0.9f)))
            },
    )
}

private fun DrawScope.drawCroppedImage(image: ImageBitmap, shift: Float) {
    if (image.width <= 0 || image.height <= 0) return
    // 8% maior que a tela, para ter margem de deslocamento no parallax.
    val scale = max(size.width / image.width, size.height / image.height) * 1.08f
    val dw = image.width * scale
    val dh = image.height * scale
    val dx = (size.width - dw) / 2f - shift * (dw - size.width) / 2f
    val dy = (size.height - dh) / 2f
    drawImage(
        image = image,
        dstOffset = IntOffset(dx.roundToInt(), dy.roundToInt()),
        dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
        filterQuality = FilterQuality.Medium,
    )
}

private fun DrawScope.drawPreset(preset: WallpaperPreset, shift: Float) {
    drawRect(Brush.linearGradient(preset.base, start = Offset.Zero, end = Offset(size.width, size.height)))
    val longest = max(size.width, size.height)
    for (blob in preset.blobs) {
        val center = Offset(
            x = blob.x * size.width - shift * size.width * 0.05f * blob.depth,
            y = blob.y * size.height,
        )
        val radius = blob.radius * longest
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(blob.color.copy(alpha = 0.75f), blob.color.copy(alpha = 0f)),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }
}
