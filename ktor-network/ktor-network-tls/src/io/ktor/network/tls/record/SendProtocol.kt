package io.ktor.network.tls.record

import io.ktor.network.tls.*
import kotlinx.coroutines.channels.*
import kotlinx.io.core.*
import java.security.*
import javax.crypto.spec.*

internal suspend fun SendChannel<TLSRecord>.sendHandshakeRecord(
    handshakeType: TLSHandshakeType,
    block: BytePacketBuilder.() -> Unit
) {
    val handshakeBody = buildPacket(block = block)

    val recordBody = buildPacket {
        writeTLSHandshakeType(handshakeType, handshakeBody.remaining.toInt())
        writePacket(handshakeBody)
    }

//    digest.update(recordBody)
    send(TLSRecord(TLSRecordType.Handshake, packet = recordBody))
}

internal suspend fun SendChannel<TLSRecord>.sendClientKeys(
    cipherSuite: CipherSuite,
    details: ExchangeDetails,
    clientSeed: ByteArray,
    serverSeed: ByteArray,
    random: SecureRandom
): SecretKeySpec {
    val preSecret = generatePreSecret(cipherSuite.exchangeType, details.encryptionInfo)

    try {
        sendClientKeyExchange(cipherSuite.exchangeType, details, preSecret, random)

        return masterSecret(SecretKeySpec(preSecret, cipherSuite.macName), clientSeed, serverSeed)
    } finally {
        preSecret.fill(0)
    }
}

internal suspend fun SendChannel<TLSRecord>.sendClientKeyExchange(
    exchangeType: SecretExchangeType,
    details: ExchangeDetails,
    preSecret: ByteArray,
    random: SecureRandom
) {
    val packet = when (exchangeType) {
        SecretExchangeType.RSA -> buildPacket {
            writeEncryptedPreMasterSecret(preSecret, details.encryptionInfo.serverPublic, random)
        }
        SecretExchangeType.ECDHE -> buildPacket {
            if (details.certificateRequested) return@buildPacket // Key exchange has already completed implicit in the certificate message.
            writePublicKeyUncompressed(details.encryptionInfo.clientPublic)
        }
        SecretExchangeType.RSA_PSK -> TODO()
        SecretExchangeType.DHE_RSA -> TODO()
        SecretExchangeType.ECDHE_RSA -> TODO()
    }

    sendHandshakeRecord(TLSHandshakeType.ClientKeyExchange) { writePacket(packet) }
}


internal suspend fun SendChannel<TLSRecord>.sendChangeCipherSpec() {
    send(TLSRecord(TLSRecordType.ChangeCipherSpec, packet = buildPacket { writeByte(1) }))
}

internal suspend fun SendChannel<TLSRecord>.sendClientFinished(masterKey: SecretKeySpec, checksum: ByteArray) {
    val finished = finished(checksum, masterKey)
    sendHandshakeRecord(TLSHandshakeType.Finished) {
        writePacket(finished)
    }
}

internal suspend fun SendChannel<TLSRecord>.sendClientCertificate(): Unit = TODO()

internal fun SendChannel<TLSRecord>.sendClientCertificateVerify() {
    throw TLSException("Client certificates unsupported")
}
