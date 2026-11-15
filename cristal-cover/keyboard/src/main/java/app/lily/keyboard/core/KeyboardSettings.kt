package app.lily.keyboard.core

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/** Estilos visuais de partida. Qualquer um pode ser copiado e virar um tema próprio. */
@Serializable
enum class ThemeStyle(val label: String) {
    LIQUID_GLASS("Liquid Glass"),
    ONE_UI("Inspirado no One UI"),
    MINIMAL("Minimalista"),
    AMOLED("AMOLED escuro"),
    CLASSIC("Clássico"),
    CUSTOM("Personalizado"),
}

/**
 * Tema do teclado. Cores em ARGB (Long, para caber no JSON sem sinal).
 *
 * @param glass intensidade do efeito de vidro nas teclas (brilho na borda e reflexo), de 0 a 1.
 * @param keyAlpha transparência das teclas (1 = opacas).
 * @param backgroundAlpha transparência do fundo (abaixo de 1 deixa ver o app atrás).
 */
@Immutable
@Serializable
data class KeyboardTheme(
    val id: String,
    val name: String,
    val style: ThemeStyle,
    val dark: Boolean = true,
    val background: Long = 0xFF15131F,
    val background2: Long = 0xFF241B3D,
    val backgroundImage: String? = null,
    val imageDim: Float = 0.25f,
    val imageBlur: Float = 0.4f,
    val backgroundAlpha: Float = 1f,
    val keyColor: Long = 0x33FFFFFF,
    val functionKeyColor: Long = 0x1FFFFFFF,
    val accentColor: Long = 0xFFB79CFF,
    val textColor: Long = 0xFFFFFFFF,
    val symbolColor: Long = 0xB3FFFFFF,
    val keyAlpha: Float = 1f,
    val glass: Float = 0.8f,
    val borders: Boolean = true,
    val cornerRadius: Float = 10f,
    val keyGap: Float = 5f,
    val builtIn: Boolean = true,
)

object Themes {
    val liquid = KeyboardTheme(
        id = "liquid", name = "Liquid Glass", style = ThemeStyle.LIQUID_GLASS,
        background = 0xFF14121E, background2 = 0xFF2A1F49, keyColor = 0x2EFFFFFF, functionKeyColor = 0x1AFFFFFF,
        accentColor = 0xFFB79CFF, glass = 0.9f, cornerRadius = 12f, keyGap = 5f,
    )
    val liquidLight = KeyboardTheme(
        id = "liquid_light", name = "Liquid Glass claro", style = ThemeStyle.LIQUID_GLASS, dark = false,
        background = 0xFFE9E6F3, background2 = 0xFFD7D0EC, keyColor = 0xCCFFFFFF, functionKeyColor = 0x80FFFFFF,
        accentColor = 0xFF6C4BD6, textColor = 0xFF17141F, symbolColor = 0x9917141F, glass = 0.9f, cornerRadius = 12f,
    )
    val oneUi = KeyboardTheme(
        id = "oneui", name = "One UI", style = ThemeStyle.ONE_UI, dark = false,
        background = 0xFFF2F2F5, background2 = 0xFFF2F2F5, keyColor = 0xFFFFFFFF, functionKeyColor = 0xFFE1E1E8,
        accentColor = 0xFF3E7BFA, textColor = 0xFF111111, symbolColor = 0x99111111, glass = 0f, borders = false,
        cornerRadius = 9f, keyGap = 5f,
    )
    val oneUiDark = KeyboardTheme(
        id = "oneui_dark", name = "One UI escuro", style = ThemeStyle.ONE_UI,
        background = 0xFF17171A, background2 = 0xFF17171A, keyColor = 0xFF2C2C31, functionKeyColor = 0xFF222226,
        accentColor = 0xFF3E7BFA, glass = 0f, borders = false, cornerRadius = 9f,
    )
    val minimal = KeyboardTheme(
        id = "minimal", name = "Minimalista", style = ThemeStyle.MINIMAL,
        background = 0xFF101114, background2 = 0xFF101114, keyColor = 0x00FFFFFF, functionKeyColor = 0x00FFFFFF,
        accentColor = 0xFFFFFFFF, glass = 0f, borders = false, cornerRadius = 8f, keyGap = 4f,
    )
    val amoled = KeyboardTheme(
        id = "amoled", name = "AMOLED", style = ThemeStyle.AMOLED,
        background = 0xFF000000, background2 = 0xFF000000, keyColor = 0xFF141416, functionKeyColor = 0xFF0C0C0D,
        accentColor = 0xFF8E7CFF, glass = 0.25f, borders = true, cornerRadius = 10f,
    )
    val classic = KeyboardTheme(
        id = "classic", name = "Clássico", style = ThemeStyle.CLASSIC,
        background = 0xFF263238, background2 = 0xFF263238, keyColor = 0xFF3B4A51, functionKeyColor = 0xFF31404A,
        accentColor = 0xFF5BB8FF, glass = 0f, borders = false, cornerRadius = 4f, keyGap = 4f,
    )

