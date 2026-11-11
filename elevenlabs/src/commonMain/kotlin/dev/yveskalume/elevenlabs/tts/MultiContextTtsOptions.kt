package dev.yveskalume.elevenlabs.tts

import dev.yveskalume.elevenlabs.models.ModelId
import dev.yveskalume.elevenlabs.realtime.KeepAlive

/**
 * Connection-level configuration for a [MultiContextTtsSession].
 *
 * These values are fixed when the WebSocket is opened and apply to every context. Settings that
 * can differ per context live in [MultiContextTtsContextOptions].
 */
public class MultiContextTtsOptions private constructor(
    /** Model identifier, or `null` to use the service default. */
    public val modelId: ModelId?,
    /** Language code, or `null` to let the selected model infer it. */
    public val languageCode: String?,
    /** Encoding and sample rate of emitted audio. */
    public val outputFormat: OutputFormat,
    /** Whether request logging is enabled on ElevenLabs. Disable for zero-retention mode. */
    public val enableLogging: Boolean,
    /** Whether audio events include character alignment information. */
    public val syncAlignment: Boolean,
    /**
     * Whether ElevenLabs should reduce latency by generating as soon as it has enough text instead
     * of following a chunk length schedule. Recommended when full sentences or phrases are sent.
     */
    public val autoMode: Boolean,
    /** Text normalization behavior, or `null` to use the service default. */
    public val textNormalization: TextNormalization?,
    /**
     * Seed for deterministic sampling, from 0 through 4294967295, or `null` for random sampling.
     */
    public val seed: Long?,
    /** Whether the service should parse SSML tags in submitted text. */
    public val enableSsmlParsing: Boolean,
    /**
     * Client and server timeout configuration. [RealtimeTtsTimeouts.inactivityTimeoutSeconds]
     * applies per context.
     */
    public val timeouts: RealtimeTtsTimeouts,
    /** Automatic keepalive applied to every open context. */
    public val keepAlive: KeepAlive,
) {
    init {
        require(languageCode == null || languageCode.isNotBlank()) { "languageCode cannot be blank." }
        require(seed == null || seed in 0..MAX_SEED) { "seed must be between 0 and $MAX_SEED." }
        require(
            !keepAlive.enabled ||
                keepAlive.intervalMillis < timeouts.inactivityTimeoutSeconds * 1_000L,
        ) {
            "The keepalive interval must be shorter than the ElevenLabs inactivity timeout."
        }
    }

    /** Returns a builder with these values, to create a changed copy. */
    public fun toBuilder(): Builder = Builder().also {
        it.modelId = modelId
        it.languageCode = languageCode
        it.outputFormat = outputFormat
        it.enableLogging = enableLogging
        it.syncAlignment = syncAlignment
        it.autoMode = autoMode
        it.textNormalization = textNormalization
        it.seed = seed
        it.enableSsmlParsing = enableSsmlParsing
        it.timeouts = timeouts
        it.keepAlive = keepAlive
    }

    /** Builds a [MultiContextTtsOptions]. Every value starts at its default. */
    public class Builder {
        /** Model identifier, or `null` to use the service default. */
        public var modelId: ModelId? = null
        /** Language code, or `null` to let the selected model infer it. */
        public var languageCode: String? = null
        /** Encoding and sample rate of emitted audio. */
        public var outputFormat: OutputFormat = OutputFormat.Mp3_44100_128
        /** Whether request logging is enabled on ElevenLabs. Disable for zero-retention mode. */
        public var enableLogging: Boolean = true
        /** Whether audio events include character alignment information. */
        public var syncAlignment: Boolean = false
        /**
         * Whether ElevenLabs should reduce latency by generating as soon as it has enough text
         * instead
         * of following a chunk length schedule. Recommended when full sentences or phrases are
         * sent.
         */
        public var autoMode: Boolean = false
        /** Text normalization behavior, or `null` to use the service default. */
        public var textNormalization: TextNormalization? = null
        /**
         * Seed for deterministic sampling, from 0 through 4294967295, or `null` for random
         * sampling.
         */
        public var seed: Long? = null
        /** Whether the service should parse SSML tags in submitted text. */
        public var enableSsmlParsing: Boolean = false
        /**
         * Client and server timeout configuration. [RealtimeTtsTimeouts.inactivityTimeoutSeconds]
         * applies per context.
         */
        public var timeouts: RealtimeTtsTimeouts = RealtimeTtsTimeouts()
        /** Automatic keepalive applied to every open context. */
        public var keepAlive: KeepAlive = KeepAlive()

        /**
         * Creates the [MultiContextTtsOptions].
         *
         * @throws IllegalArgumentException if a value is invalid.
         */
        @Throws(IllegalArgumentException::class)
        public fun build(): MultiContextTtsOptions = MultiContextTtsOptions(
            modelId,
            languageCode,
            outputFormat,
            enableLogging,
            syncAlignment,
            autoMode,
            textNormalization,
            seed,
            enableSsmlParsing,
            timeouts,
            keepAlive,
        )
    }

    override fun equals(other: Any?): Boolean =
        other is MultiContextTtsOptions &&
            modelId == other.modelId &&
            languageCode == other.languageCode &&
            outputFormat == other.outputFormat &&
            enableLogging == other.enableLogging &&
            syncAlignment == other.syncAlignment &&
            autoMode == other.autoMode &&
            textNormalization == other.textNormalization &&
            seed == other.seed &&
            enableSsmlParsing == other.enableSsmlParsing &&
            timeouts == other.timeouts &&
            keepAlive == other.keepAlive

    override fun hashCode(): Int {
        var result = modelId.hashCode()
        result = 31 * result + languageCode.hashCode()
        result = 31 * result + outputFormat.hashCode()
        result = 31 * result + enableLogging.hashCode()
        result = 31 * result + syncAlignment.hashCode()
        result = 31 * result + autoMode.hashCode()
        result = 31 * result + textNormalization.hashCode()
        result = 31 * result + seed.hashCode()
        result = 31 * result + enableSsmlParsing.hashCode()
        result = 31 * result + timeouts.hashCode()
        result = 31 * result + keepAlive.hashCode()
        return result
    }

    override fun toString(): String =
        "MultiContextTtsOptions(modelId=$modelId, languageCode=$languageCode, " +
            "outputFormat=$outputFormat, enableLogging=$enableLogging, " +
            "syncAlignment=$syncAlignment, autoMode=$autoMode, " +
            "textNormalization=$textNormalization, seed=$seed, " +
            "enableSsmlParsing=$enableSsmlParsing, timeouts=$timeouts, keepAlive=$keepAlive)"

    private companion object {
        const val MAX_SEED = 4_294_967_295L
    }
}

