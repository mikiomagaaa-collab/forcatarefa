package app.cristal.cover.data.control

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/** Tamanho de um módulo, em células da grade de 4 colunas. */
@Serializable
enum class ModuleSize(val w: Int, val h: Int, val label: String) {
    SMALL(1, 1, "Pequeno"),
    WIDE(2, 1, "Largo"),
    TALL(1, 2, "Alto"),
    LARGE(2, 2, "Grande"),
    BAR(4, 1, "Faixa"),
    PANEL(4, 2, "Painel"),
}

/** Grupos usados na lista "Adicionar controle". */
enum class ControlGroup(val label: String) {
    CONNECTIVITY("Conexões"),
    SYSTEM("Sistema"),
    SLIDERS("Brilho e som"),
    MEDIA("Mídia e status"),
    SHORTCUTS("Atalhos"),
}

/**
 * Tipos de módulo da Liquid Control Center.
 *
 * @param toggle tem estado ligado/desligado (os outros só executam uma ação).
 */
@Serializable
enum class ControlKind(
    val label: String,
    val group: ControlGroup,
    val sizes: List<ModuleSize>,
    val toggle: Boolean = false,
) {
    CONNECTIVITY("Conexões", ControlGroup.CONNECTIVITY, listOf(ModuleSize.LARGE, ModuleSize.BAR)),
    WIFI("Wi-Fi", ControlGroup.CONNECTIVITY, ToggleSizes, toggle = true),
    BLUETOOTH("Bluetooth", ControlGroup.CONNECTIVITY, ToggleSizes, toggle = true),
    MOBILE_DATA("Dados móveis", ControlGroup.CONNECTIVITY, ToggleSizes, toggle = true),
    AIRPLANE("Modo avião", ControlGroup.CONNECTIVITY, ToggleSizes, toggle = true),
    HOTSPOT("Ponto de acesso", ControlGroup.CONNECTIVITY, ToggleSizes),
    NFC("NFC", ControlGroup.CONNECTIVITY, ToggleSizes, toggle = true),
    LOCATION("Localização", ControlGroup.CONNECTIVITY, ToggleSizes, toggle = true),

    TORCH("Lanterna", ControlGroup.SYSTEM, ToggleSizes, toggle = true),
    RINGER("Som / vibrar", ControlGroup.SYSTEM, ToggleSizes, toggle = true),
    DND("Não perturbe", ControlGroup.SYSTEM, ToggleSizes, toggle = true),
    ROTATION("Rotação automática", ControlGroup.SYSTEM, ToggleSizes, toggle = true),
    DARK_MODE("Modo escuro", ControlGroup.SYSTEM, ToggleSizes, toggle = true),
    BATTERY_SAVER("Economia de bateria", ControlGroup.SYSTEM, ToggleSizes, toggle = true),
    SCREENSHOT("Captura de tela", ControlGroup.SYSTEM, ToggleSizes),
    LOCK("Bloquear tela", ControlGroup.SYSTEM, ToggleSizes),
    RECENTS("Recentes", ControlGroup.SYSTEM, ToggleSizes),
    SYSTEM_PANEL("Painel do sistema", ControlGroup.SYSTEM, ToggleSizes),

    BRIGHTNESS("Brilho", ControlGroup.SLIDERS, listOf(ModuleSize.TALL, ModuleSize.BAR, ModuleSize.WIDE)),
    VOLUME("Volume", ControlGroup.SLIDERS, listOf(ModuleSize.TALL, ModuleSize.BAR, ModuleSize.WIDE)),

    MEDIA("Tocando agora", ControlGroup.MEDIA, listOf(ModuleSize.LARGE, ModuleSize.PANEL, ModuleSize.BAR)),
    BATTERY("Bateria", ControlGroup.MEDIA, listOf(ModuleSize.SMALL, ModuleSize.WIDE, ModuleSize.LARGE)),

    TIMER("Timer", ControlGroup.SHORTCUTS, ToggleSizes),
    ALARM("Alarmes", ControlGroup.SHORTCUTS, ToggleSizes),
    CALCULATOR("Calculadora", ControlGroup.SHORTCUTS, ToggleSizes),
    CAMERA("Câmera", ControlGroup.SHORTCUTS, ToggleSizes),
    SEARCH("Buscar apps", ControlGroup.SHORTCUTS, ToggleSizes),
    WALLPAPER("Papel de parede", ControlGroup.SHORTCUTS, ToggleSizes),
    SETTINGS("Configurações do Cristal", ControlGroup.SHORTCUTS, ToggleSizes),
    LILY("L.I.L.Y", ControlGroup.SHORTCUTS, ToggleSizes),
    ;

    val defaultSize: ModuleSize get() = sizes.first()
}

