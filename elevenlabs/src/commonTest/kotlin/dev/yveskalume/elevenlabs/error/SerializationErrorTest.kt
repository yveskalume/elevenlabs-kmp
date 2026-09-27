package dev.yveskalume.elevenlabs.error

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SerializationErrorTest {

    @Test
    fun `diagnostic response body is truncated`() {
        val exception = SerializationError(
            cause = IllegalArgumentException("malformed"),
            details = ElevenLabsErrorDetails(
                statusCode = null,
                message = "Could not decode realtime response.",
                responseBody = "x".repeat(20_000),
            ),
        )

        val responseBody = requireNotNull(exception.details?.responseBody)
        assertTrue(responseBody.length < 20_000)
        assertTrue(responseBody.endsWith("… [truncated]"))
        assertEquals("Could not decode realtime response.", exception.message)
    }
}
