package io.ktor.client.engine.jetty

import io.ktor.client.engine.*
import org.eclipse.jetty.util.ssl.*


/**
 * Configuration for [Jetty] implementation of [HttpClientEngineFactory].
 */
class JettyEngineConfig : HttpClientEngineConfig() {
    /**
     * A Jetty's [SslContextFactory].
     */
    var sslContextFactory: SslContextFactory = SslContextFactory()
}