/**
 * Creates a [MultiContextTtsOptions]. Set the values to change in [configure].
 *
 * @throws IllegalArgumentException if a value is invalid.
 */
@Throws(IllegalArgumentException::class)
public fun MultiContextTtsOptions(
    configure: MultiContextTtsOptions.Builder.() -> Unit = {},
): MultiContextTtsOptions = MultiContextTtsOptions.Builder().apply(configure).build()

/** Configuration of a single context in a [MultiContextTtsSession]. */
public class MultiContextTtsContextOptions private constructor(
    /** Voice tuning for this context, or `null` to use the voice defaults. */
    public val voiceSettings: VoiceSettings?,
    /** Server-side text chunking for this context, or `null` to use the service default. */
    public val generationConfig: RealtimeTtsGenerationConfig?,
    /** Pronunciation dictionaries applied to this context, in priority order. */
    public val pronunciationDictionaries: List<PronunciationDictionaryLocator>,
) {
    init {
        require(pronunciationDictionaries.size <= MAX_PRONUNCIATION_DICTIONARIES) {
            "At most $MAX_PRONUNCIATION_DICTIONARIES pronunciation dictionaries can be applied."
        }
    }

    /** Returns a builder with these values, to create a changed copy. */
    public fun toBuilder(): Builder = Builder().also {
        it.voiceSettings = voiceSettings
        it.generationConfig = generationConfig
        it.pronunciationDictionaries = pronunciationDictionaries
    }

    /** Builds a [MultiContextTtsContextOptions]. Every value starts at its default. */
    public class Builder {
        /** Voice tuning for this context, or `null` to use the voice defaults. */
        public var voiceSettings: VoiceSettings? = null
        /** Server-side text chunking for this context, or `null` to use the service default. */
        public var generationConfig: RealtimeTtsGenerationConfig? = null
        /** Pronunciation dictionaries applied to this context, in priority order. */
        public var pronunciationDictionaries: List<PronunciationDictionaryLocator> = emptyList()

        /**
         * Creates the [MultiContextTtsContextOptions].
         *
         * @throws IllegalArgumentException if a value is invalid.
         */
        @Throws(IllegalArgumentException::class)
        public fun build(): MultiContextTtsContextOptions = MultiContextTtsContextOptions(
            voiceSettings,
            generationConfig,
            pronunciationDictionaries,
        )
    }

    override fun equals(other: Any?): Boolean =
        other is MultiContextTtsContextOptions &&
            voiceSettings == other.voiceSettings &&
            generationConfig == other.generationConfig &&
            pronunciationDictionaries == other.pronunciationDictionaries

    override fun hashCode(): Int {
        var result = voiceSettings.hashCode()
        result = 31 * result + generationConfig.hashCode()
        result = 31 * result + pronunciationDictionaries.hashCode()
        return result
    }

    override fun toString(): String =
        "MultiContextTtsContextOptions(voiceSettings=$voiceSettings, " +
            "generationConfig=$generationConfig, " +
            "pronunciationDictionaries=$pronunciationDictionaries)"

    private companion object {
        const val MAX_PRONUNCIATION_DICTIONARIES = 3
    }
}

/**
 * Creates a [MultiContextTtsContextOptions]. Set the values to change in [configure].
 *
 * @throws IllegalArgumentException if a value is invalid.
 */
@Throws(IllegalArgumentException::class)
public fun MultiContextTtsContextOptions(
    configure: MultiContextTtsContextOptions.Builder.() -> Unit = {},
): MultiContextTtsContextOptions = MultiContextTtsContextOptions.Builder().apply(configure).build()

/** Reference to a specific version of a pronunciation dictionary stored in ElevenLabs. */
public class PronunciationDictionaryLocator @Throws(IllegalArgumentException::class) constructor(
    /** Identifier of the pronunciation dictionary. */
    public val dictionaryId: String,
    /** Version of the dictionary to apply. */
    public val versionId: String,
) {
    init {
        require(dictionaryId.isNotBlank()) { "dictionaryId cannot be blank." }
        require(versionId.isNotBlank()) { "versionId cannot be blank." }
    }

    override fun equals(other: Any?): Boolean =
        other is PronunciationDictionaryLocator &&
            dictionaryId == other.dictionaryId &&
            versionId == other.versionId

    override fun hashCode(): Int {
        var result = dictionaryId.hashCode()
        result = 31 * result + versionId.hashCode()
        return result
    }

    override fun toString(): String =
        "PronunciationDictionaryLocator(dictionaryId=$dictionaryId, versionId=$versionId)"
}

/** Controls whether ElevenLabs normalizes text, such as spelling out numbers, before synthesis. */
public enum class TextNormalization {
    /** Let ElevenLabs decide. */
    Auto,

    /** Always normalize text. */
    On,

    /** Never normalize text. */
    Off,
}
