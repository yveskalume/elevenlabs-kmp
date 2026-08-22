package dev.yveskalume.elevenlabs.models

import dev.yveskalume.elevenlabs.error.ApiException
import dev.yveskalume.elevenlabs.error.ElevenLabsException
import dev.yveskalume.elevenlabs.error.NetworkError
import dev.yveskalume.elevenlabs.error.SerializationError
import dev.yveskalume.elevenlabs.error.TimeoutError
import kotlin.coroutines.cancellation.CancellationException

/** Discovers models available to the configured ElevenLabs account. */
public interface ModelsApi {
    /**
     * Returns the models currently available to the account.
     *
     * @throws ApiException when ElevenLabs returns a non-successful HTTP response.
     * @throws NetworkError when no HTTP response can be obtained.
     * @throws TimeoutError when the request exceeds a transport timeout.
     * @throws SerializationError when the response does not match the documented schema.
     */
    @Throws(ElevenLabsException::class, CancellationException::class)
    public suspend fun list(): List<Model>
}
