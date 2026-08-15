package dev.yveskalume.elevenlabs.tts

data class RealtimeTtsAlignment(
    val characters: List<String>,
    val characterStartTimesMs: List<Int>,
    val characterDurationsMs: List<Int>,
)