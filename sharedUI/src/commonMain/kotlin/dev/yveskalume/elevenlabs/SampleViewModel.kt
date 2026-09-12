package dev.yveskalume.elevenlabs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.yveskalume.elevenlabs.feature.stt.STTLogicHandlerImpl
import dev.yveskalume.elevenlabs.feature.stt.STTLogicHandler
import dev.yveskalume.elevenlabs.feature.tts.TTSLogicHandlerImpl
import dev.yveskalume.elevenlabs.feature.tts.TTSLogicHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SampleFeature {
    TextToSpeech,
    SpeechToText,
}

class SampleViewModel(
    apiKey: String,
    audioPlayer: AndroidAudioPlayer,
    microphone: AndroidMicrophoneRecorder,
) : ViewModel() {

    private val client = ElevenLabs { apiKey(apiKey) }
    private val _selectedFeature = MutableStateFlow(SampleFeature.TextToSpeech)
    val selectedFeature = _selectedFeature.asStateFlow()

    val ttsLogicHandler: TTSLogicHandler = TTSLogicHandlerImpl(client, audioPlayer, viewModelScope)
    val sttLogicHandler: STTLogicHandler = STTLogicHandlerImpl(client, microphone, viewModelScope)

    fun selectFeature(feature: SampleFeature) {
        if (_selectedFeature.value == feature) return
        when (_selectedFeature.value) {
            SampleFeature.TextToSpeech -> ttsLogicHandler.stopPlayback()
            SampleFeature.SpeechToText -> {
                if (sttLogicHandler.uiState.value.isListening) sttLogicHandler.toggleListening()
            }
        }
        _selectedFeature.value = feature
    }

    override fun onCleared() {
        ttsLogicHandler.close()
        sttLogicHandler.close()
        client.close()
    }
}
