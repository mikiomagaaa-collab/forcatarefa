package app.cristal.cover.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cristal.cover.data.prefs.PrefKeys
import app.cristal.cover.data.prefs.Settings
import app.cristal.cover.data.prefs.WallpaperConfig
import app.cristal.cover.data.prefs.WallpaperImages
import app.cristal.cover.data.prefs.WallpaperKind
import app.cristal.cover.data.prefs.WallpaperSlot
import app.cristal.cover.data.wallpaper.SubjectStatus
import app.cristal.cover.ui.components.WallpaperLayer
import app.cristal.cover.ui.components.WallpaperPresets
import app.cristal.cover.ui.components.WallpaperTextShadow
import app.cristal.cover.ui.launcher.GlassButton
import app.cristal.cover.ui.launcher.SwitchRow
import kotlin.math.roundToInt

/** Proporção (largura/altura) aproximada de cada tela do Razr, para as prévias. */
private fun WallpaperSlot.aspect(): Float = if (onCover) 0.99f else 0.41f

/**
 * Wallpaper Studio: quatro áreas independentes (início e bloqueio, tela interna e externa).
 * Cada uma pode ter imagem, enquadramento e ajustes próprios, ou acompanhar outra área.
 */
@Composable
fun WallpaperStudioPage(vm: SettingsViewModel, settings: Settings, wallpaper: ImageBitmap?, onBack: () -> Unit) {
    val images by vm.wallpapers.collectAsStateWithLifecycle()
    var editingName by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = WallpaperSlot.fromName(editingName)
    BackHandler(enabled = editing != null) { editingName = null }

    if (editing != null) {
        SlotEditor(vm, settings, images, wallpaper, editing) { editingName = null }
        return
    }
    SettingsScaffold("Wallpaper Studio", settings, wallpaper, onBack) {
        item {
            SettingsCard("Tela interna") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SlotPreview(settings, images, WallpaperSlot.HOME_INNER, Modifier.weight(1f)) { editingName = it.name }
                    SlotPreview(settings, images, WallpaperSlot.LOCK_INNER, Modifier.weight(1f)) { editingName = it.name }
                }
            }
        }
        item {
            SettingsCard("Tela externa") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SlotPreview(settings, images, WallpaperSlot.HOME_COVER, Modifier.weight(1f)) { editingName = it.name }
                    SlotPreview(settings, images, WallpaperSlot.LOCK_COVER, Modifier.weight(1f)) { editingName = it.name }
                }
            }
        }
        item {
            SettingsCard("Geral") {
                InfoText(
                    "Toque em uma área para escolher a imagem e o enquadramento. Uma área pode ter papel de parede " +
                        "próprio ou usar o mesmo de outra; nada é sincronizado sem você pedir.",
                )
                SwitchRow(
                    title = "Parallax entre páginas",
                    subtitle = "O fundo desliza de leve ao trocar de página na tela inicial.",
                    checked = settings.wallpaperParallax,
                    onChange = { vm.set(PrefKeys.WALLPAPER_PARALLAX, it) },
                )
            }
        }
    }
}

/** Desenha o papel de parede que realmente vale para [slot], já seguindo "igual a outra área". */
@Composable
private fun SlotWallpaper(settings: Settings, images: WallpaperImages, slot: WallpaperSlot, extraDim: Float = 0f) {
    val view = settings.forSlot(slot)
    WallpaperLayer(
        image = if (view.wallpaperKind == WallpaperKind.IMAGE) images[view.wallpaperSlot] else null,
        presetIndex = view.wallpaperPreset,
        dim = (view.wallpaperDim + extraDim).coerceAtMost(0.8f),
        blur = view.wallpaperBlur,
        zoom = view.wallpaperZoom,
        offsetX = view.wallpaperOffsetX,
        offsetY = view.wallpaperOffsetY,
        saturation = view.wallpaperSaturation,
    )
}

