package dev.yveskalume.elevenlabs

import dev.yveskalume.elevenlabs.voices.Voice

data class SampleUiState(
    val apiKey: String = "",
    val voices: List<Voice> = emptyList(),
    val selectedVoiceId: String? = null,
    val text: String = "",
    val mode: SynthesisMode = SynthesisMode.Generate,
    val requestState: RequestState = RequestState.Idle
) {
    val selectedVoice: Voice?
        get() = voices.firstOrNull { it.id == selectedVoiceId }

    val canPlay: Boolean
        get() = requestState == RequestState.Idle && selectedVoice != null && text.isNotBlank()
}

sealed interface RequestState {
    data object Idle  : RequestState
    data object Loading : RequestState
    data class Error(val message: String) : RequestState
}

enum class SynthesisMode {
    Generate,
    Stream,
}