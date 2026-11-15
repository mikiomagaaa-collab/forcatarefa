package app.cristal.cover.data.home

import androidx.compose.runtime.Immutable
import app.cristal.cover.data.apps.AppInfo

/** Widgets embutidos, com o tamanho padrão em células (colunas × linhas). */
enum class WidgetKind(val spanX: Int, val spanY: Int) {
    CLOCK(4, 2),
    WEATHER(2, 2),
    MUSIC(4, 2),
    BATTERY(2, 2),
    AGENDA(4, 2);

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
