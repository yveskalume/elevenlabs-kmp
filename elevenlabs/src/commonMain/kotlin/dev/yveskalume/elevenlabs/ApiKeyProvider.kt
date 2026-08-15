package dev.yveskalume.elevenlabs

/**
 * Supplies an ElevenLabs API key immediately before an authenticated request.
 *
 * Implementations may read from a rotating credential store. The SDK does not cache returned
 * keys and rejects blank values.
 */
public fun interface ApiKeyProvider {
    /** Returns the API key to use for the next request. */
    public suspend fun getApiKey(): String
}
