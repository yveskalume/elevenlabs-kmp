package dev.yveskalume.elevenlabs.tts

import kotlinx.coroutines.flow.Flow

interface MultiContextTtsSession {
    /** A buffered, single-consumer stream preserving WebSocket arrival order across contexts. */
    val events: Flow<MultiContextTtsEvent>

    /** Creates a new independent generation context on this WebSocket. */
    suspend fun openContext(
        contextId: String,
        options: MultiContextTtsContextOptions = MultiContextTtsContextOptions(),
    ): MultiContextTtsContext

    /** Gracefully closes every context and then closes the WebSocket. */
    suspend fun close()
}

interface MultiContextTtsContext {
    val id: String

    suspend fun sendText(text: String, flush: Boolean = false)

    suspend fun flush()

    suspend fun keepAlive()

    /** Closes only this context, leaving the WebSocket and other contexts active. */
    suspend fun close()
}
