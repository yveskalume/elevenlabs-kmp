package dev.yveskalume.elevenlabs.dialogue

import dev.yveskalume.elevenlabs.models.ModelId
import dev.yveskalume.elevenlabs.realtime.KeepAlive
import dev.yveskalume.elevenlabs.tts.OutputFormat
import dev.yveskalume.elevenlabs.tts.PronunciationDictionaryLocator
import dev.yveskalume.elevenlabs.tts.TextNormalization

/**
 * Connection-level configuration for a [MultiContextDialogueSession]. Speakers and their settings
 * are chosen per context, in [MultiContextDialogueSession.openContext].
 */
public class MultiContextDialogueOptions private constructor(
    /**
     * An Eleven v3 or v4 model, or `null` for the service default,
     * [ModelId.ElevenV3Conversational]. Only [ModelId.ElevenV3] allows several voices per context;
     * the others allow one.
     */
    public val modelId: ModelId?,
    /** Language code used for text normalization, or `null` to let the model infer it. */
    public val languageCode: String?,
    /** Encoding and sample rate of emitted audio. */
    public val outputFormat: OutputFormat,
    /** Whether request logging is enabled on ElevenLabs. Disable for zero-retention mode. */
    public val enableLogging: Boolean,
    /** Whether audio events include character alignment information. */
    public val syncAlignment: Boolean,
    /** Text normalization behavior, or `null` to use the service default. */
    public val textNormalization: TextNormalization?,
    /**
     * Seed for deterministic sampling, from 1 through 4294967295, or `null` for random sampling.
     */
    public val seed: Long?,
    /** Client-side timeouts. */
    public val timeouts: MultiContextDialogueTimeouts,
    /**
     * Automatic keepalive for open contexts. ElevenLabs closes a context after 20 seconds without
     * input, so the interval must be shorter than that.
     */
    public val keepAlive: KeepAlive,
) {
    init {
        require(modelId == null || DIALOGUE_MODEL_PREFIXES.any { modelId.value.startsWith(it) }) {
            "Text-to-dialogue requires an Eleven v3 or v4 model (\"eleven_v3…\" or \"eleven_v4…\")."
        }
        require(languageCode == null || languageCode.isNotBlank()) { "languageCode cannot be blank." }
        require(seed == null || seed in 1..MAX_SEED) { "seed must be between 1 and $MAX_SEED." }
        require(!keepAlive.enabled || keepAlive.intervalMillis < INACTIVITY_TIMEOUT_MILLIS) {
            "The keepalive interval must be shorter than the 20-second dialogue inactivity timeout."
        }
    }

    /** Returns a builder with these values, to create a changed copy. */
    public fun toBuilder(): Builder = Builder().also {
        it.modelId = modelId
        it.languageCode = languageCode
        it.outputFormat = outputFormat
        it.enableLogging = enableLogging
        it.syncAlignment = syncAlignment
        it.textNormalization = textNormalization
        it.seed = seed
        it.timeouts = timeouts
        it.keepAlive = keepAlive
    }

    /** Builds a [MultiContextDialogueOptions]. Every value starts at its default. */
    public class Builder {
        /**
         * An Eleven v3 or v4 model, or `null` for the service default,
         * [ModelId.ElevenV3Conversational]. Only [ModelId.ElevenV3] allows several voices per
         * context;
         * the others allow one.
         */
        public var modelId: ModelId? = null
        /** Language code used for text normalization, or `null` to let the model infer it. */
        public var languageCode: String? = null
        /** Encoding and sample rate of emitted audio. */
        public var outputFormat: OutputFormat = OutputFormat.Mp3_44100_128
        /** Whether request logging is enabled on ElevenLabs. Disable for zero-retention mode. */
        public var enableLogging: Boolean = true
        /** Whether audio events include character alignment information. */
        public var syncAlignment: Boolean = false
        /** Text normalization behavior, or `null` to use the service default. */
        public var textNormalization: TextNormalization? = null
        /**
         * Seed for deterministic sampling, from 1 through 4294967295, or `null` for random
         * sampling.
         */
        public var seed: Long? = null
        /** Client-side timeouts. */
        public var timeouts: MultiContextDialogueTimeouts = MultiContextDialogueTimeouts()
        /**
         * Automatic keepalive for open contexts. ElevenLabs closes a context after 20 seconds
         * without
         * input, so the interval must be shorter than that.
         */
        public var keepAlive: KeepAlive = KeepAlive()

        /**
         * Creates the [MultiContextDialogueOptions].
         *
         * @throws IllegalArgumentException if a value is invalid.
         */
        @Throws(IllegalArgumentException::class)
        public fun build(): MultiContextDialogueOptions = MultiContextDialogueOptions(
            modelId,
            languageCode,
            outputFormat,
            enableLogging,
            syncAlignment,
            textNormalization,
            seed,
            timeouts,
            keepAlive,
        )
    }

    override fun equals(other: Any?): Boolean =
        other is MultiContextDialogueOptions &&
            modelId == other.modelId &&
            languageCode == other.languageCode &&
            outputFormat == other.outputFormat &&
            enableLogging == other.enableLogging &&
            syncAlignment == other.syncAlignment &&
            textNormalization == other.textNormalization &&
            seed == other.seed &&
            timeouts == other.timeouts &&
            keepAlive == other.keepAlive

    override fun hashCode(): Int {
        var result = modelId.hashCode()
        result = 31 * result + languageCode.hashCode()
        result = 31 * result + outputFormat.hashCode()
        result = 31 * result + enableLogging.hashCode()
        result = 31 * result + syncAlignment.hashCode()
        result = 31 * result + textNormalization.hashCode()
        result = 31 * result + seed.hashCode()
        result = 31 * result + timeouts.hashCode()
        result = 31 * result + keepAlive.hashCode()
        return result
    }

    override fun toString(): String =
        "MultiContextDialogueOptions(modelId=$modelId, languageCode=$languageCode, " +
            "outputFormat=$outputFormat, enableLogging=$enableLogging, " +
            "syncAlignment=$syncAlignment, textNormalization=$textNormalization, seed=$seed, " +
            "timeouts=$timeouts, keepAlive=$keepAlive)"

    // ElevenLabs allows several voices per context only for eleven_v3. The conversational v3
    // model, which is the default, and every v4 model allow one.
    internal val maxVoicesPerContext: Int
        get() = if (modelId == ModelId.ElevenV3) {
            MultiContextDialogueSession.MAX_VOICES_PER_CONTEXT
        } else {
            1
        }

    private companion object {
        val DIALOGUE_MODEL_PREFIXES = listOf("eleven_v3", "eleven_v4")
        const val MAX_SEED = 4_294_967_295L
        const val INACTIVITY_TIMEOUT_MILLIS = 20_000L
    }
}

