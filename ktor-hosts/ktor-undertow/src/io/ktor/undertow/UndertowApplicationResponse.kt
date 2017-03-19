package io.ktor.undertow

import io.undertow.util.*
import io.ktor.cio.*
import io.ktor.content.*
import io.ktor.host.*
import io.ktor.http.*
import io.ktor.response.*

class UndertowApplicationResponse(call: UndertowApplicationCall) : BaseApplicationResponse(call) {
    suspend override fun respondUpgrade(upgrade: FinalContent.ProtocolUpgrade) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    suspend override fun responseChannel(): WriteChannel = UndertowWriteChannel(exchange.responseSender)

    private val exchange = call.exchange

    @Volatile
    private var responseMessageSent = false

    override fun setStatus(statusCode: HttpStatusCode) {
        exchange.statusCode = statusCode.value
        exchange.reasonPhrase = statusCode.description
    }

    override val headers: ResponseHeaders = object : ResponseHeaders() {
        override fun hostAppendHeader(name: String, value: String) {
            if (responseMessageSent)
                throw UnsupportedOperationException("Headers can no longer be set because response was already completed")
            call.exchange.responseHeaders.add(HttpString.tryFromString(name), value)
        }

        override fun getHostHeaderNames(): List<String> {
            return call.exchange.responseHeaders.headerNames.map { it.toString() }
        }

        override fun getHostHeaderValues(name: String): List<String> {
            return call.exchange.responseHeaders.eachValue(HttpString.tryFromString(name)).map { it.toString() }
        }
    }
}