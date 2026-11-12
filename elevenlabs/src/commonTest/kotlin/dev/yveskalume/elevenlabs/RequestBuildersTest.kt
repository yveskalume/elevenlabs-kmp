package dev.yveskalume.elevenlabs

import dev.yveskalume.elevenlabs.models.ModelId
import dev.yveskalume.elevenlabs.realtime.KeepAlive
import dev.yveskalume.elevenlabs.stt.RealtimeSttOptions
import dev.yveskalume.elevenlabs.stt.SpeechToTextRequest
import dev.yveskalume.elevenlabs.tts.RealtimeTtsGenerationConfig
import dev.yveskalume.elevenlabs.tts.RealtimeTtsOptions
import dev.yveskalume.elevenlabs.tts.RealtimeTtsReconnectPolicy
import dev.yveskalume.elevenlabs.tts.RealtimeTtsTimeouts
import dev.yveskalume.elevenlabs.tts.TextToSpeechRequest
import dev.yveskalume.elevenlabs.tts.VoiceSettings
import dev.yveskalume.elevenlabs.voices.ListVoicesRequest
import kotlinx.io.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RequestBuildersTest {

    @Test
    fun `factories apply defaults and the configured values`() {
        val request = TextToSpeechRequest(voiceId = "voice-1", text = "Hello") {
            modelId = ModelId.ElevenFlashV2_5
            voiceSettings = VoiceSettings { stability = 0.5 }
        }

        assertEquals("voice-1", request.voiceId)
        assertEquals(ModelId.ElevenFlashV2_5, request.modelId)
        assertEquals(0.5, request.voiceSettings?.stability)
        assertEquals(true, request.enableLogging)
    }

    @Test
    fun `toBuilder creates a changed copy and leaves the original alone`() {
        val first = ListVoicesRequest { pageSize = 25 }
        val next = first.toBuilder().apply { nextPageToken = "next-1" }.build()

        assertEquals(null, first.nextPageToken)
        assertEquals("next-1", next.nextPageToken)
        assertEquals(25, next.pageSize)
        assertEquals(first, first.toBuilder().build())
    }

    @Test
    fun `equality hashing and toString cover every value`() {
        val options = RealtimeTtsOptions {
            syncAlignment = true
            timeouts = RealtimeTtsTimeouts { finishTimeoutMillis = 1_000 }
        }
        val same = RealtimeTtsOptions {
            syncAlignment = true
            timeouts = RealtimeTtsTimeouts { finishTimeoutMillis = 1_000 }
        }

        assertEquals(options, same)
        assertEquals(options.hashCode(), same.hashCode())
        assertNotEquals(options, options.toBuilder().apply { enableSsmlParsing = true }.build())
        assertTrue("finishTimeoutMillis=1000" in options.toString())
        assertEquals(
            RealtimeTtsReconnectPolicy.BeforeFirstAudio { maxAttempts = 5 },
            RealtimeTtsReconnectPolicy.BeforeFirstAudio { maxAttempts = 5 },
        )
    }

    @Test
    fun `builders validate when they build`() {
        assertFailsWith<IllegalArgumentException> { VoiceSettings { stability = 1.5 } }
        assertFailsWith<IllegalArgumentException> { KeepAlive.Builder().apply { intervalMillis = 0 }.build() }
        assertFailsWith<IllegalArgumentException> {
            RealtimeTtsOptions {
                keepAlive = KeepAlive { intervalMillis = 70_000 }
            }
        }
        assertFailsWith<IllegalArgumentException> { RealtimeTtsReconnectPolicy.BeforeFirstAudio { maxAttempts = 0 } }
    }

    @Test
    fun `speech to text requires a file name and content type`() {
        assertFailsWith<IllegalArgumentException> {
            SpeechToTextRequest(audio = byteArrayOf(1), fileName = "a.mp3", contentType = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            SpeechToTextRequest(audio = byteArrayOf(), fileName = "a.mp3", contentType = "audio/mpeg")
        }
        assertFailsWith<IllegalArgumentException> {
            SpeechToTextRequest(fileName = "a.mp3", contentType = "audio/mpeg", openAudio = { Buffer() }) {
                audioSize = 0
            }
        }
    }

    @Test
    fun `an in-memory speech to text request always uploads the array's length`() {
        val request = SpeechToTextRequest(audio = byteArrayOf(1, 2, 3), fileName = "a.mp3", contentType = "audio/mpeg") {
            audioSize = 99
        }

        assertEquals(3L, request.audioSize)
    }

    @Test
    fun `options keep their own copy of the lists they validate`() {
        val keyterms = mutableListOf("kotlin")
        val schedule = mutableListOf(50)
        val options = RealtimeSttOptions { this.keyterms = keyterms }
        val config = RealtimeTtsGenerationConfig(schedule)

        repeat(60) { keyterms += "term-$it" }
        schedule += -1

        assertEquals(listOf("kotlin"), options.keyterms)
        assertEquals(listOf(50), config.chunkLengthSchedule)
    }

    @Test
    fun `a single-use token is hidden from toString`() {
        val authorization = Authorization.SingleUseToken("secret-token")

        assertTrue("secret-token" !in authorization.toString())
        assertEquals(Authorization.SingleUseToken("secret-token"), authorization)
    }
}
