package io.ktor.network.tls.record

import io.ktor.network.tls.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

internal class CipherDecryptor(
    private val cipherSuite: CipherSuite,
    private val key: ByteArray
) {
    private var incomingCounter: Long = 0

    internal fun decryptRecord(record: TLSRecord): TLSRecord {
        val type = record.type
        val packet = record.packet
        val packetSize = packet.remaining
        val recordIv = packet.readLong()

        val cipher = decryptCipher(
            cipherSuite, key, type, packetSize.toInt(), recordIv, incomingCounter
        )

        incomingCounter += 1

        return TLSRecord(type, record.version, packet.decrypted(cipher))
    }
}

internal class CipherEncryptor(
    private val cipherSuite: CipherSuite,
    private val key: ByteArray
) {
    var outgoingCounter: Long = 0

    fun encryptRecord(record: TLSRecord): TLSRecord {
        val type = record.type
        val packet = record.packet

        val cipher = encryptCipher(
            cipherSuite,
            key, type, packet.remaining.toInt(), outgoingCounter, outgoingCounter
        )

        val encryptedPacket = record.packet.encrypted(cipher, outgoingCounter)
        outgoingCounter += 1

        return TLSRecord(type, record.version, encryptedPacket)
    }
}

internal fun decryptedChannel(
    input: ReceiveChannel<TLSRecord>,
    decryptor: CipherDecryptor
): ReceiveChannel<TLSRecord> = input.map { decryptor.decryptRecord(it) }



internal fun CoroutineScope.encryptedChannel(
    input: SendChannel<TLSRecord>,
    encryptor: CipherEncryptor
): SendChannel<TLSRecord> = input.map { encryptor.encryptRecord(it) }

//        loop@ while (true) {
//            when (record.type) {
//                TLSRecordType.ChangeCipherSpec -> {
//                    check(!useCipher)
//                    val flag = packet.readByte()
//                    if (flag != 1.toByte()) throw TLSException("Expected flag: 1, received $flag in ChangeCipherSpec")
//                    useCipher = true
//                    continue@loop
//                }
//                else -> channel.send(TLSRecord(record.type, packet = packet))
//            }
//        }
//}
//    actor {
//    channel.consumeEach { rawRecord ->
//        val record = {
//
//            TLSRecord(rawRecord.type, packet = packet)
//        }()
//
//        if (rawRecord.type == TLSRecordType.ChangeCipherSpec) useCipher = true
//        rawOutput.writeRecord(record)
//    }
//
//    rawOutput.close()
//}

