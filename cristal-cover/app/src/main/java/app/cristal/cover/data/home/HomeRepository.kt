package app.cristal.cover.data.home

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.provider.Settings as SystemSettings
import app.cristal.cover.core.IoDispatcher
import app.cristal.cover.core.safely
import app.cristal.cover.data.apps.AppInfo
import app.cristal.cover.data.db.HomeDao
import app.cristal.cover.data.db.HomeItemEntity
import app.cristal.cover.data.db.ItemType
import app.cristal.cover.data.prefs.PrefKeys
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
 * Guarda e edita as telas iniciais. A tela interna e a externa têm layouts independentes
 * ([HomeProfile]): cada uma com a sua área de trabalho, o seu dock e a sua grade.
 *
 * Todas as operações são serializadas por um mutex, para que dois gestos rápidos nunca deixem dois
 * itens na mesma célula.
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

    private fun cols(profile: HomeProfile): Int = settingsRepository.settings.value.columns(profile == HomeProfile.COVER)
    private fun rows(profile: HomeProfile): Int = settingsRepository.settings.value.rows(profile == HomeProfile.COVER)
    private val dockCapacity: Int get() = settingsRepository.settings.value.dockCount

    private suspend fun <T> locked(block: suspend () -> T): T = withContext(io) { mutex.withLock { block() } }

    private suspend fun desktop(profile: HomeProfile): List<HomeItemEntity> = dao.getByContainer(profile.desktop)

    /**
     * Cria o layout inicial de uma tela, uma única vez. Quem apagou tudo de propósito não recebe os
     * itens de volta: o que vale é o marcador gravado depois da primeira criação.
     */
    suspend fun ensureDefaultLayout(apps: List<AppInfo>, profile: HomeProfile) = locked {
        if (apps.isEmpty()) return@locked
        val seededKey = if (profile == HomeProfile.COVER) PrefKeys.HOME_SEEDED_COVER else PrefKeys.HOME_SEEDED_INNER
        if (settingsRepository.get(seededKey) == true) return@locked
        val empty = dao.getByContainer(profile.desktop).isEmpty() && dao.getByContainer(profile.dock).isEmpty()
        if (empty) dao.insertAll(buildDefault(apps, profile))
        settingsRepository.set(seededKey, true)
    }

    private suspend fun buildDefault(apps: List<AppInfo>, profile: HomeProfile): List<HomeItemEntity> {
        val settings = settingsRepository.current()
        val cover = profile == HomeProfile.COVER
        val c = settings.columns(cover)
        val r = settings.rows(cover)
        val picks = DefaultApps.resolve(context, apps)
        val items = ArrayList<HomeItemEntity>()

        fun widget(kind: WidgetKind, x: Int, y: Int, spanX: Int, spanY: Int) = HomeItemEntity(
            type = ItemType.WIDGET, container = profile.desktop, page = 0, cellX = x, cellY = y,
            spanX = spanX, spanY = spanY, widgetKind = kind.name,
        )

        // Dock: telefone, mensagens, navegador, câmera (ou o que houver).
        val dockApps = picks.dock.take(settings.dockCount)
        dockApps.forEachIndexed { index, app ->
            items += HomeItemEntity(type = ItemType.APP, container = profile.dock, cellX = index, componentKey = app.key)
        }

        // Relógio no topo. Na tela interna, que é alta, cabem também clima e data logo abaixo.
        val clockSpanY = WidgetKind.CLOCK.spanY.coerceAtMost(r - 1).coerceAtLeast(1)
        items += widget(WidgetKind.CLOCK, 0, 0, WidgetKind.CLOCK.spanX.coerceAtMost(c), clockSpanY)
        var firstAppRow = clockSpanY
        if (!cover && c >= 4 && r >= 6) {
            items += widget(WidgetKind.WEATHER, 0, clockSpanY, 2, 2)
            items += widget(WidgetKind.DATE, 2, clockSpanY, 2, 2)
            firstAppRow = clockSpanY + 2
        }

        val dockKeys = dockApps.map { it.key }.toSet()
        val favorites = picks.favorites.filter { it.key !in dockKeys }
        var index = 0
        for (y in firstAppRow until r) {
            for (x in 0 until c) {
                val app = favorites.getOrNull(index++) ?: break
                items += HomeItemEntity(
                    type = ItemType.APP, container = profile.desktop, page = 0, cellX = x, cellY = y, componentKey = app.key,
                )
            }
        }
        return items
    }

    suspend fun addAppToDesktop(key: String, preferredPage: Int, profile: HomeProfile): Boolean = locked {
        val slot = GridMath.firstFree(desktop(profile), cols(profile), rows(profile), startPage = preferredPage.coerceAtLeast(0))
        dao.insert(
            HomeItemEntity(
                type = ItemType.APP, container = profile.desktop, page = slot.page, cellX = slot.x, cellY = slot.y,
                componentKey = key,
            ),
        )
        true
    }

    /** Adiciona ao dock. Devolve `false` se o dock estiver cheio. */
    suspend fun addAppToDock(key: String, profile: HomeProfile): Boolean = locked {
        val dock = dao.getByContainer(profile.dock)
        if (dock.size >= dockCapacity) return@locked false
        dao.insert(HomeItemEntity(type = ItemType.APP, container = profile.dock, cellX = dock.size, componentKey = key))
        true
    }

    suspend fun addWidget(
        kind: WidgetKind,
        preferredPage: Int,
        profile: HomeProfile,
        appWidgetId: Int = -1,
        spanX: Int = kind.spanX,
        spanY: Int = kind.spanY,
        extra: String? = null,
    ): Boolean = locked {
        val c = cols(profile)
        val r = rows(profile)
        val sx = spanX.coerceIn(1, c)
        val sy = spanY.coerceIn(1, r)
        val slot = GridMath.firstFree(desktop(profile), c, r, sx, sy, startPage = preferredPage.coerceAtLeast(0))
        dao.insert(
            HomeItemEntity(
                type = ItemType.WIDGET,
                container = profile.desktop,
                page = slot.page,
                cellX = slot.x,
                cellY = slot.y,
                spanX = sx,
                spanY = sy,
                widgetKind = kind.name,
                // Para widgets de outros apps, o título guarda o id no host (evita mudar o banco).
                title = extra ?: if (appWidgetId >= 0) appWidgetId.toString() else null,
            ),
        )
        true
    }

    /** Troca os dados próprios de um widget (como as fotos do widget de foto). */
    suspend fun setExtra(id: Long, extra: String) = locked {
        val item = dao.getById(id) ?: return@locked
        if (item.type == ItemType.WIDGET) dao.update(item.copy(title = extra))
    }

    /**
     * Novo tamanho para um widget, mantendo o canto de cima à esquerda. Recusa se sair da grade
     * ou se encostar em outro item da página.
     */
    suspend fun resize(id: Long, spanX: Int, spanY: Int, profile: HomeProfile): Boolean = locked {
        val item = dao.getById(id) ?: return@locked false
        if (item.type != ItemType.WIDGET || item.container != profile.desktop) return@locked false
        val c = cols(profile)
        val r = rows(profile)
        if (spanX < 1 || spanY < 1 || item.cellX + spanX > c || item.cellY + spanY > r) return@locked false
        if (!GridMath.canPlace(desktop(profile), item.page, item.cellX, item.cellY, spanX, spanY, c, r, ignoreId = id)) return@locked false
        dao.update(item.copy(spanX = spanX, spanY = spanY))
        true
    }

    /** Remove um item. Pastas levam os atalhos de dentro junto. */
    suspend fun remove(id: Long) = locked {
        val item = dao.getById(id) ?: return@locked
        if (item.type == ItemType.FOLDER) dao.deleteByContainer(item.id)
        dao.deleteById(id)
        if (item.container > 0) collapseFolderIfNeeded(item.container)
        if (HomeProfile.isDock(item.container)) renumber(item.container)
        normalizePages()
    }

    suspend fun renameFolder(id: Long, title: String) = locked {
        val folder = dao.getById(id) ?: return@locked
        if (folder.type == ItemType.FOLDER) dao.update(folder.copy(title = title.trim().take(40)))
    }

    /**
     * Solta o item [id] na célula indicada da área de trabalho de [profile].
     * - célula livre: move;
     * - célula com app: cria uma pasta com os dois (se o item solto também for um app);
     * - célula com pasta: entra na pasta;
     * - caso contrário, troca de lugar quando os tamanhos permitem.
     */
    suspend fun dropOnCell(id: Long, page: Int, x: Int, y: Int, profile: HomeProfile): DropResult = locked {
        val item = dao.getById(id) ?: return@locked DropResult.REJECTED
        val c = cols(profile)
        val r = rows(profile)
        val all = desktop(profile)
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
                            type = ItemType.FOLDER, container = profile.desktop, page = target.page,
                            cellX = target.cellX, cellY = target.cellY, title = "",
                        ),
                    )
                    dao.update(target.copy(container = folderId, page = 0, cellX = 0, cellY = 0))
                    moveInto(item, container = folderId, rank = 1)
                    return@locked DropResult.FOLDER_CREATED
                }
            }
        }

        if (GridMath.canPlace(all, page, tx, ty, sx, sy, c, r, ignoreId = id)) {
            moveInto(item, profile.desktop, page = page, x = tx, y = ty, spanX = sx, spanY = sy)
            return@locked DropResult.MOVED
        }

        // Troca simples: só entre itens do mesmo tamanho que já estão nesta área de trabalho.
        if (target != null && item.container == profile.desktop &&
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

    /** Solta o item no dock de [profile], na posição [index]. Só apps e pastas cabem no dock. */
    suspend fun dropOnDock(id: Long, index: Int, profile: HomeProfile): DropResult = locked {
        val item = dao.getById(id) ?: return@locked DropResult.REJECTED
        if (item.type == ItemType.WIDGET) return@locked DropResult.REJECTED
        val dock = dao.getByContainer(profile.dock).filter { it.id != id }.toMutableList()
        val fromDock = item.container == profile.dock
        if (!fromDock && dock.size >= dockCapacity) {
            // Dock cheio: solta sobre uma pasta para entrar nela, ou recusa.
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
                e.copy(container = profile.dock, page = 0, cellX = i, cellY = 0, spanX = 1, spanY = 1)
            },
        )
        if (oldContainer > 0) collapseFolderIfNeeded(oldContainer)
        normalizePages()
        DropResult.MOVED
    }

    /** Move um item do dock (ou de dentro de uma pasta) para a primeira célula livre da área de trabalho. */
    suspend fun moveToDesktop(id: Long, preferredPage: Int, profile: HomeProfile) = locked {
        val item = dao.getById(id) ?: return@locked
        if (item.container == profile.desktop) return@locked
        val slot = GridMath.firstFree(desktop(profile), cols(profile), rows(profile), startPage = preferredPage.coerceAtLeast(0))
        moveInto(item, profile.desktop, page = slot.page, x = slot.x, y = slot.y)
    }

    /** Ajusta os itens de uma tela depois que a grade dela muda. Não faz nada se tudo já cabe. */
    suspend fun reflow(newCols: Int, newRows: Int, profile: HomeProfile) = locked {
        val all = desktop(profile)
        if (all.isEmpty()) return@locked
        val before = all.associateBy { it.id }
        val changed = GridMath.reflow(all, newCols, newRows).filter { it != before[it.id] }
        if (changed.isNotEmpty()) dao.updateAll(changed)
    }

    /** Devolve para a área de trabalho o que não couber mais no dock. */
    suspend fun trimDock(capacity: Int, profile: HomeProfile) = locked {
        val dock = dao.getByContainer(profile.dock)
        if (dock.size <= capacity) return@locked
        for (extra in dock.drop(capacity)) {
            val slot = GridMath.firstFree(desktop(profile), cols(profile), rows(profile))
            dao.update(extra.copy(container = profile.desktop, page = slot.page, cellX = slot.x, cellY = slot.y))
        }
    }

    suspend fun removePackage(packageName: String) = locked {
        dao.deleteByPackage(packageName)
        for (folder in dao.getAll().filter { it.type == ItemType.FOLDER }) collapseFolderIfNeeded(folder.id)
        for (profile in HomeProfile.entries) renumber(profile.dock)
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
        if (HomeProfile.isDock(oldContainer) && container != oldContainer) renumber(oldContainer)
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
                    children[0].copy(container = folder.container, page = folder.page, cellX = folder.cellX, cellY = folder.cellY),
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

    /** Elimina páginas vazias no meio, em cada tela. */
    private suspend fun normalizePages() {
        for (profile in HomeProfile.entries) {
            val all = desktop(profile)
            val compacted = GridMath.compactPages(all)
            if (compacted !== all) {
                val changed = compacted.filterIndexed { i, e -> e.page != all[i].page }
                if (changed.isNotEmpty()) dao.updateAll(changed)
            }
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

        fun byCategory(category: String): AppInfo? = byIntent(Intent(Intent.ACTION_MAIN).addCategory(category))

        fun byPackages(vararg names: String): AppInfo? = names.firstNotNullOfOrNull { byPackage[it]?.firstOrNull() }

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
