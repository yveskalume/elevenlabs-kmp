package dev.yveskalume.elevenlabs.stt

import dev.yveskalume.elevenlabs.error.ElevenLabsException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * A controllable realtime speech-to-text WebSocket session.
 *
 * Sessions support one [events] collector. Close the session when audio capture ends.
 */
public interface RealtimeSttSession {
    /**
     * A buffered, single-consumer stream of server events.
     *
     * Collection terminates with [dev.yveskalume.elevenlabs.error.ElevenLabsException.RealtimeServer] for a server
     * protocol failure, [dev.yveskalume.elevenlabs.error.ElevenLabsException.Network] for transport failure, or
     * [dev.yveskalume.elevenlabs.error.ElevenLabsException.Serialization] when a server message cannot be decoded.
     */
    public val events: Flow<RealtimeSttEvent>

    /**
     * Sends a non-empty [audio] chunk encoded according to [RealtimeSttOptions.audioFormat].
     * When [commit] is true, the accumulated audio is committed after this chunk.
     *
     * @throws IllegalArgumentException if [audio] is empty.
     * @throws IllegalStateException if you already called [close].
     * @throws ElevenLabsException with the original failure if the session has failed, for example
     * because the server closed the connection.
     */
    @Throws(
        ElevenLabsException::class,
        IllegalArgumentException::class,
        IllegalStateException::class,
        CancellationException::class,
    )
    public suspend fun sendAudio(audio: ByteArray, commit: Boolean = false)

    /**
     * Commits the audio accumulated since the previous commit.
     *
     * @throws IllegalStateException if you already called [close].
     * @throws ElevenLabsException with the original failure if the session has failed.
     */
    @Throws(
        ElevenLabsException::class,
        IllegalArgumentException::class,
        IllegalStateException::class,
        CancellationException::class,
    )
    public suspend fun commit()

    /** Immediately closes the realtime session. */
    public suspend fun close()
}
