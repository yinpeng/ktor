package io.ktor.server.jetty

import io.ktor.application.*
import io.ktor.server.engine.*
import kotlinx.coroutines.experimental.*
import org.eclipse.jetty.server.*
import java.net.*
import java.util.concurrent.*

/**
 * [ApplicationEngine] base type for running in a standalone Jetty
 */
open class JettyApplicationEngineBase(
    environment: ApplicationEngineEnvironment,
    configure: Configuration.() -> Unit
) : BaseApplicationEngine(environment) {

    class Configuration : BaseApplicationEngine.Configuration() {
        /**
         * Property function that will be called during Jetty server initialization
         * with the server instance as receiver.
         */
        var configureServer: Server.() -> Unit = {}
    }

    private val configuration = Configuration().apply(configure)

    protected val server: Server = Server().apply {
        configuration.configureServer(this)
        initializeServer(environment)
    }

    override fun start(wait: Boolean): JettyApplicationEngineBase {
        runBlocking {
            startAndGetBindings()
        }
        if (wait) {
            server.join()
            stop(1, 5, TimeUnit.SECONDS)
        }
        return this
    }

    override suspend fun startAndGetBindings(): List<EngineConnectionBinding> {
        environment.start()
        server.start()
        return environment.connectors.zip(server.connectors).map {
            EngineConnectionBinding(it.first, InetSocketAddress(it.first.host, (it.second as ServerConnector).localPort))
        }
    }

    override fun stop(gracePeriod: Long, timeout: Long, timeUnit: TimeUnit) {
        environment.monitor.raise(ApplicationStopPreparing, environment)
        server.stopTimeout = timeUnit.toMillis(timeout)
        server.stop()
        server.destroy()
        environment.stop()
    }

    override fun toString(): String {
        return "Jetty($environment)"
    }
}
