package app.lily.ai.avatar

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
enum class AvatarStyle(val label: String, val detail: String) {
    HOLO("Holográfica", "Luz, linhas de varredura e bordas em espectro"),
    GLASS("Liquid Glass", "Vidro translúcido com reflexos suaves"),
    MINIMAL("Minimalista", "Traços limpos, sem efeitos"),
}

@Serializable
enum class AvatarPresentation(val label: String, val detail: String) {
    BUST("Busto", "Rosto, cabelo, ombros e o halo"),
    FACE("Rosto", "Só o rosto, mais próximo"),
    ORB("Orbe", "A esfera da L.I.L.Y com olhos, a forma mais discreta"),
}

@Serializable
enum class AvatarSize(val label: String, val dp: Int) {
    SMALL("Pequena", 72),
    MEDIUM("Média", 104),
    LARGE("Grande", 148),
}

@Serializable
enum class AvatarMotion(val label: String) {
    FULL("Completas"),
    REDUCED("Suaves"),
    OFF("Paradas"),
}

/** Quando a avatar aparece por cima de outros apps (precisa da permissão de sobreposição). */
@Serializable
enum class OverlayMode(val label: String, val detail: String) {
    OFF("Desligada", "Só dentro da L.I.L.Y"),
    TASKS("Durante tarefas", "Aparece enquanto a L.I.L.Y trabalha, ouve ou fala, e some depois"),
    ALWAYS("Sempre", "Fica visível enquanto a L.I.L.Y estiver aberta em segundo plano"),
}

@Serializable
enum class OverlayCorner(val label: String) {
    TOP_START("Alto à esquerda"),
    TOP_END("Alto à direita"),
    BOTTOM_START("Embaixo à esquerda"),
    BOTTOM_END("Embaixo à direita"),
}

/** A aparência e a presença da avatar (guardadas nas configurações). */
@Immutable
@Serializable
data class AvatarLook(
    val enabled: Boolean = true,
    val style: AvatarStyle = AvatarStyle.HOLO,
    val presentation: AvatarPresentation = AvatarPresentation.BUST,
    /** -1 = cor do tema; 0..359 = matiz escolhida. */
    val hue: Int = -1,
    val size: AvatarSize = AvatarSize.MEDIUM,
    val opacity: Float = 0.96f,
    val motion: AvatarMotion = AvatarMotion.FULL,
    val overlay: OverlayMode = OverlayMode.OFF,
    val corner: OverlayCorner = OverlayCorner.BOTTOM_END,
)