@Composable
private fun SlotPreview(
    settings: Settings,
    images: WallpaperImages,
    slot: WallpaperSlot,
    modifier: Modifier = Modifier,
    height: Dp = 168.dp,
    onClick: (WallpaperSlot) -> Unit,
) {
    val config = settings.wallpapers[slot] ?: WallpaperConfig()
    Column(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) { onClick(slot) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .height(height)
                .aspectRatio(slot.aspect(), matchHeightConstraintsFirst = true)
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, LocalContentColor.current.copy(alpha = 0.4f), RoundedCornerShape(18.dp)),
            contentAlignment = if (slot.isLock) Alignment.TopCenter else Alignment.BottomCenter,
        ) {
            SlotWallpaper(settings, images, slot)
            if (slot.isLock) {
                // Indica que é a tela de bloqueio: um relógio de exemplo.
                Text(
                    "9:41",
                    color = Color.White,
                    style = TextStyle(fontSize = if (slot.onCover) 34.sp else 16.sp, fontWeight = FontWeight.Bold, shadow = WallpaperTextShadow),
                    modifier = Modifier.padding(top = if (slot.onCover) 22.dp else 26.dp),
                )
            } else {
                // Indica que é a tela inicial: a pílula do dock.
                Box(
                    Modifier
                        .padding(bottom = 8.dp)
                        .fillMaxWidth(0.7f)
                        .height(if (slot.onCover) 16.dp else 9.dp)
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(50)),
                )
            }
        }
        Text(
            if (slot.isLock) "Bloqueio" else "Início",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            config.follows?.let { "Igual a ${it.shortLabel}" } ?: "Próprio",
            fontSize = 11.sp,
            color = LocalContentColor.current.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun SlotEditor(
    vm: SettingsViewModel,
    settings: Settings,
    images: WallpaperImages,
    wallpaper: ImageBitmap?,
    slot: WallpaperSlot,
    onBack: () -> Unit,
) {
    val config = settings.wallpapers[slot] ?: WallpaperConfig()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setWallpaperImage(slot, uri)
    }
    val others = WallpaperSlot.entries.filter { it != slot }
    SettingsScaffold(slot.label, settings, wallpaper, onBack) {
        item {
            SettingsCard("Prévia") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    SlotPreview(settings, images, slot, height = 230.dp) { }
                }
            }
        }
        item {
            SettingsCard("Origem") {
                ChoiceRow<WallpaperSlot?>(
                    "Este ambiente usa",
                    listOf<Pair<WallpaperSlot?, String>>(null to "Papel de parede próprio") + others.map { it to "Igual a ${it.shortLabel}" },
                    config.follows,
                ) { choice -> vm.updateWallpaper(slot) { it.copy(follows = choice) } }
                if (config.follows != null) {
                    InfoText("Para personalizar só este ambiente, escolha \"Papel de parede próprio\".")
                }
            }
        }
        if (config.follows == null) {
            item {
                SettingsCard("Imagem") {
                    GlassButton("Escolher da galeria", Modifier.fillMaxWidth().padding(bottom = 8.dp), primary = true) {
                        runCatching {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }.onFailure { vm.message("Não foi possível abrir o seletor de fotos nesta tela.") }
                    }
                    val presets = WallpaperPresets.all
                    for (rowStart in presets.indices step 3) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (index in rowStart until rowStart + 3) {
                                if (index < presets.size) {
                                    PresetTile(
                                        index = index,
                                        name = presets[index].name,
                                        selected = config.kind == WallpaperKind.PRESET && config.preset == index,
                                        modifier = Modifier.weight(1f),
                                    ) { vm.useWallpaperPreset(slot, index) }
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
            if (config.kind == WallpaperKind.IMAGE) {
                item {
                    SettingsCard("Enquadramento") {
                        SliderRow("Zoom", config.zoom, 1f..3f, label = { "${(it * 100).roundToInt()}%" }) { v ->
                            vm.updateWallpaper(slot) { it.copy(zoom = v) }
                        }
                        SliderRow("Posição horizontal", config.offsetX, -1f..1f, label = { positionLabel(it, "esquerda", "direita") }) { v ->
                            vm.updateWallpaper(slot) { it.copy(offsetX = v) }
                        }
                        SliderRow("Posição vertical", config.offsetY, -1f..1f, label = { positionLabel(it, "cima", "baixo") }) { v ->
                            vm.updateWallpaper(slot) { it.copy(offsetY = v) }
                        }
                        InfoText("Use o zoom e a posição para tirar o assunto da foto de trás do relógio ou das câmeras.")
                    }
                }
            }
            item {
                SettingsCard("Profundidade") {
                    if (config.kind == WallpaperKind.IMAGE) {
                        val statuses by vm.subjectStatus.collectAsStateWithLifecycle()
                        val error by vm.subjectError.collectAsStateWithLifecycle()
                        val status = statuses[slot] ?: SubjectStatus.NONE
                        SwitchRow(
                            "Efeito de profundidade",
                            config.depth,
                            { on ->
                                vm.updateWallpaper(slot) { it.copy(depth = on) }
                                if (on && status != SubjectStatus.READY && status != SubjectStatus.WORKING) vm.detectSubject(slot)
                            },
                            subtitle = "O objeto principal da foto (pessoa, pet, prédio) fica na frente do relógio do bloqueio.",
                        )
                        if (config.depth) {
                            InfoText(
                                when (status) {
                                    SubjectStatus.WORKING -> "Recortando o objeto da foto no aparelho…"
                                    SubjectStatus.READY -> "Objeto recortado. Veja no bloqueio (Tela de bloqueio > Pré-visualizar)."
                                    SubjectStatus.NO_SUBJECT -> "Não encontrei um objeto principal nesta foto. Fotos com uma pessoa ou objeto em destaque funcionam melhor."
                                    SubjectStatus.UNAVAILABLE -> error ?: "O recorte não está disponível neste aparelho."
                                    SubjectStatus.NONE -> "Toque em \"Recortar objeto\" para preparar o efeito."
                                },
                            )
                            if (config.blur >= 0.05f) InfoText("Com desfoque ligado o efeito fica desativado: a foto precisa estar nítida.")
                            if (status != SubjectStatus.WORKING) {
                                GlassButton(
                                    if (status == SubjectStatus.READY) "Recortar de novo" else "Recortar objeto",
                                    Modifier.fillMaxWidth().padding(top = 6.dp),
                                ) { vm.detectSubject(slot) }
                            }
                        }
                    }
                    SwitchRow(
                        "Papel de parede espacial",
                        config.spatial,
                        { on -> vm.updateWallpaper(slot) { it.copy(spatial = on) } },
                        subtitle = "A imagem se move de leve quando você inclina o aparelho, dando sensação de profundidade. Gasta um pouco mais de bateria.",
                    )
                }
            }
            item {
                SettingsCard("Ajustes") {
                    SliderRow("Escurecer", config.dim, 0f..0.7f, label = { "${(it * 100).roundToInt()}%" }) { v ->
                        vm.updateWallpaper(slot) { it.copy(dim = v) }
                    }
                    SliderRow("Desfocar", config.blur, 0f..1f, label = { "${(it * 100).roundToInt()}%" }) { v ->
                        vm.updateWallpaper(slot) { it.copy(blur = v) }
                    }
                    if (config.kind == WallpaperKind.IMAGE) {
                        SliderRow("Saturação", config.saturation, 0f..2f, label = { "${(it * 100).roundToInt()}%" }) { v ->
                            vm.updateWallpaper(slot) { it.copy(saturation = v) }
                        }
                    }
                    GlassButton("Restaurar o padrão", Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        vm.updateWallpaper(slot) { WallpaperConfig(follows = null) }
                    }
                }
            }
        }
    }
}

private fun positionLabel(value: Float, negative: String, positive: String): String = when {
    value < -0.05f -> "${(-value * 100).roundToInt()}% $negative"
    value > 0.05f -> "${(value * 100).roundToInt()}% $positive"
    else -> "centro"
}

@Composable
fun PresetTile(index: Int, name: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(88.dp)
                .clip(RoundedCornerShape(18.dp))
                .border(
                    if (selected) 2.dp else 0.75.dp,
                    LocalContentColor.current.copy(alpha = if (selected) 1f else 0.3f),
                    RoundedCornerShape(18.dp),
                ),
        ) {
            WallpaperLayer(image = null, presetIndex = index, dim = 0f)
        }
        Text(name, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
    }
}
