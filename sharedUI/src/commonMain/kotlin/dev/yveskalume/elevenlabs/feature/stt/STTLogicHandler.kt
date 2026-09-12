package dev.yveskalume.elevenlabs.feature.stt

import dev.yveskalume.elevenlabs.AndroidMicrophoneRecorder
import dev.yveskalume.elevenlabs.ElevenLabs
import dev.yveskalume.elevenlabs.feature.LoadingState
import dev.yveskalume.elevenlabs.stt.RealtimeSttAudioFormat
import dev.yveskalume.elevenlabs.stt.RealtimeSttCommitStrategy
import dev.yveskalume.elevenlabs.stt.RealtimeSttEvent
import dev.yveskalume.elevenlabs.stt.RealtimeSttOptions
import dev.yveskalume.elevenlabs.stt.RealtimeSttSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface STTLogicHandler {
    val uiState: StateFlow<STTUiState>

    fun toggleListening()
    fun clearError()
    fun close()
}

internal class STTLogicHandlerImpl(
    private val client: ElevenLabs,
    private val microphone: AndroidMicrophoneRecorder,
    private val scope: CoroutineScope,
) : STTLogicHandler {

    private val _uiState = MutableStateFlow(STTUiState())
    override val uiState = _uiState.asStateFlow()

    private var transcriptionJob: Job? = null
    private var microphoneCollectionJob: Job? = null
    private var session: RealtimeSttSession? = null

    override fun toggleListening() {
        if (_uiState.value.isListening) stopListening() else startListening()
    }

    override fun clearError() {
        _uiState.update { state ->
            if (state.processingState is LoadingState.Error) {
                state.copy(processingState = LoadingState.Idle(Unit))
            } else state
        }
    }

    override fun close() {
        transcriptionJob?.cancel()
        microphone.stop()
    }

    private fun startListening() {
        if (_uiState.value.processingState == LoadingState.Loading) return
        transcriptionJob?.cancel()
        transcriptionJob = scope.launch {
            _uiState.update {
                it.copy(
                    partialTranscript = "",
                    processingState = LoadingState.Loading,
                    isStopping = false,
                )
            }
            try {
                val activeSession = client.speechToText.openRealtimeSession(
                    options = RealtimeSttOptions(
                        audioFormat = RealtimeSttAudioFormat.Pcm16000,
                        commitStrategy = RealtimeSttCommitStrategy.Manual,
                        includeTimestamps = true,
                        includeLanguageDetection = true,
                    ),
                )
                session = activeSession
                microphoneCollectionJob = launch {
                    microphone.audio.collect { bytes ->
                        if (bytes.isNotEmpty()) activeSession.sendAudio(bytes)
                    }
                }
                microphone.start(STT_SAMPLE_RATE)
                activeSession.events.collect { event ->
                    when (event) {
                        is RealtimeSttEvent.PartialTranscript -> updatePartial(event.text)
                        is RealtimeSttEvent.FinalTranscript -> updatePartial(event.text)
                        is RealtimeSttEvent.CommittedTranscript -> {
                            appendCommitted(event.text)
                            finishListening()
                        }
                        else -> Unit
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                _uiState.update {
                    it.copy(
                        processingState = LoadingState.Error(
                            throwable.message ?: "Could not transcribe microphone audio.",
                        ),
                        isStopping = false,
                    )
                }
            } finally {
                cleanup()
            }
        }
    }

    private fun stopListening() {
        if (_uiState.value.isStopping) return
        _uiState.update { it.copy(isStopping = true) }
        if (session == null) {
            transcriptionJob?.cancel()
            microphone.stop()
            _uiState.update {
                it.copy(processingState = LoadingState.Idle(Unit), isStopping = false)
            }
            return
        }
        scope.launch {
            try {
                microphone.stop()
                microphoneCollectionJob?.cancelAndJoin()
                microphoneCollectionJob = null
                session?.commit()
            } catch (throwable: Throwable) {
                transcriptionJob?.cancel()
                _uiState.update {
                    it.copy(
                        processingState = LoadingState.Error(
                            throwable.message ?: "Could not finish the transcription.",
                        ),
                        isStopping = false,
                    )
                }
            }
        }
    }

    private fun updatePartial(text: String) {
        _uiState.update { it.copy(partialTranscript = text) }
    }

    private fun appendCommitted(text: String) {
        if (text.isBlank()) return
        _uiState.update { state ->
            state.copy(
                committedTranscript = listOf(state.committedTranscript, text)
                    .filter { it.isNotBlank() }
                    .joinToString(" "),
                partialTranscript = "",
            )
        }
    }

    private suspend fun finishListening() {
        cleanup()
        _uiState.update {
            it.copy(processingState = LoadingState.Idle(Unit), isStopping = false)
        }
    }

    private suspend fun cleanup() {
        microphone.stop()
        microphoneCollectionJob?.cancel()
        microphoneCollectionJob = null
        val activeSession = session
        session = null
        withContext(NonCancellable) { runCatching { activeSession?.close() } }
        _uiState.update { it.copy(isStopping = false) }
    }

    private companion object {
        const val STT_SAMPLE_RATE = 16_000
    }
}
