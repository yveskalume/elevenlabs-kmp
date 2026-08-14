package dev.yveskalume.elevenlabs.tts

data class VoiceSettings(
    val stability: Double? = null,
    val similarityBoost: Double? = null,
    val style: Double? = null,
    val useSpeakerBoost: Boolean? = null,
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