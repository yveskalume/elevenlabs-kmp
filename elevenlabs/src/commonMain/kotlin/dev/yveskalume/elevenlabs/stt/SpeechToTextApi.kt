package dev.yveskalume.elevenlabs.stt

/** Transcribes complete files and realtime audio streams. */
public interface SpeechToTextApi {
    /**
     * Transcribes the complete audio or video file in [request].
     *
     * @throws IllegalArgumentException when a request field or credential is invalid.
     * @throws dev.yveskalume.elevenlabs.ElevenLabsException when the request fails.
     */
    public suspend fun transcribe(
        request: SpeechToTextRequest,
        authorization: SpeechToTextAuthorization = SpeechToTextAuthorization.ConfiguredApiKey,
    ): Transcription

    /**
     * Opens a WebSocket session for live audio transcription.
     *
     * The caller owns the returned session and must call [RealtimeSttSession.close].
     *
     * @throws IllegalArgumentException when an option or credential is invalid.
     * @throws dev.yveskalume.elevenlabs.ElevenLabsException.Realtime if the connection fails.
     */
    public suspend fun openRealtimeSession(
        options: RealtimeSttOptions = RealtimeSttOptions(),
        authorization: SpeechToTextAuthorization = SpeechToTextAuthorization.ConfiguredApiKey,
    ): RealtimeSttSession
}
