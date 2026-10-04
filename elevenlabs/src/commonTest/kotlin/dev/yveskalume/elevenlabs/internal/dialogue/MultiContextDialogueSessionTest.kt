package dev.yveskalume.elevenlabs.internal.dialogue

import dev.yveskalume.elevenlabs.dialogue.DialogueEvent
import dev.yveskalume.elevenlabs.dialogue.DialogueLine
import dev.yveskalume.elevenlabs.dialogue.MultiContextDialogueContextOptions
import dev.yveskalume.elevenlabs.dialogue.MultiContextDialogueOptions
import dev.yveskalume.elevenlabs.dialogue.MultiContextDialogueSession
import dev.yveskalume.elevenlabs.error.ElevenLabsErrorKind
import dev.yveskalume.elevenlabs.error.RealtimeServerError
import dev.yveskalume.elevenlabs.internal.tts.RealtimeConnectionFrame
import dev.yveskalume.elevenlabs.internal.tts.RealtimeTtsConnection
import dev.yveskalume.elevenlabs.tts.OutputFormat
import dev.yveskalume.elevenlabs.tts.PronunciationDictionaryLocator
import dev.yveskalume.elevenlabs.tts.RealtimeTtsKeepAlive
import dev.yveskalume.elevenlabs.tts.TextNormalization
import io.ktor.http.Url
import io.ktor.websocket.CloseReason
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
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
class MultiContextDialogueSessionTest {

    @Test
    fun `URL contains dialogue options and token`() {
        val url = Url(
            buildMultiContextDialogueUrl(
                baseUrl = "https://api.elevenlabs.io",
                options = MultiContextDialogueOptions(
                    modelId = "eleven_v3",
                    languageCode = "fr",
                    outputFormat = OutputFormat.Pcm_24000,
                    syncAlignment = true,
                    textNormalization = TextNormalization.On,
                    seed = 7,
                ),
                singleUseToken = "token-1",
            ),
        )

        assertEquals("wss", url.protocol.name)
        assertEquals("/v1/text-to-dialogue/multi-stream-input", url.encodedPath)
        assertEquals("eleven_v3", url.parameters["model_id"])
        assertEquals("fr", url.parameters["language_code"])
        assertEquals("pcm_24000", url.parameters["output_format"])
        assertEquals("true", url.parameters["sync_alignment"])
        assertEquals("on", url.parameters["apply_text_normalization"])
        assertEquals("7", url.parameters["seed"])
        assertEquals("token-1", url.parameters["single_use_token"])
        assertNull(url.parameters["inactivity_timeout"])
    }

    @Test
    fun `options reject values ElevenLabs would refuse`() {
        assertFailsWith<IllegalArgumentException> { MultiContextDialogueOptions(modelId = "eleven_flash_v2_5") }
        assertFailsWith<IllegalArgumentException> { MultiContextDialogueOptions(seed = 0) }
        assertFailsWith<IllegalArgumentException> {
            MultiContextDialogueOptions(keepAlive = RealtimeTtsKeepAlive(intervalMillis = 20_000))
        }
        assertFailsWith<IllegalArgumentException> { MultiContextDialogueContextOptions(stability = 1.5) }
        assertFailsWith<IllegalArgumentException> { DialogueLine("alice", "") }
    }

