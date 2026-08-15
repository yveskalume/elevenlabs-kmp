package dev.yveskalume.elevenlabs.tts

/** Optional controls that tune the generated voice. */
public data class VoiceSettings(
    /** Voice consistency from 0 to 1, or `null` to use the service default. */
    val stability: Double? = null,
    /** Similarity to the source voice from 0 to 1. */
    val similarityBoost: Double? = null,
    /** Style exaggeration accepted by the selected model; must not be negative. */
    val style: Double? = null,
    /** Whether to enable speaker similarity boost. */
    val useSpeakerBoost: Boolean? = null,
    /** Speech-speed multiplier; must be greater than zero. */
    val speed: Double? = null,
) {
    init {
        require(stability == null || stability in 0.0..1.0) { "stability must be between 0 and 1." }
        require(similarityBoost == null || similarityBoost in 0.0..1.0) {
            "similarityBoost must be between 0 and 1."
        }
        require(style == null || style >= 0.0) { "style cannot be negative." }
        require(speed == null || speed > 0.0) { "speed must be greater than 0." }
    }
}
