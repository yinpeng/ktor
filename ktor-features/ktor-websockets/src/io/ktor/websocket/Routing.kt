package io.ktor.websocket

import io.ktor.application.*
import io.ktor.cio.*
import io.ktor.http.*
import io.ktor.pipeline.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*

fun Route.webSocketProtocol(protocol: String, block: Route.() -> Unit) {
    select(WebSocketProtocolsSelector(protocol)).block()
}

fun Route.webSocketRaw(handler: suspend WebSocketSession.(WebSocketUpgrade.Dispatchers) -> Unit) {
    application.feature(WebSockets) // early require

    header(HttpHeaders.Connection, "Upgrade") {
        header(HttpHeaders.Upgrade, "websocket") {
            handle {
                val protocol = call.parameters[HttpHeaders.SecWebSocketProtocol]
                call.respondWebSocketRaw(protocol, handler)
            }
        }
    }
}

fun Route.webSocketRaw(path: String, handler: suspend WebSocketSession.(WebSocketUpgrade.Dispatchers) -> Unit) {
    application.feature(WebSockets) // early require

    route(HttpMethod.Get, path) {
        webSocketRaw(handler)
    }
}

fun Route.webSocket(handler: suspend DefaultWebSocketSession.() -> Unit) {
    webSocketRaw { dispatchers ->
        proceedWebSocket(dispatchers, handler)
    }
}

fun Route.webSocket(path: String, handler: suspend DefaultWebSocketSession.() -> Unit) {
    webSocketRaw(path) { dispatchers ->
        proceedWebSocket(dispatchers, handler)
    }
}

// these two functions could be potentially useful for users however it is not clear how to provide them better
// so for now they are still private

private suspend fun ApplicationCall.respondWebSocketRaw(protocol: String? = null, handler: suspend WebSocketSession.(WebSocketUpgrade.Dispatchers) -> Unit) {
    respond(WebSocketUpgrade(this, protocol, handler))
}

private suspend fun WebSocketSession.proceedWebSocket(dispatchers: WebSocketUpgrade.Dispatchers, handler: suspend DefaultWebSocketSession.() -> Unit) {
    val webSockets = application.feature(WebSockets)

    val raw = this
    val ws = DefaultWebSocketSessionImpl(raw, dispatchers.hostContext, dispatchers.userAppContext, NoPool)
    ws.pingInterval = webSockets.pingInterval
    ws.timeout = webSockets.timeout

    ws.run(handler)
}

private class WebSocketProtocolsSelector(val requiredProtocol: String) : RouteSelector(RouteSelectorEvaluation.qualityConstant) {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        val protocols = context.headers[HttpHeaders.SecWebSocketProtocol] ?: return RouteSelectorEvaluation.Failed
        if (requiredProtocol in parseHeaderValue(protocols).map { it.value }) {
            return RouteSelectorEvaluation(true, RouteSelectorEvaluation.qualityConstant,
                    valuesOf(HttpHeaders.SecWebSocketProtocol, listOf(requiredProtocol)))
        }

        return RouteSelectorEvaluation.Failed
    }
}



