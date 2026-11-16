package app.lily.ai

import app.lily.ai.models.Catalog
import app.lily.ai.models.Category
import app.lily.ai.models.Compat
import app.lily.ai.models.Compatibility
import app.lily.ai.models.ModelStore
import app.lily.ai.models.Runtime
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
    @Test
    fun `fichas do catalogo sao completas e fixadas numa versao`() {
        assertEquals(Catalog.all.size, Catalog.all.map { it.id }.toSet().size)
        for (m in Catalog.downloadable) {
            assertTrue(m.id, m.revision!!.matches(Regex("[0-9a-f]{40}")))
            assertTrue(m.id, m.files.isNotEmpty())
            for (f in m.files) {
                assertTrue("${m.id}/${f.path}", f.bytes > 0)
                assertTrue("${m.id}/${f.path}", f.sha256!!.matches(Regex("[0-9a-f]{64}")))
                assertTrue(m.url(f).startsWith("https://huggingface.co/${m.repo}/resolve/${m.revision}/"))
            }
            if (m.runtime == Runtime.LITERT_LM) assertTrue(m.id, (m.contextTokens ?: 0) >= 2048)
        }
    }

    @Test
    fun `tamanhos publicados batem`() {
        assertEquals(343_490_560L, Catalog.qwen3Small.totalBytes)
        assertEquals(2_588_147_712L, Catalog.gemma4.totalBytes)
        assertEquals(24, Catalog.iris.files.size)
        assertEquals(11_308_505_836L, Catalog.iris.totalBytes)
    }

    @Test
    fun `compatibilidade usa o aparelho de verdade`() {
        assertEquals(Compat.COMPATIBLE, Compatibility.evaluate(Catalog.qwen3Small, razr()).level)
        assertEquals(Compat.COMPATIBLE, Compatibility.evaluate(Catalog.qwen3, razr()).level)
        assertEquals(Compat.COMPATIBLE, Compatibility.evaluate(Catalog.gemma4, razr()).level)
        // Um aparelho de 6 GB (o Android informa ~5,5 GB): roda, com a memória justa.
        assertEquals(Compat.LIMITED, Compatibility.evaluate(Catalog.gemma4, razr(totalGb = 5.5)).level)
        // No Razr de 8 GB, o Phi-4 mini (3,9 GB) roda com outros apps fechados.
        assertEquals(Compat.LIMITED, Compatibility.evaluate(Catalog.phi4, razr()).level)
        assertEquals(Compat.EXPERIMENTAL, Compatibility.evaluate(Catalog.iris, razr()).level)
        assertEquals(Compat.INCOMPATIBLE, Compatibility.evaluate(Catalog.iris, razr(gpu = false)).level)
        assertEquals(Compat.INCOMPATIBLE, Compatibility.evaluate(Catalog.qwen3, razr(arm64 = false)).level)
        val small = Compatibility.evaluate(Catalog.phi4, razr(totalGb = 4.0))
        assertEquals(Compat.INCOMPATIBLE, small.level)
        assertTrue(small.reasons.first().contains("RAM"))
    }

    @Test
    fun `espaco que falta conta a folga de 1 GB`() {
        val gb = 1024L * 1024 * 1024
        assertEquals(0L, Compatibility.missingStorage(Catalog.qwen3Small, razr(freeGb = 10.0)))
        val missing = Compatibility.missingStorage(Catalog.gemma4, razr(freeGb = 2.0))
        assertEquals(Catalog.gemma4.totalBytes + gb - 2 * gb, missing)
    }

    private fun fill(store: ModelStore, spec: app.lily.ai.models.ModelSpec) {
        for (f in spec.files) {
            val file = store.file(spec, f)
            file.parentFile!!.mkdirs()
            RandomAccessFile(file, "rw").use { it.setLength(f.bytes) }
        }
    }

    @Test
    fun `loja registra instalacao padroes historico e desinstalacao`() {
        val dir = Files.createTempDirectory("modelos").toFile()
        var now = 1_000L
        val store = ModelStore(dir) { now++ }
        val spec = Catalog.qwen3Small
        assertFalse(store.isInstalled(spec))
        fill(store, spec)
        assertFalse("sem registro não conta", store.isInstalled(spec))
        store.markInstalled(spec, update = false)
        assertTrue(store.isInstalled(spec))
        assertTrue(spec.id in store.installedIds())
        assertEquals(spec.id, store.defaults()[Category.CHAT])
        assertFalse(store.updateAvailable(spec))
        // Um segundo modelo de conversa não rouba o padrão escolhido.
        val other = Catalog.qwen3
        fill(store, other)
        store.markInstalled(other, update = false)
        assertEquals(spec.id, store.defaults()[Category.CHAT])
        store.setDefault(Category.CHAT, other)
        assertEquals(other.id, store.defaults()[Category.CHAT])
        // Arquivo cortado: deixa de contar como instalado.
        RandomAccessFile(store.file(spec, spec.files.first()), "rw").use { it.setLength(10) }
        assertFalse(store.isInstalled(spec))
        store.uninstall(spec)
        assertFalse(File(dir, spec.id).exists())
        assertEquals(listOf("Desinstalado", "Instalado", "Instalado"), store.state.value.history.map { it.action })
        // O estado sobrevive a reabrir a loja.
        val again = ModelStore(dir)
        assertTrue(other.id in again.installedIds())
        assertEquals(other.id, again.defaults()[Category.CHAT])
    }
}
