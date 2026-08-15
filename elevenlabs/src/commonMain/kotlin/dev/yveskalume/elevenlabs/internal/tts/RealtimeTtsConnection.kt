package dev.yveskalume.elevenlabs.internal.tts

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText

internal interface RealtimeTtsConnection {
    suspend fun send(value: String)
    suspend fun receive(): RealtimeConnectionFrame
    suspend fun close()
}


internal class RealtimeTtsConnectionImpl(
    private val session: DefaultClientWebSocketSession,
) : RealtimeTtsConnection {
    override suspend fun send(value: String) {
        session.send(Frame.Text(value))
    }

    override suspend fun receive(): RealtimeConnectionFrame {
        while (true) {
            when (val frame = session.incoming.receiveCatching().getOrNull()) {
                is Frame.Text -> return RealtimeConnectionFrame.Text(frame.readText())
                is Frame.Close, null -> {
                    val reason = session.closeReason.await()
                    return RealtimeConnectionFrame.Closed(
                        code = reason?.code,
                        reason = reason?.message,
                    )
                }

                else -> Unit
            }
        }
    }

    override suspend fun close() {
        session.close(CloseReason(CloseReason.Codes.NORMAL, "Client closed realtime TTS session."))
    }
}
