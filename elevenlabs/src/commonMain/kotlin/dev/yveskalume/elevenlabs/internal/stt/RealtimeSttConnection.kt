package dev.yveskalume.elevenlabs.internal.stt

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText

internal interface RealtimeSttConnection {
    suspend fun send(value: String)
    suspend fun receive(): RealtimeSttConnectionFrame
    suspend fun close()
}


internal class RealtimeSttConnectionImpl(
    private val session: DefaultClientWebSocketSession,
) : RealtimeSttConnection {
    override suspend fun send(value: String) {
        session.send(Frame.Text(value))
    }

    override suspend fun receive(): RealtimeSttConnectionFrame {
        while (true) {
            when (val frame = session.incoming.receiveCatching().getOrNull()) {
                is Frame.Text -> return RealtimeSttConnectionFrame.Text(frame.readText())
                is Frame.Close, null -> {
                    val reason = session.closeReason.await()
                    return RealtimeSttConnectionFrame.Closed(reason?.code, reason?.message)
                }

                else -> Unit
            }
        }
    }

    override suspend fun close() {
        session.close(CloseReason(CloseReason.Codes.NORMAL, "Client closed realtime STT session."))
    }
}
