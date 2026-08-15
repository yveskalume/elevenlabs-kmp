package dev.yveskalume.elevenlabs.stt

/** Result of a batch speech-to-text request. */
public data class Transcription(
    /** Detected or requested language code, when returned. */
    val languageCode: String?,
    /** Confidence of automatic language detection, when returned. */
    val languageProbability: Double?,
    /** Complete transcript text. */
    val text: String,
    /** Timestamped transcript units, when requested and returned. */
    val words: List<TranscriptionWord>,
)
