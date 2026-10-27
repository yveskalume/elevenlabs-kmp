package dev.yveskalume.elevenlabs.dialogue

import dev.yveskalume.elevenlabs.tts.OutputFormat
import dev.yveskalume.elevenlabs.tts.PronunciationDictionaryLocator
import dev.yveskalume.elevenlabs.tts.RealtimeTtsKeepAlive
import dev.yveskalume.elevenlabs.tts.TextNormalization

/**
 * Connection-level configuration for a [MultiContextDialogueSession]. Speakers and their settings
 * are chosen per context, in [MultiContextDialogueSession.openContext].
 */
public data class MultiContextDialogueOptions(
    /**
     * Eleven v3 model identifier, or `null` for the service default, `eleven_v3_conversational`,
     * which allows one voice per context.
     */
    val modelId: String? = null,
    /** Language code used for text normalization, or `null` to let the model infer it. */
    val languageCode: String? = null,
    /** Encoding and sample rate of emitted audio. */
    val outputFormat: OutputFormat = OutputFormat.Mp3_44100_128,
    /** Whether request logging is enabled on ElevenLabs. Disable for zero-retention mode. */
    val enableLogging: Boolean = true,
    /** Whether audio events include character alignment information. */
    val syncAlignment: Boolean = false,
    /** Text normalization behavior, or `null` to use the service default. */
    val textNormalization: TextNormalization? = null,
    /** Seed for deterministic sampling, from 1 through 4294967295, or `null` for random sampling. */
    val seed: Long? = null,
    /** Client-side timeouts. */
    val timeouts: MultiContextDialogueTimeouts = MultiContextDialogueTimeouts(),
    /**
     * Automatic keepalive for open contexts. ElevenLabs closes a context after 20 seconds without
     * input, so the interval must be shorter than that.
     */
    val keepAlive: RealtimeTtsKeepAlive = RealtimeTtsKeepAlive(),
) {
    init {
        require(modelId == null || modelId.startsWith(V3_MODEL_PREFIX)) {
            "Text-to-dialogue requires an Eleven v3 model (\"$V3_MODEL_PREFIX…\")."
        }
        require(languageCode == null || languageCode.isNotBlank()) { "languageCode cannot be blank." }
        require(seed == null || seed in 1..MAX_SEED) { "seed must be between 1 and $MAX_SEED." }
        require(!keepAlive.enabled || keepAlive.intervalMillis < INACTIVITY_TIMEOUT_MILLIS) {
            "The keepalive interval must be shorter than the 20-second dialogue inactivity timeout."
        }
    }

    internal val maxVoicesPerContext: Int
        get() = if (modelId == null || modelId == CONVERSATIONAL_MODEL) {
            1
        } else {
            MultiContextDialogueSession.MAX_VOICES_PER_CONTEXT
        }

    private companion object {
        const val V3_MODEL_PREFIX = "eleven_v3"
        const val CONVERSATIONAL_MODEL = "eleven_v3_conversational"
        const val MAX_SEED = 4_294_967_295L
        const val INACTIVITY_TIMEOUT_MILLIS = 20_000L
    }
}

/** Configuration of a single context in a [MultiContextDialogueSession]. */
public data class MultiContextDialogueContextOptions(
    /**
     * Voice stability from 0 to 1, or `null` for the default. Lower values give a broader emotional
     * range. It is the only voice setting Eleven v3 dialogue supports.
     */
    val stability: Double? = null,
    /** Pronunciation dictionaries applied to this context, in priority order. */
    val pronunciationDictionaries: List<PronunciationDictionaryLocator> = emptyList(),
) {
    init {
        require(stability == null || stability in 0.0..1.0) { "stability must be between 0 and 1." }
    }
}

/** Client-side time limits for a [MultiContextDialogueSession]. */
public data class MultiContextDialogueTimeouts(
    /** Maximum time allowed to establish the connection, in milliseconds. */
    val connectTimeoutMillis: Long = 10_000,
    /** Maximum time allowed for a send operation, in milliseconds. */
    val sendTimeoutMillis: Long = 10_000,
    /** Maximum time a finishing context or session waits for ElevenLabs, in milliseconds. */
    val finishTimeoutMillis: Long = 60_000,
) {
    init {
        require(connectTimeoutMillis > 0) { "connectTimeoutMillis must be greater than 0." }
        require(sendTimeoutMillis > 0) { "sendTimeoutMillis must be greater than 0." }
        require(finishTimeoutMillis > 0) { "finishTimeoutMillis must be greater than 0." }
    }
}
