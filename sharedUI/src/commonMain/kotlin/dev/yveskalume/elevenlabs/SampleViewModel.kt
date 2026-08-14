package dev.yveskalume.elevenlabs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.yveskalume.elevenlabs.tts.TextToSpeechRequest
import dev.yveskalume.elevenlabs.voices.ListVoicesRequest
import dev.yveskalume.elevenlabs.voices.Voice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SampleViewModel(apiKey: String) : ViewModel() {
    private val _uiState = MutableStateFlow(SampleUiState(apiKey = apiKey))
    val uiState = _uiState.asStateFlow()

    private val audioChannel = Channel<ByteArray>(Channel.BUFFERED)
    val audioToPlay = audioChannel.receiveAsFlow()

    private val client = ElevenLabs {
        apiKey(apiKey)
    }

    init {
        loadVoices()
    }

    fun updateText(value: String) {
        _uiState.update { it.copy(text = value) }
    }

    fun selectVoice(voiceId: String) {
        _uiState.update { it.copy(selectedVoiceId = voiceId) }
    }

    fun selectMode(mode: SynthesisMode) {
        _uiState.update { it.copy(mode = mode) }
    }

    fun loadVoices() {
        launchRequest {
            val page = client.voices.list(ListVoicesRequest(pageSize = 100))
            _uiState.update {
                it.copy(
                    voices = page.voices,
                    selectedVoiceId = page.voices.firstOrNull()?.id,
                )
            }
        }
    }

    fun synthesize() {
        val current = _uiState.value
        val voice = current.selectedVoice ?: return
        if (!current.canPlay) return

        when (current.mode) {
            SynthesisMode.Generate -> generate(current, voice)
            SynthesisMode.Stream -> stream(current, voice)
        }
    }

    fun clearError() {
        _uiState.update { state ->
            if (state.requestState is RequestState.Error) {
                state.copy(requestState = RequestState.Idle)
            } else {
                state
            }
        }
    }

    private fun generate(current: SampleUiState, voice: Voice) {
        launchRequest {
            val audio = client.textToSpeech.generate(
                TextToSpeechRequest(voiceId = voice.id, text = current.text),
            )
            audioChannel.send(audio.bytes)
        }
    }

    private fun stream(current: SampleUiState, voice: Voice) {
        launchRequest {
            val chunks = mutableListOf<ByteArray>()
            var totalBytes = 0L
            client.textToSpeech.stream(
                TextToSpeechRequest(voiceId = voice.id, text = current.text),
            ).collect { chunk ->
                chunks += chunk.bytes
                totalBytes += chunk.bytes.size
            }
            audioChannel.send(chunks.joinAudioChunks(totalBytes))
        }
    }

    private fun launchRequest(block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(requestState = RequestState.Loading) }
            try {
                block()
                _uiState.update { it.copy(requestState = RequestState.Idle) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                _uiState.update {
                    it.copy(
                        requestState = RequestState.Error(
                            throwable.message ?: "Unexpected error",
                        ),
                    )
                }
            }
        }
    }

    private fun List<ByteArray>.joinAudioChunks(totalBytes: Long): ByteArray {
        val result = ByteArray(totalBytes.toInt())
        var offset = 0
        forEach { chunk ->
            chunk.copyInto(result, destinationOffset = offset)
            offset += chunk.size
        }
        return result
    }

    override fun onCleared() {
        client.close()
        super.onCleared()
    }
}
