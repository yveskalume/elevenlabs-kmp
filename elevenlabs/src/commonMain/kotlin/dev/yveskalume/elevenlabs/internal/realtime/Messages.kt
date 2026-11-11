package dev.yveskalume.elevenlabs.internal.realtime

import dev.yveskalume.elevenlabs.internal.http.JSON
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.json.jsonObject
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Whether the JSON object [raw] has a field outside [knownFields], meaning ElevenLabs sent a
 * message this SDK version does not understand.
 *
 * Call it only for a message that produced nothing to deliver, so the frequent audio messages are
 * never parsed twice.
 */
internal fun hasUnrecognizedFields(raw: String, knownFields: Set<String>): Boolean =
    JSON.parseToJsonElement(raw).jsonObject.keys.any { it !in knownFields }

/**
 * Receives this channel's elements as a flow that allows one collector at a time.
 *
 * Two collectors of a channel would silently split its elements between them. A second
 * concurrent collector fails instead. Collecting again after the first collector stopped is
 * allowed and resumes with the elements not yet received.
 */
@OptIn(ExperimentalAtomicApi::class)
internal fun <T> ReceiveChannel<T>.receiveAsSingleCollectorFlow(): Flow<T> {
    val collecting = AtomicBoolean(false)
    val elements = receiveAsFlow()
    return flow {
        check(collecting.compareAndSet(expectedValue = false, newValue = true)) {
            "These events support one collector at a time. Collect them once and share the " +
                "results, for example with shareIn()."
        }
        try {
            emitAll(elements)
        } finally {
            collecting.store(false)
        }
    }
}
