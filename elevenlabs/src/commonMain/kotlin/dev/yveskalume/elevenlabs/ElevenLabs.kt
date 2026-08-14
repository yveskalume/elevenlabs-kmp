package dev.yveskalume.elevenlabs

import dev.yveskalume.elevenlabs.models.ModelsApi
import dev.yveskalume.elevenlabs.tts.TextToSpeechApi
import dev.yveskalume.elevenlabs.voices.VoicesApi
import dev.yveskalume.elevenlabs.internal.models.ModelsApiImpl
import dev.yveskalume.elevenlabs.internal.tts.TextToSpeechApiImpl
import dev.yveskalume.elevenlabs.internal.voices.VoicesApiImpl
import dev.yveskalume.elevenlabs.internal.http.ElevenLabsHttpClient

class ElevenLabs private constructor(
    private val http: ElevenLabsHttpClient,
) {
    constructor(configure: ElevenLabsConfiguration.() -> Unit) : this(
        ElevenLabsHttpClient.create(ElevenLabsConfiguration().apply(configure)),
    )

    val voices: VoicesApi = VoicesApiImpl(http)
    val models: ModelsApi = ModelsApiImpl(http)

    val textToSpeech: TextToSpeechApi = TextToSpeechApiImpl(http)

    /** Releases network resources owned by this client. */
    fun close() {
        http.close()
    }
}

