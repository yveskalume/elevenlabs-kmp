package dev.yveskalume.elevenlabs.realtime

/** Character-level timing metadata for a realtime audio event. */
public data class Alignment(
    /** Characters in their spoken order. */
    val characters: List<String>,
    /** Start time of each corresponding character, in milliseconds. */
    val characterStartTimesMs: List<Int>,
    /** Duration of each corresponding character, in milliseconds. */
    val characterDurationsMs: List<Int>,
)
