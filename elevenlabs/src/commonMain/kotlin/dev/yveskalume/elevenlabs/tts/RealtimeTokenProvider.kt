package dev.yveskalume.elevenlabs.tts

/**
 * Supplies a fresh single-use token immediately before a realtime TTS connection is opened.
 * The SDK does not cache returned tokens and rejects blank values.
 */
public fun interface RealtimeTokenProvider {
    /** Returns a fresh endpoint-appropriate token for the next connection. */
    public suspend fun getToken(): String
}
