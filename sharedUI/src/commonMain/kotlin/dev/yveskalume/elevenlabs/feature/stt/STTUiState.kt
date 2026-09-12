package dev.yveskalume.elevenlabs.feature.stt

import dev.yveskalume.elevenlabs.feature.LoadingState

data class STTUiState(
    val committedTranscript: String = "",
    val partialTranscript: String = "",
    val processingState: LoadingState<Unit> = LoadingState.Idle(Unit),
    val isStopping: Boolean = false,
) {
    val isListening: Boolean
        get() = processingState == LoadingState.Loading

    val transcript: String
        get() = listOf(committedTranscript, partialTranscript)
            .filter { it.isNotBlank() }
            .joinToString(" ")
}
