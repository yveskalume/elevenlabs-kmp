package dev.yveskalume.elevenlabs.stt

/** Timestamp detail requested for a batch transcription. */
public enum class TimestampsGranularity(internal val value: String) {
    /** Do not request timestamped transcription units. */
    None("none"),
    /** Request one timestamp range per word. */
    Word("word"),
    /** Request one timestamp range per character. */
    Character("character"),
}
