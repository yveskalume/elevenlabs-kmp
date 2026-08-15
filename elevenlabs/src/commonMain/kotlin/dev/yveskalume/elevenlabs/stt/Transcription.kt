package dev.yveskalume.elevenlabs.stt

data class Transcription(
    val languageCode: String?,
    val languageProbability: Double?,
    val text: String,
    val words: List<TranscriptionWord>,
)