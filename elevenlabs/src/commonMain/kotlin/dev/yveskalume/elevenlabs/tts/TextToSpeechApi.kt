package dev.yveskalume.elevenlabs.tts

import dev.yveskalume.elevenlabs.error.ApiException
import dev.yveskalume.elevenlabs.error.ElevenLabsException
import dev.yveskalume.elevenlabs.error.NetworkError
import dev.yveskalume.elevenlabs.error.SerializationError
import dev.yveskalume.elevenlabs.error.TimeoutError
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow

/** Generates speech from complete or incrementally produced text. */
public interface TextToSpeechApi {
    /**
     * Generates a complete audio response for [request].
     *
     * @throws IllegalArgumentException when a request field is invalid.
     * @throws ApiException when ElevenLabs returns a non-successful HTTP response.
     * @throws NetworkError when no HTTP response can be obtained.
     * @throws TimeoutError when the request exceeds a transport timeout.
     * @throws SerializationError when the response does not match the documented schema.
     */
    @Throws(ElevenLabsException::class, CancellationException::class)
    public suspend fun generate(request: TextToSpeechRequest): Audio

    /**
     * Performs HTTP response streaming. The returned flow is cold and each collection starts a new request.
     * Cancelling collection closes the underlying response.
     * Each emitted [AudioChunk] is an arbitrary transport chunk, not necessarily a codec frame.
     *
     * Collection terminates with [ApiException], [NetworkError], or [TimeoutError] when the request
     * fails. These failures are emitted by the flow rather than thrown when [stream] is called.
     */
    public fun stream(request: TextToSpeechRequest): Flow<AudioChunk>

    /**
     * Streams partial text input to ElevenLabs and emits audio as it becomes available.
     * The returned flow is cold and opens a new realtime session for each collection.
     * Empty input values are ignored, and normal input completion gracefully finishes the session.
     *
     * Collection terminates with [dev.yveskalume.elevenlabs.error.RealtimeServerError] for a server
     * protocol failure, [NetworkError] for transport failure, or [TimeoutError] for a timeout.
     */
    public fun realtime(
        voiceId: String,
        text: Flow<String>,
        options: RealtimeTtsOptions = RealtimeTtsOptions(),
        authorization: RealtimeTtsAuthorization = RealtimeTtsAuthorization.ConfiguredApiKey,
    ): Flow<AudioChunk>

    /**
     * Opens a controllable realtime text-to-speech session for [voiceId].
     *
     * The caller owns the returned session and must call [RealtimeTtsSession.close].
     *
     * @throws IllegalArgumentException if [voiceId] is blank or an option is invalid.
     * @throws dev.yveskalume.elevenlabs.error.RealtimeServerError for a realtime protocol failure.
     * @throws NetworkError for a transport failure.
     * @throws TimeoutError when opening times out.
     */
    @Throws(ElevenLabsException::class, CancellationException::class)
    public suspend fun openRealtimeSession(
        voiceId: String,
        options: RealtimeTtsOptions = RealtimeTtsOptions(),
        authorization: RealtimeTtsAuthorization = RealtimeTtsAuthorization.ConfiguredApiKey,
    ): RealtimeTtsSession
}
