package dev.yveskalume.elevenlabs

import dev.yveskalume.elevenlabs.voices.Voice

data class SampleUiState(
    val apiKey: String = "",
    val voices: List<Voice> = emptyList(),
    val selectedVoiceId: String? = null,
    val text: String = "",
    val feature: SampleFeature = SampleFeature.TextToSpeech,
    val mode: SynthesisMode = SynthesisMode.Generate,
    val partialTranscript: String = "",
    val committedTranscript: String = "",
    val isStoppingTranscription: Boolean = false,
    val requestState: RequestState = RequestState.Idle
) {
    val selectedVoice: Voice?
        get() = voices.firstOrNull { it.id == selectedVoiceId }

    val canPlay: Boolean
        get() = requestState == RequestState.Idle && selectedVoice != null && text.isNotBlank()

    val isTranscribing: Boolean
        get() = feature == SampleFeature.SpeechToText && requestState == RequestState.Loading

    val isLoading: Boolean
        get() = requestState == RequestState.Loading
    val isRealtimeActive: Boolean
        get() = isLoading && mode == SynthesisMode.Realtime
    val transcriptText: String
        get() = partialTranscript.ifBlank { committedTranscript }
}

sealed interface RequestState {
    data object Idle : RequestState
    data object Loading : RequestState
    data class Error(val message: String) : RequestState
}

enum class SynthesisMode {
    Generate,
    Stream,
    Realtime,
}

enum class SampleFeature {
    TextToSpeech,
    SpeechToText,
}
