package dev.yveskalume.elevenlabs.tts

import dev.yveskalume.elevenlabs.error.ElevenLabsException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * One ElevenLabs WebSocket that hosts several independent text-to-speech contexts.
 *
 * Each context is an independent generation stream with its own text buffer, voice settings, and
 * audio output. A typical use is a conversational agent: every agent reply is spoken in a new
 * context, and when the user interrupts, the current context is closed and a new one is opened
 * without paying for a new connection.
 *
 * At most [MultiContextTtsSession.MAX_CONTEXTS] contexts may be open at the same time. Contexts are identified by an ID
 * that is unique for the lifetime of the session.
 *
 * Call [finish] to let ElevenLabs complete every context and close the connection, or [close] to
 * tear the connection down immediately. A transport or protocol failure ends the session and every
 * open context's [MultiContextTtsContext.events] flow terminates with that failure.
 */
public interface MultiContextTtsSession {
    /**
     * Opens a new context on this connection.
     *
     * The context is registered with ElevenLabs immediately, so [options] apply to all text sent
     * through it. Pass [contextId] to choose the ID yourself, for example to correlate it with a
     * message ID in your application; otherwise a unique ID is generated.
     *
     * @throws IllegalArgumentException if [contextId] is blank or was already used in this session.
     * @throws IllegalStateException if [MultiContextTtsSession.MAX_CONTEXTS] contexts are already open, or if the session
     * is finishing, closed, or failed.
     * @throws dev.yveskalume.elevenlabs.error.TimeoutError if sending the registration times out.
     * @throws dev.yveskalume.elevenlabs.error.NetworkError for a transport failure.
     */
    @Throws(ElevenLabsException::class, CancellationException::class)
    public suspend fun openContext(
        options: MultiContextTtsContextOptions = MultiContextTtsContextOptions(),
        contextId: String? = null,
    ): MultiContextTtsContext

    /**
     * Speaks [text] in a new context and emits its audio.
     *
     * The returned flow is cold. Each collection opens a new context, sends every non-empty value
     * of [text], finishes the context when [text] completes, and completes after the final audio
     * chunk. Cancelling collection closes the context without affecting the session or other
     * contexts, which makes it the natural building block for interruptions:
     *
     * ```kotlin
     * var speaking: Job? = null
     *
     * fun onAgentReply(reply: Flow<String>) {
     *     speaking?.cancel() // interrupt the previous reply
     *     speaking = scope.launch {
     *         session.stream(reply).collect { player.write(it.bytes) }
     *     }
     * }
     * ```
     *
     * Collection terminates with the same failures as [MultiContextTtsContext.events].
     */
    public fun stream(
        text: Flow<String>,
        options: MultiContextTtsContextOptions = MultiContextTtsContextOptions(),
    ): Flow<AudioChunk>

    /**
     * Asks ElevenLabs to complete every open context and close the connection, then suspends until
     * the connection is closed.
     *
     * Open contexts keep delivering their remaining audio and then emit [RealtimeTtsEvent.Finished].
     * Calling [finish] on a session that is already finishing or closed does nothing.
     *
     * @throws dev.yveskalume.elevenlabs.error.TimeoutError if the connection does not close within
     * [RealtimeTtsTimeouts.finishTimeoutMillis]; the session is closed in that case.
     * @throws ElevenLabsException if the connection fails while finishing.
     */
    @Throws(ElevenLabsException::class, CancellationException::class)
    public suspend fun finish()

    /**
     * Immediately closes the connection and every context.
     *
     * Open context flows complete without an error and without [RealtimeTtsEvent.Finished].
     * Calling it more than once is safe.
     */
    public suspend fun close()

    public companion object {
        /** Maximum number of contexts ElevenLabs allows to be open at the same time on one connection. */
        public const val MAX_CONTEXTS: Int = 5
    }
}

/**
 * One independent generation stream inside a [MultiContextTtsSession].
 *
 * A context behaves like a [RealtimeTtsSession] multiplexed over a shared connection:
 * [events] emits the audio generated for this context only, [finish] completes it gracefully, and
 * [close] stops it immediately. Closing or failing one context never affects the others.
 *
 * If a context stays idle longer than the ElevenLabs inactivity timeout, the server closes it and
 * [events] completes with [RealtimeTtsEvent.Finished]. Automatic keepalive, enabled by default in
 * [MultiContextTtsOptions.keepAlive], prevents this while the context is open.
 */
public interface MultiContextTtsContext : RealtimeTtsSession {
    /** Identifier of this context, unique within its session. */
    public val id: String

    /**
     * A buffered, single-consumer stream of the audio generated for this context.
     *
     * The flow completes after [RealtimeTtsEvent.Finished], or without it once [close] is called.
     * Audio still buffered or in flight when [close] is called is discarded. Events are buffered
     * without limit so that a slow consumer of one context never delays the others.
     *
     * Collection terminates with [dev.yveskalume.elevenlabs.error.RealtimeServerError] when
     * ElevenLabs reports an error for this context or the connection closes unexpectedly,
     * [dev.yveskalume.elevenlabs.error.TimeoutError] when [finish] does not complete in time,
     * [dev.yveskalume.elevenlabs.error.NetworkError] for transport failures, or
     * [dev.yveskalume.elevenlabs.error.SerializationError] when a message cannot be decoded.
     */
    override val events: Flow<RealtimeTtsEvent>

    /**
     * Resets the ElevenLabs inactivity timeout for this context without generating audio.
     *
     * This is only needed when [MultiContextTtsOptions.keepAlive] is disabled.
     *
     * @throws IllegalStateException if the context or session is finishing or closed.
     */
    @Throws(ElevenLabsException::class, CancellationException::class)
    public suspend fun keepAlive()

    /**
     * Generates any buffered text and closes this context once its audio is complete.
     *
     * Completion is reported by [RealtimeTtsEvent.Finished] on [events]. The context continues to
     * count toward [MultiContextTtsSession.MAX_CONTEXTS] until then. Calling it again, or after
     * [close], does nothing.
     */
    override suspend fun finish()

    /**
     * Stops this context immediately, for example when the listener interrupts.
     *
     * Undelivered audio is discarded, [events] completes, and the slot is released right away so a
     * replacement context can be opened. Other contexts are not affected. Calling it more than once,
     * or after the context finished, is safe.
     */
    override suspend fun close()
}
