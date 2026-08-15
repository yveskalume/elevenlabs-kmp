package dev.yveskalume.elevenlabs.stt

data class TranscriptionWord(
    val text: String,
    val startSeconds: Double?,
    val endSeconds: Double?,
    val type: String,
    val speakerId: String?,
    val logProbability: Double?,
    val channelIndex: Int?,
)
