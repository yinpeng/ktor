package io.ktor.undertow

import io.undertow.server.*
import io.ktor.application.*
import io.ktor.host.*

class UndertowApplicationCall(application: Application, val exchange: HttpServerExchange) : BaseApplicationCall(application) {
    override val request = UndertowApplicationRequest(this)
    override val response = UndertowApplicationResponse(this)
}