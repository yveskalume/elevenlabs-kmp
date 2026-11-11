package dev.yveskalume.elevenlabs.stt

/**
 * Supplies single-use credentials for speech-to-text operations.
 *
 * A blank value or a thrown exception fails the operation with
 * [dev.yveskalume.elevenlabs.error.ElevenLabsException.MissingCredentials].
 */
public fun interface SpeechToTextTokenProvider {
    /** Returns a fresh endpoint-appropriate single-use token. */
    @Throws(Exception::class)
    public suspend fun getToken(): String
}
