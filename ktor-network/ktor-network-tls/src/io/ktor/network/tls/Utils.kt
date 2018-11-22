package io.ktor.network.tls

import io.ktor.network.tls.extensions.*
import kotlinx.io.core.*
import java.security.*
import java.security.cert.*
import java.security.interfaces.*
import java.security.spec.*
import javax.crypto.*
import javax.net.ssl.*

internal fun verifyNegotiation(serverHello: TLSServerHello, suites: List<CipherSuite>) {
    val suite = serverHello.cipherSuite
    check(suite in suites) {
        "Unsupported negotiated cipher suite ${suite.ianaName} in SERVER_HELLO"
    }
}

internal fun generateKeys(
    type: SecretExchangeType,
    packet: ByteReadPacket,
    serverPublicKey: PublicKey,
    clientSeed: ByteArray,
    serverSeed: ByteArray
): EncryptionInfo {
    when (type) {
        SecretExchangeType.ECDHE -> {
            val curve = packet.readCurveParams()
            val point = packet.readECPoint(curve.fieldSize)
            val hashAndSign = packet.readHashAndSign()
            val signatureSize = packet.readShort().toInt() and 0xffff
            val serverSignature = packet.readBytes(signatureSize)

            verifyECDHESignature(serverPublicKey, clientSeed, serverSeed, serverSignature, curve, point, hashAndSign)
            return generateECKeys(curve, point)
        }
        SecretExchangeType.RSA -> {
            packet.release()
            error("Server key exchange handshake doesn't expected in RCA exchange type")
        }
        SecretExchangeType.RSA_PSK -> TODO()
        SecretExchangeType.DHE_RSA -> TODO()
        SecretExchangeType.ECDHE_RSA -> TODO()
    }
}

internal fun verifyECDHESignature(
    serverPublicKey: PublicKey,
    clientSeed: ByteArray,
    serverSeed: ByteArray,
    serverSignature: ByteArray,
    curve: NamedCurve, point: ECPoint, hashAndSign: HashAndSign
) {
    val params = buildPacket {
        // TODO: support other curve types
        writeByte(ServerKeyExchangeType.NamedCurve.code.toByte())
        writeShort(curve.code)
        writeECPoint(point, curve.fieldSize)
    }

    val signature = Signature.getInstance(hashAndSign.name)!!.apply {
        initVerify(serverPublicKey)
        update(buildPacket {
            writeFully(clientSeed)
            writeFully(serverSeed)
            writePacket(params)
        }.readBytes())
    }

    if (!signature.verify(serverSignature)) throw TLSException("Failed to verify signed message")
}

internal fun generatePreSecret(
    exchangeType: SecretExchangeType,
    encryptionInfo: EncryptionInfo
): ByteArray = when (exchangeType) {
    SecretExchangeType.RSA -> encryptionInfo.seed.also {
        it[0] = 0x03
        it[1] = 0x03
    }
    SecretExchangeType.ECDHE -> KeyAgreement.getInstance("ECDH")!!.run {
        init(encryptionInfo.clientPrivate)
        doPhase(encryptionInfo.serverPublic, true)
        generateSecret()
    }
    SecretExchangeType.RSA_PSK -> TODO()
    SecretExchangeType.DHE_RSA -> TODO()
    SecretExchangeType.ECDHE_RSA -> TODO()
}

internal fun SecureRandom.generateClientSeed(): ByteArray {
    return generateSeed(32)!!.also {
        val unixTime = (System.currentTimeMillis() / 1000L)
        it[0] = (unixTime shr 24).toByte()
        it[1] = (unixTime shr 16).toByte()
        it[2] = (unixTime shr 8).toByte()
        it[3] = (unixTime shr 0).toByte()
    }
}

internal fun generateECKeys(curve: NamedCurve, serverPoint: ECPoint): EncryptionInfo {
    val clientKeys = KeyPairGenerator.getInstance("EC")!!.run {
        initialize(ECGenParameterSpec(curve.name))
        generateKeyPair()!!
    }

    @Suppress("UNCHECKED_CAST")
    val publicKey = clientKeys.public as ECPublicKey
    val factory = KeyFactory.getInstance("EC")!!
    val serverPublic = factory.generatePublic(ECPublicKeySpec(serverPoint, publicKey.params!!))!!

    return EncryptionInfo(serverPublic, clientKeys.public, clientKeys.private)
}

internal fun List<X509Certificate>.verifyCertificateChain(
    trustManager: X509TrustManager,
    exchangeType: SecretExchangeType
): PublicKey? {
    if (isEmpty()) throw TLSException("Server sent no certificate")
    trustManager.checkServerTrusted(toTypedArray(), exchangeType.jdkName)

    return firstOrNull()?.publicKey
}

internal fun findTrustManager(): X509TrustManager {
    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    factory.init(null as KeyStore?)
    val manager = factory.trustManagers

    return manager.first { it is X509TrustManager } as X509TrustManager
}
