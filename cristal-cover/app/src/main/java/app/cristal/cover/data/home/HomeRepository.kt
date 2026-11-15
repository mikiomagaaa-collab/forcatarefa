package app.cristal.cover.data.home

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.provider.Settings as SystemSettings
import app.cristal.cover.core.IoDispatcher
import app.cristal.cover.core.safely
import app.cristal.cover.data.apps.AppInfo
import app.cristal.cover.data.db.Container
import app.cristal.cover.data.db.HomeDao
import app.cristal.cover.data.db.HomeItemEntity
import app.cristal.cover.data.db.ItemType
import app.cristal.cover.data.prefs.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** O que aconteceu ao soltar um item arrastado. */
enum class DropResult { MOVED, FOLDER_CREATED, ADDED_TO_FOLDER, SWAPPED, REJECTED }

/**
 * Guarda e edita a tela inicial. Todas as operações são serializadas por um mutex, para que dois
 * gestos rápidos nunca deixem dois itens na mesma célula.
 */
@Singleton
class HomeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: HomeDao,
    private val settingsRepository: SettingsRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val mutex = Mutex()

    val items: Flow<List<HomeItemEntity>> = dao.observeAll()

    private val cols: Int get() = settingsRepository.settings.value.gridColumns
    private val rows: Int get() = settingsRepository.settings.value.gridRows
    private val dockCapacity: Int get() = settingsRepository.settings.value.dockCount

    private suspend fun <T> locked(block: suspend () -> T): T = withContext(io) { mutex.withLock { block() } }

    private suspend fun desktop(): List<HomeItemEntity> = dao.getByContainer(Container.DESKTOP)

    /** Cria a tela inicial padrão na primeira execução. Não faz nada se já houver itens. */
    suspend fun ensureDefaultLayout(apps: List<AppInfo>) = locked {
        if (dao.count() > 0 || apps.isEmpty()) return@locked
        val settings = settingsRepository.current()
        val c = settings.gridColumns
        val r = settings.gridRows
        val picks = DefaultApps.resolve(context, apps)
        val items = ArrayList<HomeItemEntity>()

        // Dock: telefone, mensagens, navegador, câmera (ou o que houver).
        val dockApps = picks.dock.take(settings.dockCount)
        dockApps.forEachIndexed { index, app ->
            items += HomeItemEntity(type = ItemType.APP, container = Container.DOCK, cellX = index, componentKey = app.key)
        }

        // Página 1: relógio no topo e os favoritos logo abaixo.
        val clockSpanX = WidgetKind.CLOCK.spanX.coerceAtMost(c)
        val clockSpanY = WidgetKind.CLOCK.spanY.coerceAtMost(r - 1).coerceAtLeast(1)
        items += HomeItemEntity(
            type = ItemType.WIDGET,
            container = Container.DESKTOP,
            page = 0,
            cellX = 0,
            cellY = 0,
            spanX = clockSpanX,
            spanY = clockSpanY,
            widgetKind = WidgetKind.CLOCK.name,
        )
        val dockKeys = dockApps.map { it.key }.toSet()
        val favorites = picks.favorites.filter { it.key !in dockKeys }.take(c * (r - clockSpanY))
        var index = 0
        for (y in clockSpanY until r) {
            for (x in 0 until c) {
                val app = favorites.getOrNull(index++) ?: break
                items += HomeItemEntity(
                    type = ItemType.APP,
                    container = Container.DESKTOP,
                    page = 0,
                    cellX = x,
                    cellY = y,
                    componentKey = app.key,
                )
            }
        }
        dao.insertAll(items)
    }

    suspend fun addAppToDesktop(key: String, preferredPage: Int = 0): Boolean = locked {
        val slot = GridMath.firstFree(desktop(), cols, rows, startPage = preferredPage.coerceAtLeast(0))
        dao.insert(
            HomeItemEntity(
                type = ItemType.APP,
                container = Container.DESKTOP,
                page = slot.page,
                cellX = slot.x,
                cellY = slot.y,
                componentKey = key,
            ),
        )
        true
    }

    /** Adiciona ao dock. Devolve `false` se o dock estiver cheio. */
    suspend fun addAppToDock(key: String): Boolean = locked {
        val dock = dao.getByContainer(Container.DOCK)
        if (dock.size >= dockCapacity) return@locked false
        dao.insert(
            HomeItemEntity(type = ItemType.APP, container = Container.DOCK, cellX = dock.size, componentKey = key),
        )
        true
    }

    suspend fun addWidget(kind: WidgetKind, preferredPage: Int = 0): Boolean = locked {
        val sx = kind.spanX.coerceAtMost(cols)
        val sy = kind.spanY.coerceAtMost(rows)
        val slot = GridMath.firstFree(desktop(), cols, rows, sx, sy, startPage = preferredPage.coerceAtLeast(0))
        dao.insert(
            HomeItemEntity(
                type = ItemType.WIDGET,
                container = Container.DESKTOP,
                page = slot.page,
                cellX = slot.x,
                cellY = slot.y,
                spanX = sx,
                spanY = sy,
                widgetKind = kind.name,
            ),
        )
        true
    }

    /** Remove um item. Pastas levam os atalhos de dentro junto. */
    suspend fun remove(id: Long) = locked {
        val item = dao.getById(id) ?: return@locked
        if (item.type == ItemType.FOLDER) dao.deleteByContainer(item.id)
        dao.deleteById(id)
        if (item.container > 0) collapseFolderIfNeeded(item.container)
        if (item.container == Container.DOCK) renumber(Container.DOCK)
        normalizePages()
    }

    suspend fun renameFolder(id: Long, title: String) = locked {
        val folder = dao.getById(id) ?: return@locked
        if (folder.type == ItemType.FOLDER) dao.update(folder.copy(title = title.trim().take(40)))
    }

    /**
     * Solta o item [id] na célula indicada da área de trabalho.
     * - célula livre: move;
     * - célula com app: cria uma pasta com os dois (se o item solto também for um app);
     * - célula com pasta: entra na pasta;
     * - caso contrário, troca de lugar quando os tamanhos permitem.
     */
    suspend fun dropOnCell(id: Long, page: Int, x: Int, y: Int): DropResult = locked {
        val item = dao.getById(id) ?: return@locked DropResult.REJECTED
        val c = cols
        val r = rows
        val all = desktop()
        val sx = item.spanX.coerceAtMost(c)
        val sy = item.spanY.coerceAtMost(r)
        val tx = x.coerceIn(0, c - sx)
        val ty = y.coerceIn(0, r - sy)
        val target = GridMath.itemAt(all, page, x.coerceIn(0, c - 1), y.coerceIn(0, r - 1), ignoreId = id)

        if (target != null && item.type == ItemType.APP) {
            when (target.type) {
                ItemType.FOLDER -> {
                    val count = dao.getByContainer(target.id).size
                    moveInto(item, container = target.id, rank = count)
                    return@locked DropResult.ADDED_TO_FOLDER
                }

                ItemType.APP -> {
                    val folderId = dao.insert(
                        HomeItemEntity(
                            type = ItemType.FOLDER,
                            container = Container.DESKTOP,
                            page = target.page,
                            cellX = target.cellX,
                            cellY = target.cellY,
                            title = "",
                        ),
                    )
                    dao.update(target.copy(container = folderId, page = 0, cellX = 0, cellY = 0))
                    moveInto(item, container = folderId, rank = 1)
                    return@locked DropResult.FOLDER_CREATED
                }
            }
        }

        if (GridMath.canPlace(all, page, tx, ty, sx, sy, c, r, ignoreId = id)) {
            moveInto(item, Container.DESKTOP, page = page, x = tx, y = ty, spanX = sx, spanY = sy)
            return@locked DropResult.MOVED
        }

        // Troca simples: só entre itens do mesmo tamanho que já estão na área de trabalho.
        if (target != null && item.container == Container.DESKTOP &&
            target.spanX == item.spanX && target.spanY == item.spanY
        ) {
            dao.updateAll(
                listOf(
                    item.copy(page = target.page, cellX = target.cellX, cellY = target.cellY),
                    target.copy(page = item.page, cellX = item.cellX, cellY = item.cellY),
                ),
            )
            return@locked DropResult.SWAPPED
        }
        DropResult.REJECTED
    }

    /** Solta o item no dock, na posição [index]. Só apps e pastas cabem no dock. */
    suspend fun dropOnDock(id: Long, index: Int): DropResult = locked {
        val item = dao.getById(id) ?: return@locked DropResult.REJECTED
        if (item.type == ItemType.WIDGET) return@locked DropResult.REJECTED
        val dock = dao.getByContainer(Container.DOCK).filter { it.id != id }.toMutableList()
        val fromDock = item.container == Container.DOCK
        if (!fromDock && dock.size >= dockCapacity) {
            // Dock cheio: solta sobre um app para criar pasta, ou recusa.
            val target = dock.getOrNull(index.coerceIn(0, dock.lastIndex.coerceAtLeast(0)))
            if (target != null && item.type == ItemType.APP && target.type == ItemType.FOLDER) {
                moveInto(item, container = target.id, rank = dao.getByContainer(target.id).size)
                return@locked DropResult.ADDED_TO_FOLDER
            }
            return@locked DropResult.REJECTED
        }
        val oldContainer = item.container
        dock.add(index.coerceIn(0, dock.size), item)
        dao.updateAll(
            dock.mapIndexed { i, e ->
                e.copy(container = Container.DOCK, page = 0, cellX = i, cellY = 0, spanX = 1, spanY = 1)
            },
        )
        if (oldContainer > 0) collapseFolderIfNeeded(oldContainer)
        normalizePages()
        DropResult.MOVED
    }

    /** Move um item do dock (ou de dentro de uma pasta) para a primeira célula livre da área de trabalho. */
    suspend fun moveToDesktop(id: Long, preferredPage: Int = 0) = locked {
        val item = dao.getById(id) ?: return@locked
        if (item.container == Container.DESKTOP) return@locked
        val slot = GridMath.firstFree(desktop(), cols, rows, startPage = preferredPage.coerceAtLeast(0))
        moveInto(item, Container.DESKTOP, page = slot.page, x = slot.x, y = slot.y)
    }

    /** Reorganiza tudo depois que o usuário muda o tamanho da grade. */
    suspend fun reflow(newCols: Int, newRows: Int) = locked {
        val all = desktop()
        if (all.isEmpty()) return@locked
        val before = all.associateBy { it.id }
        val changed = GridMath.reflow(all, newCols, newRows).filter { it != before[it.id] }
        if (changed.isNotEmpty()) dao.updateAll(changed)
    }

    /** Devolve para a área de trabalho o que não couber mais no dock. */
    suspend fun trimDock(capacity: Int) = locked {
        val dock = dao.getByContainer(Container.DOCK)
        if (dock.size <= capacity) return@locked
        for (extra in dock.drop(capacity)) {
            val slot = GridMath.firstFree(desktop(), cols, rows)
            dao.update(extra.copy(container = Container.DESKTOP, page = slot.page, cellX = slot.x, cellY = slot.y))
        }
    }

    suspend fun removePackage(packageName: String) = locked {
        dao.deleteByPackage(packageName)
        for (folder in dao.getAll().filter { it.type == ItemType.FOLDER }) collapseFolderIfNeeded(folder.id)
        renumber(Container.DOCK)
        normalizePages()
    }

    suspend fun exportAll(): List<HomeItemEntity> = withContext(io) { dao.getAll() }

    suspend fun importAll(items: List<HomeItemEntity>) = locked { dao.replaceAll(items) }

    // ---------------------------------------------------------------------------------------------

    private suspend fun moveInto(
        item: HomeItemEntity,
        container: Long,
        page: Int = 0,
        x: Int = 0,
        y: Int = 0,
        spanX: Int = item.spanX,
        spanY: Int = item.spanY,
        rank: Int = -1,
    ) {
        val oldContainer = item.container
        val updated = if (rank >= 0) {
            item.copy(container = container, page = 0, cellX = rank, cellY = 0, spanX = 1, spanY = 1)
        } else {
            item.copy(container = container, page = page, cellX = x, cellY = y, spanX = spanX, spanY = spanY)
        }
        dao.update(updated)
        if (oldContainer > 0 && oldContainer != container) collapseFolderIfNeeded(oldContainer)
        if (oldContainer == Container.DOCK && container != Container.DOCK) renumber(Container.DOCK)
        normalizePages()
    }

    /** Pasta com um item vira o próprio item; pasta vazia some. */
    private suspend fun collapseFolderIfNeeded(folderId: Long) {
        val folder = dao.getById(folderId) ?: return
        if (folder.type != ItemType.FOLDER) return
        val children = dao.getByContainer(folderId)
        when (children.size) {
            0 -> dao.deleteById(folderId)
            1 -> {
                dao.update(
                    children[0].copy(
                        container = folder.container,
                        page = folder.page,
                        cellX = folder.cellX,
                        cellY = folder.cellY,
                    ),
                )
                dao.deleteById(folderId)
            }

            else -> renumber(folderId)
        }
    }

    private suspend fun renumber(container: Long) {
        val list = dao.getByContainer(container).sortedBy { it.cellX }
        val fixed = list.mapIndexedNotNull { index, e -> if (e.cellX != index) e.copy(cellX = index) else null }
        if (fixed.isNotEmpty()) dao.updateAll(fixed)
    }

    /** Elimina páginas vazias no meio. */
    private suspend fun normalizePages() {
        val all = desktop()
        val compacted = GridMath.compactPages(all)
        if (compacted !== all) {
            val changed = compacted.filterIndexed { i, e -> e.page != all[i].page }
            if (changed.isNotEmpty()) dao.updateAll(changed)
        }
    }
}

