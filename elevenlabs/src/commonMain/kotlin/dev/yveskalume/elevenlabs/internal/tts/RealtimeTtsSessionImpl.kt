package dev.yveskalume.elevenlabs.internal.tts

import dev.yveskalume.elevenlabs.ElevenLabsError
import dev.yveskalume.elevenlabs.ElevenLabsException
import dev.yveskalume.elevenlabs.internal.tts.dtos.DecodedRealtimeTtsMessage
import dev.yveskalume.elevenlabs.internal.tts.dtos.RealtimeTtsMessages
import dev.yveskalume.elevenlabs.tts.RealtimeTtsEvent
import dev.yveskalume.elevenlabs.tts.RealtimeTtsOptions
import dev.yveskalume.elevenlabs.tts.RealtimeTtsSession
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

internal class RealtimeTtsSessionImpl private constructor(
    private val connection: RealtimeTtsConnection,
) : RealtimeTtsSession {
    private enum class State { Active, Finishing, Closed }

    private val stateMutex = Mutex()
    private var state = State.Active
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val eventChannel = Channel<RealtimeTtsEvent>(Channel.BUFFERED)
    private var receiverJob: Job? = null

    override val events: Flow<RealtimeTtsEvent> = eventChannel.receiveAsFlow()

    override suspend fun sendText(text: String, flush: Boolean) {
        require(text.isNotEmpty()) { "Realtime TTS text cannot be empty. Call finish() to end the session." }
        stateMutex.withLock {
            check(state == State.Active) { "The realtime TTS session is finishing or closed." }
            connection.send(RealtimeTtsMessages.text(text, flush))
        }
    }

    override suspend fun flush() {
        stateMutex.withLock {
            check(state == State.Active) { "The realtime TTS session is finishing or closed." }
            connection.send(RealtimeTtsMessages.text(" ", flush = true))
        }
    }

    override suspend fun finish() {
        stateMutex.withLock {
            when (state) {
                State.Active -> {
                    connection.send(RealtimeTtsMessages.finish())
                    state = State.Finishing
                }
                State.Finishing, State.Closed -> Unit
            }
        }
    }

    override suspend fun close() {
        val shouldClose = stateMutex.withLock {
            if (state == State.Closed) {
                false
            } else {
                state = State.Closed
                true
            }
        }
        if (!shouldClose) return

        receiverJob?.cancel()
        withContext(NonCancellable) { runCatching { connection.close() } }
        eventChannel.close()
        scope.cancel()
    }

    private fun startReceiving() {
        receiverJob = scope.launch {
            var failure: Throwable? = null
            try {
                while (true) {
                    when (val frame = connection.receive()) {
                        is RealtimeConnectionFrame.Text -> {
                            if (handle(frame.value)) return@launch
                        }
                        is RealtimeConnectionFrame.Closed -> {
                            val wasClosedLocally = stateMutex.withLock { state == State.Closed }
                            if (!wasClosedLocally) {
                                throw ElevenLabsException.Realtime(
                                    message = frame.reason
                                        ?: "The realtime TTS connection closed before a final response.",
                                    closeCode = frame.code,
                                )
                            }
                            return@launch
                        }
                    }
                }
            } catch (cancellation: CancellationException) {
                val wasClosedLocally = stateMutex.withLock { state == State.Closed }
                if (!wasClosedLocally) failure = cancellation
            } catch (throwable: Throwable) {
                failure = throwable
            } finally {
                stateMutex.withLock { state = State.Closed }
                withContext(NonCancellable) { runCatching { connection.close() } }
                eventChannel.close(failure)
            }
        }
    }

    private suspend fun handle(rawMessage: String): Boolean {
        val decoded = try {
            RealtimeTtsMessages.decode(rawMessage)
        } catch (throwable: Throwable) {
            throw ElevenLabsException.Serialization(
                error = ElevenLabsError(
                    statusCode = null,
                    message = "Could not decode an ElevenLabs realtime TTS response.",
                    responseBody = rawMessage,
                ),
                cause = throwable,
            )
        }

        when (decoded) {
            is DecodedRealtimeTtsMessage.Event -> {
                eventChannel.send(decoded.value)
                if (decoded.value == RealtimeTtsEvent.Finished) {
                    stateMutex.withLock { state = State.Closed }
                    return true
                }
            }
            is DecodedRealtimeTtsMessage.Error -> throw ElevenLabsException.Realtime(
                message = decoded.message,
                responseBody = rawMessage,
            )
            DecodedRealtimeTtsMessage.Unknown -> Unit
        }
        return false
    }

    internal companion object {
        suspend fun open(
            connection: RealtimeTtsConnection,
            options: RealtimeTtsOptions,
        ): RealtimeTtsSessionImpl {
            try {
                connection.send(RealtimeTtsMessages.initialization(options))
            } catch (throwable: Throwable) {
                withContext(NonCancellable) { runCatching { connection.close() } }
                throw throwable
            }
            return RealtimeTtsSessionImpl(connection).also { it.startReceiving() }
        }
    }
}
