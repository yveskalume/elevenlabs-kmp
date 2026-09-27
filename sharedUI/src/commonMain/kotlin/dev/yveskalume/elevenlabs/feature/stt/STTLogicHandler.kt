package dev.yveskalume.elevenlabs.feature.stt

import dev.yveskalume.elevenlabs.ElevenLabs
import dev.yveskalume.elevenlabs.MicrophoneRecorder
import dev.yveskalume.elevenlabs.feature.LogicHandler
import dev.yveskalume.elevenlabs.feature.userMessage
import dev.yveskalume.elevenlabs.stt.RealtimeSttAudioFormat
import dev.yveskalume.elevenlabs.stt.RealtimeSttCommitStrategy
import dev.yveskalume.elevenlabs.stt.RealtimeSttEvent
import dev.yveskalume.elevenlabs.stt.RealtimeSttOptions
import dev.yveskalume.elevenlabs.stt.RealtimeSttSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class STTLogicHandler(
    private val client: ElevenLabs,
    private val microphone: MicrophoneRecorder,
    scope: CoroutineScope,
) : LogicHandler<STTUiState, STTAction> {

    private val _uiState = MutableStateFlow(STTUiState())
    override val uiState: StateFlow<STTUiState> = _uiState.asStateFlow()

    private val sessionCommands = Channel<SessionCommand>(Channel.CONFLATED)

    init {
        scope.launch {
            sessionCommands.receiveAsFlow().collectLatest { command ->
                when (command) {
                    SessionCommand.Start -> runSession()
                    SessionCommand.Cancel -> Unit // arriving here already cancelled the previous session
                }
            }
        }
    }

    override fun onAction(action: STTAction) {
        when (action) {
            STTAction.ToggleListening -> toggleListening()
            STTAction.Cancel -> cancel()
            STTAction.ClearError -> _uiState.update { state ->
                if (state.status is ListeningStatus.Error) state.copy(status = ListeningStatus.Idle) else state
            }
        }
    }

    private fun toggleListening() {
        when (_uiState.value.status) {
            ListeningStatus.Idle, is ListeningStatus.Error -> {
                _uiState.update {
                    it.copy(partialTranscript = "", status = ListeningStatus.Connecting)
                }
                sessionCommands.trySend(SessionCommand.Start)
            }
            ListeningStatus.Connecting -> cancel()
            // The running session watches for Finishing and commits (see transcribe()).
            ListeningStatus.Listening -> _uiState.update { it.copy(status = ListeningStatus.Finishing) }
            ListeningStatus.Finishing -> Unit
        }
    }

    private fun cancel() {
        microphone.stop()
        _uiState.update {
            if (it.status.isActive) it.copy(status = ListeningStatus.Idle, partialTranscript = "") else it
        }
        sessionCommands.trySend(SessionCommand.Cancel)
    }

    private suspend fun runSession() {
        try {
            val session = client.speechToText.openRealtimeSession(options = SESSION_OPTIONS)
            try {
                transcribe(session)
            } finally {
                microphone.stop()
                // close() suspends; NonCancellable lets it run even when we got here by cancellation.
                withContext(NonCancellable) { runCatching { session.close() } }
            }
            transition(from = { it == ListeningStatus.Listening || it == ListeningStatus.Finishing }, to = ListeningStatus.Idle)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            transition(
                from = { it.isActive },
                to = ListeningStatus.Error(throwable.userMessage("Could not transcribe microphone audio.")),
            )
        }
    }

    private suspend fun transcribe(session: RealtimeSttSession) = coroutineScope {
        val micPump = launch(start = CoroutineStart.UNDISPATCHED) {
            microphone.audio.collect { bytes -> if (bytes.isNotEmpty()) session.sendAudio(bytes) }
        }
        launch {
            _uiState.first { it.status == ListeningStatus.Finishing }
            microphone.stop()
            micPump.cancelAndJoin()
            session.commit()
        }

        microphone.start(STT_SAMPLE_RATE)
        transition(from = { it == ListeningStatus.Connecting }, to = ListeningStatus.Listening)

        val committed = session.events
            .onEach(::render)
            .firstOrNull { it is RealtimeSttEvent.CommittedTranscript }

        coroutineContext.cancelChildren()

        // The server closed the stream without a committed transcript: surface it
        // instead of silently going back to Idle.
        checkNotNull(committed) { "The connection closed before the transcript arrived. Try again." }
    }

    private fun render(event: RealtimeSttEvent) {
        when (event) {
            is RealtimeSttEvent.PartialTranscript -> _uiState.update { it.copy(partialTranscript = event.text) }
            is RealtimeSttEvent.FinalTranscript -> _uiState.update { it.copy(partialTranscript = event.text) }
            is RealtimeSttEvent.CommittedTranscript -> appendCommitted(event.text)
            else -> Unit
        }
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

    /**
     * Guarded state change: only applied when the current status matches [from].
     * The worker runs asynchronously, so by the time it wants to say "Listening"
     * the user may already have cancelled; the guard stops a stale coroutine
     * from overwriting the newer state.
     */
    private inline fun transition(from: (ListeningStatus) -> Boolean, to: ListeningStatus) {
        _uiState.update { if (from(it.status)) it.copy(status = to) else it }
    }

    private enum class SessionCommand { Start, Cancel }

    private companion object {
        const val STT_SAMPLE_RATE = 16_000
        val SESSION_OPTIONS = RealtimeSttOptions(
            audioFormat = RealtimeSttAudioFormat.Pcm16000,
            commitStrategy = RealtimeSttCommitStrategy.Manual,
            includeTimestamps = true,
            includeLanguageDetection = true,
        )
    }
}