    @Test
    fun `contexts register voices and send lines as inputs`() = runTest {
        val connection = FakeConnection()
        val session = openSession(connection, modelId = "eleven_v3")

        val scene = session.openContext(
            voiceIds = listOf("alice", "bob"),
            options = MultiContextDialogueContextOptions(
                stability = 0.3,
                pronunciationDictionaries = listOf(PronunciationDictionaryLocator("dict", "v1")),
            ),
            contextId = "scene-1",
        )
        scene.send(DialogueLine("alice", "Did you hear that?", newTurn = true))
        scene.send(listOf(DialogueLine("bob", "Hear what?", newTurn = true), DialogueLine("bob", " Nothing.")))
        scene.flush()
        scene.keepAlive()
        scene.finish()

        val sent = connection.sentJson()
        assertEquals(6, sent.size)
        sent.forEach { assertEquals("scene-1", it.string("context_id")) }
        assertEquals("""["alice","bob"]""", sent[0]["voices"].toString())
        assertEquals("0.3", sent[0].getValue("voice_settings").jsonObject.string("stability"))
        assertEquals(
            """[{"pronunciation_dictionary_id":"dict","version_id":"v1"}]""",
            sent[0]["pronunciation_dictionary_locators"].toString(),
        )
        assertEquals(
            """[{"text":"Did you hear that?","voice_id":"alice","new_turn":true}]""",
            sent[1]["inputs"].toString(),
        )
        assertEquals(2, sent[2].getValue("inputs").jsonArray.size)
        assertNull(sent[2].getValue("inputs").jsonArray[1].jsonObject["new_turn"])
        assertEquals(setOf("context_id", "flush"), sent[3].keys)
        assertEquals(setOf("context_id", "keep_alive"), sent[4].keys)
        // close_context flushes in the dialogue protocol, so finish() sends only that.
        assertEquals(setOf("context_id", "close_context"), sent[5].keys)
        session.close()
    }

    @Test
    fun `invalid voices are rejected before anything is sent`() = runTest {
        val connection = FakeConnection()
        val conversational = openSession(connection)

        assertFailsWith<IllegalArgumentException> { conversational.openContext(emptyList()) }
        assertFailsWith<IllegalArgumentException> { conversational.openContext(listOf("a", "a")) }
        // eleven_v3_conversational, the default model, allows one voice per context.
        assertFailsWith<IllegalArgumentException> { conversational.openContext(listOf("a", "b")) }
        assertTrue(connection.sent.isEmpty())

        val context = conversational.openContext(listOf("alice"))
        assertFailsWith<IllegalArgumentException> { context.send(DialogueLine("bob", "Hi")) }
        assertEquals(1, connection.sent.size)
        conversational.close()

        val v3 = openSession(FakeConnection(), modelId = "eleven_v3")
        v3.openContext(List(MultiContextDialogueSession.MAX_VOICES_PER_CONTEXT) { "voice-$it" })
        assertFailsWith<IllegalArgumentException> {
            v3.openContext(List(MultiContextDialogueSession.MAX_VOICES_PER_CONTEXT + 1) { "voice-$it" })
        }
        v3.close()
    }

    @Test
    fun `audio, turn boundaries and completion are routed per context`() = runTest {
        val connection = FakeConnection()
        val session = openSession(connection)
        val first = session.openContext(listOf("alice"), contextId = "first")
        val second = session.openContext(listOf("bob"), contextId = "second")
        val firstEvents = async { first.events.toList() }
        val secondEvents = async { second.events.toList() }

        connection.serverSends(
            """{"context_id":"first","audio":"${b64(1)}",
               "alignment":{"chars":["H"],"char_start_times_ms":[0],"char_durations_ms":[80]}}""",
        )
        connection.serverSends("""{"context_id":"second","audio":"${b64(2)}"}""")
        connection.serverSends("""{"context_id":"first","is_final_audio_for_turn":true}""")
        connection.serverSends("""{"context_id":"first","is_final":true}""")
        connection.serverSends("""{"context_id":"second","is_final":true}""")
        runCurrent()

        val events = firstEvents.await()
        assertEquals(3, events.size)
        val audio = assertIs<DialogueEvent.Audio>(events[0])
        assertContentEquals(byteArrayOf(1), audio.bytes)
        assertEquals(listOf(80), audio.alignment?.characterDurationsMs)
        assertEquals(DialogueEvent.TurnFinished, events[1])
        assertEquals(DialogueEvent.Finished, events[2])

        val other = secondEvents.await()
        assertContentEquals(byteArrayOf(2), assertIs<DialogueEvent.Audio>(other[0]).bytes)
        assertEquals(DialogueEvent.Finished, other[1])
        session.close()
    }

