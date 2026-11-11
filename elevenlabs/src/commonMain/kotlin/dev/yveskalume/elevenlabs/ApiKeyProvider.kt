package dev.yveskalume.elevenlabs

/**
 * Supplies an ElevenLabs API key immediately before an authenticated request.
 *
 * Implementations may read from a rotating credential store. The SDK does not cache returned
 * keys. A blank value or a thrown exception fails the request with
 * [dev.yveskalume.elevenlabs.error.ElevenLabsException.MissingCredentials].
 */
public fun interface ApiKeyProvider {
    /** Returns the API key to use for the next request. */
    @Throws(Exception::class)
    public suspend fun getApiKey(): String
}
