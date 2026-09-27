package dev.yveskalume.elevenlabs.feature.tts

sealed interface TTSAction {
    data object LoadVoices : TTSAction
    data class UpdateText(val value: String) : TTSAction
    data class SelectVoice(val voiceId: String) : TTSAction
    data class SelectMode(val mode: TTSMode) : TTSAction
    data object CreateSpeech : TTSAction
    data object StopPlayback : TTSAction
    data object ClearError : TTSAction
}
