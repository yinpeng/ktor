package io.ktor.undertow

import io.undertow.server.*
import io.ktor.cio.*
import io.ktor.content.*
import io.ktor.host.*
import io.ktor.request.*
import io.ktor.util.*

class UndertowApplicationRequest(call: UndertowApplicationCall) : BaseApplicationRequest(call) {
    private val exchange = call.exchange

    override fun receiveContent() = UndertowIncomingContent(this, exchange)

    override val local = UndertowConnectionPoint(call.exchange)
    override val cookies = UndertowRequestCookies(this)

    override val headers by lazy {
        ValuesMap.build(caseInsensitiveKey = true) {
            val headers = call.exchange.requestHeaders
            for (it in headers) {
                appendAll(it.headerName.toString(), it)
            }
        }
    }

    override val queryParameters by lazy {
        parseQueryString(call.exchange.queryString)
    }

}

class UndertowIncomingContent(override val request: UndertowApplicationRequest, private val exchange: HttpServerExchange) : IncomingContent {
    override fun readChannel(): ReadChannel {
        return UndertowReadChannel(exchange.requestChannel)
    }

    override fun multiPartData(): MultiPartData {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

class UndertowRequestCookies(request: ApplicationRequest) : RequestCookies(request) {
    // TODO: get cookies from undertow if they are already processed
}