package dev.yveskalume.elevenlabs.tts

/** Input for complete or HTTP-streamed text-to-speech generation. */
public data class TextToSpeechRequest(
    /** Identifier of the voice that should speak the text. */
    val voiceId: String,
    /** Non-blank text to synthesize. */
    val text: String,
    /** Model identifier, or `null` to use the service default. */
    val modelId: String? = null,
    /** Language code, or `null` to let the service infer it. */
    val languageCode: String? = null,
    /** Optional voice tuning parameters. */
    val voiceSettings: VoiceSettings? = null,
    /** Encoding and sample rate of the returned audio. */
    val outputFormat: OutputFormat = OutputFormat.Mp3_44100_128,
    /** Whether request logging is enabled. */
    val enableLogging: Boolean = true,
) {
    init {
        require(voiceId.isNotBlank()) { "voiceId cannot be blank." }
        require(text.isNotBlank()) { "text cannot be blank." }
    }
}
