package dev.yveskalume.elevenlabs.error

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ElevenLabsExceptionTest {

    @Test
    fun `diagnostic response body is truncated`() {
        val exception = ElevenLabsException.Serialization(
            cause = IllegalArgumentException("malformed"),
            message = "Could not decode realtime response.",
            responseBody = "x".repeat(20_000),
        )

        val responseBody = requireNotNull(exception.responseBody)
        assertTrue(responseBody.length < 20_000)
        assertTrue(responseBody.endsWith("… [truncated]"))
        assertEquals("Could not decode realtime response.", exception.message)
    }
}