    // Exclusivos do Liquid Flip, com a paleta do launcher.
    val lavanda = KeyboardTheme(
        id = "lf_lavanda", name = "Lavanda", style = ThemeStyle.LIQUID_GLASS,
        background = 0xFF271A52, background2 = 0xFF6E57C2, keyColor = 0x33FFFFFF, functionKeyColor = 0x22FFFFFF,
        accentColor = 0xFFFFC6F0, glass = 1f, cornerRadius = 14f,
    )
    val violeta = KeyboardTheme(
        id = "lf_violeta", name = "Violeta profundo", style = ThemeStyle.LIQUID_GLASS,
        background = 0xFF0B0716, background2 = 0xFF3A1269, keyColor = 0x29FFFFFF, functionKeyColor = 0x17FFFFFF,
        accentColor = 0xFF9D6BFF, glass = 0.9f, cornerRadius = 12f,
    )
    val prata = KeyboardTheme(
        id = "lf_prata", name = "Prata", style = ThemeStyle.LIQUID_GLASS, dark = false,
        background = 0xFFD9DCE3, background2 = 0xFFF4F5F8, keyColor = 0xB3FFFFFF, functionKeyColor = 0x66FFFFFF,
        accentColor = 0xFF5A5F6E, textColor = 0xFF1A1C22, symbolColor = 0x991A1C22, glass = 1f, cornerRadius = 12f,
    )
    val aurora = KeyboardTheme(
        id = "lf_aurora", name = "Aurora", style = ThemeStyle.LIQUID_GLASS,
        background = 0xFF07122B, background2 = 0xFF3F1A6B, keyColor = 0x2BFFFFFF, functionKeyColor = 0x18FFFFFF,
        accentColor = 0xFF52FFB0, glass = 0.9f, cornerRadius = 12f,
    )

    /** Segue o modo claro/escuro do sistema (vira [liquid] ou [liquidLight]). */
    val auto = liquid.copy(id = "auto", name = "Automático")

    fun resolve(theme: KeyboardTheme, systemDark: Boolean): KeyboardTheme =
        if (theme.id == auto.id) (if (systemDark) liquid else liquidLight) else theme

    val builtIn: List<KeyboardTheme> = listOf(auto, liquid, liquidLight, lavanda, violeta, prata, aurora, oneUi, oneUiDark, minimal, amoled, classic)
}

@Serializable
enum class CorrectionStyle(val label: String, val description: String) {
    FORMAL("Formal", "Corrige erros e completa abreviações (vc → você)."),
    NATURAL("Natural", "Corrige erros e mantém o seu jeito; sugere a forma completa sem trocar."),
    INFORMAL("Informal", "Só corrige erros claros. Abreviações e gírias ficam como você escreveu."),
}

@Serializable
enum class SizeMode(val label: String, val keyHeight: Float) {
    COMPACT("Compacto", 44f),
    NORMAL("Normal", 50f),
    EXPANDED("Expandido", 56f),
}

