package dev.yveskalume.elevenlabs.internal.realtime

import dev.yveskalume.elevenlabs.error.ElevenLabsErrorDetails
import dev.yveskalume.elevenlabs.error.NetworkError
import dev.yveskalume.elevenlabs.error.RealtimeServerError
import dev.yveskalume.elevenlabs.error.SerializationError
import dev.yveskalume.elevenlabs.error.TimeoutError
import dev.yveskalume.elevenlabs.internal.error.toRealtimeFailure
import dev.yveskalume.elevenlabs.internal.tts.RealtimeConnectionFrame
import dev.yveskalume.elevenlabs.internal.tts.RealtimeTtsConnection
import io.ktor.websocket.CloseReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.milliseconds

/**
 * Wire protocol of a multi-context ElevenLabs WebSocket.
 *
 * The engine owns lifecycle, routing, keepalive, and timeouts; a protocol only knows how to encode
 * control messages and decode incoming frames. Text-to-speech and text-to-dialogue differ only here.
 */
internal interface MultiContextProtocol<E : Any> {
    /** Event emitted on a context's flow when ElevenLabs reports that the context is complete. */
    val finishedEvent: E

    fun keepAlive(contextId: String): String
    fun flush(contextId: String): String
    fun closeContext(contextId: String): String
    fun closeSocket(): String

    /** Decodes one text frame. May throw; the engine reports that as a serialization failure. */
    fun decode(raw: String): List<MultiContextInbound<E>>
}

/** A decoded server frame, already classified by the protocol. */
internal sealed interface MultiContextInbound<out E : Any> {
    data class Event<E : Any>(val contextId: String, val event: E) : MultiContextInbound<E>
    data class ContextFinished(val contextId: String) : MultiContextInbound<Nothing>
    data class ContextError(
        val contextId: String,
        val message: String,
        val errorCode: String? = null,
    ) : MultiContextInbound<Nothing>

    data class ConnectionError(
        val message: String,
        val errorCode: String? = null,
    ) : MultiContextInbound<Nothing>
}

internal data class MultiContextEngineConfig(
    val sendTimeoutMillis: Long,
    val finishTimeoutMillis: Long,
    val keepAliveEnabled: Boolean,
    val keepAliveIntervalMillis: Long,
    val maxContexts: Int,
    /** Human-readable name used in error messages, such as "Multi-context TTS". */
    val label: String,
)

/**
 * Multiplexes independent contexts over one realtime connection.
 *
 * Design notes:
 * - Every context owns an unbounded channel. A bounded channel would let one slow consumer block
 *   the single receive loop and therefore every other context (head-of-line blocking). The amount
 *   of buffered audio is bounded by the text the caller sent.
 * - Sends run in [NonCancellable] and are bounded by the send timeout, so a frame is either fully
 *   enqueued or not sent at all. Cancelling a caller (for example an interrupted speaker) never
 *   leaves the shared connection in an unknown state and never fails other contexts.
 * - Only connection-level problems fail the session. A context-level server error, a context
 *   finish timeout, or a context close only affects that context.
 */
