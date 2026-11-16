package app.lily.keyboard

import android.app.Application
import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageInfo
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.lily.shared.bridge.AiBridge
import app.lily.shared.bridge.AiClient
import app.lily.shared.bridge.AiListener
import app.lily.shared.bridge.AiRequest
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A ponte do teclado com a L.I.L.Y AI quando ela não sobe: o teclado nunca fica esperando para
 * sempre; avisa uma vez e tenta ligar de novo no próximo pedido.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiClientTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val errors = mutableListOf<String>()
    private val listener = object : AiListener {
        override fun onChunk(text: String, model: String?) = Unit
        override fun onDone(text: String, model: String?, millis: Long) = Unit
        override fun onError(message: String, code: String) {
            errors += message
        }
    }

    @Before
    fun aiInstalled() {
        shadowOf(app.packageManager).installPackage(PackageInfo().apply { packageName = AiBridge.PACKAGE })
        shadowOf(app).grantPermissions(AiBridge.PERMISSION)
    }

    /** Um contexto em que a ligação com o serviço da IA é aceita e nunca chega (o processo dela não subiu). */
    private class Silent(base: Application) : ContextWrapper(base) {
        val connections = mutableListOf<ServiceConnection>()
        var unbinds = 0
        override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean {
            connections += conn
            return true
        }

        override fun unbindService(conn: ServiceConnection) {
            unbinds++
        }
    }

    @Test
    fun `sem resposta em 10 segundos, avisa uma vez e solta a ligação`() {
        val ctx = Silent(app)
        val client = AiClient(ctx)
        assertTrue(client.request(AiRequest("CORRECT", "vc viu?"), listener))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(9))
        assertTrue(errors.isEmpty())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(listOf("A L.I.L.Y AI não respondeu. Abra o app uma vez e tente de novo."), errors)
        assertEquals(1, ctx.unbinds)
        // O próximo pedido liga de novo.
        assertTrue(client.request(AiRequest("CORRECT", "de novo"), listener))
        assertEquals(2, ctx.connections.size)
    }

    @Test
    fun `o processo da IA morreu antes de responder`() {
        val ctx = Silent(app)
        AiClient(ctx).request(AiRequest("CORRECT", "oi"), listener)
        ctx.connections.single().onBindingDied(ComponentName(AiBridge.PACKAGE, AiBridge.SERVICE))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(12))
        assertEquals(listOf("A L.I.L.Y AI foi fechada pelo sistema. Tente de novo."), errors)
    }

    @Test
    fun `ligação recusada vira aviso, sem travar`() {
        val ctx = Silent(app)
        AiClient(ctx).request(AiRequest("CORRECT", "oi"), listener)
        ctx.connections.single().onNullBinding(ComponentName(AiBridge.PACKAGE, AiBridge.SERVICE))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(12))
        assertEquals(listOf("A L.I.L.Y AI recusou a ligação. Abra o app uma vez e tente de novo."), errors)
    }

    @Test
    fun `cancelar antes de ligar não gera aviso`() {
        val client = AiClient(Silent(app))
        client.request(AiRequest("CORRECT", "oi"), listener)
        client.cancel()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(12))
        assertTrue(errors.isEmpty())
    }
}