private val ToggleSizes = listOf(ModuleSize.SMALL, ModuleSize.WIDE)

@Immutable
@Serializable
data class ControlModule(val id: Long, val kind: ControlKind, val size: ModuleSize = kind.defaultSize) {
    /** Garante um tamanho aceito pelo tipo (útil ao importar um backup antigo). */
    fun normalized(): ControlModule = if (size in kind.sizes) this else copy(size = kind.defaultSize)
}

@Immutable
@Serializable
data class ControlLayout(
    val pages: List<List<ControlModule>> = emptyList(),
    /** Mostra as notificações como a última página da central. */
    val notificationsPage: Boolean = true,
) {
    val nextId: Long get() = (pages.flatten().maxOfOrNull { it.id } ?: 0L) + 1

    /** Remove páginas vazias e tamanhos inválidos; sempre sobra ao menos uma página. */
    fun cleaned(): ControlLayout {
        val clean = pages.map { page -> page.map { it.normalized() } }.filter { it.isNotEmpty() }
        return copy(pages = clean.ifEmpty { listOf(emptyList()) })
    }
}

/** Posição calculada de um módulo na grade. */
@Immutable
data class ControlPlacement(val module: ControlModule, val x: Int, val y: Int)

object ControlGrid {
    const val COLUMNS = 4

    /**
     * Encaixa os módulos na grade, na ordem, cada um no primeiro espaço livre (como no iOS).
     * Devolve as posições e o número de linhas usadas.
     */
    fun pack(modules: List<ControlModule>, columns: Int = COLUMNS): Pair<List<ControlPlacement>, Int> {
        val taken = mutableListOf<BooleanArray>()
        fun free(x: Int, y: Int, w: Int, h: Int): Boolean {
            if (x + w > columns) return false
            for (yy in y until y + h) {
                while (taken.size <= yy) taken.add(BooleanArray(columns))
                for (xx in x until x + w) if (taken[yy][xx]) return false
            }
            return true
        }
        val result = ArrayList<ControlPlacement>(modules.size)
        for (module in modules) {
            val w = module.size.w.coerceAtMost(columns)
            val h = module.size.h
            var y = 0
            var placed = false
            while (!placed) {
                for (x in 0..columns - w) {
                    if (free(x, y, w, h)) {
                        for (yy in y until y + h) for (xx in x until x + w) taken[yy][xx] = true
                        result.add(ControlPlacement(module, x, y))
                        placed = true
                        break
                    }
                }
                if (!placed) y++
            }
        }
        val rows = taken.indexOfLast { row -> row.any { it } } + 1
        return result to rows
    }
}

/** Predefinições prontas. */
enum class ControlPreset(val label: String, val description: String) {
    LIQUID_FLIP("Liquid Flip", "Conexões e música no topo, brilho e volume ao lado, atalhos embaixo."),
    IOS("Estilo iOS", "Organização da Central de Controle do iPhone."),
    ONE_UI("Estilo One UI", "Fileiras de botões redondos e barras de brilho, como no Galaxy."),
    ;

