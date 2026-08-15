package dev.yveskalume.elevenlabs.tts

import kotlinx.coroutines.flow.Flow

interface RealtimeTtsSession {
    /** A buffered, single-consumer stream of events received from ElevenLabs. */
    val events: Flow<RealtimeTtsEvent>

    /** Sends non-empty partial text. Whitespace is preserved. */
    suspend fun sendText(text: String, flush: Boolean = false)

    /** Forces generation of text currently buffered by ElevenLabs. */
    suspend fun flush()

    /** Gracefully signals that no more text will be sent. */
    suspend fun finish()

    /** Immediately closes this session. */
    suspend fun close()
}