@Serializable
enum class OneHandSide { LEFT, RIGHT }

@Serializable
enum class KeyboardLanguage(val label: String, val asset: String, val tag: String) {
    PT_BR("Português (Brasil)", "pt_br.dict", "pt-BR"),
    EN("English", "en.dict", "en-US"),
}

/** Atalhos da barra superior. */
@Serializable
enum class ToolbarItem(val label: String) {
    EMOJI("Emojis"),
    CLIPBOARD("Área de transferência"),
    EDIT("Edição de texto"),
    VOICE("Ditado por voz"),
    ONE_HAND("Modo de uma mão"),
    LANGUAGE("Idioma"),
    THEMES("Temas"),
    SETTINGS("Configurações"),
}

@Immutable
@Serializable
data class KeyboardSettings(
    // Aparência
    val themeId: String = Themes.auto.id,
    val customThemes: List<KeyboardTheme> = emptyList(),
    val fontScale: Float = 1f,
    val animations: Float = 1f,
    val keyPreview: Boolean = true,

    // Layout e ergonomia
    val sizeMode: SizeMode = SizeMode.NORMAL,
    /** Multiplica a altura das teclas (0,8 a 1,3). */
    val heightScale: Float = 1f,
    /** Margem nas laterais, em dp. */
    val sideMargin: Float = 0f,
    /** Sobe o teclado a partir da borda de baixo, em dp. */
    val bottomOffset: Float = 0f,
    val oneHanded: Boolean = false,
    val oneHandSide: OneHandSide = OneHandSide.RIGHT,
    /** Largura no modo de uma mão, em fração da tela. */
    val oneHandWidth: Float = 0.82f,
    val numberRow: Boolean = false,
    val cedillaKey: Boolean = false,
    /** Largura das teclas de pontuação ao lado do espaço (0,8 a 1,4 tecla); o espaço usa o resto. */
    val punctuationWidth: Float = 1f,
    /** Aumenta as teclas mais usadas (espaço, apagar, enter) em altura na última linha. */
    val bigBottomRow: Boolean = false,

    // Digitação
    val autoCorrect: Boolean = true,
    val correctionStyle: CorrectionStyle = CorrectionStyle.NATURAL,
    val suggestions: Boolean = true,
    val emojiSuggestions: Boolean = true,
    val gestureTyping: Boolean = true,
    val gestureTrail: Boolean = true,
    val spaceCursor: Boolean = true,
    val deleteSwipe: Boolean = true,
    val doubleSpacePeriod: Boolean = true,
    val autoCapitalize: Boolean = true,
    val longPressMs: Int = 300,
    val sound: Boolean = false,
    val soundVolume: Float = 0.4f,
    val vibration: Boolean = true,
    /** 0 = leve, 1 = forte. */
    val vibrationStrength: Float = 0.4f,

    // Idiomas
    val languages: List<KeyboardLanguage> = listOf(KeyboardLanguage.PT_BR, KeyboardLanguage.EN),
    val language: KeyboardLanguage = KeyboardLanguage.PT_BR,

    // Barra
    val toolbar: List<ToolbarItem> = listOf(
        ToolbarItem.EMOJI, ToolbarItem.CLIPBOARD, ToolbarItem.EDIT, ToolbarItem.VOICE, ToolbarItem.ONE_HAND, ToolbarItem.THEMES, ToolbarItem.SETTINGS,
    ),

    // Voz e privacidade
    val voiceOfflineOnly: Boolean = true,
    val personalLearning: Boolean = true,
    val clipboardHistory: Boolean = true,
    val onboardingDone: Boolean = false,
) {
    val allThemes: List<KeyboardTheme> get() = Themes.builtIn + customThemes

    val theme: KeyboardTheme get() = allThemes.firstOrNull { it.id == themeId } ?: Themes.liquid

    val keyHeightDp: Float get() = sizeMode.keyHeight * heightScale.coerceIn(0.8f, 1.3f)
}
