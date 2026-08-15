package dev.yveskalume.elevenlabs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.yveskalume.elevenlabs.tts.OutputFormat
import dev.yveskalume.elevenlabs.tts.RealtimeTtsOptions
import dev.yveskalume.elevenlabs.tts.TextToSpeechRequest
import dev.yveskalume.elevenlabs.stt.RealtimeSttAudioFormat
import dev.yveskalume.elevenlabs.stt.RealtimeSttCommitStrategy
import dev.yveskalume.elevenlabs.stt.RealtimeSttEvent
import dev.yveskalume.elevenlabs.stt.RealtimeSttOptions
import dev.yveskalume.elevenlabs.stt.RealtimeSttSession
import dev.yveskalume.elevenlabs.voices.ListVoicesRequest
import dev.yveskalume.elevenlabs.voices.Voice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SampleViewModel(
    apiKey: String,
    private val microphoneAudio: Flow<ByteArray>,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SampleUiState(apiKey = apiKey))
    val uiState = _uiState.asStateFlow()

    private val audioChannel = Channel<AudioCommand>(Channel.BUFFERED)
    val audioCommands = audioChannel.receiveAsFlow()

    private val microphoneChannel = Channel<MicrophoneCommand>(Channel.BUFFERED)
    val microphoneCommands = microphoneChannel.receiveAsFlow()

    private var synthesisJob: Job? = null
    private var transcriptionJob: Job? = null
    private var microphoneCollectionJob: Job? = null
    private var transcriptionSession: RealtimeSttSession? = null

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

    fun selectFeature(feature: SampleFeature) {
        if (_uiState.value.requestState == RequestState.Loading) return
        _uiState.update { it.copy(feature = feature) }
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
            SynthesisMode.Realtime -> realtime(current, voice)
        }
    }

    fun stopPlayback() {
        synthesisJob?.cancel()
        synthesisJob = null
        audioChannel.trySend(AudioCommand.Stop)
        _uiState.update { it.copy(requestState = RequestState.Idle) }
    }

    fun toggleTranscription() {
        if (_uiState.value.isTranscribing) {
            stopTranscription()
        } else {
            startTranscription()
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
        launchSynthesis {
            val audio = client.textToSpeech.generate(
                TextToSpeechRequest(voiceId = voice.id, text = current.text),
            )
            audioChannel.send(AudioCommand.PlayComplete(audio.bytes))
        }
    }

    private fun stream(current: SampleUiState, voice: Voice) {
        launchSynthesis {
            val chunks = mutableListOf<ByteArray>()
            var totalBytes = 0L
            client.textToSpeech.stream(
                TextToSpeechRequest(voiceId = voice.id, text = current.text),
            ).collect { chunk ->
                chunks += chunk.bytes
                totalBytes += chunk.bytes.size
            }
            audioChannel.send(AudioCommand.PlayComplete(chunks.joinAudioChunks(totalBytes)))
        }
    }

    private fun realtime(current: SampleUiState, voice: Voice) {
        launchSynthesis {
            audioChannel.send(AudioCommand.StartStream(REALTIME_SAMPLE_RATE))
            try {
                client.textToSpeech.realtime(
                    voiceId = voice.id,
                    text = current.text.asRealtimeInput(),
                    options = RealtimeTtsOptions(
                        modelId = REALTIME_MODEL_ID,
                        outputFormat = OutputFormat.Pcm_24000,
                    ),
                ).collect { chunk ->
                    audioChannel.send(AudioCommand.StreamChunk(chunk.bytes))
                }
                audioChannel.send(AudioCommand.FinishStream)
            } catch (cancellation: CancellationException) {
                audioChannel.trySend(AudioCommand.Stop)
                throw cancellation
            } catch (throwable: Throwable) {
                audioChannel.trySend(AudioCommand.Stop)
                throw throwable
            }
        }
    }

    private fun startTranscription() {
        if (_uiState.value.requestState != RequestState.Idle) return
        transcriptionJob?.cancel()
        transcriptionJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    requestState = RequestState.Loading,
                    partialTranscript = "",
                    committedTranscript = "",
                    isStoppingTranscription = false,
                )
            }
            try {
                val session = client.speechToText.openRealtimeSession(
                    options = RealtimeSttOptions(
                        audioFormat = RealtimeSttAudioFormat.Pcm16000,
                        commitStrategy = RealtimeSttCommitStrategy.Manual,
                        includeTimestamps = true,
                        includeLanguageDetection = true,
                    ),
                )
                transcriptionSession = session
                microphoneCollectionJob = launch {
                    microphoneAudio.collect { bytes ->
                        if (bytes.isNotEmpty()) session.sendAudio(bytes)
                    }
                }
                microphoneChannel.send(MicrophoneCommand.Start(STT_SAMPLE_RATE))

                session.events.collect { event ->
                    when (event) {
                        is RealtimeSttEvent.PartialTranscript -> _uiState.update {
                            it.copy(partialTranscript = event.text)
                        }
                        is RealtimeSttEvent.FinalTranscript -> _uiState.update {
                            it.copy(partialTranscript = event.text)
                        }
                        is RealtimeSttEvent.CommittedTranscript -> {
                            _uiState.update {
                                it.copy(
                                    committedTranscript = event.text,
                                    partialTranscript = "",
                                )
                            }
                            finishTranscription()
                        }
                        else -> Unit
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                _uiState.update {
                    it.copy(
                        requestState = RequestState.Error(
                            throwable.message ?: "Could not transcribe microphone audio.",
                        ),
                    )
                }
            } finally {
                cleanupTranscription()
            }
        }
    }

    private fun stopTranscription() {
        if (_uiState.value.isStoppingTranscription) return
        _uiState.update { it.copy(isStoppingTranscription = true) }
        viewModelScope.launch {
            try {
                microphoneChannel.send(MicrophoneCommand.Stop)
                microphoneCollectionJob?.cancelAndJoin()
                microphoneCollectionJob = null
                transcriptionSession?.commit()
            } catch (throwable: Throwable) {
                transcriptionJob?.cancel()
                _uiState.update {
                    it.copy(
                        requestState = RequestState.Error(
                            throwable.message ?: "Could not finish the transcription.",
                        ),
                    )
                }
            }
        }
    }

    private suspend fun finishTranscription() {
        cleanupTranscription()
        _uiState.update {
            it.copy(requestState = RequestState.Idle, isStoppingTranscription = false)
        }
    }

    private suspend fun cleanupTranscription() {
        microphoneChannel.trySend(MicrophoneCommand.Stop)
        microphoneCollectionJob?.cancel()
        microphoneCollectionJob = null
        val session = transcriptionSession
        transcriptionSession = null
        withContext(NonCancellable) { runCatching { session?.close() } }
        _uiState.update { it.copy(isStoppingTranscription = false) }
    }

    private fun String.asRealtimeInput(): Flow<String> = flow {
        REALTIME_TEXT_CHUNK.findAll(this@asRealtimeInput).forEach { match ->
            emit(match.value)
            delay(REALTIME_CHUNK_DELAY_MS)
        }
    }

    private fun launchSynthesis(block: suspend () -> Unit) {
        synthesisJob?.cancel()
        synthesisJob = launchRequest(block)
    }

    private fun launchRequest(block: suspend () -> Unit): Job =
        viewModelScope.launch {
            _uiState.update { it.copy(requestState = RequestState.Loading) }
            try {
                block()
                _uiState.update { it.copy(requestState = RequestState.Idle) }
            } catch (cancellation: CancellationException) {
                _uiState.update { it.copy(requestState = RequestState.Idle) }
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
        synthesisJob?.cancel()
        transcriptionJob?.cancel()
        microphoneChannel.trySend(MicrophoneCommand.Stop)
        client.close()
    }

    private companion object {
        const val REALTIME_SAMPLE_RATE = 24_000
        const val REALTIME_CHUNK_DELAY_MS = 80L
        const val REALTIME_MODEL_ID = "eleven_flash_v2_5"
        const val STT_SAMPLE_RATE = 16_000
        val REALTIME_TEXT_CHUNK = Regex("\\S+\\s*")
    }
}

sealed interface AudioCommand {
    data class PlayComplete(val bytes: ByteArray) : AudioCommand
    data class StartStream(val sampleRate: Int) : AudioCommand
    data class StreamChunk(val bytes: ByteArray) : AudioCommand
    data object FinishStream : AudioCommand
    data object Stop : AudioCommand
}

sealed interface MicrophoneCommand {
    data class Start(val sampleRate: Int) : MicrophoneCommand
    data object Stop : MicrophoneCommand
}
