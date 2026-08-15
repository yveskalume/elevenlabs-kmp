package dev.yveskalume.elevenlabs.tts

import kotlinx.coroutines.flow.Flow

/** Generates speech from complete or incrementally produced text. */
public interface TextToSpeechApi {
    /**
     * Generates a complete audio response for [request].
     *
     * @throws IllegalArgumentException when a request field is invalid.
     * @throws dev.yveskalume.elevenlabs.ElevenLabsException when the request fails.
     */
    public suspend fun generate(request: TextToSpeechRequest): Audio

    /**
     * Performs HTTP response streaming. The returned flow is cold and each collection starts a new request.
     * Cancelling collection closes the underlying response.
     * Each emitted [AudioChunk] is an arbitrary transport chunk, not necessarily a codec frame.
     */
    public fun stream(request: TextToSpeechRequest): Flow<AudioChunk>

    /**
     * Streams partial text input to ElevenLabs and emits audio as it becomes available.
     * The returned flow is cold and opens a new realtime session for each collection.
     * Empty input values are ignored, and normal input completion gracefully finishes the session.
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
     * @throws dev.yveskalume.elevenlabs.ElevenLabsException.Realtime if the connection fails.
     */
    public suspend fun openRealtimeSession(
        voiceId: String,
        options: RealtimeTtsOptions = RealtimeTtsOptions(),
        authorization: RealtimeTtsAuthorization = RealtimeTtsAuthorization.ConfiguredApiKey,
    ): RealtimeTtsSession
}
