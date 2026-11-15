package app.cristal.cover.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@Immutable
class WallpaperBlob(val x: Float, val y: Float, val radius: Float, val color: Color, val depth: Float = 1f, val alpha: Float = 0.78f)

/** @param dark se o conjunto é escuro (define se o texto sobre o vidro fica claro ou escuro). */
@Immutable
class WallpaperPreset(val name: String, val base: List<Color>, val blobs: List<WallpaperBlob>, val dark: Boolean = true)

/**
 * Papéis de parede embutidos: gradientes "mesh" desenhados por código (não ocupam espaço no APK).
 * A ordem dos oito primeiros é fixa, porque a escolha do usuário é guardada pelo índice.
 */
object WallpaperPresets {
    val all: List<WallpaperPreset> = listOf(
        WallpaperPreset(
            "Aurora",
            listOf(Color(0xFF070B1F), Color(0xFF15124F), Color(0xFF2B1466)),
            listOf(
                WallpaperBlob(0.12f, 0.18f, 0.58f, Color(0xFF00D4FF), 1.4f),
                WallpaperBlob(0.88f, 0.26f, 0.52f, Color(0xFFFF4ECD), 0.8f),
                WallpaperBlob(0.55f, 0.95f, 0.62f, Color(0xFF52FFB0), 1.1f),
                WallpaperBlob(0.40f, 0.48f, 0.30f, Color(0xFF7B6CFF), 1.6f, 0.55f),
            ),
        ),
        WallpaperPreset(
            "Oceano",
            listOf(Color(0xFF01142B), Color(0xFF032F5C), Color(0xFF004E86)),
            listOf(
                WallpaperBlob(0.82f, 0.12f, 0.56f, Color(0xFF4FC3F7), 1.2f),
                WallpaperBlob(0.16f, 0.78f, 0.62f, Color(0xFF00E5C3), 0.9f),
                WallpaperBlob(0.62f, 0.55f, 0.36f, Color(0xFF7B8CFF), 1.5f, 0.6f),
                WallpaperBlob(0.30f, 0.20f, 0.28f, Color(0xFF0A84FF), 1.3f, 0.6f),
            ),
        ),
        WallpaperPreset(
            "Pôr do sol",
            listOf(Color(0xFF24104F), Color(0xFF6B4FB8), Color(0xFFFF8E72)),
            listOf(
                WallpaperBlob(0.78f, 0.84f, 0.62f, Color(0xFFFF6A3D), 1.2f),
                WallpaperBlob(0.18f, 0.22f, 0.52f, Color(0xFFB65CFF), 0.9f),
                WallpaperBlob(0.50f, 0.56f, 0.40f, Color(0xFFFFC371), 1.4f, 0.65f),
                WallpaperBlob(0.90f, 0.20f, 0.30f, Color(0xFFFF4E8A), 1.1f, 0.5f),
            ),
        ),
        WallpaperPreset(
            "Floresta",
            listOf(Color(0xFF051410), Color(0xFF0A3326), Color(0xFF11503D)),
            listOf(
                WallpaperBlob(0.18f, 0.18f, 0.56f, Color(0xFF7CFF6B), 1.1f, 0.6f),
                WallpaperBlob(0.86f, 0.72f, 0.56f, Color(0xFF00C9A7), 0.8f),
                WallpaperBlob(0.55f, 0.40f, 0.34f, Color(0xFFFFE98A), 1.5f, 0.45f),
            ),
        ),
        WallpaperPreset(
            "Grafite",
            listOf(Color(0xFF08090B), Color(0xFF17191E), Color(0xFF262930)),
            listOf(
                WallpaperBlob(0.82f, 0.18f, 0.56f, Color(0xFF6B7385), 1.2f, 0.6f),
                WallpaperBlob(0.14f, 0.82f, 0.56f, Color(0xFF3D4760), 0.9f),
                WallpaperBlob(0.50f, 0.50f, 0.30f, Color(0xFF9AA4B8), 1.4f, 0.4f),
            ),
        ),
        WallpaperPreset(
            "Lavanda",
            listOf(Color(0xFF271A52), Color(0xFF5C45A3), Color(0xFFA58BE0)),
            listOf(
                WallpaperBlob(0.18f, 0.78f, 0.56f, Color(0xFFFF9DE2), 1.1f),
                WallpaperBlob(0.82f, 0.18f, 0.56f, Color(0xFF8EC5FF), 0.9f),
                WallpaperBlob(0.55f, 0.50f, 0.34f, Color(0xFFFFFFFF), 1.5f, 0.35f),
            ),
        ),
        WallpaperPreset(
            "Brasa",
            listOf(Color(0xFF140406), Color(0xFF4E0815), Color(0xFFA3221A)),
            listOf(
                WallpaperBlob(0.78f, 0.24f, 0.56f, Color(0xFFFF8A00), 1.2f),
                WallpaperBlob(0.18f, 0.82f, 0.56f, Color(0xFFFF3D71), 0.9f),
                WallpaperBlob(0.45f, 0.45f, 0.30f, Color(0xFFFFD166), 1.5f, 0.5f),
            ),
        ),
        WallpaperPreset(
            "Gelo",
            listOf(Color(0xFFE4ECF7), Color(0xFFC2D3EC), Color(0xFFA7BFE2)),
            listOf(
                WallpaperBlob(0.18f, 0.18f, 0.56f, Color(0xFFFFFFFF), 1.1f),
                WallpaperBlob(0.86f, 0.76f, 0.56f, Color(0xFF7FB2FF), 0.9f, 0.6f),
                WallpaperBlob(0.55f, 0.45f, 0.36f, Color(0xFFC9B6FF), 1.5f, 0.55f),
            ),
            dark = false,
        ),
        WallpaperPreset(
            "Meia-noite",
            listOf(Color(0xFF020308), Color(0xFF070B1C), Color(0xFF0B1330)),
            listOf(
                WallpaperBlob(0.50f, 1.05f, 0.70f, Color(0xFF1E5BFF), 1.2f, 0.7f),
                WallpaperBlob(0.10f, 0.10f, 0.42f, Color(0xFF3A2A8C), 0.9f, 0.6f),
                WallpaperBlob(0.92f, 0.30f, 0.32f, Color(0xFF00B4FF), 1.5f, 0.35f),
            ),
        ),
        WallpaperPreset(
            "Uva",
            listOf(Color(0xFF12051F), Color(0xFF3B0F5C), Color(0xFF6A1B8F)),
            listOf(
                WallpaperBlob(0.20f, 0.20f, 0.56f, Color(0xFFFF5CA8), 1.2f, 0.7f),
                WallpaperBlob(0.85f, 0.80f, 0.58f, Color(0xFF7C4DFF), 0.9f),
                WallpaperBlob(0.60f, 0.40f, 0.30f, Color(0xFF4FC3F7), 1.5f, 0.4f),
            ),
        ),
        WallpaperPreset(
            "Menta",
            listOf(Color(0xFF042A2B), Color(0xFF0B5C5A), Color(0xFF17907F)),
            listOf(
                WallpaperBlob(0.82f, 0.18f, 0.56f, Color(0xFF9BFFCF), 1.2f, 0.65f),
                WallpaperBlob(0.16f, 0.80f, 0.58f, Color(0xFF00B8D4), 0.9f),
                WallpaperBlob(0.50f, 0.50f, 0.30f, Color(0xFFE8FFB0), 1.5f, 0.4f),
            ),
        ),
        WallpaperPreset(
            "Pêssego",
            listOf(Color(0xFFFFE9DC), Color(0xFFFFCDB8), Color(0xFFF7A9A0)),
            listOf(
                WallpaperBlob(0.18f, 0.18f, 0.56f, Color(0xFFFFFFFF), 1.1f, 0.8f),
                WallpaperBlob(0.86f, 0.78f, 0.58f, Color(0xFFFF7EB3), 0.9f, 0.6f),
                WallpaperBlob(0.55f, 0.40f, 0.34f, Color(0xFFFFD27F), 1.5f, 0.6f),
            ),
            dark = false,
        ),
    )

