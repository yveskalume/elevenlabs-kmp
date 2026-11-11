package dev.yveskalume.elevenlabs.tts

import dev.yveskalume.elevenlabs.error.ElevenLabsException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * A controllable realtime text-to-speech WebSocket session.
 *
 * Sessions support one [events] collector. Call [finish] for graceful completion or [close] to
 * cancel immediately. After either operation, no more text may be sent.
 */
public interface RealtimeTtsSession {
    /**
     * A buffered, single-consumer stream of events received from ElevenLabs.
     *
     * Collection terminates with [dev.yveskalume.elevenlabs.error.ElevenLabsException.RealtimeServer] for a server
     * protocol failure, [dev.yveskalume.elevenlabs.error.ElevenLabsException.Network] for transport failure,
     * [dev.yveskalume.elevenlabs.error.ElevenLabsException.Timeout] for session timeouts, or
     * [dev.yveskalume.elevenlabs.error.ElevenLabsException.Serialization] when a message cannot be decoded.
     */
    public val events: Flow<RealtimeTtsEvent>

    /**
     * Sends non-empty partial [text]. Whitespace is preserved.
     *
     * When [flush] is true, ElevenLabs is also asked to generate all currently buffered text.
     *
     * @throws IllegalArgumentException if [text] is empty.
     * @throws IllegalStateException if you already called [finish] or [close].
     * @throws dev.yveskalume.elevenlabs.error.ElevenLabsException.Timeout if sending times out.
     * @throws ElevenLabsException with the original failure if the session has failed, for example
     * because the server closed the connection.
     */
    @Throws(
        ElevenLabsException::class,
        IllegalArgumentException::class,
        IllegalStateException::class,
        CancellationException::class,
    )
    public suspend fun sendText(text: String, flush: Boolean = false)

    /**
     * Forces generation of text currently buffered.
     *
     * @throws IllegalStateException if you already called [finish] or [close].
     * @throws dev.yveskalume.elevenlabs.error.ElevenLabsException.Timeout if sending times out.
     * @throws ElevenLabsException with the original failure if the session has failed.
     */
    @Throws(
        ElevenLabsException::class,
        IllegalArgumentException::class,
        IllegalStateException::class,
        CancellationException::class,
    )
    public suspend fun flush()

    /**
     * Gracefully signals that no more text will be sent.
     *
     * Completion is reported through [events]. If the server does not finish within
     * [RealtimeTtsTimeouts.finishTimeoutMillis], that flow terminates with an error.
     *
     * @throws dev.yveskalume.elevenlabs.error.ElevenLabsException.Timeout if sending the finish signal times out.
     */
    @Throws(
        ElevenLabsException::class,
        IllegalArgumentException::class,
        IllegalStateException::class,
        CancellationException::class,
    )
    public suspend fun finish()

    /** Immediately closes this session. Calling it more than once is safe. */
    public suspend fun close()
}