internal class MultiContextEngine<E : Any>(
    private val connection: RealtimeTtsConnection,
    private val protocol: MultiContextProtocol<E>,
    private val config: MultiContextEngineConfig,
    dispatcher: CoroutineDispatcher,
) {
    private sealed interface SessionState {
        data object Active : SessionState
        data object Finishing : SessionState
        data object Closed : SessionState
        data class Failed(val cause: Throwable) : SessionState
    }

    private enum class ContextState { Open, Finishing }

    private inner class ContextRecord(val id: String) {
        val channel = Channel<E>(Channel.UNLIMITED)
        var state = ContextState.Open
        var keepAliveJob: Job? = null
        var finishTimeoutJob: Job? = null

        /** Set when the caller closed the context; stops delivery of already buffered events. */
        @Volatile
        var discarded = false

        val events: Flow<E> = channel.receiveAsFlow().takeWhile { !discarded }

        fun cancelJobs() {
            keepAliveJob?.cancel()
            finishTimeoutJob?.cancel()
        }
    }

    /** Wraps a failed send so the caller can fail the session after releasing the lock. */
    private class SendFailure(val failure: Throwable) : Exception(failure)

    private val lock = Mutex()
    private var state: SessionState = SessionState.Active

    /** Contexts that are open or finishing. A closed context is removed so late frames are dropped. */
    private val contexts = LinkedHashMap<String, ContextRecord>()
    private val usedContextIds = mutableSetOf<String>()
    private var generatedIdCounter = 0

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var receiverJob: Job? = null

    fun start() {
        receiverJob = scope.launch { receiveLoop() }
    }

    /**
     * Registers a context and sends [initialMessage], built from the final context ID.
     * Returns the context ID and its event flow.
     */
    suspend fun openContext(
        requestedId: String?,
        initialMessage: (contextId: String) -> String,
    ): Pair<String, Flow<E>> {
        require(requestedId == null || requestedId.isNotBlank()) { "contextId cannot be blank." }
        var record: ContextRecord? = null
        try {
            return guarded {
                checkSessionActive()
                val id = requestedId ?: nextGeneratedId()
                require(id !in usedContextIds) {
                    "The context ID '$id' has already been used in this session."
                }
                check(contexts.size < config.maxContexts) {
                    "${config.label} allows at most ${config.maxContexts} open contexts. " +
                        "Finish or close a context before opening another one."
                }
                val created = ContextRecord(id)
                record = created
                usedContextIds += id
                contexts[id] = created
                send(initialMessage(id))
                resetKeepAliveLocked(created)
                id to created.events
            }
        } catch (cancellation: CancellationException) {
            // The registration may have been sent. The caller never receives the handle, so close
            // the context rather than leaking one of the limited slots.
            record?.let { withContext(NonCancellable) { closeContext(it.id) } }
            throw cancellation
        }
    }

    suspend fun send(contextId: String, message: String) {
        guarded {
            checkSessionActive()
            val record = openContextLocked(contextId)
            send(message)
            resetKeepAliveLocked(record)
        }
    }

    suspend fun finishContext(contextId: String, messages: List<String>) {
        guarded {
            if (state != SessionState.Active) return@guarded
            val record = contexts[contextId] ?: return@guarded
            if (record.state != ContextState.Open) return@guarded
            record.state = ContextState.Finishing
            record.keepAliveJob?.cancel()
            messages.forEach { send(it) }
            send(protocol.closeContext(contextId))
            record.finishTimeoutJob = scope.launch { expireFinishingContext(record) }
        }
    }

    /** Closes a context immediately. Never throws: failures are reported through the session. */
    suspend fun closeContext(contextId: String) {
        try {
            guarded {
                val record = contexts.remove(contextId) ?: return@guarded
                record.discarded = true
                record.cancelJobs()
                record.channel.close()
                // A finishing context already sent close_context.
                if (state == SessionState.Active && record.state == ContextState.Open) {
                    send(protocol.closeContext(contextId))
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (ignored: Throwable) {
            // The session has already been failed and every other context notified.
        }
    }

    suspend fun finish() {
        val started = guarded {
            if (state != SessionState.Active) return@guarded false
            state = SessionState.Finishing
            contexts.values.forEach { it.keepAliveJob?.cancel() }
            // close_socket only waits for contexts that were already flushed; flush the others so
            // every open context delivers its remaining audio.
            contexts.values
                .filter { it.state == ContextState.Open }
                .forEach { send(protocol.flush(it.id)) }
            send(protocol.closeSocket())
            true
        }
        if (!started) return

        val closed = withTimeoutOrNull(config.finishTimeoutMillis.milliseconds) {
            receiverJob?.join()
        }
        if (closed == null) {
            val timeout = TimeoutError(
                message = "${config.label} did not close within ${config.finishTimeoutMillis} ms.",
            )
            failSession(timeout)
            throw timeout
        }
        val failure = lock.withLock { (state as? SessionState.Failed)?.cause }
        if (failure != null) throw failure
    }

    suspend fun close() {
        val closing = withContext(NonCancellable) {
            lock.withLock {
                if (state == SessionState.Closed || state is SessionState.Failed) {
                    emptyList()
                } else {
                    state = SessionState.Closed
                    contexts.values.toList().also { contexts.clear() }
                }
            }
        }
        closing.forEach {
            it.cancelJobs()
            it.channel.close()
        }
        receiverJob?.cancel()
        withContext(NonCancellable) { runCatching { connection.close() } }
        scope.cancel()
    }

    private suspend fun receiveLoop() {
        var failure: Throwable? = null
        try {
            while (true) {
                when (val frame = connection.receive()) {
                    is RealtimeConnectionFrame.Text -> handle(frame.value)
                    is RealtimeConnectionFrame.Closed -> {
                        val current = lock.withLock { state }
                        if (current == SessionState.Closed || current is SessionState.Failed) return
                        if (current == SessionState.Finishing &&
                            frame.code == CloseReason.Codes.NORMAL.code
                        ) {
                            completeAfterNormalClose()
                            return
                        }
                        throw RealtimeServerError(
                            // Ktor reports a missing close reason as "", not null.
                            message = frame.reason?.takeIf { it.isNotBlank() }
                                ?: "The ${config.label} connection closed unexpectedly.",
                            closeCode = frame.code,
                        )
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            val closedLocally = lock.withLock {
                state == SessionState.Closed || state is SessionState.Failed
            }
            // The transport was cancelled underneath us. Report it as a lost connection rather
            // than leaking a CancellationException into collectors that were not cancelled.
            if (!closedLocally) {
                failure = NetworkError(
                    cause = cancellation,
                    message = "The ${config.label} connection was lost.",
                )
            }
        } catch (throwable: Throwable) {
            failure = throwable.toRealtimeFailure()
        }
        failure?.let { withContext(NonCancellable) { failSession(it) } }
    }

    private suspend fun handle(raw: String) {
        val messages = try {
            protocol.decode(raw)
        } catch (throwable: Throwable) {
            throw SerializationError(
                details = ElevenLabsErrorDetails(
                    statusCode = null,
                    message = "Could not decode an ElevenLabs ${config.label} message.",
                    responseBody = raw,
                ),
                cause = throwable,
            )
        }
        for (message in messages) {
            when (message) {
                is MultiContextInbound.Event -> lock.withLock {
                    // Unknown or closed contexts are dropped on purpose: late audio after an
                    // interruption must never reach the caller.
                    contexts[message.contextId]?.channel?.trySend(message.event)
                }

                is MultiContextInbound.ContextFinished -> lock.withLock {
                    contexts.remove(message.contextId)?.let { record ->
                        record.cancelJobs()
                        record.channel.trySend(protocol.finishedEvent)
                        record.channel.close()
                    }
                }

                is MultiContextInbound.ContextError -> lock.withLock {
                    contexts.remove(message.contextId)?.let { record ->
                        record.cancelJobs()
                        record.channel.close(
                            RealtimeServerError(
                                message = message.message,
                                rawResponseBody = raw,
                                errorCode = message.errorCode,
                            ),
                        )
                    }
                }

                is MultiContextInbound.ConnectionError -> throw RealtimeServerError(
                    message = message.message,
                    rawResponseBody = raw,
                    errorCode = message.errorCode,
                )
            }
        }
    }

    /** After close_socket, a normal close means every remaining context was completed. */
    private suspend fun completeAfterNormalClose() {
        val remaining = lock.withLock {
            state = SessionState.Closed
            contexts.values.toList().also { contexts.clear() }
        }
        remaining.forEach { record ->
            record.cancelJobs()
            record.channel.trySend(protocol.finishedEvent)
            record.channel.close()
        }
        withContext(NonCancellable) { runCatching { connection.close() } }
        scope.cancel()
    }

    private suspend fun failSession(failure: Throwable) {
        val affected = lock.withLock {
            if (state == SessionState.Closed || state is SessionState.Failed) return
            state = SessionState.Failed(failure)
            contexts.values.toList().also { contexts.clear() }
        }
        affected.forEach { record ->
            record.cancelJobs()
            record.channel.close(failure)
        }
        withContext(NonCancellable) { runCatching { connection.close() } }
        scope.cancel()
    }

    private suspend fun expireFinishingContext(record: ContextRecord) {
        delay(config.finishTimeoutMillis.milliseconds)
        lock.withLock {
            if (contexts[record.id] !== record) return
            contexts.remove(record.id)
        }
        record.keepAliveJob?.cancel()
        record.channel.close(
            TimeoutError(
                message = "${config.label} context '${record.id}' did not finish within " +
                    "${config.finishTimeoutMillis} ms.",
            ),
        )
    }

    private fun resetKeepAliveLocked(record: ContextRecord) {
        if (!config.keepAliveEnabled) return
        record.keepAliveJob?.cancel()
        record.keepAliveJob = scope.launch {
            while (true) {
                delay(config.keepAliveIntervalMillis.milliseconds)
                val failure = try {
                    lock.withLock {
                        if (state != SessionState.Active) return@launch
                        if (contexts[record.id] !== record || record.state != ContextState.Open) {
                            return@launch
                        }
                        send(protocol.keepAlive(record.id))
                    }
                    null
                } catch (sendFailure: SendFailure) {
                    sendFailure.failure
                }
                if (failure != null) {
                    failSession(failure)
                    return@launch
                }
            }
        }
    }

    private fun checkSessionActive() {
        when (val current = state) {
            SessionState.Active -> Unit
            SessionState.Finishing, SessionState.Closed ->
                throw IllegalStateException("The ${config.label} session is finishing or closed.")

            is SessionState.Failed -> throw IllegalStateException(
                "The ${config.label} session failed: ${current.cause.message}",
                current.cause,
            )
        }
    }

    private fun openContextLocked(contextId: String): ContextRecord {
        val record = contexts[contextId]
        check(record != null && record.state == ContextState.Open) {
            "The ${config.label} context '$contextId' is finishing or closed."
        }
        return record
    }

    private fun nextGeneratedId(): String {
        var id: String
        do {
            id = "context-${++generatedIdCounter}"
        } while (id in usedContextIds)
        return id
    }

    /**
     * Runs [block] under the session lock. A failed send fails the whole session, because the
     * shared connection is no longer usable, and the normalized failure is rethrown to the caller.
     */
    private suspend fun <T> guarded(block: suspend () -> T): T {
        try {
            return lock.withLock { block() }
        } catch (sendFailure: SendFailure) {
            failSession(sendFailure.failure)
            throw sendFailure.failure
        }
    }

    /** Must be called while holding [lock]. */
    private suspend fun send(message: String) {
        withContext(NonCancellable) {
            try {
                withTimeout(config.sendTimeoutMillis.milliseconds) { connection.send(message) }
            } catch (timeout: TimeoutCancellationException) {
                throw SendFailure(
                    TimeoutError(
                        message = "${config.label} send timed out after ${config.sendTimeoutMillis} ms.",
                        cause = timeout,
                    ),
                )
            } catch (throwable: Throwable) {
                throw SendFailure(throwable.toRealtimeFailure())
            }
        }
    }
}
