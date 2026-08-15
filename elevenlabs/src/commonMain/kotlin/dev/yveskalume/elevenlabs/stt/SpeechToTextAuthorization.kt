package dev.yveskalume.elevenlabs.stt

/** Credentials used for batch or realtime speech-to-text operations. */
public sealed interface SpeechToTextAuthorization {
    /** Uses the API key configured on the [dev.yveskalume.elevenlabs.ElevenLabs] client. */
    public data object ConfiguredApiKey : SpeechToTextAuthorization

    /** Uses an already-fetched endpoint-appropriate single-use [token]. */
    public data class SingleUseToken(public val token: String) : SpeechToTextAuthorization {
        init {
            require(token.isNotBlank()) { "The ElevenLabs speech-to-text token cannot be blank." }
        }
    }

    /**
     * Resolves a new token for every batch request or realtime connection.
     * Tokens are never cached by the SDK.
     */
    public data class TokenProvider(
        public val provider: SpeechToTextTokenProvider,
    ) : SpeechToTextAuthorization
}
