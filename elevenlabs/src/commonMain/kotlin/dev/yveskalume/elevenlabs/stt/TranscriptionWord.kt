package dev.yveskalume.elevenlabs.stt

/** A timestamped unit in a transcription. */
public data class TranscriptionWord(
    /** Transcribed text for this unit. */
    val text: String,
    /** Start offset in seconds, when available. */
    val startSeconds: Double?,
    /** End offset in seconds, when available. */
    val endSeconds: Double?,
    /** Unit type reported by ElevenLabs, such as a word or spacing unit. */
    val type: String,
    /** Diarized speaker identifier, when available. */
    val speakerId: String?,
    /** Natural-log probability assigned to this unit, when available. */
    val logProbability: Double?,
    /** Input channel associated with this unit, when available. */
    val channelIndex: Int?,
)
