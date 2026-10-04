package dev.yveskalume.elevenlabs.internal.tts

import dev.yveskalume.elevenlabs.error.NetworkError
import dev.yveskalume.elevenlabs.error.RealtimeServerError
import dev.yveskalume.elevenlabs.error.SerializationError
import dev.yveskalume.elevenlabs.error.TimeoutError
import dev.yveskalume.elevenlabs.tts.MultiContextTtsContextOptions
import dev.yveskalume.elevenlabs.tts.MultiContextTtsOptions
import dev.yveskalume.elevenlabs.tts.MultiContextTtsSession
import dev.yveskalume.elevenlabs.tts.OutputFormat
import dev.yveskalume.elevenlabs.tts.PronunciationDictionaryLocator
import dev.yveskalume.elevenlabs.tts.RealtimeTtsEvent
import dev.yveskalume.elevenlabs.tts.RealtimeTtsGenerationConfig
import dev.yveskalume.elevenlabs.tts.RealtimeTtsKeepAlive
import dev.yveskalume.elevenlabs.tts.RealtimeTtsTimeouts
import dev.yveskalume.elevenlabs.tts.TextNormalization
import dev.yveskalume.elevenlabs.tts.VoiceSettings
import io.ktor.http.Url
import io.ktor.websocket.CloseReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MultiContextTtsSessionTest {

    // region Connection URL

    @Test
    fun `URL contains connection options and token`() {
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
                    textNormalization = TextNormalization.Off,
                    seed = 42,
                    timeouts = RealtimeTtsTimeouts(inactivityTimeoutSeconds = 90),
                ),
                singleUseToken = "token-1",
            ),
        )

        assertEquals("wss", url.protocol.name)
        assertEquals("/v1/text-to-speech/voice-1/multi-stream-input", url.encodedPath)
        assertEquals("eleven_flash_v2_5", url.parameters["model_id"])
        assertEquals("en", url.parameters["language_code"])
        assertEquals("pcm_24000", url.parameters["output_format"])
        assertEquals("true", url.parameters["sync_alignment"])
        assertEquals("true", url.parameters["auto_mode"])
        assertEquals("off", url.parameters["apply_text_normalization"])
        assertEquals("90", url.parameters["inactivity_timeout"])
        assertEquals("42", url.parameters["seed"])
        assertEquals("token-1", url.parameters["single_use_token"])
    }

    @Test
    fun `URL omits unset optional parameters`() {
        val url = Url(
            buildMultiContextTtsUrl(
                baseUrl = "https://api.elevenlabs.io",
                voiceId = "voice-1",
                options = MultiContextTtsOptions(),
                singleUseToken = null,
            ),
        )

        assertNull(url.parameters["model_id"])
        assertNull(url.parameters["apply_text_normalization"])
        assertNull(url.parameters["seed"])
        assertNull(url.parameters["single_use_token"])
    }

    @Test
    fun `options reject invalid values`() {
        assertFailsWith<IllegalArgumentException> { MultiContextTtsOptions(seed = -1) }
        assertFailsWith<IllegalArgumentException> { MultiContextTtsOptions(seed = 4_294_967_296) }
        assertFailsWith<IllegalArgumentException> { PronunciationDictionaryLocator(" ", "v1") }
        assertFailsWith<IllegalArgumentException> { PronunciationDictionaryLocator("dict", " ") }
        assertFailsWith<IllegalArgumentException> {
            MultiContextTtsContextOptions(
                pronunciationDictionaries = List(4) { PronunciationDictionaryLocator("dict-$it", "v1") },
            )
        }
    }

    // endregion

    // region Outgoing protocol

    @Test
    fun `context operations send context-scoped protocol messages`() = runTest {
        val connection = FakeConnection()
        val session = openSession(connection)

        val context = session.openContext(
            contextId = "answer-1",
            options = MultiContextTtsContextOptions(
                voiceSettings = VoiceSettings(stability = 0.5),
                generationConfig = RealtimeTtsGenerationConfig(listOf(50, 100)),
                pronunciationDictionaries = listOf(PronunciationDictionaryLocator("dict", "v2")),
            ),
        )
        context.sendText("Hello", flush = true)
        context.sendText("world")
        context.flush()
        context.keepAlive()
        context.close()

        val sent = connection.sentJson()
        assertEquals(6, sent.size)
        sent.forEach { assertEquals("answer-1", it.string("context_id")) }

        assertEquals(" ", sent[0].string("text"))
        assertEquals("0.5", sent[0].obj("voice_settings").string("stability"))
        assertEquals(
            "[50,100]",
            sent[0].obj("generation_config")["chunk_length_schedule"].toString(),
        )
        assertEquals(
            """[{"pronunciation_dictionary_id":"dict","version_id":"v2"}]""",
            sent[0]["pronunciation_dictionary_locators"].toString(),
        )

        assertEquals("Hello", sent[1].string("text"))
        assertEquals("true", sent[1].string("flush"))
        assertEquals("world", sent[2].string("text"))
        assertNull(sent[2]["flush"])
        assertEquals("", sent[3].string("text"))
        assertEquals("true", sent[3].string("flush"))
        assertEquals(setOf("context_id", "text"), sent[4].keys)
        assertEquals("", sent[4].string("text"))
        assertEquals("true", sent[5].string("close_context"))

        session.close()
    }

    @Test
    fun `generated context IDs are unique`() = runTest {
        val session = openSession(FakeConnection())

        val first = session.openContext()
        val second = session.openContext()

        assertTrue(first.id.isNotBlank())
        assertTrue(first.id != second.id)
        session.close()
    }

    @Test
    fun `invalid text and IDs are rejected before sending`() = runTest {
        val connection = FakeConnection()
        val session = openSession(connection)
        val context = session.openContext(contextId = "a")

        assertFailsWith<IllegalArgumentException> { context.sendText("") }
        assertFailsWith<IllegalArgumentException> { session.openContext(contextId = " ") }
        assertFailsWith<IllegalArgumentException> { session.openContext(contextId = "a") }

        context.close()
        // IDs stay reserved for the whole session, even after the context is closed.
        assertFailsWith<IllegalArgumentException> { session.openContext(contextId = "a") }
        session.close()
    }

    @Test
    fun `at most five contexts can be open`() = runTest {
        val session = openSession(FakeConnection())

        val contexts = List(MultiContextTtsSession.MAX_CONTEXTS) { session.openContext() }

        assertFailsWith<IllegalStateException> { session.openContext() }
        contexts.first().close()
        session.openContext()
        session.close()
    }

    // endregion

    // region Incoming routing

    @Test
    fun `audio is routed to its own context and finishes on is_final`() = runTest {
        val connection = FakeConnection()
        val session = openSession(connection)
        val first = session.openContext(contextId = "first")
        val second = session.openContext(contextId = "second")
        val firstEvents = async { first.events.toList() }
        val secondEvents = async { second.events.toList() }

        // Both field casings documented by ElevenLabs are accepted.
        connection.serverSends("""{"contextId":"second","audio":"${b64(2)}","isFinal":false}""")
        connection.serverSends("""{"context_id":"first","audio":"${b64(1)}","is_final":false}""")
        connection.serverSends("""{"contextId":"first","is_final":true}""")
        connection.serverSends("""{"context_id":"second","isFinal":true}""")
        runCurrent()

        assertAudioThenFinished(byteArrayOf(1), firstEvents.await())
        assertAudioThenFinished(byteArrayOf(2), secondEvents.await())
        session.close()
    }

    @Test
    fun `alignment is decoded in both casings`() = runTest {
        val connection = FakeConnection()
        val session = openSession(connection)
        val context = session.openContext(contextId = "a")
        val event = async { context.events.first() }

        connection.serverSends(
            """{"contextId":"a","audio":"${b64(1)}",
               "alignment":{"chars":["H","i"],"charStartTimesMs":[0,10],"charsDurationsMs":[10,20]},
               "normalized_alignment":{"chars":["h"],"char_start_times_ms":[5],"char_durations_ms":[7]}}
            """.trimIndent(),
        )
        runCurrent()

        val audio = assertIs<RealtimeTtsEvent.Audio>(event.await())
        assertEquals(listOf("H", "i"), audio.alignment?.characters)
        assertEquals(listOf(0, 10), audio.alignment?.characterStartTimesMs)
        assertEquals(listOf(10, 20), audio.alignment?.characterDurationsMs)
        assertEquals(listOf(7), audio.normalizedAlignment?.characterDurationsMs)
        session.close()
    }

    @Test
    fun `audio and is_final in one message emit both events`() = runTest {
        val connection = FakeConnection()
        val session = openSession(connection)
        val context = session.openContext(contextId = "a")
        val events = async { context.events.toList() }

        connection.serverSends("""{"contextId":"a","audio":"${b64(9)}","isFinal":true}""")
        runCurrent()

        assertAudioThenFinished(byteArrayOf(9), events.await())
        session.close()
    }

    @Test
    fun `closing a context discards buffered and late audio and releases its slot`() = runTest {
        val connection = FakeConnection()
        val session = openSession(connection)
        val interrupted = List(MultiContextTtsSession.MAX_CONTEXTS) { session.openContext() }.first()

        connection.serverSends("""{"contextId":"${interrupted.id}","audio":"${b64(1)}"}""")
        runCurrent()
        interrupted.close()
        connection.serverSends("""{"contextId":"${interrupted.id}","audio":"${b64(2)}"}""")
        connection.serverSends("""{"contextId":"${interrupted.id}","isFinal":true}""")
        runCurrent()

        assertEquals(emptyList(), interrupted.events.toList())
        assertEquals("true", connection.sentJson().last().string("close_context"))
        // The replacement can be opened immediately, which is what interruption handling needs.
        session.openContext()
        interrupted.close() // Closing twice is safe.
        session.close()
    }

    @Test
    fun `a server-closed context rejects further text`() = runTest {
        val connection = FakeConnection()
        val session = openSession(connection)
        val context = session.openContext(contextId = "a")

        // For example after the per-context inactivity timeout.
        connection.serverSends("""{"contextId":"a","isFinal":true}""")
        runCurrent()

        assertEquals(listOf<RealtimeTtsEvent>(RealtimeTtsEvent.Finished), context.events.toList())
        assertFailsWith<IllegalStateException> { context.sendText("too late") }
        session.close()
    }

    @Test
    fun `a context error only fails that context`() = runTest {
        val connection = FakeConnection()
        val session = openSession(connection)
        val failing = session.openContext(contextId = "failing")
        val healthy = session.openContext(contextId = "healthy")
        val healthyEvents = async { healthy.events.toList() }

        connection.serverSends(
            """{"contextId":"failing","error":"Invalid voice settings","error_code":"invalid_settings"}""",
        )
        runCurrent()

        val failure = assertFailsWith<RealtimeServerError> { failing.events.toList() }
        assertEquals("Invalid voice settings", failure.message)
        assertEquals("invalid_settings", failure.errorCode)

        healthy.sendText("still works")
        connection.serverSends("""{"contextId":"healthy","audio":"${b64(3)}"}""")
        connection.serverSends("""{"contextId":"healthy","isFinal":true}""")
        runCurrent()
        assertAudioThenFinished(byteArrayOf(3), healthyEvents.await())
        session.close()
    }

    // endregion

    // region Session failures

    @Test
    fun `a connection error fails every context and the session`() = runTest {
        val connection = FakeConnection()
        val session = openSession(connection)
        val first = session.openContext()
        val second = session.openContext()

        connection.serverSends("""{"error":"quota_exceeded"}""")
        runCurrent()

        assertEquals(
            "quota_exceeded",
            assertFailsWith<RealtimeServerError> { first.events.toList() }.message,
        )
        assertFailsWith<RealtimeServerError> { second.events.toList() }
        assertTrue(connection.closed)

        val stateFailure = assertFailsWith<IllegalStateException> { session.openContext() }
        assertIs<RealtimeServerError>(stateFailure.cause)
        assertFailsWith<IllegalStateException> { first.sendText("nope") }
        session.close()
    }

    @Test
    fun `an unexpected server close fails open contexts`() = runTest {
        val connection = FakeConnection()
        val session = openSession(connection)
        val context = session.openContext()

        connection.serverCloses(CloseReason.Codes.INTERNAL_ERROR.code, "")
        runCurrent()

        val failure = assertFailsWith<RealtimeServerError> { context.events.toList() }
        assertEquals(CloseReason.Codes.INTERNAL_ERROR.code, failure.closeCode)
        assertTrue(failure.message.orEmpty().isNotBlank())
        session.close()
    }

    @Test
    fun `an undecodable message fails the session with a serialization error`() = runTest {
        val connection = FakeConnection()
        val session = openSession(connection)
        val context = session.openContext()

        connection.serverSends("not json")
        runCurrent()

        assertFailsWith<SerializationError> { context.events.toList() }
        session.close()
    }

    @Test
    fun `a failed send fails the session and is reported to the caller`() = runTest {
        val connection = FakeConnection()
        val session = openSession(connection)
        val sender = session.openContext()
        val bystander = session.openContext()

        connection.sendFailure = IOException("socket reset")

        assertFailsWith<NetworkError> { sender.sendText("Hello") }
        assertFailsWith<NetworkError> { bystander.events.toList() }
        session.close()
    }

    @Test
    fun `cancelling a caller during a send does not fail the session`() = runTest {
        val connection = FakeConnection()
        val session = openSession(connection)
        val context = session.openContext()
        val gate = CompletableDeferred<Unit>()
        connection.sendGate = gate

        val sending = launch { context.sendText("interrupted") }
        runCurrent()
        sending.cancel()
        gate.complete(Unit)
        runCurrent()

        connection.sendGate = null
        context.sendText("the connection is still healthy")
        session.openContext()
        session.close()
    }

    // endregion

    // region Graceful completion

    @Test
    fun `finishing a context flushes and closes it, then emits Finished`() = runTest {
        val connection = FakeConnection()
        val session = openSession(connection)
        val context = session.openContext(contextId = "a")
        val events = async { context.events.toList() }

        context.sendText("Last words.")
        context.finish()
        context.finish() // Idempotent.

        val sent = connection.sentJson().takeLast(2)
        assertEquals("true", sent[0].string("flush"))
        assertEquals("true", sent[1].string("close_context"))
        assertFailsWith<IllegalStateException> { context.sendText("after finish") }

        connection.serverSends("""{"contextId":"a","audio":"${b64(4)}"}""")
        connection.serverSends("""{"contextId":"a","isFinal":true}""")
        runCurrent()

        assertAudioThenFinished(byteArrayOf(4), events.await())
        session.close()
    }

    @Test
    fun `a context finish timeout only fails that context`() = runTest {
        val connection = FakeConnection()
        val session = openSession(
            connection,
            timeouts = RealtimeTtsTimeouts(finishTimeoutMillis = 1_000),
        )
        val slow = session.openContext()
        val other = session.openContext()
        val slowFailure = async { runCatching { slow.events.toList() }.exceptionOrNull() }

        slow.finish()
        advanceTimeBy(1_001)
        runCurrent()

        assertIs<TimeoutError>(slowFailure.await())
        other.sendText("unaffected")
        session.close()
    }

    @Test
    fun `finishing the session closes the socket and completes open contexts`() = runTest {
        val connection = FakeConnection()
        val session = openSession(connection)
        val context = session.openContext(contextId = "a")
        val events = async { context.events.toList() }

        val finishing = async { session.finish() }
        runCurrent()
        val (flush, closeSocket) = connection.sentJson().takeLast(2)
        assertEquals("a", flush.string("context_id"))
        assertEquals("true", flush.string("flush"))
        assertEquals(setOf("close_socket"), closeSocket.keys)
        assertFailsWith<IllegalStateException> { session.openContext() }

        connection.serverSends("""{"contextId":"a","audio":"${b64(5)}"}""")
        connection.serverCloses(CloseReason.Codes.NORMAL.code, "")
        runCurrent()

        finishing.await()
        assertAudioThenFinished(byteArrayOf(5), events.await())
        session.finish() // Idempotent.
        session.close()
    }

    @Test
    fun `finishing the session times out when the server never closes`() = runTest {
        val connection = FakeConnection()
        val session = openSession(
            connection,
            timeouts = RealtimeTtsTimeouts(finishTimeoutMillis = 1_000),
        )
        val context = session.openContext()

        val failure = runCatching { session.finish() }.exceptionOrNull()

        assertIs<TimeoutError>(failure)
        assertIs<TimeoutError>(runCatching { context.events.toList() }.exceptionOrNull())
        assertTrue(connection.closed)
    }

    @Test
    fun `closing the session completes contexts without an error`() = runTest {
        val connection = FakeConnection()
        val session = openSession(connection)
        val context = session.openContext()
        val events = async { context.events.toList() }
        runCurrent()

        session.close()
        session.close() // Idempotent.

        assertEquals(emptyList(), events.await())
        assertTrue(connection.closed)
        assertFailsWith<IllegalStateException> { session.openContext() }
    }

    // endregion

    // region Keepalive

    @Test
    fun `idle contexts are kept alive until they finish`() = runTest {
        val connection = FakeConnection()
        val session = openSession(
            connection,
            keepAlive = RealtimeTtsKeepAlive(enabled = true, intervalMillis = 1_000),
        )
        val context = session.openContext(contextId = "a")

        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(1, connection.keepAliveCount("a"))

        // Activity resets the timer.
        advanceTimeBy(500)
        context.sendText("Hello")
        advanceTimeBy(900)
        runCurrent()
        assertEquals(1, connection.keepAliveCount("a"))

        context.finish()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(1, connection.keepAliveCount("a"))
        session.close()
    }

    // endregion

    // region stream()

    @Test
    fun `stream speaks text in a new context and completes`() = runTest {
        val connection = FakeConnection()
        connection.onSend = { message ->
            val json = Json.parseToJsonElement(message).jsonObject
            if ("close_context" in json) {
                val id = json.string("context_id")
                connection.serverSends("""{"contextId":"$id","audio":"${b64(7)}"}""")
                connection.serverSends("""{"contextId":"$id","isFinal":true}""")
            }
        }
        val session = openSession(connection)

        val chunks = session.stream(flowOf("Hello ", "", "world.")).toList()

        assertEquals(1, chunks.size)
        assertContentEquals(byteArrayOf(7), chunks.single().bytes)
        val texts = connection.sentJson().mapNotNull { it["text"]?.jsonPrimitive?.content }
        assertEquals(listOf(" ", "Hello ", "world.", ""), texts)
        session.close()
    }

    @Test
    fun `cancelling a stream closes its context but keeps the session usable`() = runTest {
        val connection = FakeConnection()
        val session = openSession(connection)
        // A reply that is still being generated when the listener interrupts.
        val reply = flow {
            emit("Hello")
            awaitCancellation()
        }

        val speaking = launch { session.stream(reply).collect {} }
        runCurrent()
        val contextId = connection.sentJson().first().string("context_id")
        speaking.cancel()
        runCurrent()

        assertEquals("true", connection.sentJson().last().string("close_context"))
        assertEquals(contextId, connection.sentJson().last().string("context_id"))
        session.openContext()
        session.close()
    }

    // endregion

    // region Helpers

    private suspend fun TestScope.openSession(
        connection: FakeConnection,
        timeouts: RealtimeTtsTimeouts = RealtimeTtsTimeouts(),
        keepAlive: RealtimeTtsKeepAlive = RealtimeTtsKeepAlive(enabled = false),
    ): MultiContextTtsSession = MultiContextTtsSessionImpl.open(
        openConnection = { connection },
        options = MultiContextTtsOptions(timeouts = timeouts, keepAlive = keepAlive),
        dispatcher = StandardTestDispatcher(testScheduler),
    )

    private fun assertAudioThenFinished(expected: ByteArray, events: List<RealtimeTtsEvent>) {
        assertEquals(2, events.size, "Expected audio followed by Finished, got $events")
        assertContentEquals(expected, assertIs<RealtimeTtsEvent.Audio>(events[0]).bytes)
        assertEquals(RealtimeTtsEvent.Finished, events[1])
    }

    private fun b64(vararg bytes: Byte): String = Base64.encode(bytes)

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    private fun JsonObject.obj(key: String): JsonObject = getValue(key).jsonObject

    private class FakeConnection : RealtimeTtsConnection {
        private val incoming = Channel<RealtimeConnectionFrame>(Channel.UNLIMITED)
        val sent = mutableListOf<String>()
        var closed = false
        var sendFailure: Throwable? = null
        var sendGate: CompletableDeferred<Unit>? = null
        var onSend: (String) -> Unit = {}

        override suspend fun send(value: String) {
            sendFailure?.let { throw it }
            sendGate?.await()
            sent += value
            onSend(value)
        }

        override suspend fun receive(): RealtimeConnectionFrame = incoming.receive()

        override suspend fun close() {
            closed = true
        }

        fun serverSends(json: String) {
            incoming.trySend(RealtimeConnectionFrame.Text(json))
        }

        fun serverCloses(code: Short, reason: String) {
            incoming.trySend(RealtimeConnectionFrame.Closed(code, reason))
        }

        fun sentJson(): List<JsonObject> = sent.map { Json.parseToJsonElement(it).jsonObject }

        fun keepAliveCount(contextId: String): Int = sentJson().count {
            it.keys == setOf("context_id", "text") &&
                it.string("context_id") == contextId &&
                it.string("text").isEmpty()
        }

        private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content
    }

    // endregion
}
