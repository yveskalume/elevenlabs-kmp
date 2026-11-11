package dev.yveskalume.elevenlabs.internal.error

import dev.yveskalume.elevenlabs.error.ElevenLabsException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.serialization.ContentConvertException
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException

/** Keeps realtime failures aligned with the SDK-wide error taxonomy. */
internal fun Throwable.toRealtimeFailure(): Throwable = when (this) {
    is CancellationException -> this
    is ElevenLabsException -> this
    is HttpRequestTimeoutException,
    is ConnectTimeoutException,
    is SocketTimeoutException -> ElevenLabsException.Timeout(this)

    is ContentConvertException,
    is SerializationException -> ElevenLabsException.Serialization(this)

    is IOException -> ElevenLabsException.Network(this)
    else -> ElevenLabsException.RealtimeServer(
        message = message ?: "The realtime ElevenLabs session failed.",
        cause = this,
    )
}

/**
 * A transport cancelled underneath a session that the caller did not close. Reported as a lost
 * connection rather than leaking a [CancellationException] into callers that were not cancelled.
 */
internal fun CancellationException.toConnectionLost(label: String): ElevenLabsException.Network =
    ElevenLabsException.Network(cause = this, message = "The $label connection was lost.")
