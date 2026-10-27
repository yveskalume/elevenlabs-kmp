package dev.yveskalume.elevenlabs.tts

/**
 * Connection-level configuration for a [MultiContextTtsSession].
 *
 * These values are fixed when the WebSocket is opened and apply to every context. Settings that
 * can differ per context live in [MultiContextTtsContextOptions].
 */
public data class MultiContextTtsOptions(
    /** Model identifier, or `null` to use the service default. */
    val modelId: String? = null,
    /** Language code, or `null` to let the selected model infer it. */
    val languageCode: String? = null,
    /** Encoding and sample rate of emitted audio. */
    val outputFormat: OutputFormat = OutputFormat.Mp3_44100_128,
    /** Whether request logging is enabled on ElevenLabs. Disable for zero-retention mode. */
    val enableLogging: Boolean = true,
    /** Whether audio events include character alignment information. */
    val syncAlignment: Boolean = false,
    /**
     * Whether ElevenLabs should reduce latency by generating as soon as it has enough text instead
     * of following a chunk length schedule. Recommended when full sentences or phrases are sent.
     */
    val autoMode: Boolean = false,
    /** Text normalization behavior, or `null` to use the service default. */
    val textNormalization: TextNormalization? = null,
    /** Seed for deterministic sampling, from 0 through 4294967295, or `null` for random sampling. */
    val seed: Long? = null,
    /** Whether the service should parse SSML tags in submitted text. */
    val enableSsmlParsing: Boolean = false,
    /** Client and server timeout configuration. [RealtimeTtsTimeouts.inactivityTimeoutSeconds] applies per context. */
    val timeouts: RealtimeTtsTimeouts = RealtimeTtsTimeouts(),
    /** Automatic keepalive applied to every open context. */
    val keepAlive: RealtimeTtsKeepAlive = RealtimeTtsKeepAlive(),
) {
    init {
        require(modelId == null || modelId.isNotBlank()) { "modelId cannot be blank." }
        require(languageCode == null || languageCode.isNotBlank()) { "languageCode cannot be blank." }
        require(seed == null || seed in 0..MAX_SEED) { "seed must be between 0 and $MAX_SEED." }
        require(
            !keepAlive.enabled ||
                keepAlive.intervalMillis < timeouts.inactivityTimeoutSeconds * 1_000L,
        ) {
            "The keepalive interval must be shorter than the ElevenLabs inactivity timeout."
        }
    }

    private companion object {
        const val MAX_SEED = 4_294_967_295L
    }
}

/** Configuration of a single context in a [MultiContextTtsSession]. */
public data class MultiContextTtsContextOptions(
    /** Voice tuning for this context, or `null` to use the voice defaults. */
    val voiceSettings: VoiceSettings? = null,
    /** Server-side text chunking for this context, or `null` to use the service default. */
    val generationConfig: RealtimeTtsGenerationConfig? = null,
    /** Pronunciation dictionaries applied to this context, in priority order. */
    val pronunciationDictionaries: List<PronunciationDictionaryLocator> = emptyList(),
) {
    init {
        require(pronunciationDictionaries.size <= MAX_PRONUNCIATION_DICTIONARIES) {
            "At most $MAX_PRONUNCIATION_DICTIONARIES pronunciation dictionaries can be applied."
        }
    }

    private companion object {
        const val MAX_PRONUNCIATION_DICTIONARIES = 3
    }
}

/** Reference to a specific version of a pronunciation dictionary stored in ElevenLabs. */
public data class PronunciationDictionaryLocator(
    /** Identifier of the pronunciation dictionary. */
    val dictionaryId: String,
    /** Version of the dictionary to apply. */
    val versionId: String,
) {
    init {
        require(dictionaryId.isNotBlank()) { "dictionaryId cannot be blank." }
        require(versionId.isNotBlank()) { "versionId cannot be blank." }
    }
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
