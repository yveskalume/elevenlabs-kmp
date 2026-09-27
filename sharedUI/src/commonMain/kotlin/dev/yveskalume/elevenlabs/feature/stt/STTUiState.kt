package dev.yveskalume.elevenlabs.feature.stt

sealed interface ListeningStatus {
    data object Idle : ListeningStatus

    /** Opening the realtime session and starting the microphone. */
    data object Connecting : ListeningStatus

    /** Microphone audio is streaming to the session. */
    data object Listening : ListeningStatus

    /** Microphone stopped, waiting for the committed transcript. */
    data object Finishing : ListeningStatus
    data class Error(val message: String) : ListeningStatus

    val isActive: Boolean
        get() = this == Connecting || this == Listening || this == Finishing
}

data class STTUiState(
    val committedTranscript: String = "",
    val partialTranscript: String = "",
    val status: ListeningStatus = ListeningStatus.Idle,
) {
    val isListening: Boolean
        get() = status == ListeningStatus.Listening

    val transcript: String
        get() = listOf(committedTranscript, partialTranscript)
            .filter { it.isNotBlank() }
            .joinToString(" ")
}
