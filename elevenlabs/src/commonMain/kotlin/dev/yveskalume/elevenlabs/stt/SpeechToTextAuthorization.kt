package dev.yveskalume.elevenlabs.stt

sealed interface SpeechToTextAuthorization {
    data object ConfiguredApiKey : SpeechToTextAuthorization

    data class SingleUseToken(val token: String) : SpeechToTextAuthorization {
        init {
            require(token.isNotBlank()) { "The ElevenLabs speech-to-text token cannot be blank." }
        }
    }

    /** Resolves a new token for every batch request or realtime connection. */
    data class TokenProvider(
        val provider: SpeechToTextTokenProvider,
    ) : SpeechToTextAuthorization
}