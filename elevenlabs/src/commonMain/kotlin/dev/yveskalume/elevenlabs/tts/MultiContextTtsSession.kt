package dev.yveskalume.elevenlabs.tts

import kotlinx.coroutines.flow.Flow

public interface MultiContextTtsSession {
    /** A buffered, single-consumer stream preserving WebSocket arrival order across contexts. */
    public val events: Flow<MultiContextTtsEvent>

    /** Creates a new independent generation context on this WebSocket. */
    public suspend fun openContext(
        contextId: String,
        options: MultiContextTtsContextOptions = MultiContextTtsContextOptions(),
    ): MultiContextTtsContext

    /** Gracefully closes every context and then closes the WebSocket. */
    public suspend fun close()
}

public interface MultiContextTtsContext {
    public val id: String

    public suspend fun sendText(text: String, flush: Boolean = false)

    public suspend fun flush()

    public suspend fun keepAlive()

    /** Closes only this context, leaving the WebSocket and other contexts active. */
    public suspend fun close()
}
