package org.jetbrains.ktor.tests.routing

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.tests.*
import org.junit.*
import kotlin.test.*

class RoutingProcessingTest {
    @Test fun `host with routing on GET foo-bar`() {
        val testHost = createTestHost()
        testHost.application.routing {
            get("/foo/bar") {
                call.respond(HttpStatusCode.OK)
            }
        }

        on("making get request to /foo/bar") {
            val result = testHost.handleRequest {
                uri = "/foo/bar"
                method = HttpMethod.Get
            }
            it("should be handled") {
                assertTrue(result.requestHandled)
            }
            it("should have a response with OK status") {
                assertEquals(HttpStatusCode.OK, result.response.status())
            }
        }

        on("making post request to /foo/bar") {
            val result = testHost.handleRequest {
                uri = "/foo/bar"
                method = HttpMethod.Post
            }
            it("should not be handled") {
                assertFalse(result.requestHandled)
            }
        }
    }

    @Test fun `host with routing on GET user with parameter`() {
        val testHost = createTestHost()
        var username = ""
        testHost.application.routing {
            route("user") {
                param("name") {
                    method(HttpMethod.Get) {
                        handle {
                            username = call.parameters["name"] ?: ""
                        }
                    }
                }
            }
        }
        on("making get request to /user with query parameters") {
            testHost.handleRequest {
                uri = "/user?name=john"
                method = HttpMethod.Get
            }
            it("should have extracted username") {
                assertEquals("john", username)
            }
        }

    }

    @Test fun `host with routing on GET user with surrounded parameter`() {
        val testHost = createTestHost()
        var username = ""
        testHost.application.routing {
            get("/user-{name}-get") {
                username = call.parameters["name"] ?: ""
            }
        }
        on("making get request to /user with query parameters") {
            testHost.handleRequest {
                uri = "/user-john-get"
                method = HttpMethod.Get
            }
            it("should have extracted username") {
                assertEquals("john", username)
            }
        }

    }

    @Test fun `host with routing on GET -user-username with interceptors`() {
        val testHost = createTestHost()

        var userIntercepted = false
        var wrappedWithInterceptor = false
        var userName = ""
        var userNameGotWithinInterceptor = false

        testHost.application.routing {
            route("user") {
                intercept(ApplicationCallPipeline.Call) { call ->
                    userIntercepted = true
                    wrappedWithInterceptor = true
                    onSuccess {
                        wrappedWithInterceptor = false
                    }
                }
                get("{username}") {
                    userName = call.parameters["username"] ?: ""
                    userNameGotWithinInterceptor = wrappedWithInterceptor
                }
            }
        }

        on("handling GET /user/john") {
            testHost.handleRequest {
                uri = "/user/john"
                method = HttpMethod.Get
            }
            assertTrue(userIntercepted, "should have processed interceptor on /user node")
            assertTrue(userNameGotWithinInterceptor, "should have processed /user/username in context of interceptor")
            assertEquals(userName, "john", "should have processed get handler on /user/username node")
        }
    }

    @Test fun `verify interception order when outer should be after`() {
        val testHost = createTestHost()

        var userIntercepted = false
        var wrappedWithInterceptor = false
        var rootIntercepted = false
        var userName = ""
        var routingInterceptorWrapped = false

        testHost.application.routing {
            intercept(ApplicationCallPipeline.Call) {
                wrappedWithInterceptor = true
                rootIntercepted = true
                onFinish {
                    wrappedWithInterceptor = false
                }
            }

            route("user") {
                intercept(ApplicationCallPipeline.Infrastructure) {
                    userIntercepted = true
                    routingInterceptorWrapped = wrappedWithInterceptor
                }
                get("{username}") {
                    userName = call.parameters["username"] ?: ""
                }
            }
        }

        on("handling GET /user/john") {
            testHost.handleRequest {
                uri = "/user/john"
                method = HttpMethod.Get
            }
            assertTrue(userIntercepted, "should have processed interceptor on /user node")
            assertFalse(routingInterceptorWrapped, "should have processed nested routing interceptor in a prior phase")
            assertTrue(rootIntercepted, "should have processed root interceptor")
            assertEquals(userName, "john", "should have processed get handler on /user/username node")
        }
    }