    fun get(index: Int): WallpaperPreset = all[index.mod(all.size)]
}

/**
 * Desenha o papel de parede do launcher (imagem do usuário ou preset).
 *
 * @param zoom ampliação da imagem (1 = preenche a tela); [offsetX] e [offsetY], de -1 a 1, movem o enquadramento.
 * @param saturation 0 = preto e branco, 1 = original, 2 = cores fortes (só para imagens).
 * @param blur desfoque de 0 a 1 aplicado à própria imagem (um fundo mais calmo realça o vidro).
 * @param parallax deslocamento horizontal de -1 a 1, lido só na fase de desenho, para que trocar
 * de página não provoque recomposição.
 */
@Composable
fun WallpaperLayer(
    image: ImageBitmap?,
    presetIndex: Int,
    dim: Float,
    modifier: Modifier = Modifier,
    blur: Float = 0f,
    zoom: Float = 1f,
    offsetX: Float = 0f,
    offsetY: Float = 0f,
    saturation: Float = 1f,
    parallax: () -> Float = { 0f },
    /** Inclinação do aparelho (-1 a 1 em cada eixo), para o papel de parede espacial. */
    tilt: () -> Offset = NoTilt,
    /** Amplia um pouco a imagem para sobrar margem para o movimento espacial. */
    spatial: Boolean = false,
) {
    val preset = WallpaperPresets.get(presetIndex)
    Spacer(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val radius = blur.coerceIn(0f, 1f) * 28.dp.toPx()
                renderEffect = if (radius > 0.5f) BlurEffect(radius, radius, TileMode.Mirror) else null
            }
            .drawBehind {
                val shift = parallax().coerceIn(-1f, 1f)
                val t = if (spatial) tilt() else Offset.Zero
                if (image != null) {
                    drawCroppedImage(image, shift, zoom, offsetX, offsetY, saturation, t, spatial)
                } else {
                    drawPreset(preset, shift, t)
                }
                // Vinheta suave: escurece os cantos e dá profundidade.
                val longest = max(size.width, size.height)
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = if (image != null) 0.14f else 0.24f)),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = longest * 0.85f,
                    ),
                )
                if (dim > 0.001f) drawRect(Color.Black.copy(alpha = dim.coerceIn(0f, 0.9f)))
            },
    )
}

