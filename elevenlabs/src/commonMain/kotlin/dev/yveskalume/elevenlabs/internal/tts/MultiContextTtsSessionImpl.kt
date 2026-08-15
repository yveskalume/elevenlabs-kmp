package dev.yveskalume.elevenlabs.internal.tts

import dev.yveskalume.elevenlabs.ElevenLabsError
import dev.yveskalume.elevenlabs.ElevenLabsException
import dev.yveskalume.elevenlabs.internal.tts.dtos.DecodedMultiContextTtsMessage
import dev.yveskalume.elevenlabs.internal.tts.dtos.MultiContextTtsMessages
import dev.yveskalume.elevenlabs.tts.MultiContextTtsContext
import dev.yveskalume.elevenlabs.tts.MultiContextTtsContextOptions
import dev.yveskalume.elevenlabs.tts.MultiContextTtsEvent
import dev.yveskalume.elevenlabs.tts.MultiContextTtsOptions
import dev.yveskalume.elevenlabs.tts.MultiContextTtsSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds

internal class MultiContextTtsSessionImpl private constructor(
    private val connection: RealtimeTtsConnection,
    private val options: MultiContextTtsOptions,
    dispatcher: CoroutineDispatcher,
) : MultiContextTtsSession {
    private enum class State { Active, Closing, Closed }

    private class ContextRecord(
        val handle: MultiContextTtsContext,
        var keepAliveJob: Job? = null,
    )

    private val stateMutex = Mutex()
    private var state = State.Active
    private val contexts = mutableMapOf<String, ContextRecord>()
    private val usedContextIds = mutableSetOf<String>()

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val eventChannel = Channel<MultiContextTtsEvent>(Channel.BUFFERED)
    private var receiverJob: Job? = null

    override val events: Flow<MultiContextTtsEvent> = eventChannel.receiveAsFlow()

    override suspend fun openContext(
        contextId: String,
        options: MultiContextTtsContextOptions,
    ): MultiContextTtsContext {
        require(contextId.isNotBlank()) { "contextId cannot be blank." }
        var attemptedSend = false
        return try {
            stateMutex.withLock {
                check(state == State.Active) { "The multi-context TTS session is closing or closed." }
                require(contextId !in usedContextIds) {
                    "The context ID '$contextId' has already been used in this session."
                }
                check(contexts.size < MAX_CONTEXTS) {
                    "An ElevenLabs multi-context TTS session supports at most $MAX_CONTEXTS active contexts."
                }

                attemptedSend = true
                sendWithTimeout(MultiContextTtsMessages.initialize(contextId, options))
                val handle = MultiContextTtsContextImpl(contextId, this)
                val record = ContextRecord(handle)
                contexts[contextId] = record
                usedContextIds += contextId
                resetKeepAliveLocked(contextId, record)
                handle
            }
        } catch (cancellation: CancellationException) {
            if (attemptedSend) {
                withContext(NonCancellable) { failSession(cancellation) }
            }
            throw cancellation
        } catch (throwable: Throwable) {
            if (attemptedSend) failSession(throwable)
            throw throwable
        }
    }

    internal suspend fun sendText(contextId: String, text: String, flush: Boolean) {
        require(text.isNotEmpty()) { "Multi-context TTS text cannot be empty. Call keepAlive() instead." }
        performContextAction(
            contextId = contextId,
            message = MultiContextTtsMessages.text(contextId, text, flush),
            resetKeepAlive = true,
        )
    }

    internal suspend fun flush(contextId: String) {
        performContextAction(
            contextId = contextId,
            message = MultiContextTtsMessages.flush(contextId),
            resetKeepAlive = true,
        )
    }

    internal suspend fun keepAlive(contextId: String) {
        performContextAction(
            contextId = contextId,
            message = MultiContextTtsMessages.keepAlive(contextId),
            resetKeepAlive = true,
        )
    }

    internal suspend fun closeContext(contextId: String) {
        var attemptedSend = false
        try {
            stateMutex.withLock {
                check(state == State.Active) { "The multi-context TTS session is closing or closed." }
                val record = checkNotNull(contexts.remove(contextId)) {
                    "The multi-context TTS context '$contextId' is closed."
                }
                record.keepAliveJob?.cancel()
                attemptedSend = true
                sendWithTimeout(MultiContextTtsMessages.closeContext(contextId))
            }
        } catch (cancellation: CancellationException) {
            if (attemptedSend) {
                withContext(NonCancellable) { failSession(cancellation) }
            }
            throw cancellation
        } catch (throwable: Throwable) {
            if (attemptedSend) failSession(throwable)
            throw throwable
        }
    }

    private suspend fun performContextAction(
        contextId: String,
        message: String,
        resetKeepAlive: Boolean,
    ) {
        var attemptedSend = false
        try {
            stateMutex.withLock {
                check(state == State.Active) { "The multi-context TTS session is closing or closed." }
                val record = checkNotNull(contexts[contextId]) {
                    "The multi-context TTS context '$contextId' is closed."
                }
                attemptedSend = true
                sendWithTimeout(message)
                if (resetKeepAlive) resetKeepAliveLocked(contextId, record)
            }
        } catch (cancellation: CancellationException) {
            if (attemptedSend) {
                withContext(NonCancellable) { failSession(cancellation) }
            }
            throw cancellation
        } catch (throwable: Throwable) {
            if (attemptedSend) failSession(throwable)
            throw throwable
        }
    }

    private fun resetKeepAliveLocked(contextId: String, record: ContextRecord) {
        if (!options.keepAlive.enabled || state != State.Active) return
        record.keepAliveJob?.cancel()
        record.keepAliveJob = scope.launch {
            while (true) {
                delay(options.keepAlive.intervalMillis)
                try {
                    stateMutex.withLock {
                        if (state != State.Active || contexts[contextId] !== record) return@launch
                        sendWithTimeout(MultiContextTtsMessages.keepAlive(contextId))
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    failSession(throwable)
                    return@launch
                }
            }
        }
    }

    override suspend fun close() {
        var failure: Throwable? = null
        val closeStarted = withContext(NonCancellable) {
            stateMutex.withLock {
                if (state != State.Active) {
                    false
                } else {
                    state = State.Closing
                    contexts.values.forEach { it.keepAliveJob?.cancel() }
                    try {
                        sendWithTimeout(MultiContextTtsMessages.closeSocket())
                    } catch (throwable: Throwable) {
                        failure = throwable
                    }
                    state = State.Closed
                    true
                }
            }
        }
        if (!closeStarted) return

        failure?.let {
            failSession(it)
            throw it
        }

        val closeTimeout = try {
            withTimeout(options.timeouts.finishTimeoutMillis) {
                receiverJob?.join()
            }
            null
        } catch (timeout: TimeoutCancellationException) {
            ElevenLabsException.Realtime(
                message = "Multi-context TTS did not close within ${options.timeouts.finishTimeoutMillis} ms.",
                cause = timeout,
            )
        }
        if (closeTimeout != null) {
            failSession(closeTimeout)
            throw closeTimeout
        }
        scope.cancel()
    }

    private fun startReceiving() {
        receiverJob = scope.launch {
            var failure: Throwable? = null
            try {
                while (true) {
                    when (val frame = connection.receive()) {
                        is RealtimeConnectionFrame.Text -> handle(frame.value)
                        is RealtimeConnectionFrame.Closed -> {
                            val closedLocally = stateMutex.withLock { state != State.Active }
                            if (closedLocally) return@launch
                            throw ElevenLabsException.Realtime(
                                message = frame.reason
                                    ?: "The multi-context TTS connection closed unexpectedly.",
                                closeCode = frame.code,
                            )
                        }
                    }
                }
            } catch (cancellation: CancellationException) {
                val closedLocally = stateMutex.withLock { state == State.Closed }
                if (!closedLocally) failure = cancellation
            } catch (throwable: Throwable) {
                failure = throwable
            } finally {
                stateMutex.withLock {
                    state = State.Closed
                    contexts.values.forEach { it.keepAliveJob?.cancel() }
                    contexts.clear()
                }
                withContext(NonCancellable) { runCatching { connection.close() } }
                eventChannel.close(failure)
            }
        }
    }

    private suspend fun handle(rawMessage: String) {
        val decoded = try {
            MultiContextTtsMessages.decode(rawMessage)
        } catch (throwable: Throwable) {
            throw ElevenLabsException.Serialization(
                error = ElevenLabsError(
                    statusCode = null,
                    message = "Could not decode an ElevenLabs multi-context TTS response.",
                    responseBody = rawMessage,
                ),
                cause = throwable,
            )
        }

        when (decoded) {
            is DecodedMultiContextTtsMessage.Event -> {
                when (val event = decoded.value) {
                    is MultiContextTtsEvent.ContextFinished,
                    is MultiContextTtsEvent.ContextError,
                    -> stateMutex.withLock {
                        contexts.remove(event.contextId)?.keepAliveJob?.cancel()
                    }
                    is MultiContextTtsEvent.Audio -> Unit
                }
                eventChannel.send(decoded.value)
            }
            is DecodedMultiContextTtsMessage.ConnectionError -> {
                throw ElevenLabsException.Realtime(
                    message = decoded.message,
                    responseBody = rawMessage,
                )
            }
            DecodedMultiContextTtsMessage.Unknown -> Unit
        }
    }

    private suspend fun failSession(failure: Throwable) {
        val stateChanged = stateMutex.withLock {
            if (state == State.Closed) {
                false
            } else {
                state = State.Closed
                contexts.values.forEach { it.keepAliveJob?.cancel() }
                contexts.clear()
                true
            }
        }
        if (stateChanged) {
            eventChannel.close(failure)
            receiverJob?.cancel()
        }
        withContext(NonCancellable) { runCatching { connection.close() } }
        if (stateChanged) scope.cancel()
    }

    private suspend fun sendWithTimeout(message: String) {
        try {
            withTimeout(options.timeouts.sendTimeoutMillis) { connection.send(message) }
        } catch (timeout: TimeoutCancellationException) {
            throw ElevenLabsException.Realtime(
                message = "Multi-context TTS send timed out after ${options.timeouts.sendTimeoutMillis} ms.",
                cause = timeout,
            )
        }
    }

    private class MultiContextTtsContextImpl(
        override val id: String,
        private val session: MultiContextTtsSessionImpl,
    ) : MultiContextTtsContext {
        override suspend fun sendText(text: String, flush: Boolean) {
            session.sendText(id, text, flush)
        }

        override suspend fun flush() {
            session.flush(id)
        }

        override suspend fun keepAlive() {
            session.keepAlive(id)
        }

        override suspend fun close() {
            session.closeContext(id)
        }
    }

    internal companion object {
        const val MAX_CONTEXTS = 5

        suspend fun open(
            openConnection: suspend () -> RealtimeTtsConnection,
            options: MultiContextTtsOptions,
            dispatcher: CoroutineDispatcher = Dispatchers.Default,
        ): MultiContextTtsSessionImpl {
            val connection = try {
                withTimeout(options.timeouts.connectTimeoutMillis.milliseconds) { openConnection() }
            } catch (timeout: TimeoutCancellationException) {
                throw ElevenLabsException.Realtime(
                    message = "Multi-context TTS connection timed out after ${options.timeouts.connectTimeoutMillis} ms.",
                    cause = timeout,
                )
            }
            return MultiContextTtsSessionImpl(connection, options, dispatcher).also {
                it.startReceiving()
            }
        }
    }
}
