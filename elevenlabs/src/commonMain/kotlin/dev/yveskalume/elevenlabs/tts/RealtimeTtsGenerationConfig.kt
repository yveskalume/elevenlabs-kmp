package dev.yveskalume.elevenlabs.tts

/** Controls how much text ElevenLabs buffers before realtime audio generation. */
public class RealtimeTtsGenerationConfig @Throws(IllegalArgumentException::class) constructor(
    chunkLengthSchedule: List<Int>,
) {
    /** Positive character-count thresholds applied successively by the service. */
    public val chunkLengthSchedule: List<Int> = chunkLengthSchedule.toList() // So later changes can't bypass init.

    init {
        require(chunkLengthSchedule.isNotEmpty()) { "chunkLengthSchedule cannot be empty." }
        require(chunkLengthSchedule.all { it > 0 }) {
            "Every chunkLengthSchedule value must be greater than 0."
        }
    }

    override fun equals(other: Any?): Boolean =
        other is RealtimeTtsGenerationConfig &&
            chunkLengthSchedule == other.chunkLengthSchedule

    override fun hashCode(): Int {
        var result = chunkLengthSchedule.hashCode()
        return result
    }

    override fun toString(): String =
        "RealtimeTtsGenerationConfig(chunkLengthSchedule=$chunkLengthSchedule)"
}
