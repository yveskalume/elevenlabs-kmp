package dev.yveskalume.elevenlabs.internal.stt

import dev.yveskalume.elevenlabs.ElevenLabsError
import dev.yveskalume.elevenlabs.ElevenLabsException
import dev.yveskalume.elevenlabs.internal.stt.dtos.DecodedRealtimeSttMessage
import dev.yveskalume.elevenlabs.internal.stt.dtos.RealtimeSttMessages
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

    private val stateMutex = Mutex()
    private var closed = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val eventChannel = Channel<RealtimeSttEvent>(Channel.BUFFERED)
    private val receiverJob: Job

    override val events: Flow<RealtimeSttEvent> = eventChannel.receiveAsFlow()

    init {
        receiverJob = scope.launch { receiveEvents() }
    }

    override suspend fun sendAudio(audio: ByteArray, commit: Boolean) {
        require(audio.isNotEmpty()) { "Realtime STT audio cannot be empty. Call commit() instead." }
        stateMutex.withLock {
            check(!closed) { "The realtime STT session is closed." }
            connection.send(RealtimeSttMessages.audio(audio, commit))
        }
    }

    override suspend fun commit() {
        stateMutex.withLock {
            check(!closed) { "The realtime STT session is closed." }
            connection.send(RealtimeSttMessages.commit())
        }
    }

    override suspend fun close() {
        val shouldClose = stateMutex.withLock {
            if (closed) false else {
                closed = true
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
                        val wasClosedLocally = stateMutex.withLock { closed }
                        if (!wasClosedLocally) {
                            throw ElevenLabsException.Realtime(
                                message = frame.reason
                                    ?: "The realtime STT connection closed unexpectedly.",
                                closeCode = frame.code,
                            )
                        }
                        return
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            val wasClosedLocally = stateMutex.withLock { closed }
            if (!wasClosedLocally) failure = cancellation
        } catch (throwable: Throwable) {
            failure = throwable
        } finally {
            stateMutex.withLock { closed = true }
            withContext(NonCancellable) { runCatching { connection.close() } }
            eventChannel.close(failure)
        }
    }

    private suspend fun handle(rawMessage: String) {
        val decoded = try {
            RealtimeSttMessages.decode(rawMessage)
        } catch (throwable: Throwable) {
            throw ElevenLabsException.Serialization(
                error = ElevenLabsError(
                    statusCode = null,
                    message = "Could not decode an ElevenLabs realtime STT response.",
                    responseBody = rawMessage,
                ),
                cause = throwable,
            )
        }

        when (decoded) {
            is DecodedRealtimeSttMessage.Event -> eventChannel.send(decoded.value)
            is DecodedRealtimeSttMessage.Error -> throw ElevenLabsException.Realtime(
                message = decoded.message,
                responseBody = rawMessage,
            )
        }
    }
}
