package io.ktor.client.features.logging

import io.ktor.application.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import kotlin.test.*

class LoggingTest : TestWithKtor() {
    val content = "Response data"

    override val server: ApplicationEngine = embeddedServer(Jetty, port = serverPort) {
        routing {
            get("/") {
                call.respondText("home page")
            }
            post("/") {
                assertEquals(content, call.receiveText())
                call.respondText("/", status = HttpStatusCode.Created)
            }
            get("/301") {
                call.respondRedirect("/")
            }
        }
    }


    @Test
    fun loggingLevelTest() {
        assertEquals(
            """
REQUEST: http://localhost:$serverPort/
METHOD: HttpMethod(value=GET)
HEADERS
-> Accept: */*
BODY Content-Type: null
BODY START
BODY END
RESPONSE: 200 OK
METHOD: HttpMethod(value=GET)
FROM: http://localhost:$serverPort/
HEADERS
-> Content-Type: text/plain;charset=utf-8
-> Content-Length: 9
BODY Content-Type: text/plain; charset=utf-8
BODY START
home page
BODY END

        """.trimIndent(), makeLog(HttpMethod.Get, "/", null, LogLevel.ALL)
        )

        assertEquals(
            """
REQUEST: http://localhost:$serverPort/
METHOD: HttpMethod(value=GET)
HEADERS
-> Accept: */*
RESPONSE: 200 OK
METHOD: HttpMethod(value=GET)
FROM: http://localhost:$serverPort/
HEADERS
-> Content-Type: text/plain;charset=utf-8
-> Content-Length: 9

        """.trimIndent(), makeLog(HttpMethod.Get, "/", null, LogLevel.HEADERS)
        )
        assertEquals(
            """
REQUEST: http://localhost:$serverPort/
METHOD: HttpMethod(value=GET)
BODY Content-Type: null
BODY START
BODY END
RESPONSE: 200 OK
METHOD: HttpMethod(value=GET)
FROM: http://localhost:$serverPort/
BODY Content-Type: text/plain; charset=utf-8
BODY START
home page
BODY END

        """.trimIndent(), makeLog(HttpMethod.Get, "/", null, LogLevel.BODY)
        )
        assertEquals(
            """
REQUEST: http://localhost:$serverPort/
METHOD: HttpMethod(value=GET)
RESPONSE: 200 OK
METHOD: HttpMethod(value=GET)
FROM: http://localhost:$serverPort/

        """.trimIndent(), makeLog(HttpMethod.Get, "/", null, LogLevel.INFO)
        )

        assertEquals("", makeLog(HttpMethod.Get, "/", null, LogLevel.NONE))
    }

    @Test
    fun logPostBodyTest() {
        assertEquals(
            """
REQUEST: http://localhost:$serverPort/
METHOD: HttpMethod(value=POST)
HEADERS
-> Accept: */*
BODY Content-Type: text/plain; charset=UTF-8
BODY START
Response data
BODY END
RESPONSE: 201 Created
METHOD: HttpMethod(value=POST)
FROM: http://localhost:$serverPort/
HEADERS
-> Content-Type: text/plain;charset=utf-8
-> Content-Length: 1
BODY Content-Type: text/plain; charset=utf-8
BODY START
/
BODY END

        """.trimIndent(), makeLog(HttpMethod.Post, "/", content, LogLevel.ALL)
        )
    }

    @Test
    fun logRedirectTest() {
        assertEquals(
            """
REQUEST: http://localhost:$serverPort/301
METHOD: HttpMethod(value=GET)
HEADERS
-> Accept: */*
BODY Content-Type: null
BODY START
BODY END
REQUEST: http://localhost:$serverPort/
METHOD: HttpMethod(value=GET)
HEADERS
-> Accept: */*
BODY Content-Type: null
BODY START
BODY END
RESPONSE: 302 Found
METHOD: HttpMethod(value=GET)
FROM: http://localhost:$serverPort/301
HEADERS
-> Location: /
-> Content-Length: 0
BODY Content-Type: null
BODY START

BODY END
RESPONSE: 200 OK
METHOD: HttpMethod(value=GET)
FROM: http://localhost:$serverPort/
HEADERS
-> Content-Type: text/plain;charset=utf-8
-> Content-Length: 9
BODY Content-Type: text/plain; charset=utf-8
BODY START
home page
BODY END

        """.trimIndent(), makeLog(HttpMethod.Get, "/301", null, LogLevel.ALL)
        )
    }


    fun makeLog(requestMethod: HttpMethod, path: String, body: String?, logLevel: LogLevel): String {
        val testLogger = TestLogger()
        clientTest {

            config {
                install(Logging) {
                    logger = testLogger
                    level = logLevel
                }
            }

            test { client ->
                client.request<String> {
                    method = requestMethod

                    url {
                        encodedPath = path
                    }

                    port = serverPort

                    body?.let { this@request.body = body }
                }
            }
        }
        return testLogger.dump()
    }
}

private class TestLogger : Logger {
    private val state = StringBuilder()

    override fun log(message: String) {
        state.append("$message\n")
    }

    fun dump(): String = state.toString()
}
