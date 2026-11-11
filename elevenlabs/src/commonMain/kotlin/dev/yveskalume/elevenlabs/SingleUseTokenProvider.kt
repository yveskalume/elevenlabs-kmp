package dev.yveskalume.elevenlabs

/**
 * Supplies a fresh single-use token immediately before a request or realtime connection.
 *
 * Tokens are issued for one kind of endpoint, so return one that matches the operation it is used
 * for. The SDK does not cache returned tokens. A blank value or a thrown exception fails the
 * operation with [dev.yveskalume.elevenlabs.error.ElevenLabsException.MissingCredentials].
 */
public fun interface SingleUseTokenProvider {
    /** Returns a fresh token for the next request or connection. */
    @Throws(Exception::class)
    public suspend fun getToken(): String
}
