package dev.yveskalume.elevenlabs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.yveskalume.elevenlabs.feature.stt.STTAction
import dev.yveskalume.elevenlabs.feature.stt.STTLogicHandler
import dev.yveskalume.elevenlabs.feature.tts.TTSAction
import dev.yveskalume.elevenlabs.feature.tts.TTSLogicHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SampleFeature {
    TextToSpeech,
    SpeechToText,
}

class SampleViewModel(
    private val client: ElevenLabs,
    private val audioPlayer: AudioPlayer,
    private val microphone: MicrophoneRecorder,
) : ViewModel() {

    private val _selectedFeature = MutableStateFlow(SampleFeature.TextToSpeech)
    val selectedFeature = _selectedFeature.asStateFlow()

    private val tts = TTSLogicHandler(client, audioPlayer, viewModelScope)
    private val stt = STTLogicHandler(client, microphone, viewModelScope)

    val ttsState = tts.uiState
    val sttState = stt.uiState

    fun onTtsAction(action: TTSAction) = tts.onAction(action)
    fun onSttAction(action: STTAction) = stt.onAction(action)

    fun selectFeature(feature: SampleFeature) {
        if (_selectedFeature.value == feature) return
        when (_selectedFeature.value) {
            SampleFeature.TextToSpeech -> tts.onAction(TTSAction.StopPlayback)
            SampleFeature.SpeechToText -> stt.onAction(STTAction.Cancel)
        }
        _selectedFeature.value = feature
    }

    fun stopActiveAudio() {
        tts.onAction(TTSAction.StopPlayback)
        stt.onAction(STTAction.Cancel)
    }

    override fun onCleared() {
        audioPlayer.close()
        microphone.close()
        client.close()
    }
}
