package dev.yveskalume.elevenlabs.stt

sealed interface RealtimeSttEvent {
    data class SessionStarted(val sessionId: String) : RealtimeSttEvent
    data class PartialTranscript(val text: String) : RealtimeSttEvent
    data class FinalTranscript(
        val text: String,
        val words: List<TranscriptionWord> = emptyList(),
        val languageCode: String? = null,
    ) : RealtimeSttEvent
    data class CommittedTranscript(
        val text: String,
        val words: List<TranscriptionWord> = emptyList(),
        val languageCode: String? = null,
    ) : RealtimeSttEvent
    data class Unknown(val messageType: String?, val rawMessage: String) : RealtimeSttEvent
}