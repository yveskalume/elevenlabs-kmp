package dev.yveskalume.elevenlabs.internal.tts

import dev.yveskalume.elevenlabs.error.ElevenLabsErrorDetails
import dev.yveskalume.elevenlabs.error.NetworkError
import dev.yveskalume.elevenlabs.error.RealtimeServerError
import dev.yveskalume.elevenlabs.error.SerializationError
import dev.yveskalume.elevenlabs.error.TimeoutError
import dev.yveskalume.elevenlabs.internal.tts.dtos.DecodedRealtimeTtsMessage
import dev.yveskalume.elevenlabs.internal.tts.dtos.RealtimeTtsMessages
import dev.yveskalume.elevenlabs.internal.error.toRealtimeFailure
import dev.yveskalume.elevenlabs.tts.RealtimeTtsEvent
import dev.yveskalume.elevenlabs.tts.RealtimeTtsOptions
import dev.yveskalume.elevenlabs.tts.RealtimeTtsReconnectPolicy
import dev.yveskalume.elevenlabs.tts.RealtimeTtsSession
import io.ktor.websocket.CloseReason
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
import kotlinx.io.IOException
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

internal class RealtimeTtsSessionImpl private constructor(
    private val openConnection: suspend () -> RealtimeTtsConnection,
    private val options: RealtimeTtsOptions,
    dispatcher: CoroutineDispatcher,
) : RealtimeTtsSession {
    private enum class State { Active, Finishing, Closed }

    private val stateMutex = Mutex()
    private val reconnectMutex = Mutex()
    private var state = State.Active
    private var connection: RealtimeTtsConnection? = null
    private var connectionAttempts = 0
    private var reconnecting = false
    private var audioReceived = false
    private val messagesToReplay = mutableListOf<String>()

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val eventChannel = Channel<RealtimeTtsEvent>(Channel.BUFFERED)
    private var receiverJob: Job? = null
    private var keepAliveJob: Job? = null
    private var finishTimeoutJob: Job? = null

    override val events: Flow<RealtimeTtsEvent> = eventChannel.receiveAsFlow()

    override suspend fun sendText(text: String, flush: Boolean) {
        require(text.isNotEmpty()) { "Realtime TTS text cannot be empty. Call finish() to end the session." }
        sendUserMessage(RealtimeTtsMessages.text(text, flush))
    }

    override suspend fun flush() {
        sendUserMessage(RealtimeTtsMessages.text(" ", flush = true))
    }

    private suspend fun sendUserMessage(message: String) {
        var failedConnection: RealtimeTtsConnection? = null
        try {
            stateMutex.withLock {
                check(state == State.Active) { "The realtime TTS session is finishing or closed." }
                messagesToReplay += message
                if (!reconnecting) {
                    requireNotNull(connection).also { current ->
                        failedConnection = current
                        sendWithTimeout(current, message)
                    }
                }
                resetKeepAliveLocked()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            val current = failedConnection
            if (current == null) throw throwable
            if (!reconnect(current, throwable)) {
                throw throwable.toRealtimeFailure()
            }
        }
    }

    override suspend fun finish() {
        var startTimeout = false
        var failedConnection: RealtimeTtsConnection? = null
        try {
            stateMutex.withLock {
                when (state) {
                    State.Active -> {
                        state = State.Finishing
                        if (!reconnecting) {
                            requireNotNull(connection).also { current ->
                                failedConnection = current
                                sendWithTimeout(current, RealtimeTtsMessages.finish())
                            }
                        }
                        startTimeout = true
                    }
                    State.Finishing, State.Closed -> Unit
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            val current = failedConnection
            if (current == null || !reconnect(current, throwable)) {
                val failure = throwable.toRealtimeFailure()
                failSession(failure)
                throw failure
            }
            startTimeout = true
        }
        if (startTimeout) {
            keepAliveJob?.cancel()
            startFinishTimeout()
        }
    }

    override suspend fun close() {
        val connectionToClose = stateMutex.withLock {
            if (state == State.Closed) return
            state = State.Closed
            reconnecting = false
            connection.also { connection = null }
        }

        keepAliveJob?.cancel()
        finishTimeoutJob?.cancel()
        receiverJob?.cancel()
        withContext(NonCancellable) { runCatching { connectionToClose?.close() } }
        eventChannel.close()
        scope.cancel()
    }

    private fun startReceiving() {
        receiverJob = scope.launch {
            var failure: Throwable? = null
            try {
                while (true) {
                    val activeConnection = stateMutex.withLock { connection }
                        ?: return@launch
                    val frame = try {
                        activeConnection.receive()
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (throwable: Throwable) {
                        if (reconnect(activeConnection, throwable)) continue
                        throw throwable
                    }

                    when (frame) {
                        is RealtimeConnectionFrame.Text -> {
                            if (handle(frame.value)) return@launch
                        }
                        is RealtimeConnectionFrame.Closed -> {
                            val wasClosedLocally = stateMutex.withLock { state == State.Closed }
                            if (wasClosedLocally) return@launch

                            val exception = RealtimeServerError(
                                message = frame.reason
                                    ?: "The realtime TTS connection closed before a final response.",
                                closeCode = frame.code,
                            )
                            if (reconnect(activeConnection, exception)) continue
                            throw exception
                        }
                    }
                }
            } catch (cancellation: CancellationException) {
                val wasClosedLocally = stateMutex.withLock { state == State.Closed }
                if (!wasClosedLocally) failure = cancellation
            } catch (throwable: Throwable) {
                failure = throwable.toRealtimeFailure()
            } finally {
                val connectionToClose = stateMutex.withLock {
                    state = State.Closed
                    reconnecting = false
                    connection.also { connection = null }
                }
                keepAliveJob?.cancel()
                finishTimeoutJob?.cancel()
                withContext(NonCancellable) { runCatching { connectionToClose?.close() } }
                eventChannel.close(failure)
            }
        }
    }

    private fun startKeepAlive() {
        if (!options.keepAlive.enabled) return
        resetKeepAliveLocked()
    }

    private fun resetKeepAliveLocked() {
        if (!options.keepAlive.enabled || state != State.Active) return
        keepAliveJob?.cancel()
        keepAliveJob = scope.launch {
            while (true) {
                delay(options.keepAlive.intervalMillis.milliseconds)
                var keepAliveFailure: Pair<RealtimeTtsConnection, Throwable>? = null
                stateMutex.withLock {
                    if (state != State.Active) return@launch
                    if (!reconnecting) {
                        val current = requireNotNull(connection)
                        try {
                            sendWithTimeout(current, RealtimeTtsMessages.text(" "))
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (throwable: Throwable) {
                            keepAliveFailure = current to throwable.toRealtimeFailure()
                        }
                    }
                }
                keepAliveFailure?.let { (failed, failure) ->
                    try {
                        if (!reconnect(failed, failure)) {
                            failSession(failure)
                            return@launch
                        }
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (throwable: Throwable) {
                        failSession(throwable.toRealtimeFailure())
                        return@launch
                    }
                }
            }
        }
    }

    private suspend fun failSession(failure: Throwable) {
        val connectionToClose = stateMutex.withLock {
            if (state == State.Closed) return
            state = State.Closed
            reconnecting = false
            connection.also { connection = null }
        }
        eventChannel.close(failure)
        receiverJob?.cancel()
        withContext(NonCancellable) { runCatching { connectionToClose?.close() } }
    }

    private fun startFinishTimeout() {
        finishTimeoutJob = scope.launch {
            delay(options.timeouts.finishTimeoutMillis.milliseconds)
            val timeout = TimeoutError(
                message = "Realtime TTS did not finish within ${options.timeouts.finishTimeoutMillis} ms.",
            )
            val connectionToClose = stateMutex.withLock {
                if (state != State.Finishing) return@launch
                state = State.Closed
                connection.also { connection = null }
            }
            eventChannel.close(timeout)
            receiverJob?.cancel()
            withContext(NonCancellable) { runCatching { connectionToClose?.close() } }
        }
    }

    private suspend fun reconnect(
        failedConnection: RealtimeTtsConnection,
        initialFailure: Throwable,
    ): Boolean = reconnectMutex.withLock {
        reconnectLocked(failedConnection, initialFailure)
    }

    private suspend fun reconnectLocked(
        failedConnection: RealtimeTtsConnection,
        initialFailure: Throwable,
    ): Boolean {
        if (!canRetry(initialFailure)) return false

        val connectionStatus = stateMutex.withLock {
            when {
                state == State.Closed || audioReceived -> ConnectionStatus.Unavailable
                connection !== failedConnection && connection != null -> ConnectionStatus.AlreadyReconnected
                connection !== failedConnection -> ConnectionStatus.Unavailable
                else -> {
                    reconnecting = true
                    connection = null
                    ConnectionStatus.Accepted
                }
            }
        }
        when (connectionStatus) {
            ConnectionStatus.AlreadyReconnected -> return true
            ConnectionStatus.Unavailable -> return false
            ConnectionStatus.Accepted -> Unit
        }
        withContext(NonCancellable) { runCatching { failedConnection.close() } }

        var failure = initialFailure
        while (canRetry(failure)) {
            delayBeforeRetry()
            connectionAttempts++
            val newConnection = try {
                openAndInitialize()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                failure = throwable
                continue
            }

            try {
                val installed = stateMutex.withLock {
                    if (state == State.Closed) {
                        false
                    } else {
                        messagesToReplay.forEach { sendWithTimeout(newConnection, it) }
                        if (state == State.Finishing) {
                            sendWithTimeout(newConnection, RealtimeTtsMessages.finish())
                        }
                        connection = newConnection
                        reconnecting = false
                        true
                    }
                }
                if (installed) return true
                withContext(NonCancellable) { runCatching { newConnection.close() } }
                return false
            } catch (cancellation: CancellationException) {
                withContext(NonCancellable) { runCatching { newConnection.close() } }
                throw cancellation
            } catch (throwable: Throwable) {
                withContext(NonCancellable) { runCatching { newConnection.close() } }
                failure = throwable
            }
        }

        stateMutex.withLock { reconnecting = false }
        throw failure.toRealtimeFailure()
    }

    private fun canRetry(failure: Throwable): Boolean {
        val policy = options.reconnectPolicy as? RealtimeTtsReconnectPolicy.BeforeFirstAudio
            ?: return false
        if (audioReceived || connectionAttempts >= policy.maxAttempts) return false
        return when (failure) {
            is CancellationException -> false
            is SerializationError -> false
            is RealtimeServerError -> {
                if (failure.responseBody != null) return false
                failure.closeCode == null || failure.closeCode in RETRYABLE_CLOSE_CODES
            }
            is NetworkError,
            is TimeoutError,
            is IOException -> true
            else -> false
        }
    }

    private suspend fun delayBeforeRetry() {
        val policy = options.reconnectPolicy as RealtimeTtsReconnectPolicy.BeforeFirstAudio
        var baseDelay = policy.initialDelayMillis
        repeat((connectionAttempts - 1).coerceAtLeast(0)) {
            baseDelay = if (baseDelay >= policy.maxDelayMillis / 2) {
                policy.maxDelayMillis
            } else {
                min(policy.maxDelayMillis, baseDelay * 2)
            }
        }
        if (baseDelay == 0L) return
        val jitter = (baseDelay * policy.jitterFactor).toLong()
        val actualDelay = if (jitter == 0L) {
            baseDelay
        } else {
            Random.nextLong(
                from = (baseDelay - jitter).coerceAtLeast(0),
                until = baseDelay + jitter + 1,
            )
        }
        delay(actualDelay.milliseconds)
    }

    private suspend fun openAndInitialize(): RealtimeTtsConnection {
        val newConnection = try {
            withTimeout(options.timeouts.connectTimeoutMillis.milliseconds) { openConnection() }
        } catch (timeout: TimeoutCancellationException) {
            throw TimeoutError(
                message = "Realtime TTS connection timed out after ${options.timeouts.connectTimeoutMillis} ms.",
                cause = timeout,
            )
        }
        try {
            sendWithTimeout(newConnection, RealtimeTtsMessages.initialization(options))
        } catch (throwable: Throwable) {
            withContext(NonCancellable) { runCatching { newConnection.close() } }
            throw throwable
        }
        return newConnection
    }

    private suspend fun sendWithTimeout(connection: RealtimeTtsConnection, message: String) {
        try {
            withTimeout(options.timeouts.sendTimeoutMillis.milliseconds) { connection.send(message) }
        } catch (timeout: TimeoutCancellationException) {
            throw TimeoutError(
                message = "Realtime TTS send timed out after ${options.timeouts.sendTimeoutMillis} ms.",
                cause = timeout,
            )
        }
    }

    private suspend fun handle(rawMessage: String): Boolean {
        val decoded = try {
            RealtimeTtsMessages.decode(rawMessage)
        } catch (throwable: Throwable) {
            throw SerializationError(
                details = ElevenLabsErrorDetails(
                    statusCode = null,
                    message = "Could not decode an ElevenLabs realtime TTS response.",
                    responseBody = rawMessage,
                ),
                cause = throwable,
            )
        }

        when (decoded) {
            is DecodedRealtimeTtsMessage.Event -> {
                if (decoded.value is RealtimeTtsEvent.Audio) {
                    stateMutex.withLock {
                        audioReceived = true
                        messagesToReplay.clear()
                    }
                }
                eventChannel.send(decoded.value)
                if (decoded.value == RealtimeTtsEvent.Finished) {
                    stateMutex.withLock { state = State.Closed }
                    return true
                }
            }
            is DecodedRealtimeTtsMessage.Error -> throw RealtimeServerError(
                message = decoded.message,
                rawResponseBody = rawMessage,
            )
            DecodedRealtimeTtsMessage.Unknown -> Unit
        }
        return false
    }

    internal companion object {
        private enum class ConnectionStatus { Accepted, AlreadyReconnected, Unavailable }

        private val RETRYABLE_CLOSE_CODES = setOf(
            CloseReason.Codes.GOING_AWAY.code,
            CloseReason.Codes.INTERNAL_ERROR.code,
            CloseReason.Codes.SERVICE_RESTART.code,
            CloseReason.Codes.TRY_AGAIN_LATER.code,
        )

        suspend fun open(
            openConnection: suspend () -> RealtimeTtsConnection,
            options: RealtimeTtsOptions,
            dispatcher: CoroutineDispatcher = Dispatchers.Default,
        ): RealtimeTtsSessionImpl {
            val session = RealtimeTtsSessionImpl(openConnection, options, dispatcher)
            while (true) {
                session.connectionAttempts++
                try {
                    session.connection = session.openAndInitialize()
                    session.startReceiving()
                    session.startKeepAlive()
                    return session
                } catch (cancellation: CancellationException) {
                    session.scope.cancel()
                    throw cancellation
                } catch (throwable: Throwable) {
                    if (!session.canRetry(throwable)) {
                        session.scope.cancel()
                        throw throwable
                    }
                    session.delayBeforeRetry()
                }
            }
        }
    }
}
