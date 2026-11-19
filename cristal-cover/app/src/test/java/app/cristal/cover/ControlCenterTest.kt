package app.cristal.cover

import app.cristal.cover.data.control.ControlGrid
import app.cristal.cover.data.control.ControlKind
import app.cristal.cover.data.control.ControlLayout
import app.cristal.cover.data.control.ControlModule
import app.cristal.cover.data.control.ControlPreset
import app.cristal.cover.data.control.ModuleSize
import app.cristal.cover.ui.launcher.addModule
import app.cristal.cover.ui.launcher.moveModule
import app.cristal.cover.ui.launcher.moveModuleToNextPage
import app.cristal.cover.ui.launcher.removeModule
import app.cristal.cover.ui.launcher.resizeModule
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlCenterTest {

    private fun m(id: Long, kind: ControlKind, size: ModuleSize = kind.defaultSize) = ControlModule(id, kind, size)

    @Test
    fun `grade encaixa como no iOS`() {
        // Conexões 2x2, música 2x2, depois brilho e volume altos e dois botões.
        val modules = listOf(
            m(1, ControlKind.CONNECTIVITY), m(2, ControlKind.MEDIA),
            m(3, ControlKind.BRIGHTNESS), m(4, ControlKind.VOLUME), m(5, ControlKind.TORCH), m(6, ControlKind.RINGER),
            m(7, ControlKind.DND), m(8, ControlKind.ROTATION),
        )
        val (placed, rows) = ControlGrid.pack(modules)
        val pos = placed.associate { it.module.id to (it.x to it.y) }
        assertEquals(0 to 0, pos[1])
        assertEquals(2 to 0, pos[2])
        assertEquals(0 to 2, pos[3])
        assertEquals(1 to 2, pos[4])
        // Os botões pequenos preenchem o buraco ao lado dos controles altos.
        assertEquals(2 to 2, pos[5])
        assertEquals(3 to 2, pos[6])
        assertEquals(2 to 3, pos[7])
        assertEquals(3 to 3, pos[8])
        assertEquals(4, rows)
    }

    @Test
    fun `faixa ocupa a linha inteira e nada se sobrepoe`() {
        val modules = listOf(m(1, ControlKind.WIFI), m(2, ControlKind.BRIGHTNESS, ModuleSize.BAR), m(3, ControlKind.TORCH))
        val (placed, rows) = ControlGrid.pack(modules)
        val pos = placed.associate { it.module.id to (it.x to it.y) }
        assertEquals(0 to 1, pos[2])
        assertEquals(1 to 0, pos[3])
        assertEquals(2, rows)
        for (preset in ControlPreset.entries) {
            for (cover in listOf(true, false)) {
                for (page in preset.build(cover).pages) assertNoOverlap(page)
            }
        }
    }

    private fun assertNoOverlap(page: List<ControlModule>) {
        val (placed, _) = ControlGrid.pack(page)
        val cells = HashSet<Pair<Int, Int>>()
        for (p in placed) {
            assertTrue("${p.module.kind} sai da grade", p.x + p.module.size.w <= ControlGrid.COLUMNS)
            for (dx in 0 until p.module.size.w) for (dy in 0 until p.module.size.h) {
                assertTrue("sobreposição em ${p.module.kind}", cells.add((p.x + dx) to (p.y + dy)))
            }
        }
    }

    @Test
    fun `edicao move redimensiona e remove`() {
        var layout = ControlLayout(listOf(listOf(m(1, ControlKind.WIFI), m(2, ControlKind.TORCH), m(3, ControlKind.BRIGHTNESS))))
        layout = layout.moveModule(3, -1)
        assertEquals(listOf(1L, 3L, 2L), layout.pages[0].map { it.id })
        layout = layout.resizeModule(3)
        assertEquals(ModuleSize.BAR, layout.pages[0].first { it.id == 3L }.size)
        layout = layout.resizeModule(3).resizeModule(3)
        assertEquals(ModuleSize.TALL, layout.pages[0].first { it.id == 3L }.size)
        layout = layout.moveModuleToNextPage(1)
        assertEquals(2, layout.pages.size)
        assertEquals(listOf(1L), layout.pages[1].map { it.id })
        layout = layout.addModule(ControlKind.CAMERA, page = 1)
        assertEquals(ControlKind.CAMERA, layout.pages[1].last().kind)
        assertEquals(4L, layout.pages[1].last().id)
        layout = layout.removeModule(2)
        assertFalse(layout.pages.flatten().any { it.id == 2L })
    }

    @Test
    fun `limpeza tira paginas vazias e tamanhos invalidos`() {
        val raw = ControlLayout(listOf(emptyList(), listOf(m(1, ControlKind.WIFI, ModuleSize.PANEL)), emptyList()))
        val clean = raw.cleaned()
        assertEquals(1, clean.pages.size)
        assertEquals(ModuleSize.SMALL, clean.pages[0][0].size)
        assertEquals(1, ControlLayout().cleaned().pages.size)
    }

    @Test
    fun `layout sobrevive ao json`() {
        val json = Json { ignoreUnknownKeys = true }
        val layout = ControlPreset.ONE_UI.build(onCover = false).copy(notificationsPage = false)
        val back = json.decodeFromString(ControlLayout.serializer(), json.encodeToString(ControlLayout.serializer(), layout))
        assertEquals(layout, back)
    }
}
