package io.ktor.undertow

import io.ktor.host.*

object Undertow : ApplicationHostFactory<UndertowApplicationHost> {
    override fun create(environment: ApplicationHostEnvironment) = UndertowApplicationHost(environment)
}

