package dev.yveskalume.elevenlabs.stt

enum class TimestampsGranularity(internal val value: String) {
    None("none"),
    Word("word"),
    Character("character"),
}