package app.cristal.cover.data.home

import app.cristal.cover.data.db.HomeItemEntity

/** Célula de destino na área de trabalho. */
data class CellSlot(val page: Int, val x: Int, val y: Int)

/**
 * Aritmética da grade, sem nenhuma dependência do Android, para poder ser testada em JVM.
 * Todos os métodos recebem apenas os itens da área de trabalho (container DESKTOP).
 */
object GridMath {

    fun occupancy(items: List<HomeItemEntity>, page: Int, cols: Int, rows: Int, ignoreId: Long = -1): Array<BooleanArray> {
        val grid = Array(rows) { BooleanArray(cols) }
        for (item in items) {
            if (item.page != page || item.id == ignoreId) continue
            for (y in item.cellY until item.cellY + item.spanY) {
                for (x in item.cellX until item.cellX + item.spanX) {
                    if (y in 0 until rows && x in 0 until cols) grid[y][x] = true
                }
            }
        }
        return grid
    }

    fun fits(grid: Array<BooleanArray>, x: Int, y: Int, spanX: Int, spanY: Int): Boolean {
        val rows = grid.size
        val cols = if (rows == 0) 0 else grid[0].size
        if (x < 0 || y < 0 || x + spanX > cols || y + spanY > rows) return false
        for (yy in y until y + spanY) {
            for (xx in x until x + spanX) {
                if (grid[yy][xx]) return false
            }
        }
        return true
    }

    fun canPlace(
        items: List<HomeItemEntity>,
        page: Int,
        x: Int,
        y: Int,
        spanX: Int,
        spanY: Int,
        cols: Int,
        rows: Int,
        ignoreId: Long = -1,
    ): Boolean = fits(occupancy(items, page, cols, rows, ignoreId), x, y, spanX, spanY)

    /** Item que ocupa a célula (x, y) da página, se houver. */
    fun itemAt(items: List<HomeItemEntity>, page: Int, x: Int, y: Int, ignoreId: Long = -1): HomeItemEntity? =
        items.firstOrNull {
            it.page == page && it.id != ignoreId &&
                x >= it.cellX && x < it.cellX + it.spanX && y >= it.cellY && y < it.cellY + it.spanY
        }

    /**
     * Primeira posição livre para um item de [spanX]×[spanY], procurando a partir de [startPage].
     * Se nenhuma página existente tiver espaço, devolve a célula (0,0) de uma página nova.
     */
    fun firstFree(
        items: List<HomeItemEntity>,
        cols: Int,
        rows: Int,
        spanX: Int = 1,
        spanY: Int = 1,
        startPage: Int = 0,
    ): CellSlot {
        val sx = spanX.coerceIn(1, cols)
        val sy = spanY.coerceIn(1, rows)
        val lastPage = items.maxOfOrNull { it.page } ?: -1
        for (page in startPage.coerceAtLeast(0)..lastPage.coerceAtLeast(startPage)) {
            val grid = occupancy(items, page, cols, rows)
            for (y in 0..rows - sy) {
                for (x in 0..cols - sx) {
                    if (fits(grid, x, y, sx, sy)) return CellSlot(page, x, y)
                }
            }
        }
        return CellSlot((lastPage + 1).coerceAtLeast(startPage), 0, 0)
    }

    /**
     * Ajusta os itens a uma grade de outro tamanho. Quem ainda cabe onde está não se mexe; só os
     * itens que ficaram fora da grade (ou sobrepostos) vão para a primeira posição livre, a partir
     * da própria página. Itens maiores que a grade são encolhidos. Chamar de novo com a mesma grade
     * não muda nada.
     */
    fun reflow(items: List<HomeItemEntity>, cols: Int, rows: Int): List<HomeItemEntity> {
        val ordered = items.sortedWith(compareBy({ it.page }, { it.cellY }, { it.cellX }, { it.id }))
        val kept = ArrayList<HomeItemEntity>(ordered.size)
        val displaced = ArrayList<HomeItemEntity>()
        for (item in ordered) {
            val resized = item.copy(spanX = item.spanX.coerceIn(1, cols), spanY = item.spanY.coerceIn(1, rows))
            if (canPlace(kept, resized.page, resized.cellX, resized.cellY, resized.spanX, resized.spanY, cols, rows)) {
                kept += resized
            } else {
                displaced += resized
            }
        }
        for (item in displaced) {
            val slot = firstFree(kept, cols, rows, item.spanX, item.spanY, startPage = item.page.coerceAtLeast(0))
            kept += item.copy(page = slot.page, cellX = slot.x, cellY = slot.y)
        }
        return compactPages(kept)
    }

    /** Remove páginas vazias no meio, renumerando as seguintes. */
    fun compactPages(items: List<HomeItemEntity>): List<HomeItemEntity> {
        val used = items.map { it.page }.distinct().sorted()
        if (used.isEmpty() || used == used.indices.toList()) return items
        val remap = used.withIndex().associate { (index, page) -> page to index }
        return items.map { it.copy(page = remap.getValue(it.page)) }
    }
}
