package io.ktor.network.tls.record

import io.ktor.network.tls.*
import io.ktor.util.*
import kotlinx.coroutines.channels.*

internal fun ReceiveChannel<TLSRecord>.digested(digest: Digest): ReceiveChannel<TLSRecord> = map {
    digest.update(it.packet)
    it
}

internal fun SendChannel<TLSRecord>.digested(digest: Digest): SendChannel<TLSRecord> = map {
    digest.update(it.packet)
    it
}
