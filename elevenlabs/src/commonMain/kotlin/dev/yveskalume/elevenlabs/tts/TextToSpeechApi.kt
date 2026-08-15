package dev.yveskalume.elevenlabs.tts

import kotlinx.coroutines.flow.Flow

interface TextToSpeechApi {
    suspend fun generate(request: TextToSpeechRequest): Audio

    /**
     * Performs HTTP response streaming. The returned flow is cold and each collection starts a new request.
     * Cancelling collection closes the underlying response.
     */
    fun stream(request: TextToSpeechRequest): Flow<AudioChunk>

    /**
     * Streams partial text input to ElevenLabs and emits audio as it becomes available.
     * The returned flow is cold and opens a new realtime session for each collection.
     */
    fun realtime(
        voiceId: String,
        text: Flow<String>,
        options: RealtimeTtsOptions = RealtimeTtsOptions(),
        authorization: RealtimeTtsAuthorization = RealtimeTtsAuthorization.ConfiguredApiKey,
    ): Flow<AudioChunk>

    /** Opens a controllable realtime text-to-speech session. */
    suspend fun openRealtimeSession(
        voiceId: String,
        options: RealtimeTtsOptions = RealtimeTtsOptions(),
        authorization: RealtimeTtsAuthorization = RealtimeTtsAuthorization.ConfiguredApiKey,
    ): RealtimeTtsSession
}
