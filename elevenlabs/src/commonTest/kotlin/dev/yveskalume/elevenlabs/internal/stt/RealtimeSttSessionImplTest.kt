package dev.yveskalume.elevenlabs.internal.stt

import dev.yveskalume.elevenlabs.error.ElevenLabsException
import io.ktor.websocket.CloseReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RealtimeSttSessionImplTest {

    @Test
    fun `outbound transport failure is normalized`() = runTest {
        val session = RealtimeSttSessionImpl(
            connection = object : RealtimeSttConnection {
                override suspend fun send(value: String) {
                    throw IOException("disconnected")
                }

                override suspend fun receive(): RealtimeSttConnectionFrame = awaitCancellation()

                override suspend fun close() = Unit
            },
        )

        assertFailsWith<ElevenLabsException.Network> { session.sendAudio(byteArrayOf(1)) }
        assertFailsWith<ElevenLabsException.Network> { session.commit() }
        session.close()
    }

    @Test
    fun `closed session preserves local state failure`() = runTest {
        val session = RealtimeSttSessionImpl(
            connection = object : RealtimeSttConnection {
                override suspend fun send(value: String) = Unit
                override suspend fun receive(): RealtimeSttConnectionFrame = awaitCancellation()
                override suspend fun close() = Unit
            },
        )
        session.close()

        assertFailsWith<IllegalStateException> { session.commit() }
    }

    @Test
    fun `sends after the server ended the session rethrow its failure`() = runTest {
        val session = RealtimeSttSessionImpl(
            connection = object : RealtimeSttConnection {
                override suspend fun send(value: String) = Unit
                override suspend fun receive(): RealtimeSttConnectionFrame =
                    RealtimeSttConnectionFrame.Closed(
                        code = CloseReason.Codes.INTERNAL_ERROR.code,
                        reason = "",
                    )

                override suspend fun close() = Unit
            },
        )
        assertFailsWith<ElevenLabsException.RealtimeServer> { session.events.toList() }

        val failure = assertFailsWith<ElevenLabsException.RealtimeServer> {
            session.sendAudio(byteArrayOf(1))
        }
        assertEquals(CloseReason.Codes.INTERNAL_ERROR.code, failure.closeCode)
        assertFailsWith<ElevenLabsException.RealtimeServer> { session.commit() }

        // The session did fail, so closing it afterwards does not turn this into a state error.
        session.close()
        assertFailsWith<ElevenLabsException.RealtimeServer> { session.commit() }
    }

    @Test
    fun `a failed send fails the session`() = runTest {
        var sends = 0
        var closed = false
        val session = RealtimeSttSessionImpl(
            connection = object : RealtimeSttConnection {
                override suspend fun send(value: String) {
                    sends++
                    throw IOException("disconnected")
                }

                override suspend fun receive(): RealtimeSttConnectionFrame = awaitCancellation()

                override suspend fun close() {
                    closed = true
                }
            },
        )

        val failure = assertFailsWith<ElevenLabsException.Network> { session.commit() }

        assertEquals(failure, assertFailsWith<ElevenLabsException.Network> { session.commit() })
        assertEquals(1, sends)
        assertFailsWith<ElevenLabsException.Network> { session.events.toList() }
        assertTrue(closed)
        session.close()
    }

    @Test
    fun `a transport cancelled underneath the session is a network failure`() = runTest {
        val session = RealtimeSttSessionImpl(
            connection = object : RealtimeSttConnection {
                override suspend fun send(value: String) = Unit
                override suspend fun receive(): RealtimeSttConnectionFrame =
                    throw CancellationException("transport cancelled")

                override suspend fun close() = Unit
            },
        )

        assertFailsWith<ElevenLabsException.Network> { session.events.toList() }
        assertFailsWith<ElevenLabsException.Network> { session.commit() }
        session.close()
    }
}
