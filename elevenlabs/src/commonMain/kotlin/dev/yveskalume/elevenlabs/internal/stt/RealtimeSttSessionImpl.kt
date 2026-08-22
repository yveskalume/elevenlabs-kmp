package dev.yveskalume.elevenlabs.internal.stt

import dev.yveskalume.elevenlabs.error.ElevenLabsErrorDetails
import dev.yveskalume.elevenlabs.error.RealtimeServerError
import dev.yveskalume.elevenlabs.error.SerializationError
import dev.yveskalume.elevenlabs.internal.stt.dtos.DecodedRealtimeSttMessage
import dev.yveskalume.elevenlabs.internal.stt.dtos.RealtimeSttMessages
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
            send(RealtimeSttMessages.audio(audio, commit))
        }
    }

    override suspend fun commit() {
        stateMutex.withLock {
            check(!closed) { "The realtime STT session is closed." }
            send(RealtimeSttMessages.commit())
        }
    }

    private suspend fun send(message: String) {
        try {
            connection.send(message)
        } catch (throwable: Throwable) {
            throw throwable.toRealtimeFailure()
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
                            throw RealtimeServerError(
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
            failure = throwable.toRealtimeFailure()
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
            throw SerializationError(
                cause = throwable,
                details = ElevenLabsErrorDetails(
                    statusCode = null,
                    message = "Could not decode an ElevenLabs realtime STT response.",
                    responseBody = rawMessage,
                ),
            )
        }

        when (decoded) {
            is DecodedRealtimeSttMessage.Event -> eventChannel.send(decoded.value)
            is DecodedRealtimeSttMessage.Error -> throw RealtimeServerError(
                message = decoded.message,
                rawResponseBody = rawMessage,
            )
        }
    }
}
