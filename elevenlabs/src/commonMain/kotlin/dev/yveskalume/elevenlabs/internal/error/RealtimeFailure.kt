package dev.yveskalume.elevenlabs.internal.error

import dev.yveskalume.elevenlabs.error.ElevenLabsException
import dev.yveskalume.elevenlabs.error.NetworkError
import dev.yveskalume.elevenlabs.error.RealtimeServerError
import dev.yveskalume.elevenlabs.error.SerializationError
import dev.yveskalume.elevenlabs.error.TimeoutError
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
    is SocketTimeoutException -> TimeoutError(this)

    is ContentConvertException,
    is SerializationException -> SerializationError(this)

    is IOException -> NetworkError(this)
    else -> RealtimeServerError(
        message = message ?: "The realtime ElevenLabs session failed.",
        cause = this,
    )
}
