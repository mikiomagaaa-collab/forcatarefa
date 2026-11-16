package app.lily.ai

import app.lily.ai.ui.Nav
import app.lily.ai.ui.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NavTest {
    @Test
    fun `objeto guardado vive enquanto a tela esta na pilha`() {
        val nav = Nav()
        var disposed = 0
        nav.go(Route.Chat())
        val key = nav.current.key
        val a = nav.retain(key, { Any() }, { disposed++ })
        nav.update(key, Route.Chat(id = "c1"))
        assertEquals(key, nav.current.key)
        assertSame(a, nav.retain(key, { Any() }, { disposed++ }))
        nav.go(Route.Hub())
        assertTrue(nav.back())
        assertSame(a, nav.retain(key, { Any() }, { disposed++ }))
        assertEquals(0, disposed)
        assertTrue(nav.back())
        assertEquals(1, disposed)
        assertFalse(nav.back())
    }

    @Test
    fun `nova conversa troca o estado e a tela externa passa a conversa adiante`() {
        val nav = Nav()
        var disposed = 0
        nav.go(Route.Chat())
        val first = nav.current.key
        nav.retain(first, { Any() }, { disposed++ })
        nav.reset(Route.Chat())
        assertEquals(1, disposed)
        assertTrue(nav.current.key != first)
        val cover = Any()
        nav.retain(Nav.COVER_KEY, { cover })
        val moved = nav.release<Any>(Nav.COVER_KEY)
        assertSame(cover, moved)
        nav.goWith(Route.Chat(id = "x"), moved!!) {}
        assertSame(cover, nav.retain(nav.current.key, { Any() }))
    }
}
