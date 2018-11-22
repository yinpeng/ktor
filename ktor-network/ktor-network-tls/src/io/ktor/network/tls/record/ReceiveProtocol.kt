package io.ktor.network.tls.record

import io.ktor.network.tls.*
import kotlinx.coroutines.channels.*
import kotlinx.io.core.*


internal fun processServerHello(handshake: TLSHandshake): TLSServerHello {
    check(handshake.type == TLSHandshakeType.ServerHello) {
        ("Expected TLS handshake ServerHello but got ${handshake.type}")
    }

    return handshake.packet.readTLSServerHello()
}

internal fun processChangeCipherSpec(handshake: TLSHandshake) {
}

internal suspend fun processServerFinished(handshake: TLSHandshake): ByteArray {
    if (handshake.type != TLSHandshakeType.Finished)
        throw TLSException("Finished handshake expected, received: $handshake")

    return handshake.packet.readBytes()
}