/** Descobre os apps "essenciais" do aparelho para montar a primeira tela. */
internal object DefaultApps {
    class Picks(val dock: List<AppInfo>, val favorites: List<AppInfo>)

    fun resolve(context: Context, apps: List<AppInfo>): Picks {
        val pm = context.packageManager
        val byPackage = apps.groupBy { it.packageName }

        fun byIntent(intent: Intent): AppInfo? = safely("resolver app padrão") {
            val info = pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()))
            val pkg = info?.activityInfo?.packageName ?: return@safely null
            byPackage[pkg]?.firstOrNull()
        }

        fun byCategory(category: String): AppInfo? =
            byIntent(Intent(Intent.ACTION_MAIN).addCategory(category))

        fun byPackages(vararg names: String): AppInfo? =
            names.firstNotNullOfOrNull { byPackage[it]?.firstOrNull() }

        val phone = byIntent(Intent(Intent.ACTION_DIAL)) ?: byPackages("com.google.android.dialer", "com.android.dialer")
        val messages = byCategory(Intent.CATEGORY_APP_MESSAGING) ?: byPackages("com.google.android.apps.messaging")
        val browser = byCategory(Intent.CATEGORY_APP_BROWSER) ?: byPackages("com.android.chrome")
        val camera = byIntent(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
            ?: byIntent(Intent(MediaStore.ACTION_IMAGE_CAPTURE))
            ?: byPackages("com.motorola.camera3", "com.motorola.camera5", "com.google.android.GoogleCamera")

        val dock = listOfNotNull(phone, messages, browser, camera).distinctBy { it.key }

        val favorites = listOfNotNull(
            byPackages("com.whatsapp"),
            byCategory(Intent.CATEGORY_APP_GALLERY) ?: byPackages("com.google.android.apps.photos"),
            byCategory(Intent.CATEGORY_APP_MAPS) ?: byPackages("com.google.android.apps.maps"),
            byCategory(Intent.CATEGORY_APP_MUSIC) ?: byPackages("com.spotify.music", "com.google.android.apps.youtube.music"),
            byPackages("com.google.android.youtube"),
            byCategory(Intent.CATEGORY_APP_EMAIL) ?: byPackages("com.google.android.gm"),
            byCategory(Intent.CATEGORY_APP_CALENDAR) ?: byPackages("com.google.android.calendar"),
            byCategory(Intent.CATEGORY_APP_CALCULATOR) ?: byPackages("com.google.android.calculator"),
            byPackages("com.android.vending"),
            byIntent(Intent(SystemSettings.ACTION_SETTINGS)),
            byCategory(Intent.CATEGORY_APP_CONTACTS),
            byPackages("com.instagram.android", "org.telegram.messenger", "com.google.android.deskclock"),
        ).distinctBy { it.key }

        // Completa com os primeiros da lista se o aparelho tiver poucos dos apps acima.
        val filler = apps.filter { a -> favorites.none { it.key == a.key } && dock.none { it.key == a.key } }
        val dockFilled = (dock + filler).distinctBy { it.key }.take(6)
        return Picks(dock = dockFilled, favorites = (favorites + filler).distinctBy { it.key })
    }
}
