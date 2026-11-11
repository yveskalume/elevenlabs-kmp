package dev.yveskalume.elevenlabs.tts

import dev.yveskalume.elevenlabs.models.ModelId

/** Input for complete or HTTP-streamed text-to-speech generation. */
public class TextToSpeechRequest private constructor(
    /** Identifier of the voice that should speak the text. */
    public val voiceId: String,
    /** Non-blank text to synthesize. */
    public val text: String,
    /** Model identifier, or `null` to use the service default. */
    public val modelId: ModelId?,
    /** Language code, or `null` to let the service infer it. */
    public val languageCode: String?,
    /** Optional voice tuning parameters. */
    public val voiceSettings: VoiceSettings?,
    /** Encoding and sample rate of the returned audio. */
    public val outputFormat: OutputFormat,
    /** Whether request logging is enabled. */
    public val enableLogging: Boolean,
) {
    init {
        require(voiceId.isNotBlank()) { "voiceId cannot be blank." }
        require(text.isNotBlank()) { "text cannot be blank." }
    }

    /** Returns a builder with these values, to create a changed copy. */
    public fun toBuilder(): Builder = Builder(voiceId = voiceId, text = text).also {
        it.modelId = modelId
        it.languageCode = languageCode
        it.voiceSettings = voiceSettings
        it.outputFormat = outputFormat
        it.enableLogging = enableLogging
    }

    /**
     * Builds a [TextToSpeechRequest]. Required values are constructor parameters; the others start
     * at their defaults.
     */
    public class Builder(
        /** Identifier of the voice that should speak the text. */
        public var voiceId: String,
        /** Non-blank text to synthesize. */
        public var text: String,
    ) {
        /** Model identifier, or `null` to use the service default. */
        public var modelId: ModelId? = null
        /** Language code, or `null` to let the service infer it. */
        public var languageCode: String? = null
        /** Optional voice tuning parameters. */
        public var voiceSettings: VoiceSettings? = null
        /** Encoding and sample rate of the returned audio. */
        public var outputFormat: OutputFormat = OutputFormat.Mp3_44100_128
        /** Whether request logging is enabled. */
        public var enableLogging: Boolean = true

        /**
         * Creates the [TextToSpeechRequest].
         *
         * @throws IllegalArgumentException if a value is invalid.
         */
        @Throws(IllegalArgumentException::class)
        public fun build(): TextToSpeechRequest = TextToSpeechRequest(
            voiceId,
            text,
            modelId,
            languageCode,
            voiceSettings,
            outputFormat,
            enableLogging,
        )
    }

    override fun equals(other: Any?): Boolean =
        other is TextToSpeechRequest &&
            voiceId == other.voiceId &&
            text == other.text &&
            modelId == other.modelId &&
            languageCode == other.languageCode &&
            voiceSettings == other.voiceSettings &&
            outputFormat == other.outputFormat &&
            enableLogging == other.enableLogging

    override fun hashCode(): Int {
        var result = voiceId.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + modelId.hashCode()
        result = 31 * result + languageCode.hashCode()
        result = 31 * result + voiceSettings.hashCode()
        result = 31 * result + outputFormat.hashCode()
        result = 31 * result + enableLogging.hashCode()
        return result
    }

    override fun toString(): String =
        "TextToSpeechRequest(voiceId=$voiceId, text=$text, modelId=$modelId, " +
            "languageCode=$languageCode, voiceSettings=$voiceSettings, " +
            "outputFormat=$outputFormat, enableLogging=$enableLogging)"
}

/**
 * Creates a [TextToSpeechRequest]. Set optional values in [configure].
 *
 * @throws IllegalArgumentException if a value is invalid.
 */
@Throws(IllegalArgumentException::class)
public fun TextToSpeechRequest(
    voiceId: String,
    text: String,
    configure: TextToSpeechRequest.Builder.() -> Unit = {},
): TextToSpeechRequest = TextToSpeechRequest.Builder(voiceId, text).apply(configure).build()
