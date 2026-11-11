package dev.yveskalume.elevenlabs.internal.realtime

import dev.yveskalume.elevenlabs.dialogue.DialogueEvent
import dev.yveskalume.elevenlabs.internal.dialogue.MultiContextDialogueProtocol
import dev.yveskalume.elevenlabs.internal.http.ElevenLabsHttpClient
import dev.yveskalume.elevenlabs.internal.tts.RealtimeConnectionFrame
import dev.yveskalume.elevenlabs.internal.tts.RealtimeTtsConnection
import dev.yveskalume.elevenlabs.internal.tts.TextToSpeechApiImpl
import dev.yveskalume.elevenlabs.internal.tts.dtos.DecodedRealtimeTtsMessage
import dev.yveskalume.elevenlabs.internal.tts.dtos.MultiContextTtsProtocol
import dev.yveskalume.elevenlabs.internal.tts.dtos.RealtimeTtsMessages
import dev.yveskalume.elevenlabs.realtime.Alignment
import dev.yveskalume.elevenlabs.realtime.KeepAlive
import dev.yveskalume.elevenlabs.tts.RealtimeTtsEvent
import dev.yveskalume.elevenlabs.tts.RealtimeTtsOptions
import dev.yveskalume.elevenlabs.tts.audioOnly
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RealtimeMessagesTest {

    @Test
    fun `realtime TTS reads the documented camelCase fields`() {
        val audio = RealtimeTtsMessages.decode(
            """{"audio":"${b64(1, 2)}","normalizedAlignment":{"chars":["a"],"charStartTimesMs":[0],"charDurationsMs":[5]},""" +
                """"alignment":{"chars":["A"],"charStartTimesMs":[10],"charDurationsMs":[20]}}""",
        )

        val event = assertIs<RealtimeTtsEvent.Audio>(assertIs<DecodedRealtimeTtsMessage.Event>(audio).value)
        assertEquals(Alignment(listOf("A"), listOf(10), listOf(20)), event.alignment)
        assertEquals(Alignment(listOf("a"), listOf(0), listOf(5)), event.normalizedAlignment)
        assertEquals(
            DecodedRealtimeTtsMessage.Event(RealtimeTtsEvent.Finished),
            RealtimeTtsMessages.decode("""{"isFinal":true,"audio":null}"""),
        )
    }

    @Test
    fun `realtime TTS reports unrecognized messages and ignores recognized empty ones`() {
        val raw = """{"usage":{"characters":12}}"""

        assertEquals(DecodedRealtimeTtsMessage.Event(RealtimeTtsEvent.Unknown(raw)), RealtimeTtsMessages.decode(raw))
        assertEquals(DecodedRealtimeTtsMessage.Ignored, RealtimeTtsMessages.decode("""{"audio":null,"isFinal":false}"""))
    }

    @Test
    fun `multi-context TTS reports unrecognized messages to their context`() {
        val raw = """{"contextId":"a","usage":{"characters":12}}"""

        assertEquals(
            listOf(MultiContextInbound.Event("a", RealtimeTtsEvent.Unknown(raw))),
            MultiContextTtsProtocol.decode(raw),
        )
        assertEquals(emptyList(), MultiContextTtsProtocol.decode("""{"contextId":"a","audio":"","isFinal":false}"""))
    }

    @Test
    fun `dialogue reports unrecognized messages but not documented unused fields`() {
        val raw = """{"context_id":"a","warning":"slow down"}"""

        assertEquals(listOf(MultiContextInbound.Event("a", DialogueEvent.Unknown(raw))), MultiContextDialogueProtocol.decode(raw))
        assertEquals(
            emptyList(),
            MultiContextDialogueProtocol.decode("""{"context_id":"a","audio":"","normalized_alignment":null}"""),
        )
    }

    @Test
    fun `streamRealtime keeps alignment and audioOnly keeps the bytes`() = runTest {
        val audioMessage = """{"audio":"${b64(7)}","alignment":{"chars":["H"],"charStartTimesMs":[0],"charDurationsMs":[9]}}"""
        val frames = Channel<RealtimeConnectionFrame>(Channel.UNLIMITED).apply {
            trySend(RealtimeConnectionFrame.Text(audioMessage))
        }
        val connection = object : RealtimeTtsConnection {
            // Like the server, only send the final message once the client asked to finish.
            override suspend fun send(value: String) {
                if (value == RealtimeTtsMessages.finish()) {
                    frames.trySend(RealtimeConnectionFrame.Text("""{"isFinal":true}"""))
                }
            }
            override suspend fun receive(): RealtimeConnectionFrame = frames.receive()
            override suspend fun close() = Unit
        }
        val api = TextToSpeechApiImpl(
            http = unusedHttp(),
            realtimeConnectionFactory = { _, _, _ -> connection },
        )
        val options = RealtimeTtsOptions(syncAlignment = true, keepAlive = KeepAlive(enabled = false))

        val events = api.streamRealtime("voice-1", flowOf("Hi"), options).toList()

        val audio = assertIs<RealtimeTtsEvent.Audio>(events.first())
        assertEquals(listOf("H"), audio.alignment?.characters)
        assertEquals(RealtimeTtsEvent.Finished, events.last())
        assertSame(audio.bytes, flowOf(audio).audioOnly().first().bytes)
    }

    @Test
    fun `events allow one collector at a time`() = runTest {
        val channel = Channel<Int>(Channel.UNLIMITED).apply { trySend(1) }
        val events = channel.receiveAsSingleCollectorFlow()

        val first = async { events.collect { awaitCancellation() } }
        runCurrent()

        assertFailsWith<IllegalStateException> { events.toList() }
        first.cancel()
        first.join()
        channel.trySend(2)
        channel.close()
        assertTrue(events.toList() == listOf(2))
    }

    private fun unusedHttp() = ElevenLabsHttpClient(
        baseUrl = "https://api.elevenlabs.io",
        apiKeyProvider = null,
        client = HttpClient(MockEngine { error("No request should be sent.") }),
        ownsClient = true,
    )

    private fun b64(vararg bytes: Byte): String = Base64.encode(bytes)
}