/**
 * Creates a [MultiContextDialogueOptions]. Set the values to change in [configure].
 *
 * @throws IllegalArgumentException if a value is invalid.
 */
@Throws(IllegalArgumentException::class)
public fun MultiContextDialogueOptions(
    configure: MultiContextDialogueOptions.Builder.() -> Unit = {},
): MultiContextDialogueOptions = MultiContextDialogueOptions.Builder().apply(configure).build()

/** Configuration of a single context in a [MultiContextDialogueSession]. */
public class MultiContextDialogueContextOptions private constructor(
    /**
     * Voice stability from 0 to 1, or `null` for the default. Lower values give a broader emotional
     * range. It is the only voice setting Eleven v3 dialogue supports.
     */
    public val stability: Double?,
    /** Pronunciation dictionaries applied to this context, in priority order. */
    public val pronunciationDictionaries: List<PronunciationDictionaryLocator>,
) {
    init {
        require(stability == null || stability in 0.0..1.0) { "stability must be between 0 and 1." }
    }

    /** Returns a builder with these values, to create a changed copy. */
    public fun toBuilder(): Builder = Builder().also {
        it.stability = stability
        it.pronunciationDictionaries = pronunciationDictionaries
    }

    /** Builds a [MultiContextDialogueContextOptions]. Every value starts at its default. */
    public class Builder {
        /**
         * Voice stability from 0 to 1, or `null` for the default. Lower values give a broader
         * emotional
         * range. It is the only voice setting Eleven v3 dialogue supports.
         */
        public var stability: Double? = null
        /** Pronunciation dictionaries applied to this context, in priority order. */
        public var pronunciationDictionaries: List<PronunciationDictionaryLocator> = emptyList()

        /**
         * Creates the [MultiContextDialogueContextOptions].
         *
         * @throws IllegalArgumentException if a value is invalid.
         */
        @Throws(IllegalArgumentException::class)
        public fun build(): MultiContextDialogueContextOptions = MultiContextDialogueContextOptions(
            stability,
            pronunciationDictionaries,
        )
    }

    override fun equals(other: Any?): Boolean =
        other is MultiContextDialogueContextOptions &&
            stability == other.stability &&
            pronunciationDictionaries == other.pronunciationDictionaries

    override fun hashCode(): Int {
        var result = stability.hashCode()
        result = 31 * result + pronunciationDictionaries.hashCode()
        return result
    }

    override fun toString(): String =
        "MultiContextDialogueContextOptions(stability=$stability, " +
            "pronunciationDictionaries=$pronunciationDictionaries)"
}

