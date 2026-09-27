package dev.yveskalume.elevenlabs.internal.tts

import dev.yveskalume.elevenlabs.error.NetworkError
import dev.yveskalume.elevenlabs.error.RealtimeServerError
import dev.yveskalume.elevenlabs.error.TimeoutError
import dev.yveskalume.elevenlabs.tts.RealtimeTtsEvent
import dev.yveskalume.elevenlabs.tts.RealtimeTtsKeepAlive
import dev.yveskalume.elevenlabs.tts.RealtimeTtsOptions
import dev.yveskalume.elevenlabs.tts.RealtimeTtsTimeouts
import io.ktor.websocket.CloseReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RealtimeTtsSessionImplTest {

    @Test
    fun `send after finishing preserves local state failure`() = runTest {
        val connection = object : RealtimeTtsConnection {
            override suspend fun send(value: String) = Unit
            override suspend fun receive(): RealtimeConnectionFrame = awaitCancellation()
            override suspend fun close() = Unit
        }
        val session = RealtimeTtsSessionImpl.open(
            openConnection = { connection },
            options = RealtimeTtsOptions(keepAlive = RealtimeTtsKeepAlive(enabled = false)),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        session.finish()

        assertFailsWith<IllegalStateException> { session.sendText("too late") }
        assertFailsWith<IllegalStateException> { session.flush() }
        session.close()
    }

    @Test
    fun `keepalive transport failure is normalized`() = runTest {
        var sends = 0
        val connection = object : RealtimeTtsConnection {
            override suspend fun send(value: String) {
                if (sends++ > 0) throw IOException("keepalive failed")
            }

            override suspend fun receive(): RealtimeConnectionFrame = awaitCancellation()
            override suspend fun close() = Unit
        }
        val session = RealtimeTtsSessionImpl.open(
            openConnection = { connection },
            options = RealtimeTtsOptions(
                keepAlive = RealtimeTtsKeepAlive(intervalMillis = 1),
            ),
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        val eventsFailure = async {
            runCatching { session.events.toList() }.exceptionOrNull()
        }

        advanceTimeBy(1)
        runCurrent()

        assertIs<NetworkError>(eventsFailure.await())
        session.close()
    }

    @Test
    fun `keepalive send timeout is preserved`() = runTest {
        var sends = 0
        val connection = object : RealtimeTtsConnection {
            override suspend fun send(value: String) {
                if (sends++ > 0) awaitCancellation()
            }

            override suspend fun receive(): RealtimeConnectionFrame = awaitCancellation()
            override suspend fun close() = Unit
        }
        val session = RealtimeTtsSessionImpl.open(
            openConnection = { connection },
            options = RealtimeTtsOptions(
                timeouts = RealtimeTtsTimeouts(sendTimeoutMillis = 10),
                keepAlive = RealtimeTtsKeepAlive(intervalMillis = 1),
            ),
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        val eventsFailure = async {
            runCatching { session.events.toList() }.exceptionOrNull()
        }

        advanceTimeBy(11)
        runCurrent()

        assertIs<TimeoutError>(eventsFailure.await())
        session.close()
    }

    @Test
    fun `normal close after finish ends the stream without an error`() = runTest {
        val serverClose = CompletableDeferred<Unit>()
        val connection = object : RealtimeTtsConnection {
            override suspend fun send(value: String) = Unit
            override suspend fun receive(): RealtimeConnectionFrame {
                serverClose.await()
                return RealtimeConnectionFrame.Closed(code = CloseReason.Codes.NORMAL.code, reason = "")
            }
            override suspend fun close() = Unit
        }
        val session = RealtimeTtsSessionImpl.open(
            openConnection = { connection },
            options = RealtimeTtsOptions(keepAlive = RealtimeTtsKeepAlive(enabled = false)),
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        val events = async { session.events.toList() }

        session.finish()
        serverClose.complete(Unit)
        runCurrent()

        assertEquals(listOf<RealtimeTtsEvent>(RealtimeTtsEvent.Finished), events.await())
        session.close()
    }

    @Test
    fun `server close before finish fails with a readable message`() = runTest {
        val connection = object : RealtimeTtsConnection {
            override suspend fun send(value: String) = Unit
            override suspend fun receive(): RealtimeConnectionFrame =
                RealtimeConnectionFrame.Closed(code = CloseReason.Codes.NORMAL.code, reason = "")
            override suspend fun close() = Unit
        }
        val session = RealtimeTtsSessionImpl.open(
            openConnection = { connection },
            options = RealtimeTtsOptions(keepAlive = RealtimeTtsKeepAlive(enabled = false)),
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val failure = runCatching { session.events.toList() }.exceptionOrNull()

        assertIs<RealtimeServerError>(failure)
        assertTrue(failure.message.orEmpty().isNotBlank())
        session.close()
    }
}
