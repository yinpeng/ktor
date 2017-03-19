@file:JvmName("DevelopmentHost")

package io.ktor.undertow

import io.ktor.host.*

fun main(args: Array<String>) {
    val applicationEnvironment = commandLineEnvironment(args)
    UndertowApplicationHost(applicationEnvironment).start()
}
