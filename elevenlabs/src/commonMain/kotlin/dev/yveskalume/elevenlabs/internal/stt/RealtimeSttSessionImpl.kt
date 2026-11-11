package dev.yveskalume.elevenlabs.internal.stt

import dev.yveskalume.elevenlabs.error.ElevenLabsException
import dev.yveskalume.elevenlabs.internal.stt.dtos.DecodedRealtimeSttMessage
import dev.yveskalume.elevenlabs.internal.stt.dtos.RealtimeSttMessages
import dev.yveskalume.elevenlabs.internal.error.toConnectionLost
import dev.yveskalume.elevenlabs.internal.error.toRealtimeFailure
import dev.yveskalume.elevenlabs.stt.RealtimeSttEvent
import dev.yveskalume.elevenlabs.stt.RealtimeSttSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class RealtimeSttSessionImpl(
    private val connection: RealtimeSttConnection,
) : RealtimeSttSession {

    private sealed interface State {
        data object Open : State
        data object Closed : State

        /** Ended by the server or transport. Later calls rethrow [cause]. */
        data class Failed(val cause: Throwable) : State
    }

    private val stateMutex = Mutex()
    private var state: State = State.Open
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val eventChannel = Channel<RealtimeSttEvent>(Channel.BUFFERED)
    private val receiverJob: Job

    override val events: Flow<RealtimeSttEvent> = eventChannel.receiveAsFlow()

    init {
        receiverJob = scope.launch { receiveEvents() }
    }

    override suspend fun sendAudio(audio: ByteArray, commit: Boolean) {
        require(audio.isNotEmpty()) { "Realtime STT audio cannot be empty. Call commit() instead." }
        send(RealtimeSttMessages.audio(audio, commit))
    }

    override suspend fun commit() {
        send(RealtimeSttMessages.commit())
    }

    /** A failed send fails the session, because the connection is no longer usable. */
    private suspend fun send(message: String) {
        val failure = stateMutex.withLock {
            when (val current = state) {
                // A session the server ended reports why. Only a caller's own close() is a caller mistake.
                is State.Failed -> throw current.cause
                State.Closed -> throw IllegalStateException("The realtime STT session is closed.")
                State.Open -> Unit
            }
            try {
                connection.send(message)
                null
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                throwable.toRealtimeFailure().also { state = State.Failed(it) }
            }
        } ?: return

        // Close events first: the cancelled receiver would otherwise complete them without an error.
        eventChannel.close(failure)
        receiverJob.cancel()
        withContext(NonCancellable) { runCatching { connection.close() } }
        scope.cancel()
        throw failure
    }

    override suspend fun close() {
        val shouldClose = stateMutex.withLock {
            if (state != State.Open) false else {
                state = State.Closed
                true
            }
        }
        if (!shouldClose) return

        receiverJob.cancel()
        withContext(NonCancellable) { runCatching { connection.close() } }
        eventChannel.close()
        scope.cancel()
    }

    private suspend fun receiveEvents() {
        var failure: Throwable? = null
        try {
            while (true) {
                when (val frame = connection.receive()) {
                    is RealtimeSttConnectionFrame.Text -> handle(frame.value)
                    is RealtimeSttConnectionFrame.Closed -> {
                        val wasClosedLocally = stateMutex.withLock { state != State.Open }
                        if (!wasClosedLocally) {
                            throw ElevenLabsException.RealtimeServer(
                                // Ktor reports a missing close reason as "", not null.
                                message = frame.reason?.takeIf { it.isNotBlank() }
                                    ?: "The realtime STT connection closed unexpectedly.",
                                closeCode = frame.code,
                            )
                        }
                        return
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            val wasClosedLocally = stateMutex.withLock { state != State.Open }
            if (!wasClosedLocally) failure = cancellation.toConnectionLost("realtime STT")
        } catch (throwable: Throwable) {
            failure = throwable.toRealtimeFailure()
        } finally {
            stateMutex.withLock {
                if (state == State.Open) state = failure?.let(State::Failed) ?: State.Closed
            }
            withContext(NonCancellable) { runCatching { connection.close() } }
            eventChannel.close(failure)
        }
    }

    private suspend fun handle(rawMessage: String) {
        val decoded = try {
            RealtimeSttMessages.decode(rawMessage)
        } catch (throwable: Throwable) {
            throw ElevenLabsException.Serialization(
                cause = throwable,
                message = "Could not decode an ElevenLabs realtime STT response.",
                responseBody = rawMessage,
            )
        }

        when (decoded) {
            is DecodedRealtimeSttMessage.Event -> eventChannel.send(decoded.value)
            is DecodedRealtimeSttMessage.Error -> throw ElevenLabsException.RealtimeServer(
                message = decoded.message,
                responseBody = rawMessage,
                errorCode = decoded.errorCode,
            )
        }
    }
}
