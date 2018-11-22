package io.ktor.client.features.websocket

import io.ktor.application.*
import io.ktor.client.engine.cio.*
import io.ktor.client.tests.utils.*
import io.ktor.network.tls.certificates.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Test
import java.io.*
import java.net.*
import java.security.*
import java.security.cert.*
import javax.net.ssl.*
import kotlin.test.*

class WebSocketSecureTest : TestWithKtor() {
    override val server: ApplicationEngine = embeddedServer(Netty, applicationEngineEnvironment {
        sslConnector(keyStore, "mykey", { "changeit".toCharArray() }, { "changeit".toCharArray() }) {
            this.port = serverPort
            this.keyStorePath = keyStoreFile.absoluteFile
        }

        module {
            install(io.ktor.websocket.WebSockets)
            routing {
                webSocket("/wss") {
                    for (frame in incoming) {
                        send(frame)
                    }
                }
            }
        }
    })

    @Test
    fun testPingPong() = clientTest(CIO) {
        config {
            engine {
                SecurityManager()
                https.trustManager = object : TrustManager, X509TrustManager {
                    override fun checkClientTrusted(p0: Array<out X509Certificate>?, p1: String?) {
                    }

                    override fun checkServerTrusted(p0: Array<out X509Certificate>?, p1: String?) {
                    }

                    override fun getAcceptedIssuers(): Array<X509Certificate>? {
                        return null
                    }

                }
            }
            install(WebSockets)
        }

        test { client ->
            client.wss(port = serverPort, path = "wss") {
                assertTrue(masking)

                repeat(10) {
                    ping(it.toString())
                }
            }
        }
    }

    @Test
    fun testRemoteSecurePingPong() = clientTest(CIO) {
        val remote = "echo.websocket.org"

        config {
            install(WebSockets)
        }

        test { client->
            client.wss(host = remote) {
                repeat(10) {
                    ping(it.toString())
                }
            }
        }
    }

    companion object {
        val keyStoreFile = File("build/temp.jks")
        lateinit var keyStore: KeyStore
        lateinit var sslContext: SSLContext
        lateinit var trustManager: X509TrustManager

        @BeforeClass
        @JvmStatic
        fun setupAll() {
            keyStore = generateCertificate(keyStoreFile, algorithm = "SHA256withECDSA", keySizeInBits = 256)
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(keyStore)
            sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, tmf.trustManagers, null)
            trustManager = tmf.trustManagers.first { it is X509TrustManager } as X509TrustManager
        }
    }
}
