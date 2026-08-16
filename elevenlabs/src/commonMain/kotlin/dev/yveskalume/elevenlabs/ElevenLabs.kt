package dev.yveskalume.elevenlabs

import dev.yveskalume.elevenlabs.models.ModelsApi
import dev.yveskalume.elevenlabs.tts.TextToSpeechApi
import dev.yveskalume.elevenlabs.voices.VoicesApi
import dev.yveskalume.elevenlabs.internal.models.ModelsApiImpl
import dev.yveskalume.elevenlabs.internal.tts.TextToSpeechApiImpl
import dev.yveskalume.elevenlabs.internal.voices.VoicesApiImpl
import dev.yveskalume.elevenlabs.internal.http.ElevenLabsHttpClient
import dev.yveskalume.elevenlabs.internal.stt.SpeechToTextApiImpl
import dev.yveskalume.elevenlabs.stt.SpeechToTextApi

/**
 * Entry point for the ElevenLabs API.
 *
 * A client can be shared by concurrent coroutines. It owns its underlying network resources;
 * call [close] when it is no longer needed. Operations attempted after closing may fail.
 */
public class ElevenLabs private constructor(
    private val http: ElevenLabsHttpClient,
) {
    /** Creates a client from configuration applied by [configure]. */
    public constructor(configure: ElevenLabsConfiguration.() -> Unit) : this(
        ElevenLabsHttpClient.create(ElevenLabsConfiguration().apply(configure)),
    )

    /** APIs for discovering and retrieving voices. */
    public val voices: VoicesApi = VoicesApiImpl(http)

    /** APIs for discovering models supported by ElevenLabs. */
    public val models: ModelsApi = ModelsApiImpl(http)

    /** Batch, HTTP-streamed, and realtime text-to-speech APIs. */
    public val textToSpeech: TextToSpeechApi = TextToSpeechApiImpl(http)

    /** Batch and realtime speech-to-text APIs. */
    public val speechToText: SpeechToTextApi = SpeechToTextApiImpl(http)

    /**
     * Releases network resources owned by this client.
     *
     * Closing the client more than once is safe. Active and subsequent operations may fail.
     */
    public fun close() {
        http.close()
    }
}
