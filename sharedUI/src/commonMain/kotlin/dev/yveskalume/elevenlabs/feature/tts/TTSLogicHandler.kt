package dev.yveskalume.elevenlabs.feature.tts

import dev.yveskalume.elevenlabs.AndroidAudioPlayer
import dev.yveskalume.elevenlabs.ElevenLabs
import dev.yveskalume.elevenlabs.feature.LoadingState
import dev.yveskalume.elevenlabs.tts.OutputFormat
import dev.yveskalume.elevenlabs.tts.RealtimeTtsOptions
import dev.yveskalume.elevenlabs.tts.TextToSpeechRequest
import dev.yveskalume.elevenlabs.voices.ListVoicesRequest
import dev.yveskalume.elevenlabs.voices.Voice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds


interface TTSLogicHandler {
    val uiState: StateFlow<TTSUiState>

    fun loadVoices()
    fun updateText(value: String)
    fun selectVoice(voiceId: String)
    fun selectMode(mode: TTSMode)
    fun createSpeech()
    fun stopPlayback()
    fun clearError()
    fun close()
}

internal class TTSLogicHandlerImpl(
    private val client: ElevenLabs,
    private val audioPlayer: AndroidAudioPlayer,
    private val scope: CoroutineScope,
) : TTSLogicHandler {

    private val _uiState = MutableStateFlow(TTSUiState())
    override val uiState = _uiState.asStateFlow().onStart {
        loadVoices()
    }.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TTSUiState()
    )

    private var synthesisJob: Job? = null


    override fun loadVoices() {
        scope.launch {
            _uiState.update { it.copy(voices = LoadingState.Loading) }
            val voiceState = try {
                val voices = client.voices.list(ListVoicesRequest(pageSize = 100)).voices
                LoadingState.Idle(voices)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                LoadingState.Error(throwable.message ?: "Could not load voices.")
            }
            _uiState.update { state ->
                state.copy(
                    voices = voiceState,
                )
            }
        }
    }

    override fun updateText(value: String) {
        _uiState.update { it.copy(text = value) }
    }

    override fun selectVoice(voiceId: String) {
        _uiState.update { it.copy(selectedVoiceId = voiceId) }
    }

    override fun selectMode(mode: TTSMode) {
        _uiState.update { it.copy(mode = mode) }
    }

    override fun createSpeech() {
        val state = _uiState.value
        val voice = state.selectedVoice ?: return
        if (!state.canCreateSpeech || state.isProcessing) return
        synthesisJob?.cancel()
        synthesisJob = scope.launch {
            _uiState.update { it.copy(processingState = LoadingState.Loading) }
            val processingState = try {
                when (state.mode) {
                    TTSMode.Generate -> generate(state.text, voice)
                    TTSMode.Stream -> stream(state.text, voice)
                    TTSMode.Realtime -> realtime(state.text, voice)
                }
                LoadingState.Idle(Unit)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                audioPlayer.stop()
                LoadingState.Error(throwable.message ?: "Could not create speech.")
            }
            _uiState.update { it.copy(processingState = processingState) }
        }
    }

    override fun stopPlayback() {
        synthesisJob?.cancel()
        synthesisJob = null
        audioPlayer.stop()
        _uiState.update { it.copy(processingState = LoadingState.Idle(Unit)) }
    }

    override fun clearError() {
        _uiState.update { state ->
            if (state.processingState is LoadingState.Error) {
                state.copy(processingState = LoadingState.Idle(Unit))
            } else state
        }
    }

    override fun close() {
        synthesisJob?.cancel()
        audioPlayer.stop()
    }

    private suspend fun generate(text: String, voice: Voice) {
        val audio = client.textToSpeech.generate(
            TextToSpeechRequest(voiceId = voice.id, text = text),
        )
        audioPlayer.play(audio.bytes)
    }

    private suspend fun stream(text: String, voice: Voice) {
        val chunks = mutableListOf<ByteArray>()
        var totalBytes = 0L
        client.textToSpeech.stream(
            TextToSpeechRequest(voiceId = voice.id, text = text),
        ).collect { chunk ->
            chunks += chunk.bytes
            totalBytes += chunk.bytes.size
        }
        audioPlayer.play(chunks.joinAudioChunks(totalBytes))
    }

    private suspend fun realtime(text: String, voice: Voice) {
        audioPlayer.startStream(REALTIME_SAMPLE_RATE)
        try {
            client.textToSpeech.realtime(
                voiceId = voice.id,
                text = text.asRealtimeInput(),
                options = RealtimeTtsOptions(
                    modelId = REALTIME_MODEL_ID,
                    outputFormat = OutputFormat.Pcm_24000,
                ),
            ).collect { chunk -> audioPlayer.writeStream(chunk.bytes) }
            audioPlayer.finishStream()
        } catch (throwable: Throwable) {
            audioPlayer.stop()
            throw throwable
        }
    }

    private fun String.asRealtimeInput(): Flow<String> = flow {
        REALTIME_TEXT_CHUNK.findAll(this@asRealtimeInput).forEach { match ->
            emit(match.value)
            delay(REALTIME_CHUNK_DELAY_MS.milliseconds)
        }
    }

    private fun List<ByteArray>.joinAudioChunks(totalBytes: Long): ByteArray {
        require(totalBytes <= Int.MAX_VALUE) { "Generated audio is too large to play." }
        val result = ByteArray(totalBytes.toInt())
        var offset = 0
        forEach { chunk ->
            chunk.copyInto(result, destinationOffset = offset)
            offset += chunk.size
        }
        return result
    }

    private companion object {
        const val REALTIME_SAMPLE_RATE = 24_000
        const val REALTIME_CHUNK_DELAY_MS = 80L
        const val REALTIME_MODEL_ID = "eleven_flash_v2_5"
        val REALTIME_TEXT_CHUNK = Regex("\\S+\\s*")
    }
}