    fun build(onCover: Boolean): ControlLayout {
        var id = 0L
        fun m(kind: ControlKind, size: ModuleSize = kind.defaultSize) = ControlModule(++id, kind, size)
        val pages: List<List<ControlModule>> = when (this) {
            LIQUID_FLIP -> if (onCover) {
                listOf(
                    listOf(
                        m(ControlKind.CONNECTIVITY), m(ControlKind.BRIGHTNESS), m(ControlKind.VOLUME),
                        m(ControlKind.TORCH), m(ControlKind.RINGER), m(ControlKind.DND), m(ControlKind.SCREENSHOT),
                    ),
                    listOf(
                        m(ControlKind.MEDIA, ModuleSize.PANEL),
                        m(ControlKind.ROTATION), m(ControlKind.LOCATION), m(ControlKind.BATTERY_SAVER), m(ControlKind.LOCK),
                    ),
                )
            } else {
                listOf(
                    listOf(
                        m(ControlKind.CONNECTIVITY), m(ControlKind.MEDIA),
                        m(ControlKind.BRIGHTNESS), m(ControlKind.VOLUME), m(ControlKind.TORCH), m(ControlKind.RINGER),
                        m(ControlKind.DND), m(ControlKind.ROTATION),
                        m(ControlKind.SCREENSHOT), m(ControlKind.TIMER), m(ControlKind.CALCULATOR), m(ControlKind.CAMERA),
                        m(ControlKind.BATTERY, ModuleSize.WIDE), m(ControlKind.LOCK), m(ControlKind.SETTINGS),
                    ),
                    listOf(
                        m(ControlKind.LOCATION, ModuleSize.WIDE), m(ControlKind.DARK_MODE, ModuleSize.WIDE),
                        m(ControlKind.BATTERY_SAVER, ModuleSize.WIDE), m(ControlKind.NFC, ModuleSize.WIDE),
                        m(ControlKind.HOTSPOT, ModuleSize.WIDE), m(ControlKind.RECENTS, ModuleSize.WIDE),
                        m(ControlKind.SEARCH), m(ControlKind.WALLPAPER), m(ControlKind.ALARM), m(ControlKind.SYSTEM_PANEL),
                    ),
                )
            }
            IOS -> listOf(
                listOf(
                    m(ControlKind.CONNECTIVITY), m(ControlKind.MEDIA),
                    m(ControlKind.ROTATION), m(ControlKind.RINGER), m(ControlKind.BRIGHTNESS), m(ControlKind.VOLUME),
                    m(ControlKind.DND, ModuleSize.WIDE),
                    m(ControlKind.TORCH), m(ControlKind.TIMER), m(ControlKind.CALCULATOR), m(ControlKind.CAMERA),
                ),
                listOf(
                    m(ControlKind.LOCATION), m(ControlKind.DARK_MODE), m(ControlKind.BATTERY_SAVER), m(ControlKind.SCREENSHOT),
                    m(ControlKind.BATTERY, ModuleSize.WIDE), m(ControlKind.LOCK), m(ControlKind.SETTINGS),
                ),
            )
            ONE_UI -> listOf(
                listOf(
                    m(ControlKind.WIFI), m(ControlKind.RINGER), m(ControlKind.BLUETOOTH), m(ControlKind.ROTATION),
                    m(ControlKind.AIRPLANE), m(ControlKind.TORCH), m(ControlKind.MOBILE_DATA), m(ControlKind.LOCATION),
                    m(ControlKind.BRIGHTNESS, ModuleSize.BAR),
                    m(ControlKind.MEDIA, ModuleSize.BAR),
                ),
                listOf(
                    m(ControlKind.DND), m(ControlKind.DARK_MODE), m(ControlKind.BATTERY_SAVER), m(ControlKind.NFC),
                    m(ControlKind.HOTSPOT), m(ControlKind.SCREENSHOT), m(ControlKind.LOCK), m(ControlKind.SETTINGS),
                    m(ControlKind.VOLUME, ModuleSize.BAR),
                ),
            )
        }
        return ControlLayout(pages = pages)
    }
}
