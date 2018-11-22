package io.ktor.network.tls.record

import io.ktor.network.tls.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.io.*

internal fun CoroutineScope.records(
    input: ByteReadChannel
): ReceiveChannel<TLSRecord> = produce {
    while (!input.isClosedForRead) {
        val record = input.readTLSRecord()

        when (record.type) {
            TLSRecordType.Alert -> {
                val packet = record.packet
                val level = TLSAlertLevel.byCode(packet.readByte())
                val code = TLSAlertType.byCode(packet.readByte())

                if (code == TLSAlertType.CloseNotify) return@produce
                val cause = TLSException("Received alert during handshake. Level: $level, code: $code")
                input.cancel(cause)

                throw cause
            }
            else -> channel.send(record)
        }
    }
}

internal fun CoroutineScope.handshakesFrom(input: ReceiveChannel<TLSRecord>): ReceiveChannel<TLSHandshake> = produce {
    while (true) {
        val record = input.receive()
        val packet = record.packet

        while (packet.remaining > 0) {
            val handshake = packet.readTLSHandshake()
            if (handshake.type == TLSHandshakeType.HelloRequest) continue

//            if (handshake.type != TLSHandshakeType.Finished) {
//                digest += handshake
//            }

            return handshake
        }
    }
}


internal suspend fun ReceiveChannel<TLSRecord>.receiveHandshake(): TLSHandshake {

}
