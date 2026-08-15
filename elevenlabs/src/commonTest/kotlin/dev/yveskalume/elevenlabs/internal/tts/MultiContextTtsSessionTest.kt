package dev.yveskalume.elevenlabs.internal.tts

import dev.yveskalume.elevenlabs.ElevenLabsException
import dev.yveskalume.elevenlabs.tts.MultiContextTtsContextOptions
import dev.yveskalume.elevenlabs.tts.MultiContextTtsEvent
import dev.yveskalume.elevenlabs.tts.MultiContextTtsOptions
import dev.yveskalume.elevenlabs.tts.OutputFormat
import dev.yveskalume.elevenlabs.tts.RealtimeTtsGenerationConfig
import dev.yveskalume.elevenlabs.tts.RealtimeTtsKeepAlive
import dev.yveskalume.elevenlabs.tts.RealtimeTtsTimeouts
import dev.yveskalume.elevenlabs.tts.TextNormalization
import dev.yveskalume.elevenlabs.tts.VoiceSettings
import io.ktor.http.Url
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MultiContextTtsSessionTest {

    @Test
    fun `URL contains multi-context options and token`() {
        val url = Url(
            buildMultiContextTtsUrl(
                baseUrl = "https://api.elevenlabs.io",
                voiceId = "voice-1",
                options = MultiContextTtsOptions(
                    modelId = "eleven_flash_v2_5",
                    languageCode = "en",
                    outputFormat = OutputFormat.Pcm_24000,
                    syncAlignment = true,
                    autoMode = true,
                    applyTextNormalization = TextNormalization.Off,
                    seed = 42,
                    timeouts = RealtimeTtsTimeouts(inactivityTimeoutSeconds = 90),
                ),
                singleUseToken = "token-1",
            ),
        )

        assertEquals("wss", url.protocol.name)
        assertEquals("/v1/text-to-speech/voice-1/multi-stream-input", url.encodedPath)
        assertEquals("pcm_24000", url.parameters["output_format"])
        assertEquals("true", url.parameters["sync_alignment"])
        assertEquals("true", url.parameters["auto_mode"])
        assertEquals("off", url.parameters["apply_text_normalization"])
        assertEquals("90", url.parameters["inactivity_timeout"])
        assertEquals("42", url.parameters["seed"])
        assertEquals("token-1", url.parameters["single_use_token"])
    }

    @Test
    fun `context actions use context-aware protocol messages`() = runTest {
        val connection = FakeMultiContextConnection()
        val session = openSession(connection)
        val context = session.openContext(
            contextId = "answer-1",
            options = MultiContextTtsContextOptions(
                voiceSettings = VoiceSettings(stability = 0.5),
                generationConfig = RealtimeTtsGenerationConfig(listOf(50, 100)),
            ),
        )

        context.sendText("Hello", flush = true)
        context.flush()
        context.keepAlive()
        context.close()
        session.close()

        assertTrue(connection.sent[0].contains("\"context_id\":\"answer-1\""))
        assertTrue(connection.sent[0].contains("\"voice_settings\":{\"stability\":0.5}"))
        assertTrue(connection.sent[0].contains("\"chunk_length_schedule\":[50,100]"))
        assertEquals("{\"context_id\":\"answer-1\",\"text\":\"Hello\",\"flush\":true}", connection.sent[1])
        assertEquals("{\"context_id\":\"answer-1\",\"flush\":true}", connection.sent[2])
        assertEquals("{\"context_id\":\"answer-1\",\"text\":\"\"}", connection.sent[3])
        assertEquals("{\"context_id\":\"answer-1\",\"close_context\":true}", connection.sent[4])
        assertEquals("{\"close_socket\":true}", connection.sent[5])
        assertTrue(connection.closed)
    }

    @Test
    fun `audio and alignment are routed by context ID`() = runTest {
        val connection = FakeMultiContextConnection()
        val session = openSession(connection)
        session.openContext("answer-1")
        val event = async { session.events.first() }

        connection.emit(
            RealtimeConnectionFrame.Text(
                """{
                    "context_id":"answer-1",
                    "audio":"AQI=",
                    "is_final":false,
                    "alignment":{
                        "chars":["H","i"],
                        "char_start_times_ms":[0,10],
                        "char_durations_ms":[10,12]
                    }
                }""".trimIndent(),
            ),
        )
        runCurrent()

        val audio = assertIs<MultiContextTtsEvent.Audio>(event.await())
        assertEquals("answer-1", audio.contextId)
        assertContentEquals(byteArrayOf(1, 2), audio.bytes)
        assertEquals(listOf("H", "i"), audio.alignment?.characters)
        session.close()
    }

    @Test
    fun `final context event frees one of five active slots`() = runTest {
        val connection = FakeMultiContextConnection()
        val session = openSession(connection)
        repeat(5) { session.openContext("context-$it") }

        assertFailsWith<IllegalStateException> { session.openContext("context-5") }

        val finalEvent = async { session.events.first() }
        connection.emit(
            RealtimeConnectionFrame.Text(
                "{\"context_id\":\"context-0\",\"is_final\":true}",
            ),
        )
        runCurrent()
        assertIs<MultiContextTtsEvent.ContextFinished>(finalEvent.await())

        session.openContext("context-5")
        session.close()
    }

    @Test
    fun `context IDs cannot be reused within one socket`() = runTest {
        val session = openSession(FakeMultiContextConnection())
        val context = session.openContext("answer-1")
        context.close()

        assertFailsWith<IllegalArgumentException> { session.openContext("answer-1") }
        session.close()
    }

    @Test
    fun `keepalive is independent for every active context`() = runTest {
        val connection = FakeMultiContextConnection()
        val session = openSession(
            connection = connection,
            options = MultiContextTtsOptions(
                keepAlive = RealtimeTtsKeepAlive(intervalMillis = 100),
                timeouts = RealtimeTtsTimeouts(inactivityTimeoutSeconds = 1),
            ),
        )
        session.openContext("first")
        session.openContext("second")

        advanceTimeBy(100)
        runCurrent()

        assertTrue(connection.sent.contains("{\"context_id\":\"first\",\"text\":\"\"}"))
        assertTrue(connection.sent.contains("{\"context_id\":\"second\",\"text\":\"\"}"))
        session.close()
    }

    @Test
    fun `context error leaves other contexts active`() = runTest {
        val connection = FakeMultiContextConnection()
        val session = openSession(connection)
        session.openContext("first")
        val second = session.openContext("second")
        val event = async { session.events.first() }

        connection.emit(
            RealtimeConnectionFrame.Text(
                "{\"context_id\":\"first\",\"error\":{\"message\":\"Bad text\"}}",
            ),
        )
        runCurrent()

        assertIs<MultiContextTtsEvent.ContextError>(event.await())
        second.sendText("Still active")
        assertEquals("{\"context_id\":\"second\",\"text\":\"Still active\"}", connection.sent.last())
        session.close()
    }

    @Test
    fun `connection error closes the event flow`() = runTest {
        val connection = FakeMultiContextConnection()
        val session = openSession(connection)
        val completion = async { runCatching { session.events.toList() } }

        connection.emit(
            RealtimeConnectionFrame.Text(
                "{\"error\":{\"message\":\"Socket rejected\"}}",
            ),
        )
        runCurrent()

        val error = assertIs<ElevenLabsException.Realtime>(completion.await().exceptionOrNull())
        assertEquals("Socket rejected", error.message)
        assertTrue(connection.closed)
    }

    @Test
    fun `closing the socket waits for final server output`() = runTest {
        val connection = FakeMultiContextConnection(closeAfterCloseSocket = false)
        val session = openSession(connection)
        session.openContext("answer-1")
        val event = async { session.events.first() }
        val closing = async { runCatching { session.close() } }
        runCurrent()

        assertEquals("{\"close_socket\":true}", connection.sent.last())
        connection.emit(
            RealtimeConnectionFrame.Text(
                "{\"context_id\":\"answer-1\",\"audio\":\"AQI=\",\"is_final\":false}",
            ),
        )
        connection.emit(RealtimeConnectionFrame.Closed(code = 1000, reason = "Closed"))
        runCurrent()

        assertIs<MultiContextTtsEvent.Audio>(event.await())
        assertTrue(closing.await().isSuccess)
    }

    @Test
    fun `closing the socket respects the configured finish timeout`() = runTest {
        val connection = FakeMultiContextConnection(closeAfterCloseSocket = false)
        val session = openSession(
            connection = connection,
            options = MultiContextTtsOptions(
                keepAlive = RealtimeTtsKeepAlive(enabled = false),
                timeouts = RealtimeTtsTimeouts(finishTimeoutMillis = 100),
            ),
        )
        val closing = async { runCatching { session.close() } }

        advanceTimeBy(100)
        runCurrent()

        val error = assertIs<ElevenLabsException.Realtime>(closing.await().exceptionOrNull())
        assertTrue(error.message.orEmpty().contains("100 ms"))
        assertTrue(connection.closed)
    }

    @Test
    fun `connection timeout is reported`() = runTest {
        val pending = CompletableDeferred<RealtimeTtsConnection>()
        val opening = async {
            runCatching {
                MultiContextTtsSessionImpl.open(
                    openConnection = { pending.await() },
                    options = MultiContextTtsOptions(
                        keepAlive = RealtimeTtsKeepAlive(enabled = false),
                        timeouts = RealtimeTtsTimeouts(connectTimeoutMillis = 100),
                    ),
                    dispatcher = StandardTestDispatcher(testScheduler),
                )
            }
        }

        advanceTimeBy(100)
        runCurrent()

        val error = assertIs<ElevenLabsException.Realtime>(opening.await().exceptionOrNull())
        assertTrue(error.message.orEmpty().contains("100 ms"))
    }

    private suspend fun kotlinx.coroutines.test.TestScope.openSession(
        connection: FakeMultiContextConnection,
        options: MultiContextTtsOptions = MultiContextTtsOptions(
            keepAlive = RealtimeTtsKeepAlive(enabled = false),
        ),
    ) = MultiContextTtsSessionImpl.open(
        openConnection = { connection },
        options = options,
        dispatcher = StandardTestDispatcher(testScheduler),
    )
}

private class FakeMultiContextConnection(
    private val closeAfterCloseSocket: Boolean = true,
) : RealtimeTtsConnection {
    val sent = mutableListOf<String>()
    var closed = false
        private set

    private val incoming = Channel<RealtimeConnectionFrame>(Channel.UNLIMITED)

    override suspend fun send(value: String) {
        sent += value
        if (closeAfterCloseSocket && value == "{\"close_socket\":true}") {
            incoming.send(RealtimeConnectionFrame.Closed(code = 1000, reason = "Closed"))
        }
    }

    override suspend fun receive(): RealtimeConnectionFrame = incoming.receive()

    override suspend fun close() {
        closed = true
    }

    suspend fun emit(frame: RealtimeConnectionFrame) {
        incoming.send(frame)
    }
}