    @Test
    fun `server errors fail the whole session with their code and kind`() = runTest {
        val connection = FakeConnection()
        val session = openSession(connection)
        val first = session.openContext(listOf("alice"))
        val second = session.openContext(listOf("bob"))

        connection.serverSends(
            """{"message":"Missing credentials","error":"authentication_required","code":1008}""",
        )
        runCurrent()

        val failure = assertFailsWith<RealtimeServerError> { first.events.toList() }
        assertEquals("Missing credentials", failure.message)
        assertEquals("authentication_required", failure.errorCode)
        assertEquals(ElevenLabsErrorKind.Authentication, failure.kind)
        assertFailsWith<RealtimeServerError> { second.events.toList() }
        assertFailsWith<IllegalStateException> { session.openContext(listOf("carol")) }
        session.close()
    }

    @Test
    fun `stream sends lines, finishes, and emits audio`() = runTest {
        val connection = FakeConnection()
        connection.onSend = { message ->
            val json = Json.parseToJsonElement(message).jsonObject
            if ("close_context" in json) {
                val id = json.string("context_id")
                connection.serverSends("""{"context_id":"$id","audio":"${b64(5)}"}""")
                connection.serverSends("""{"context_id":"$id","is_final":true}""")
            }
        }
        val session = openSession(connection)

        val chunks = session.stream(
            voiceIds = listOf("alice"),
            lines = flowOf(DialogueLine("alice", "Hello!", newTurn = true)),
        ).toList()

        assertContentEquals(byteArrayOf(5), chunks.single().bytes)
        session.close()
    }

    @Test
    fun `finishing the session flushes open contexts before closing the socket`() = runTest {
        val connection = FakeConnection()
        val session = openSession(connection)
        val context = session.openContext(listOf("alice"), contextId = "a")
        val events = async { context.events.toList() }

        val finishing = async { session.finish() }
        runCurrent()
        val (flush, closeSocket) = connection.sentJson().takeLast(2)
        assertEquals(setOf("context_id", "flush"), flush.keys)
        assertEquals(setOf("close_socket"), closeSocket.keys)

        connection.serverCloses(CloseReason.Codes.NORMAL.code)
        runCurrent()
        finishing.await()
        assertEquals(listOf<DialogueEvent>(DialogueEvent.Finished), events.await())
    }

    @Test
    fun `keepalive uses the dialogue keep_alive flag`() = runTest {
        val connection = FakeConnection()
        val session = openSession(connection, keepAlive = RealtimeTtsKeepAlive(intervalMillis = 1_000))
        session.openContext(listOf("alice"), contextId = "a")

        advanceTimeBy(1_001)
        runCurrent()

        assertEquals("true", connection.sentJson().last().string("keep_alive"))
        session.close()
    }

    private suspend fun TestScope.openSession(
        connection: FakeConnection,
        modelId: String? = null,
        keepAlive: RealtimeTtsKeepAlive = RealtimeTtsKeepAlive(enabled = false),
    ): MultiContextDialogueSession = MultiContextDialogueSessionImpl.open(
        openConnection = { connection },
        options = MultiContextDialogueOptions(modelId = modelId, keepAlive = keepAlive),
        dispatcher = StandardTestDispatcher(testScheduler),
    )

    private fun b64(vararg bytes: Byte): String = Base64.encode(bytes)

    private class FakeConnection : RealtimeTtsConnection {
        private val incoming = Channel<RealtimeConnectionFrame>(Channel.UNLIMITED)
        val sent = mutableListOf<String>()
        var onSend: (String) -> Unit = {}

        override suspend fun send(value: String) {
            sent += value
            onSend(value)
        }

        override suspend fun receive(): RealtimeConnectionFrame = incoming.receive()
        override suspend fun close() = Unit

        fun serverSends(json: String) {
            incoming.trySend(RealtimeConnectionFrame.Text(json))
        }

        fun serverCloses(code: Short) {
            incoming.trySend(RealtimeConnectionFrame.Closed(code, ""))
        }

        fun sentJson(): List<JsonObject> = sent.map { Json.parseToJsonElement(it).jsonObject }
    }
}

private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content
