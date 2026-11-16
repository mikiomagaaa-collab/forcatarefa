package app.cristal.cover.data.home

import androidx.compose.runtime.Immutable
import app.cristal.cover.data.apps.AppInfo
import app.cristal.cover.data.db.Container

/**
 * Cada tela tem a sua própria tela inicial. Os itens são separados pelo contêiner, sem mudar o
 * formato do banco: os contêineres que já existiam ficam com a tela interna.
 */
enum class HomeProfile(val desktop: Long, val dock: Long) {
    INNER(Container.DESKTOP, Container.DOCK),
    COVER(Container.DESKTOP_COVER, Container.DOCK_COVER);

    companion object {
        fun of(onCover: Boolean): HomeProfile = if (onCover) COVER else INNER
        fun isDock(container: Long): Boolean = entries.any { it.dock == container }
    }
}

/** Widgets embutidos, com o tamanho padrão em células (colunas × linhas). */
enum class WidgetKind(val spanX: Int, val spanY: Int) {
    CLOCK(4, 2),
    WEATHER(2, 2),
    MUSIC(4, 2),
    BATTERY(2, 2),
    AGENDA(4, 2),
    ANALOG_CLOCK(2, 2),
    DATE(2, 2),
    SHORTCUTS(4, 1),
    SEARCH(4, 1),

    /** Fotos escolhidas pela pessoa (uma ou várias, em sequência). */
    PHOTO(2, 2),

    /** A L.I.L.Y: perguntar por texto ou por voz. */
    LILY(4, 1),

    /** Widget de outro app; o id no host fica guardado no item. */
    APP_WIDGET(4, 2);

    val builtIn: Boolean get() = this != APP_WIDGET


    companion object {
        fun fromName(name: String?): WidgetKind? = entries.firstOrNull { it.name == name }
    }
}

/** Item já resolvido para a UI (com o app correspondente encontrado). */
@Immutable
sealed interface HomeItem {
    val id: Long
    val page: Int
    val cellX: Int
    val cellY: Int
    val spanX: Int
    val spanY: Int

    @Immutable
    data class App(
        override val id: Long,
        override val page: Int,
        override val cellX: Int,
        override val cellY: Int,
        val app: AppInfo,
        val label: String,
    ) : HomeItem {
        override val spanX: Int get() = 1
        override val spanY: Int get() = 1
    }

    @Immutable
    data class Folder(
        override val id: Long,
        override val page: Int,
        override val cellX: Int,
        override val cellY: Int,
        val title: String,
        val children: List<App>,
    ) : HomeItem {
        override val spanX: Int get() = 1
        override val spanY: Int get() = 1
    }

    @Immutable
    data class Widget(
        override val id: Long,
        override val page: Int,
        override val cellX: Int,
        override val cellY: Int,
        override val spanX: Int,
        override val spanY: Int,
        val kind: WidgetKind,
        /** Id no AppWidgetHost; só vale para [WidgetKind.APP_WIDGET]. */
        val appWidgetId: Int = -1,
        /** Dados próprios do widget (no de foto, os arquivos das fotos). */
        val extra: String? = null,
    ) : HomeItem
}

/** Tela inicial pronta para desenhar. */
@Immutable
data class HomeLayout(
    val pages: List<List<HomeItem>> = listOf(emptyList()),
    val dock: List<HomeItem> = emptyList(),
) {
    val pageCount: Int get() = pages.size

    fun findFolder(id: Long): HomeItem.Folder? =
        (pages.asSequence().flatten() + dock.asSequence()).filterIsInstance<HomeItem.Folder>().firstOrNull { it.id == id }
}
