package io.ktor.client.features.websocket

import io.ktor.http.cio.websocket.*
import io.ktor.util.*
import java.nio.*
import kotlin.test.*


internal suspend fun WebSocketSession.ping(salt: String) {
    outgoing.send(Frame.Text("text: $salt"))
    val frame = incoming.receive()
    assert(frame is Frame.Text)
    assertEquals("text: $salt", (frame as Frame.Text).readText())

    val data = "text: $salt".toByteArray()
    outgoing.send(Frame.Binary(true, ByteBuffer.wrap(data)))
    val binaryFrame = incoming.receive()
    assert(binaryFrame is Frame.Binary)

    val buffer = (binaryFrame as Frame.Binary).buffer
    val received = buffer.moveToByteArray()
    assertEquals(data.contentToString(), received.contentToString())
}