/**
 * Creates a [MultiContextDialogueContextOptions]. Set the values to change in [configure].
 *
 * @throws IllegalArgumentException if a value is invalid.
 */
@Throws(IllegalArgumentException::class)
public fun MultiContextDialogueContextOptions(
    configure: MultiContextDialogueContextOptions.Builder.() -> Unit = {},
): MultiContextDialogueContextOptions =
    MultiContextDialogueContextOptions.Builder().apply(configure).build()

/** Client-side time limits for a [MultiContextDialogueSession]. */
public class MultiContextDialogueTimeouts private constructor(
    /** Maximum time allowed to establish the connection, in milliseconds. */
    public val connectTimeoutMillis: Long,
    /** Maximum time allowed for a send operation, in milliseconds. */
    public val sendTimeoutMillis: Long,
    /** Maximum time a finishing context or session waits for ElevenLabs, in milliseconds. */
    public val finishTimeoutMillis: Long,
) {
    init {
        require(connectTimeoutMillis > 0) { "connectTimeoutMillis must be greater than 0." }
        require(sendTimeoutMillis > 0) { "sendTimeoutMillis must be greater than 0." }
        require(finishTimeoutMillis > 0) { "finishTimeoutMillis must be greater than 0." }
    }

    /** Returns a builder with these values, to create a changed copy. */
    public fun toBuilder(): Builder = Builder().also {
        it.connectTimeoutMillis = connectTimeoutMillis
        it.sendTimeoutMillis = sendTimeoutMillis
        it.finishTimeoutMillis = finishTimeoutMillis
    }

    /** Builds a [MultiContextDialogueTimeouts]. Every value starts at its default. */
    public class Builder {
        /** Maximum time allowed to establish the connection, in milliseconds. */
        public var connectTimeoutMillis: Long = 10_000
        /** Maximum time allowed for a send operation, in milliseconds. */
        public var sendTimeoutMillis: Long = 10_000
        /** Maximum time a finishing context or session waits for ElevenLabs, in milliseconds. */
        public var finishTimeoutMillis: Long = 60_000

        /**
         * Creates the [MultiContextDialogueTimeouts].
         *
         * @throws IllegalArgumentException if a value is invalid.
         */
        @Throws(IllegalArgumentException::class)
        public fun build(): MultiContextDialogueTimeouts = MultiContextDialogueTimeouts(
            connectTimeoutMillis,
            sendTimeoutMillis,
            finishTimeoutMillis,
        )
    }

    override fun equals(other: Any?): Boolean =
        other is MultiContextDialogueTimeouts &&
            connectTimeoutMillis == other.connectTimeoutMillis &&
            sendTimeoutMillis == other.sendTimeoutMillis &&
            finishTimeoutMillis == other.finishTimeoutMillis

    override fun hashCode(): Int {
        var result = connectTimeoutMillis.hashCode()
        result = 31 * result + sendTimeoutMillis.hashCode()
        result = 31 * result + finishTimeoutMillis.hashCode()
        return result
    }

    override fun toString(): String =
        "MultiContextDialogueTimeouts(connectTimeoutMillis=$connectTimeoutMillis, " +
            "sendTimeoutMillis=$sendTimeoutMillis, finishTimeoutMillis=$finishTimeoutMillis)"
}

/**
 * Creates a [MultiContextDialogueTimeouts]. Set the values to change in [configure].
 *
 * @throws IllegalArgumentException if a value is invalid.
 */
@Throws(IllegalArgumentException::class)
public fun MultiContextDialogueTimeouts(
    configure: MultiContextDialogueTimeouts.Builder.() -> Unit = {},
): MultiContextDialogueTimeouts = MultiContextDialogueTimeouts.Builder().apply(configure).build()
