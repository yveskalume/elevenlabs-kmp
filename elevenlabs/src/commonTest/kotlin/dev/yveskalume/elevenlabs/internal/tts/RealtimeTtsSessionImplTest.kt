package dev.yveskalume.elevenlabs.internal.tts

import dev.yveskalume.elevenlabs.error.ElevenLabsException
import dev.yveskalume.elevenlabs.realtime.KeepAlive
import dev.yveskalume.elevenlabs.tts.RealtimeTtsEvent
import dev.yveskalume.elevenlabs.tts.RealtimeTtsOptions
import dev.yveskalume.elevenlabs.tts.RealtimeTtsReconnectPolicy
import dev.yveskalume.elevenlabs.tts.RealtimeTtsTimeouts
import io.ktor.websocket.CloseReason
import kotlinx.coroutines.CancellationException
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
            options = RealtimeTtsOptions { keepAlive = KeepAlive { enabled = false } },
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
            options = RealtimeTtsOptions { keepAlive = KeepAlive { intervalMillis = 1 } },
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        val eventsFailure = async {
            runCatching { session.events.toList() }.exceptionOrNull()
        }

        advanceTimeBy(1)
        runCurrent()

        assertIs<ElevenLabsException.Network>(eventsFailure.await())
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
            options = RealtimeTtsOptions {
                timeouts = RealtimeTtsTimeouts { sendTimeoutMillis = 10 }
                keepAlive = KeepAlive { intervalMillis = 1 }
            },
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        val eventsFailure = async {
            runCatching { session.events.toList() }.exceptionOrNull()
        }

        advanceTimeBy(11)
        runCurrent()

        assertIs<ElevenLabsException.Timeout>(eventsFailure.await())
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
            options = RealtimeTtsOptions { keepAlive = KeepAlive { enabled = false } },
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
            options = RealtimeTtsOptions { keepAlive = KeepAlive { enabled = false } },
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val failure = runCatching { session.events.toList() }.exceptionOrNull()

        assertIs<ElevenLabsException.RealtimeServer>(failure)
        assertTrue(failure.message.orEmpty().isNotBlank())
        session.close()
    }

    @Test
    fun `sends after the server ended the session rethrow its failure`() = runTest {
        val connection = object : RealtimeTtsConnection {
            override suspend fun send(value: String) = Unit
            override suspend fun receive(): RealtimeConnectionFrame =
                RealtimeConnectionFrame.Closed(code = CloseReason.Codes.INTERNAL_ERROR.code, reason = "")
            override suspend fun close() = Unit
        }
        val session = RealtimeTtsSessionImpl.open(
            openConnection = { connection },
            options = RealtimeTtsOptions { keepAlive = KeepAlive { enabled = false } },
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        runCurrent()

        val failure = assertFailsWith<ElevenLabsException.RealtimeServer> { session.sendText("Hello") }
        assertEquals(CloseReason.Codes.INTERNAL_ERROR.code, failure.closeCode)
        assertFailsWith<ElevenLabsException.RealtimeServer> { session.flush() }

        // The session did fail, so closing it afterwards does not turn this into a state error.
        session.close()
        assertFailsWith<ElevenLabsException.RealtimeServer> { session.sendText("Hello") }
    }

    @Test
    fun `a transport cancelled underneath the session is a network failure`() = runTest {
        val connection = object : RealtimeTtsConnection {
            override suspend fun send(value: String) = Unit
            override suspend fun receive(): RealtimeConnectionFrame =
                throw CancellationException("transport cancelled")
            override suspend fun close() = Unit
        }
        val session = RealtimeTtsSessionImpl.open(
            openConnection = { connection },
            options = RealtimeTtsOptions { keepAlive = KeepAlive { enabled = false } },
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        runCurrent()

        assertFailsWith<ElevenLabsException.Network> { session.events.toList() }
        assertFailsWith<ElevenLabsException.Network> { session.sendText("Hello") }
        session.close()
    }

    @Test
    fun `a failed send fails the session`() = runTest {
        var sends = 0
        var closed = false
        val connection = object : RealtimeTtsConnection {
            override suspend fun send(value: String) {
                if (sends++ > 0) throw IOException("socket reset")
            }

            override suspend fun receive(): RealtimeConnectionFrame = awaitCancellation()
            override suspend fun close() {
                closed = true
            }
        }
        val session = RealtimeTtsSessionImpl.open(
            openConnection = { connection },
            options = RealtimeTtsOptions { keepAlive = KeepAlive { enabled = false } },
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        val failure = assertFailsWith<ElevenLabsException.Network> { session.sendText("Hello") }
        val sendsBeforeRetry = sends

        assertEquals(failure, assertFailsWith<ElevenLabsException.Network> { session.flush() })
        assertEquals(sendsBeforeRetry, sends)
        assertFailsWith<ElevenLabsException.Network> { session.events.toList() }
        assertTrue(closed)
        session.close()
    }

    @Test
    fun `the first failure wins over one caused by closing the failed connection`() = runTest {
        val closedBySdk = CompletableDeferred<Unit>()
        var sends = 0
        var opens = 0
        val connection = object : RealtimeTtsConnection {
            override suspend fun send(value: String) {
                if (sends++ > 0) throw IOException("socket reset")
            }

            override suspend fun receive(): RealtimeConnectionFrame {
                closedBySdk.await()
                return RealtimeConnectionFrame.Closed(code = CloseReason.Codes.INTERNAL_ERROR.code, reason = "")
            }

            override suspend fun close() {
                closedBySdk.complete(Unit)
            }
        }
        val session = RealtimeTtsSessionImpl.open(
            openConnection = {
                if (opens++ > 0) throw IOException("still offline")
                connection
            },
            options = RealtimeTtsOptions {
                keepAlive = KeepAlive { enabled = false }
                reconnectPolicy = RealtimeTtsReconnectPolicy.BeforeFirstAudio {
                    maxAttempts = 2
                    initialDelayMillis = 0
                }
            },
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        assertFailsWith<ElevenLabsException.Network> { session.sendText("Hello") }
        runCurrent()

        assertFailsWith<ElevenLabsException.Network> { session.events.toList() }
        assertFailsWith<ElevenLabsException.Network> { session.flush() }
        session.close()
    }

    @Test
    fun `a final message the caller did not ask for completes events and rejects sends`() = runTest {
        val serverFinished = CompletableDeferred<Unit>()
        val connection = object : RealtimeTtsConnection {
            override suspend fun send(value: String) = Unit
            override suspend fun receive(): RealtimeConnectionFrame {
                if (serverFinished.isCompleted) return awaitCancellation()
                serverFinished.complete(Unit)
                return RealtimeConnectionFrame.Text("""{"is_final":true}""")
            }

            override suspend fun close() = Unit
        }
        val session = RealtimeTtsSessionImpl.open(
            openConnection = { connection },
            options = RealtimeTtsOptions { keepAlive = KeepAlive { enabled = false } },
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        assertEquals(listOf<RealtimeTtsEvent>(RealtimeTtsEvent.Finished), session.events.toList())

        assertFailsWith<ElevenLabsException.RealtimeServer> { session.sendText("too late") }
        session.close()
    }

    @Test
    fun `sending after close on a healthy session is a state error`() = runTest {
        val connection = object : RealtimeTtsConnection {
            override suspend fun send(value: String) = Unit
            override suspend fun receive(): RealtimeConnectionFrame = awaitCancellation()
            override suspend fun close() = Unit
        }
        val session = RealtimeTtsSessionImpl.open(
            openConnection = { connection },
            options = RealtimeTtsOptions { keepAlive = KeepAlive { enabled = false } },
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        session.close()

        assertFailsWith<IllegalStateException> { session.sendText("too late") }
    }

    @Test
    fun `a token provider that lost the network is retried by the reconnect policy`() = runTest {
        var attempts = 0
        val connection = object : RealtimeTtsConnection {
            override suspend fun send(value: String) = Unit
            override suspend fun receive(): RealtimeConnectionFrame = awaitCancellation()
            override suspend fun close() = Unit
        }
        val session = RealtimeTtsSessionImpl.open(
            openConnection = {
                if (attempts++ == 0) {
                    throw ElevenLabsException.MissingCredentials("offline", IOException("offline"))
                }
                connection
            },
            options = RealtimeTtsOptions {
                keepAlive = KeepAlive { enabled = false }
                reconnectPolicy = RealtimeTtsReconnectPolicy.BeforeFirstAudio { initialDelayMillis = 0 }
            },
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        assertEquals(2, attempts)
        session.close()
    }

    @Test
    fun `maxAttempts counts reconnects and not the first connection`() = runTest {
        var opens = 0

        assertFailsWith<IOException> {
            RealtimeTtsSessionImpl.open(
                openConnection = {
                    opens++
                    throw IOException("offline")
                },
                options = RealtimeTtsOptions {
                    reconnectPolicy = RealtimeTtsReconnectPolicy.BeforeFirstAudio {
                        maxAttempts = 2
                        initialDelayMillis = 0
                    }
                },
                dispatcher = StandardTestDispatcher(testScheduler),
            )
        }

        assertEquals(3, opens)
    }

    @Test
    fun `other credential failures are not retried by the reconnect policy`() = runTest {
        var attempts = 0

        assertFailsWith<ElevenLabsException.MissingCredentials> {
            RealtimeTtsSessionImpl.open(
                openConnection = {
                    attempts++
                    throw ElevenLabsException.MissingCredentials("No key is configured.")
                },
                options = RealtimeTtsOptions {
                    reconnectPolicy = RealtimeTtsReconnectPolicy.BeforeFirstAudio { initialDelayMillis = 0 }
                },
                dispatcher = StandardTestDispatcher(testScheduler),
            )
        }

        assertEquals(1, attempts)
    }
}
