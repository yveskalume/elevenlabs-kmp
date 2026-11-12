package dev.yveskalume.elevenlabs.stt

import dev.yveskalume.elevenlabs.models.ModelId

/** Configuration applied when opening a realtime speech-to-text connection. */
public class RealtimeSttOptions private constructor(
    /** Realtime speech-to-text model, or `null` to use the service default. */
    public val modelId: ModelId?,
    /** Encoding and sample rate of submitted audio. */
    public val audioFormat: RealtimeSttAudioFormat,
    /** Expected language code, or `null` for automatic detection. */
    public val languageCode: String?,
    /** Additional language codes that may occur in the audio. */
    public val secondaryLanguages: List<String>,
    /** Strategy used to commit buffered audio. */
    public val commitStrategy: RealtimeSttCommitStrategy,
    /** Whether final transcript events should include timestamped units. */
    public val includeTimestamps: Boolean,
    /** Whether transcript events should include detected language information. */
    public val includeLanguageDetection: Boolean,
    /** Up to 50 non-blank terms that bias transcription toward expected vocabulary. */
    public val keyterms: List<String>,
    /** Whether to avoid verbatim transcription behavior supported by the service. */
    public val noVerbatim: Boolean,
    /** Whether request logging is enabled on ElevenLabs. */
    public val enableLogging: Boolean,
) {
    init {
        require(languageCode == null || languageCode.isNotBlank()) { "languageCode cannot be blank." }
        require(secondaryLanguages.all(String::isNotBlank)) { "secondaryLanguages cannot contain blanks." }
        require(keyterms.size <= 50) { "Realtime STT supports at most 50 keyterms." }
        require(keyterms.all(String::isNotBlank)) { "keyterms cannot contain blanks." }
    }

    /** Returns a builder with these values, to create a changed copy. */
    public fun toBuilder(): Builder = Builder().also {
        it.modelId = modelId
        it.audioFormat = audioFormat
        it.languageCode = languageCode
        it.secondaryLanguages = secondaryLanguages
        it.commitStrategy = commitStrategy
        it.includeTimestamps = includeTimestamps
        it.includeLanguageDetection = includeLanguageDetection
        it.keyterms = keyterms
        it.noVerbatim = noVerbatim
        it.enableLogging = enableLogging
    }

    /** Builds a [RealtimeSttOptions]. Every value starts at its default. */
    public class Builder {
        /** Realtime speech-to-text model, or `null` to use the service default. */
        public var modelId: ModelId? = null
        /** Encoding and sample rate of submitted audio. */
        public var audioFormat: RealtimeSttAudioFormat = RealtimeSttAudioFormat.Pcm16000
        /** Expected language code, or `null` for automatic detection. */
        public var languageCode: String? = null
        /** Additional language codes that may occur in the audio. */
        public var secondaryLanguages: List<String> = emptyList()
        /** Strategy used to commit buffered audio. */
        public var commitStrategy: RealtimeSttCommitStrategy = RealtimeSttCommitStrategy.Manual
        /** Whether final transcript events should include timestamped units. */
        public var includeTimestamps: Boolean = false
        /** Whether transcript events should include detected language information. */
        public var includeLanguageDetection: Boolean = false
        /** Up to 50 non-blank terms that bias transcription toward expected vocabulary. */
        public var keyterms: List<String> = emptyList()
        /** Whether to avoid verbatim transcription behavior supported by the service. */
        public var noVerbatim: Boolean = false
        /** Whether request logging is enabled on ElevenLabs. */
        public var enableLogging: Boolean = true

        /**
         * Creates the [RealtimeSttOptions].
         *
         * @throws IllegalArgumentException if a value is invalid.
         */
        @Throws(IllegalArgumentException::class)
        public fun build(): RealtimeSttOptions = RealtimeSttOptions(
            modelId,
            audioFormat,
            languageCode,
            secondaryLanguages.toList(),
            commitStrategy,
            includeTimestamps,
            includeLanguageDetection,
            keyterms.toList(),
            noVerbatim,
            enableLogging,
        )
    }

    override fun equals(other: Any?): Boolean =
        other is RealtimeSttOptions &&
            modelId == other.modelId &&
            audioFormat == other.audioFormat &&
            languageCode == other.languageCode &&
            secondaryLanguages == other.secondaryLanguages &&
            commitStrategy == other.commitStrategy &&
            includeTimestamps == other.includeTimestamps &&
            includeLanguageDetection == other.includeLanguageDetection &&
            keyterms == other.keyterms &&
            noVerbatim == other.noVerbatim &&
            enableLogging == other.enableLogging

    override fun hashCode(): Int {
        var result = modelId.hashCode()
        result = 31 * result + audioFormat.hashCode()
        result = 31 * result + languageCode.hashCode()
        result = 31 * result + secondaryLanguages.hashCode()
        result = 31 * result + commitStrategy.hashCode()
        result = 31 * result + includeTimestamps.hashCode()
        result = 31 * result + includeLanguageDetection.hashCode()
        result = 31 * result + keyterms.hashCode()
        result = 31 * result + noVerbatim.hashCode()
        result = 31 * result + enableLogging.hashCode()
        return result
    }

    override fun toString(): String =
        "RealtimeSttOptions(modelId=$modelId, audioFormat=$audioFormat, " +
            "languageCode=$languageCode, secondaryLanguages=$secondaryLanguages, " +
            "commitStrategy=$commitStrategy, includeTimestamps=$includeTimestamps, " +
            "includeLanguageDetection=$includeLanguageDetection, keyterms=$keyterms, " +
            "noVerbatim=$noVerbatim, enableLogging=$enableLogging)"
}

/**
 * Creates a [RealtimeSttOptions]. Set the values to change in [configure].
 *
 * @throws IllegalArgumentException if a value is invalid.
 */
@Throws(IllegalArgumentException::class)
public fun RealtimeSttOptions(
    configure: RealtimeSttOptions.Builder.() -> Unit = {},
): RealtimeSttOptions = RealtimeSttOptions.Builder().apply(configure).build()
