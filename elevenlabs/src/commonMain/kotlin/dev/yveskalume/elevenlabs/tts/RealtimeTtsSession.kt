package dev.yveskalume.elevenlabs.tts

import kotlinx.coroutines.flow.Flow

/**
 * A controllable realtime text-to-speech WebSocket session.
 *
 * Sessions support one [events] collector. Call [finish] for graceful completion or [close] to
 * cancel immediately. After either operation, no more text may be sent.
 */
public interface RealtimeTtsSession {
    /** A buffered, single-consumer stream of events received from ElevenLabs. */
    public val events: Flow<RealtimeTtsEvent>

    /**
     * Sends non-empty partial [text]. Whitespace is preserved.
     *
     * When [flush] is true, ElevenLabs is also asked to generate all currently buffered text.
     */
    public suspend fun sendText(text: String, flush: Boolean = false)

    /** Forces generation of text currently buffered. */
    public suspend fun flush()

    /**
     * Gracefully signals that no more text will be sent.
     *
     * Completion is reported through [events]. If the server does not finish within
     * [RealtimeTtsTimeouts.finishTimeoutMillis], that flow terminates with an error.
     */
    public suspend fun finish()

    /** Immediately closes this session. Calling it more than once is safe. */
    public suspend fun close()
}
