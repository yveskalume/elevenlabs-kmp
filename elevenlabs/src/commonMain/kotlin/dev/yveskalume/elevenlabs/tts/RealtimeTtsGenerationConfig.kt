package dev.yveskalume.elevenlabs.tts

/** Controls how much text ElevenLabs buffers before realtime audio generation. */
public class RealtimeTtsGenerationConfig @Throws(IllegalArgumentException::class) constructor(
    chunkLengthSchedule: List<Int>,
) {
    /** Positive character-count thresholds applied successively by the service. */
    public val chunkLengthSchedule: List<Int> = chunkLengthSchedule.toList()

    init {
        // Checks the copy this object keeps, which later changes to the caller's list can't reach.
        require(this.chunkLengthSchedule.isNotEmpty()) { "chunkLengthSchedule cannot be empty." }
        require(this.chunkLengthSchedule.all { it > 0 }) {
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
