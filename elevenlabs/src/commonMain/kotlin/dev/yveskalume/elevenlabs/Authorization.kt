package dev.yveskalume.elevenlabs

/**
 * Credentials for operations that accept a single-use token: realtime text-to-speech and
 * text-to-dialogue, and batch and realtime speech-to-text.
 *
 * Don't ship an API key in a distributed Android or iOS app. Use [SingleUseToken] or
 * [TokenProvider] there instead, with tokens issued by your backend.
 */
public sealed interface Authorization {
    /** Uses the API key configured on the [ElevenLabs] client. */
    public data object ConfiguredApiKey : Authorization

    /** Uses an already-fetched single-use [token] issued for the endpoint being called. */
    public data class SingleUseToken @Throws(IllegalArgumentException::class) constructor(
        public val token: String,
    ) : Authorization {
        init {
            require(token.isNotBlank()) { "The ElevenLabs single-use token cannot be blank." }
        }

        /** Hides the token, so logging an [Authorization] doesn't leak a credential. */
        override fun toString(): String = "SingleUseToken(token=***)"
    }

    /** Resolves a new token for every request or connection. Tokens are never cached by the SDK. */
    public data class TokenProvider(
        public val provider: SingleUseTokenProvider,
    ) : Authorization
}
