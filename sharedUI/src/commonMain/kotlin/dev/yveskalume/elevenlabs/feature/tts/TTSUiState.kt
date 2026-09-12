package dev.yveskalume.elevenlabs.feature.tts

import dev.yveskalume.elevenlabs.feature.LoadingState
import dev.yveskalume.elevenlabs.voices.Voice

enum class TTSMode {
    Generate,
    Stream,
    Realtime,
}

data class TTSUiState(
    val voices: LoadingState<List<Voice>> = LoadingState.Loading,
    val selectedVoiceId: String? = null,
    val text: String = "",
    val mode: TTSMode = TTSMode.Generate,
    val processingState: LoadingState<Unit> = LoadingState.Idle(Unit),
) {
    val selectedVoice: Voice?
        get() = voices.dataOrNull?.firstOrNull { it.id == selectedVoiceId }
            ?: voices.dataOrNull?.firstOrNull()

    val isProcessing: Boolean
        get() = processingState == LoadingState.Loading


    val canCreateSpeech: Boolean
        get() = !isProcessing && selectedVoice != null && text.isNotBlank()
}