val NoTilt: () -> Offset = { Offset.Zero }

/**
 * O objeto principal da foto, recortado, desenhado exatamente sobre o papel de parede (mesmo
 * enquadramento, mesmo movimento). Tudo o que fica entre [WallpaperLayer] e esta camada parece
 * estar "atrás" do objeto: é o efeito de profundidade.
 */
@Composable
fun WallpaperSubjectLayer(
    subject: ImageBitmap,
    dim: Float,
    modifier: Modifier = Modifier,
    zoom: Float = 1f,
    offsetX: Float = 0f,
    offsetY: Float = 0f,
    saturation: Float = 1f,
    parallax: () -> Float = { 0f },
    tilt: () -> Offset = NoTilt,
    spatial: Boolean = false,
) {
    Spacer(
        modifier = modifier
            .fillMaxSize()
            // Camada própria: a vinheta e o escurecimento só tingem os pixels do objeto.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawBehind {
                val shift = parallax().coerceIn(-1f, 1f)
                val t = if (spatial) tilt() else Offset.Zero
                drawCroppedImage(subject, shift, zoom, offsetX, offsetY, saturation, t, spatial)
                val longest = max(size.width, size.height)
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.14f)),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = longest * 0.85f,
                    ),
                    blendMode = BlendMode.SrcAtop,
                )
                if (dim > 0.001f) drawRect(Color.Black.copy(alpha = dim.coerceIn(0f, 0.9f)), blendMode = BlendMode.SrcAtop)
            },
    )
}

private fun DrawScope.drawCroppedImage(
    image: ImageBitmap,
    shift: Float,
    zoom: Float,
    offsetX: Float,
    offsetY: Float,
    saturation: Float,
    tilt: Offset = Offset.Zero,
    spatial: Boolean = false,
) {
    if (image.width <= 0 || image.height <= 0) return
    // 8% maior que a tela, para ter margem de deslocamento no parallax (16% no modo espacial).
    val margin = if (spatial) 1.16f else 1.08f
    val scale = max(size.width / image.width, size.height / image.height) * margin * zoom.coerceIn(1f, 4f)
    val dw = image.width * scale
    val dh = image.height * scale
    val slackX = (dw - size.width) / 2f
    val slackY = (dh - size.height) / 2f
    // A posição escolhida vale primeiro; o parallax usa a folga que sobrar, sem descobrir a borda.
    val x = offsetX.coerceIn(-1f, 1f)
    val y = offsetY.coerceIn(-1f, 1f)
    val tx = (x + shift * (1f - abs(x)) + tilt.x * 0.55f).coerceIn(-1f, 1f)
    val ty = (y + tilt.y * 0.55f).coerceIn(-1f, 1f)
    val dx = -slackX - tx * slackX
    val dy = -slackY - ty * slackY
    val filter = if (abs(saturation - 1f) > 0.01f) {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(saturation.coerceIn(0f, 2f)) })
    } else {
        null
    }
    drawImage(
        image = image,
        dstOffset = IntOffset(dx.roundToInt(), dy.roundToInt()),
        dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
        colorFilter = filter,
        filterQuality = FilterQuality.Medium,
    )
}

private fun DrawScope.drawPreset(preset: WallpaperPreset, shift: Float, tilt: Offset = Offset.Zero) {
    drawRect(Brush.linearGradient(preset.base, start = Offset.Zero, end = Offset(size.width, size.height)))
    val longest = max(size.width, size.height)
    for (blob in preset.blobs) {
        val center = Offset(
            // Cada mancha tem a sua profundidade: as "mais perto" andam mais (papel de parede espacial).
            x = blob.x * size.width - shift * size.width * 0.05f * blob.depth + tilt.x * size.width * 0.045f * blob.depth,
            y = blob.y * size.height + tilt.y * size.height * 0.03f * blob.depth,
        )
        val radius = blob.radius * longest
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(blob.color.copy(alpha = blob.alpha), blob.color.copy(alpha = blob.alpha * 0.35f), blob.color.copy(alpha = 0f)),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }
}
