package dev.yveskalume.elevenlabs.tts

/** Character-level timing metadata for a realtime audio event. */
public data class RealtimeTtsAlignment(
    /** Characters in their spoken order. */
    val characters: List<String>,
    /** Start time of each corresponding character, in milliseconds. */
    val characterStartTimesMs: List<Int>,
    /** Duration of each corresponding character, in milliseconds. */
    val characterDurationsMs: List<Int>,
)
