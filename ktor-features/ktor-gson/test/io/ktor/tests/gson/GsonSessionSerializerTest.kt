package io.ktor.tests.gson

import io.ktor.gson.gsonSessionSerializer
import org.junit.Test
import kotlin.test.assertEquals

class GsonSessionSerializerTest {
    @Test
    fun testSimple() {
        val obj = Demo(10)
        val serializer = gsonSessionSerializer<Demo>()
        assertEquals("""{"a":10}""", serializer.serialize(obj))
        assertEquals(obj, serializer.deserialize(serializer.serialize(obj)))
    }
}

private data class Demo(val a: Int)
