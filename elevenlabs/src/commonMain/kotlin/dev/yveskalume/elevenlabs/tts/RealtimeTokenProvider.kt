package dev.yveskalume.elevenlabs.tts

/**
 * Supplies a fresh single-use token immediately before a realtime TTS connection is opened.
 * The SDK does not cache returned tokens. A blank value or a thrown exception fails the connection
 * with [dev.yveskalume.elevenlabs.error.ElevenLabsException.MissingCredentials].
 */
public fun interface RealtimeTokenProvider {
    /** Returns a fresh endpoint-appropriate token for the next connection. */
    @Throws(Exception::class)
    public suspend fun getToken(): String
}
