package io.ktor.gson

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import io.ktor.sessions.SessionSerializer

class GsonSessionSerializer<S : Any>(val type: Class<S>, val gson: Gson = Gson()) : SessionSerializer<S> {
    override fun serialize(session: S): String = gson.toJson(session)
    override fun deserialize(text: String): S = gson.fromJson(text, type)
}

inline fun <reified S : Any> gsonSessionSerializer(configure: GsonBuilder.() -> Unit = {}): SessionSerializer<S> {
    val gson = GsonBuilder().apply(configure).create()
    return GsonSessionSerializer(S::class.java, gson)
}