    @Test fun `verify interception order when outer should be before because of phase`() {
        val testHost = createTestHost()

        var userIntercepted = false
        var wrappedWithInterceptor = false
        var rootIntercepted = false
        var userName = ""
        var routingInterceptorWrapped = false

        testHost.application.routing {
            intercept(ApplicationCallPipeline.Infrastructure) {
                wrappedWithInterceptor = true
                rootIntercepted = true
                onFinish {
                    wrappedWithInterceptor = false
                }
            }

            route("user") {
                intercept(ApplicationCallPipeline.Call) {
                    userIntercepted = true
                    routingInterceptorWrapped = wrappedWithInterceptor
                }
                get("{username}") {
                    userName = call.parameters["username"] ?: ""
                }
            }
        }

        on("handling GET /user/john") {
            testHost.handleRequest {
                uri = "/user/john"
                method = HttpMethod.Get
            }
            assertTrue(userIntercepted, "should have processed interceptor on /user node")
            assertTrue(routingInterceptorWrapped, "should have processed nested routing interceptor in an after phase")
            assertTrue(rootIntercepted, "should have processed root interceptor")
            assertEquals(userName, "john", "should have processed get handler on /user/username node")
        }
    }

    @Test fun `verify interception order when outer should be before because of order`() {
        val testHost = createTestHost()

        var userIntercepted = false
        var wrappedWithInterceptor = false
        var rootIntercepted = false
        var userName = ""
        var routingInterceptorWrapped = false

        testHost.application.routing {
            intercept(ApplicationCallPipeline.Infrastructure) {
                wrappedWithInterceptor = true
                rootIntercepted = true
                onFinish {
                    wrappedWithInterceptor = false
                }
            }

            route("user") {
                intercept(ApplicationCallPipeline.Infrastructure) {
                    userIntercepted = true
                    routingInterceptorWrapped = wrappedWithInterceptor
                }
                get("{username}") {
                    userName = call.parameters["username"] ?: ""
                }
            }
        }

        on("handling GET /user/john") {
            testHost.handleRequest {
                uri = "/user/john"
                method = HttpMethod.Get
            }
            assertTrue(userIntercepted, "should have processed interceptor on /user node")
            assertTrue(routingInterceptorWrapped, "should have processed nested routing interceptor in an after phase")
            assertTrue(rootIntercepted, "should have processed root interceptor")
            assertEquals(userName, "john", "should have processed get handler on /user/username node")
        }
    }

    @Test fun `verify headers processing`() {
        val testHost = createTestHost()

        testHost.application.routing {
            route("/reject") {
                header("H", "value", HttpHeaderRouteSelector.MissingHeaderAction.REJECT) {
                    handle {
                        call.respond("OK")
                    }
                }
            }
            route("/accept") {
                header("H", "value", HttpHeaderRouteSelector.MissingHeaderAction.ACCEPT) {
                    handle {
                        call.respond("OK")
                    }
                }
            }
        }

        testHost.handleWebSocket("/reject") {
            addHeader("H", "value")
        }.let { call ->
            assertTrue { call.requestHandled }
        }
        testHost.handleWebSocket("/reject") {
            addHeader("H", "other-value")
        }.let { call ->
            assertFalse { call.requestHandled }
        }

        testHost.handleWebSocket("/reject") {
        }.let { call ->
            assertFalse { call.requestHandled }
        }


        testHost.handleWebSocket("/accept") {
            addHeader("H", "value")
        }.let { call ->
            assertTrue { call.requestHandled }
        }
        testHost.handleWebSocket("/accept") {
            addHeader("H", "other-value")
        }.let { call ->
            assertFalse { call.requestHandled }
        }

        testHost.handleWebSocket("/accept") {
        }.let { call ->
            assertTrue { call.requestHandled }
        }
    }

    @Test fun `verify accept header processing`() {
        val testHost = createTestHost()

        testHost.application.routing {
            route("/") {
                contentType(ContentType.Text.Plain) {
                    handle {
                        call.respond("OK")
                    }
                }
                contentType(ContentType.Application.Json) {
                    handle {
                        call.response.contentType(ContentType.Application.Json)
                        call.respond("{\"status\": \"OK\"}")
                    }
                }
            }
        }

        testHost.handleWebSocket("/") {
            addHeader(HttpHeaders.Accept, "text/plain")
        }.let { call ->
            assertTrue { call.requestHandled }
            assertEquals("OK", call.response.content)
        }

        testHost.handleWebSocket("/") {
            addHeader(HttpHeaders.Accept, "application/json")
        }.let { call ->
            assertTrue { call.requestHandled }
            assertEquals("{\"status\": \"OK\"}", call.response.content)
        }

        testHost.handleWebSocket("/") {
        }.let { call ->
            assertTrue { call.requestHandled }
            assertEquals("OK", call.response.content)
        }

        testHost.handleWebSocket("/") {
            addHeader(HttpHeaders.Accept, "text/html")
        }.let { call ->
            assertFalse { call.requestHandled }
        }
    }
}
