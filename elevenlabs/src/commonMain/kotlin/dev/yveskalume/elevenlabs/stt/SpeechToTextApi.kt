package dev.yveskalume.elevenlabs.stt

import dev.yveskalume.elevenlabs.Authorization
import dev.yveskalume.elevenlabs.error.ApiException
import dev.yveskalume.elevenlabs.error.ElevenLabsException
import kotlin.coroutines.cancellation.CancellationException

/** Transcribes complete files and realtime audio streams. */
public interface SpeechToTextApi {
    /**
     * Transcribes the complete audio or video file in [request].
     *
     * @throws ElevenLabsException.MissingCredentials when no usable key or token can be obtained.
     * @throws ApiException when ElevenLabs returns a non-successful HTTP response.
     * @throws ElevenLabsException.Network when no HTTP response can be obtained.
     * @throws ElevenLabsException.Timeout when the request exceeds a transport timeout.
     * @throws ElevenLabsException.Serialization when the response does not match the documented schema.
     */
    @Throws(
        ElevenLabsException::class,
        IllegalArgumentException::class,
        IllegalStateException::class,
        CancellationException::class,
    )
    public suspend fun transcribe(
        request: SpeechToTextRequest,
        authorization: Authorization = Authorization.ConfiguredApiKey,
    ): Transcription

    /**
     * Opens a WebSocket session for live audio transcription.
     *
     * The caller owns the returned session and must call [RealtimeSttSession.close].
     *
     * @throws ElevenLabsException.MissingCredentials when no usable key or token can be obtained.
     * @throws dev.yveskalume.elevenlabs.error.ElevenLabsException.RealtimeServer for a realtime protocol failure.
     * @throws ElevenLabsException.Network for a transport failure.
     * @throws ElevenLabsException.Timeout when opening times out.
     */
    @Throws(
        ElevenLabsException::class,
        IllegalArgumentException::class,
        IllegalStateException::class,
        CancellationException::class,
    )
    public suspend fun openRealtimeSession(
        options: RealtimeSttOptions = RealtimeSttOptions(),
        authorization: Authorization = Authorization.ConfiguredApiKey,
    ): RealtimeSttSession
}
