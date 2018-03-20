package io.ktor.sessions

/**
 * Serializes session from and to [String]
 */
interface SessionSerializer<S : Any> {
    /**
     * Serializes a complex arbitrary object into a [String].
     */
    fun serialize(session: S): String

    /**
     * Deserializes a complex arbitrary object from a [String].
     */
    fun deserialize(text: String): S
}