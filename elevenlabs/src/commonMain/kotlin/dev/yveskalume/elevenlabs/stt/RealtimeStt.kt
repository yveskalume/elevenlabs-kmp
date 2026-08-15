package dev.yveskalume.elevenlabs.stt

import kotlinx.coroutines.flow.Flow

interface RealtimeSttSession {
    /** A buffered, single-consumer stream of server events. */
    val events: Flow<RealtimeSttEvent>

    /** Sends a non-empty chunk encoded according to [RealtimeSttOptions.audioFormat]. */
    suspend fun sendAudio(audio: ByteArray, commit: Boolean = false)

    /** Commits the audio accumulated since the previous commit. */
    suspend fun commit()

    /** Immediately closes the realtime session. */
    suspend fun close()
}
