package io.ktor.util

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlin.coroutines.*


/**
 * Returns the channel that applies [transform] before element send.
 */
@InternalAPI
@UseExperimental(ObsoleteCoroutinesApi::class)
fun <Old, New> SendChannel<Old>.map(
    context: CoroutineContext = Dispatchers.Unconfined,
    transform: suspend (New) -> Old
): SendChannel<New> = GlobalScope.actor(context, onCompletion = { close(it) }) {
    consumeEach { send(transform(it)) }
}
