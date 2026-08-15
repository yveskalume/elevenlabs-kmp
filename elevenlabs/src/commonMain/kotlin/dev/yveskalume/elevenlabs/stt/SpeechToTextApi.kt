package dev.yveskalume.elevenlabs.stt

interface SpeechToTextApi {
    /** Transcribes a complete audio or video file. */
    suspend fun transcribe(
        request: SpeechToTextRequest,
        authorization: SpeechToTextAuthorization = SpeechToTextAuthorization.ConfiguredApiKey,
    ): Transcription

    /** Opens a WebSocket session for live audio transcription. */
    suspend fun openRealtimeSession(
        options: RealtimeSttOptions = RealtimeSttOptions(),
        authorization: SpeechToTextAuthorization = SpeechToTextAuthorization.ConfiguredApiKey,
    ): RealtimeSttSession
}
