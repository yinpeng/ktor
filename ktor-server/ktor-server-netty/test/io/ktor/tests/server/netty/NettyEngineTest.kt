package io.ktor.tests.server.netty

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.testing.*
import kotlinx.coroutines.experimental.*
import org.junit.Test
import java.util.concurrent.*
import kotlin.test.*

class NettyEngineTest : EngineTestSuite<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {
    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.shareWorkGroup = true
    }

    @Test
    fun name() {
        val server = embeddedServer(Netty, port = 0, module = {})
        runBlocking {
            val port = server.startAndGetBindings().first().port
            println(port)
            assertNotEquals(0, port)
            server.stop(1L, 1L, TimeUnit.SECONDS)
        }
    }
}