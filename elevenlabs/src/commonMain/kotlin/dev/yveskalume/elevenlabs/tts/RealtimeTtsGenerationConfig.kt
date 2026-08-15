package dev.yveskalume.elevenlabs.tts

data class RealtimeTtsGenerationConfig(
    val chunkLengthSchedule: List<Int>,
) {
    init {
        require(chunkLengthSchedule.isNotEmpty()) { "chunkLengthSchedule cannot be empty." }
        require(chunkLengthSchedule.all { it > 0 }) {
            "Every chunkLengthSchedule value must be greater than 0."
        }
    }
}