package io.ktor.sessions

import io.ktor.application.*
import kotlin.reflect.*

/**
 * [SessionTracker] that stores the contents of the session as part of HTTP Cookies/Headers.
 * It uses a specific [serializer] to serialize and deserialize objects of type [type].
 */
class SessionTrackerByValue<T : Any>(val type: KClass<T>, val serializer: SessionSerializer<T>) : SessionTracker<T> {
    override suspend fun load(call: ApplicationCall, transport: String?): T? {
        return transport?.let { serializer.deserialize(it) }
    }

    override suspend fun store(call: ApplicationCall, value: T): String {
        val serialized = serializer.serialize(value)
        return serialized
    }

    override fun validate(value: T) {
        if (!type.javaObjectType.isAssignableFrom(value.javaClass)) {
            throw IllegalArgumentException("Value for this session tracker expected to be of type $type but was $value")
        }
    }

    override suspend fun clear(call: ApplicationCall) {
        // it's stateless, so nothing to clear
    }
}

