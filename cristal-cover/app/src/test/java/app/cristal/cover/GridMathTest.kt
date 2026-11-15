package app.cristal.cover

import app.cristal.cover.data.db.Container
import app.cristal.cover.data.db.HomeItemEntity
import app.cristal.cover.data.db.ItemType
import app.cristal.cover.data.home.CellSlot
import app.cristal.cover.data.home.GridMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GridMathTest {

    private var nextId = 1L

    private fun item(page: Int, x: Int, y: Int, spanX: Int = 1, spanY: Int = 1) = HomeItemEntity(
        id = nextId++,
        type = if (spanX > 1 || spanY > 1) ItemType.WIDGET else ItemType.APP,
        container = Container.DESKTOP,
        page = page,
        cellX = x,
        cellY = y,
        spanX = spanX,
        spanY = spanY,
    )

    @Test
    fun `primeira posicao livre em grade vazia e a origem`() {
        assertEquals(CellSlot(0, 0, 0), GridMath.firstFree(emptyList(), cols = 4, rows = 4))
    }

    @Test
    fun `pula celulas ocupadas inclusive por widgets`() {
        val items = listOf(item(0, 0, 0, spanX = 4, spanY = 2), item(0, 0, 2))
        assertEquals(CellSlot(0, 1, 2), GridMath.firstFree(items, 4, 4))
    }

    @Test
    fun `pagina cheia leva a uma pagina nova`() {
        val full = (0 until 16).map { item(0, it % 4, it / 4) }
        assertEquals(CellSlot(1, 0, 0), GridMath.firstFree(full, 4, 4))
    }

    @Test
    fun `widget so cabe onde ha espaco para o tamanho inteiro`() {
        val items = listOf(item(0, 1, 0), item(0, 2, 1))
        val slot = GridMath.firstFree(items, 4, 4, spanX = 4, spanY = 2)
        assertEquals(CellSlot(0, 0, 2), slot)
    }

    @Test
    fun `canPlace respeita limites e ignora o proprio item`() {
        val widget = item(0, 0, 0, spanX = 2, spanY = 2)
        val items = listOf(widget)
        assertFalse(GridMath.canPlace(items, 0, 1, 1, 1, 1, 4, 4))
        assertTrue(GridMath.canPlace(items, 0, 1, 1, 2, 2, 4, 4, ignoreId = widget.id))
        assertFalse(GridMath.canPlace(items, 0, 3, 3, 2, 2, 4, 4))
        assertFalse(GridMath.canPlace(items, 0, -1, 0, 1, 1, 4, 4))
    }

    @Test
    fun `itemAt encontra o widget por qualquer celula coberta`() {
        val widget = item(0, 1, 1, spanX = 2, spanY = 2)
        assertEquals(widget, GridMath.itemAt(listOf(widget), 0, 2, 2))
        assertNull(GridMath.itemAt(listOf(widget), 0, 0, 0))
        assertNull(GridMath.itemAt(listOf(widget), 1, 2, 2))
    }

    @Test
    fun `reflow com a mesma grade nao muda nada`() {
        val items = listOf(item(0, 0, 0, spanX = 4, spanY = 2), item(0, 3, 3), item(1, 2, 1))
        assertEquals(items.sortedBy { it.id }, GridMath.reflow(items, 4, 4).sortedBy { it.id })
    }

    @Test
    fun `reflow para grade menor realoca quem ficou de fora sem sobrepor`() {
        val items = listOf(item(0, 0, 0), item(0, 4, 0), item(0, 4, 4), item(0, 2, 2, spanX = 3, spanY = 1))
        val result = GridMath.reflow(items, cols = 3, rows = 3)
        assertEquals(items.size, result.size)
        for (e in result) {
            assertTrue("fora da grade: $e", e.cellX >= 0 && e.cellY >= 0 && e.cellX + e.spanX <= 3 && e.cellY + e.spanY <= 3)
        }
        // Nenhuma célula pode ser ocupada duas vezes.
        val cells = result.flatMap { e ->
            (e.cellX until e.cellX + e.spanX).flatMap { x -> (e.cellY until e.cellY + e.spanY).map { y -> Triple(e.page, x, y) } }
        }
        assertEquals(cells.size, cells.toSet().size)
        // Quem já cabia continua onde estava.
        assertEquals(0 to 0, result.first { it.id == items[0].id }.let { it.cellX to it.cellY })
    }

    @Test
    fun `compactPages remove paginas vazias no meio`() {
        val items = listOf(item(0, 0, 0), item(2, 1, 1), item(5, 0, 0))
        assertEquals(listOf(0, 1, 2), GridMath.compactPages(items).map { it.page })
    }
}
