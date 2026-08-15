package dev.yveskalume.elevenlabs.tts

/** Controls how much text ElevenLabs buffers before realtime audio generation. */
public data class RealtimeTtsGenerationConfig(
    /** Positive character-count thresholds applied successively by the service. */
    val chunkLengthSchedule: List<Int>,
) {
    init {
        require(chunkLengthSchedule.isNotEmpty()) { "chunkLengthSchedule cannot be empty." }
        require(chunkLengthSchedule.all { it > 0 }) {
            "Every chunkLengthSchedule value must be greater than 0."
        }
    }
}
