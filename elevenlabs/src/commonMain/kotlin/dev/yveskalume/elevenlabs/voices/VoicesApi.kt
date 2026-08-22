package dev.yveskalume.elevenlabs.voices

import dev.yveskalume.elevenlabs.error.ApiException
import dev.yveskalume.elevenlabs.error.ElevenLabsException
import dev.yveskalume.elevenlabs.error.NetworkError
import dev.yveskalume.elevenlabs.error.SerializationError
import dev.yveskalume.elevenlabs.error.TimeoutError
import kotlin.coroutines.cancellation.CancellationException

/** Discovers voices available to the configured ElevenLabs account. */
public interface VoicesApi {
    /**
     * Returns one page of voices matching [request].
     *
     * Pass [VoicePage.nextPageToken] into a subsequent request to continue pagination.
     *
     * @throws ApiException when ElevenLabs returns a non-successful HTTP response.
     * @throws NetworkError when no HTTP response can be obtained.
     * @throws TimeoutError when the request exceeds a transport timeout.
     * @throws SerializationError when the response does not match the documented schema.
     */
    @Throws(ElevenLabsException::class, CancellationException::class)
    public suspend fun list(request: ListVoicesRequest = ListVoicesRequest()): VoicePage

    /**
     * Returns the voice identified by [voiceId].
     *
     * @throws IllegalArgumentException if [voiceId] is blank.
     * @throws ApiException when ElevenLabs returns a non-successful HTTP response.
     * @throws NetworkError when no HTTP response can be obtained.
     * @throws TimeoutError when the request exceeds a transport timeout.
     * @throws SerializationError when the response does not match the documented schema.
     */
    @Throws(ElevenLabsException::class, CancellationException::class)
    public suspend fun get(voiceId: String): Voice
}
