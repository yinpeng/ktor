package io.ktor.network.tls

import io.ktor.network.tls.record.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.security.*
import java.security.cert.*
import javax.net.ssl.*

private val EMPTY_SESSION = ByteArray(32)

internal class EncryptionInfo(
    val serverPublic: PublicKey,
    val clientPublic: PublicKey,
    val clientPrivate: PrivateKey,
    val seed: ByteArray = ByteArray(48)
)

internal class ExchangeDetails(
    val certificateRequested: Boolean,
    val encryptionInfo: EncryptionInfo
)

internal class TLSConnection(val input: ReceiveChannel<TLSRecord>, val output: SendChannel<TLSRecord>)

internal suspend fun CoroutineScope.handshake(
    connection: TLSConnection,
    tls: TLSVersion,
    serverName: String,
    trustManager: X509TrustManager = findTrustManager(),
    randomAlgorithm: String = "NativePRNGNonBlocking",
    cipherSuites: List<CipherSuite> = CIOCipherSuites.SupportedSuites,
    session: ByteArray = EMPTY_SESSION
): TLSConnection {
    val digest = Digest()
    val input = connection.input.digested(digest)
    val output = connection.output.digested(digest)

    // TODO: reuse nonce generator
    val random = SecureRandom.getInstance(randomAlgorithm)!!
    val clientSeed: ByteArray = random.generateClientSeed()

    val handshakesInput = handshakesFrom(input)

    output.sendClientHello(tls, cipherSuites, clientSeed, session, serverName)

    val serverHello = input.receiveServerHello()
    val serverSeed = serverHello.serverSeed
    val cipherSuite = serverHello.cipherSuite

    verifyNegotiation(serverHello, cipherSuites)

    val details = handleCertificatesAndKeys(input, serverHello, clientSeed, trustManager)
    if (details.certificateRequested) output.sendClientCertificate()

    val masterKey = output.sendClientKeys(cipherSuite, details, clientSeed, serverSeed, random)

    if (details.certificateRequested) output.sendClientCertificateVerify()

    output.sendChangeCipherSpec()

    val key: ByteArray = keyMaterial(
        masterKey, serverHello.serverSeed + clientSeed,
        cipherSuite.keyStrengthInBytes, cipherSuite.macStrengthInBytes, cipherSuite.fixedIvLength
    )

    val encryptedOutput = encryptedChannel(output, CipherEncryptor(cipherSuite, key))

    val checksum = digest.doHash(cipherSuite.hash.openSSLName)
    encryptedOutput.sendClientFinished(masterKey, checksum)

    input.receiveChangeCipherSpec()

    val decryptedInput =
        decryptedChannel(input, CipherDecryptor(cipherSuite, key))

    val serverChecksum = input.receiveServerFinished()
    val expectedChecksum = serverFinished(digest.doHash(cipherSuite.hash.openSSLName), masterKey, serverChecksum.size)

    if (!serverChecksum.contentEquals(expectedChecksum)) {
        throw TLSException(
            """Handshake: ServerFinished verification failed:
                |Expected: ${expectedChecksum.joinToString()}
                |Actual: ${serverChecksum.joinToString()}
            """.trimMargin()
        )
    }

    return TLSConnection(decryptedInput, encryptedOutput)
}

private suspend fun SendChannel<TLSRecord>.sendClientHello(
    tls: TLSVersion,
    cipherSuites: List<CipherSuite>,
    clientSeed: ByteArray,
    sessionId: ByteArray,
    serverName: String
) {
    sendHandshakeRecord(TLSHandshakeType.ClientHello) {
        writeTLSClientHello(tls, cipherSuites, clientSeed, sessionId, serverName)
    }
}

private suspend fun handleCertificatesAndKeys(
    input: ReceiveChannel<TLSRecord>,
    serverHello: TLSServerHello,
    clientSeed: ByteArray,
    trustManager: X509TrustManager
): ExchangeDetails {
    val exchangeType = serverHello.cipherSuite.exchangeType
    lateinit var serverPublicKey: PublicKey
    lateinit var encryptionInfo: EncryptionInfo
    var certificateRequested = false

    while (true) {
        val handshake = input.receiveHandshake()
        val packet = handshake.packet

        when (handshake.type) {
            TLSHandshakeType.Certificate -> {
                val certs = packet.readTLSCertificate().map { it as X509Certificate }
                serverPublicKey = certs.verifyCertificateChain(trustManager, exchangeType)
                    ?: throw TLSException("Server is untrusted: $certs")
            }
            TLSHandshakeType.CertificateRequest -> {
                certificateRequested = true
            }
            TLSHandshakeType.ServerKeyExchange -> {
                encryptionInfo = generateKeys(
                    exchangeType, packet, serverPublicKey, clientSeed, serverHello.serverSeed
                )
            }
            TLSHandshakeType.ServerDone -> {
                return ExchangeDetails(certificateRequested, encryptionInfo)
            }
            else -> throw TLSException("Unsupported message type during handshake: ${handshake.type}")
        }

        check(packet.remaining == 0L)
    }
}
