package dev.yveskalume.elevenlabs.internal.error

import dev.yveskalume.elevenlabs.internal.realtime.MultiContextInbound
import dev.yveskalume.elevenlabs.internal.stt.dtos.DecodedRealtimeSttMessage
import dev.yveskalume.elevenlabs.internal.stt.dtos.RealtimeSttMessages
import dev.yveskalume.elevenlabs.internal.tts.dtos.DecodedRealtimeTtsMessage
import dev.yveskalume.elevenlabs.internal.tts.dtos.MultiContextTtsProtocol
import dev.yveskalume.elevenlabs.internal.tts.dtos.RealtimeTtsMessages
import dev.yveskalume.elevenlabs.error.ElevenLabsErrorKind
import dev.yveskalume.elevenlabs.error.RealtimeServerError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RealtimeErrorCodeTest {

    @Test
    fun `known codes map to the same kinds as HTTP errors`() {
        assertEquals(ElevenLabsErrorKind.Authentication, realtimeErrorKind("auth_error"))
        assertEquals(ElevenLabsErrorKind.Quota, realtimeErrorKind("quota_exceeded"))
        assertEquals(ElevenLabsErrorKind.Permission, realtimeErrorKind("unaccepted_terms"))
        assertEquals(ElevenLabsErrorKind.RateLimit, realtimeErrorKind("RATE_LIMITED"))
        assertEquals(ElevenLabsErrorKind.Validation, realtimeErrorKind("input_error"))
        assertEquals(ElevenLabsErrorKind.NotFound, realtimeErrorKind("voice_not_found"))
        assertEquals(ElevenLabsErrorKind.Server, realtimeErrorKind("transcriber_error"))
    }

    @Test
    fun `unknown or missing codes stay realtime`() {
        assertEquals(ElevenLabsErrorKind.Realtime, realtimeErrorKind("something_new"))
        assertEquals(ElevenLabsErrorKind.Realtime, realtimeErrorKind(null))
    }

    @Test
    fun `the kind drives isRetryable on RealtimeServerError`() {
        val throttled = RealtimeServerError(message = "Slow down", errorCode = "rate_limited")
        val quota = RealtimeServerError(message = "No credits", errorCode = "quota_exceeded")
        val closed = RealtimeServerError(message = "Closed", closeCode = 1011.toShort())

        assertEquals(ElevenLabsErrorKind.RateLimit, throttled.kind)
        assertTrue(throttled.isRetryable)
        assertEquals(ElevenLabsErrorKind.Quota, quota.kind)
        assertFalse(quota.isRetryable)
        assertEquals(ElevenLabsErrorKind.Realtime, closed.kind)
        assertFalse(closed.isRetryable)
    }

    @Test
    fun `top-level error_code is extracted`() {
        assertEquals(
            "voice_not_registered",
            realtimeErrorCode("""{"error":"Unknown voice","error_code":"voice_not_registered"}"""),
        )
    }

    @Test
    fun `nested code or status is extracted like HTTP errors`() {
        assertEquals(
            "quota_exceeded",
            realtimeErrorCode("""{"detail":{"status":"quota_exceeded","message":"Quota exceeded"}}"""),
        )
        assertEquals("invalid_api_key", realtimeErrorCode("""{"error":{"code":"invalid_api_key"}}"""))
    }

    @Test
    fun `missing or malformed codes are null`() {
        assertNull(realtimeErrorCode("""{"error":"Something failed"}"""))
        assertNull(realtimeErrorCode("""{"error_code":""}"""))
        assertNull(realtimeErrorCode("not json"))
        assertNull(realtimeErrorCode("""["error"]"""))
    }

    @Test
    fun `realtime TTS errors carry the code`() {
        val decoded = RealtimeTtsMessages.decode(
            """{"error":"Quota exceeded","error_code":"quota_exceeded"}""",
        )

        val error = assertIs<DecodedRealtimeTtsMessage.Error>(decoded)
        assertEquals("Quota exceeded", error.message)
        assertEquals("quota_exceeded", error.errorCode)
    }

    @Test
    fun `realtime STT uses the error message type as the code`() {
        val decoded = RealtimeSttMessages.decode(
            """{"message_type":"auth_error","error":"Invalid API key"}""",
        )

        val error = assertIs<DecodedRealtimeSttMessage.Error>(decoded)
        assertEquals("Invalid API key", error.message)
        assertEquals("auth_error", error.errorCode)
    }

    @Test
    fun `generic realtime STT errors fall back to the payload code`() {
        val decoded = RealtimeSttMessages.decode(
            """{"message_type":"error","error":"Failed","error_code":"internal"}""",
        )

        assertEquals("internal", assertIs<DecodedRealtimeSttMessage.Error>(decoded).errorCode)
    }

    @Test
    fun `multi-context errors carry the code for contexts and connections`() {
        val contextError = MultiContextTtsProtocol.decode(
            """{"contextId":"a","error":"Bad settings","error_code":"invalid_voice_settings"}""",
        ).single()
        val connectionError = MultiContextTtsProtocol.decode(
            """{"error":"Too many contexts","error_code":"max_contexts_exceeded"}""",
        ).single()

        assertEquals(
            "invalid_voice_settings",
            assertIs<MultiContextInbound.ContextError>(contextError).errorCode,
        )
        assertEquals(
            "max_contexts_exceeded",
            assertIs<MultiContextInbound.ConnectionError>(connectionError).errorCode,
        )
    }
}
