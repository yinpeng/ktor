package io.ktor.undertow.tests

import io.ktor.undertow.*
import io.ktor.testing.*
import io.ktor.undertow.*

class UndertowHostTest : HostTestSuite<UndertowApplicationHost>(Undertow)