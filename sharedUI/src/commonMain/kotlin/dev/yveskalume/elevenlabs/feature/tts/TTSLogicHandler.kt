package dev.yveskalume.elevenlabs.feature.tts

import dev.yveskalume.elevenlabs.AudioPlayer
import dev.yveskalume.elevenlabs.ElevenLabs
import dev.yveskalume.elevenlabs.feature.LoadingState
import dev.yveskalume.elevenlabs.feature.LogicHandler
import dev.yveskalume.elevenlabs.feature.userMessage
import dev.yveskalume.elevenlabs.tts.OutputFormat
import dev.yveskalume.elevenlabs.tts.RealtimeTtsOptions
import dev.yveskalume.elevenlabs.tts.TextToSpeechRequest
import dev.yveskalume.elevenlabs.voices.ListVoicesRequest
import dev.yveskalume.elevenlabs.voices.Voice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

internal class TTSLogicHandler(
    private val client: ElevenLabs,
    private val audioPlayer: AudioPlayer,
    scope: CoroutineScope,
) : LogicHandler<TTSUiState, TTSAction> {

    private val voiceLoads = Channel<Unit>(Channel.CONFLATED)
    private val speechCommands = Channel<SpeechCommand>(Channel.CONFLATED)

    private val _uiState = MutableStateFlow(TTSUiState())


    override val uiState: StateFlow<TTSUiState> = _uiState
        .onStart { if (_uiState.value.voices !is LoadingState.Idle) onAction(TTSAction.LoadVoices) }
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = _uiState.value,
        )

    init {
        scope.launch { voiceLoads.receiveAsFlow().collectLatest { loadVoices() } }
        scope.launch {
            speechCommands.receiveAsFlow().collectLatest { command ->
                when (command) {
                    is SpeechCommand.Speak -> synthesize(command)
                    SpeechCommand.Stop -> Unit // arriving here already cancelled the previous block
                }
            }
        }
    }

    override fun onAction(action: TTSAction) {
        when (action) {
            TTSAction.LoadVoices -> {
                _uiState.update { it.copy(voices = LoadingState.Loading) }
                voiceLoads.trySend(Unit)
            }

            is TTSAction.UpdateText -> _uiState.update { it.copy(text = action.value) }
            is TTSAction.SelectVoice -> _uiState.update { it.copy(selectedVoiceId = action.voiceId) }
            is TTSAction.SelectMode -> _uiState.update { it.copy(mode = action.mode) }
            TTSAction.CreateSpeech -> createSpeech()
            TTSAction.StopPlayback -> {
                // Synchronous part: silence now, don't wait for the worker to be dispatched.
                audioPlayer.stop()
                _uiState.update { it.copy(processingState = LoadingState.Idle(Unit)) }
                speechCommands.trySend(SpeechCommand.Stop)
            }

            TTSAction.ClearError -> _uiState.update { state ->
                if (state.processingState is LoadingState.Error) {
                    state.copy(processingState = LoadingState.Idle(Unit))
                } else state
            }
        }
    }

    private fun createSpeech() {
        val state = _uiState.value
        val voice = state.selectedVoice
        if (!state.canCreateSpeech || voice == null) return
        // Flip to Loading synchronously so a double tap sees isProcessing and is ignored.
        _uiState.update { it.copy(processingState = LoadingState.Loading) }
        speechCommands.trySend(SpeechCommand.Speak(state.text, voice, state.mode))
    }

    private suspend fun loadVoices() {
        val result = try {
            LoadingState.Idle(client.voices.list(ListVoicesRequest(pageSize = 100)).voices)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            LoadingState.Error(throwable.userMessage("Could not load voices."))
        }
        _uiState.update { it.copy(voices = result) }
    }

    private suspend fun synthesize(command: SpeechCommand.Speak) {
        val result = try {
            when (command.mode) {
                TTSMode.Generate -> generate(command.text, command.voice)
                TTSMode.Stream -> stream(command.text, command.voice)
                TTSMode.Realtime -> realtime(command.text, command.voice)
            }
            LoadingState.Idle(Unit)
        } catch (cancellation: CancellationException) {
            audioPlayer.stop()
            throw cancellation
        } catch (throwable: Throwable) {
            audioPlayer.stop()
            LoadingState.Error(throwable.userMessage("Could not create speech."))
        }
        // Guarded transition: only finish a request that is still in flight.
        // If the user pressed Stop meanwhile, the state is already Idle and stays so.
        _uiState.update { if (it.isProcessing) it.copy(processingState = result) else it }
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
        client.textToSpeech.realtime(
            voiceId = voice.id,
            text = text.asRealtimeInput(),
            options = RealtimeTtsOptions(
                modelId = REALTIME_MODEL_ID,
                outputFormat = OutputFormat.Pcm_24000,
            ),
        ).collect { chunk -> audioPlayer.writeStream(chunk.bytes) }
        audioPlayer.finishStream()
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

    private sealed interface SpeechCommand {
        data class Speak(val text: String, val voice: Voice, val mode: TTSMode) : SpeechCommand
        data object Stop : SpeechCommand
    }

    private companion object {
        const val REALTIME_SAMPLE_RATE = 24_000
        const val REALTIME_CHUNK_DELAY_MS = 80L
        const val REALTIME_MODEL_ID = "eleven_flash_v2_5"
        val REALTIME_TEXT_CHUNK = Regex("\\S+\\s*")
    }
}
