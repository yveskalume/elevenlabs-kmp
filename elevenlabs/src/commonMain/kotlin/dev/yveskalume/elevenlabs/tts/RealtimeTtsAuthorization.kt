package dev.yveskalume.elevenlabs.tts

import dev.yveskalume.elevenlabs.internal.http.ElevenLabsHttpClient
import dev.yveskalume.elevenlabs.internal.tts.RealtimeTtsCredentials

/** Credentials used to open a realtime text-to-speech connection. */
public sealed interface RealtimeTtsAuthorization {
    /** Uses the API key configured on the [dev.yveskalume.elevenlabs.ElevenLabs] client. */
    public data object ConfiguredApiKey : RealtimeTtsAuthorization

    /** Uses an already-fetched endpoint-appropriate single-use [token]. */
    public data class SingleUseToken(public val token: String) : RealtimeTtsAuthorization {
        init {
            require(token.isNotBlank()) { "The ElevenLabs single-use token cannot be blank." }
        }
    }

    /** Resolves a new token for every realtime connection. Tokens are never cached by the SDK. */
    public data class TokenProvider(
        public val provider: RealtimeTokenProvider,
    ) : RealtimeTtsAuthorization
}

internal suspend fun RealtimeTtsAuthorization.resolveCredentials(
    http: ElevenLabsHttpClient,
): RealtimeTtsCredentials = when (this) {
    RealtimeTtsAuthorization.ConfiguredApiKey -> RealtimeTtsCredentials(
        apiKey = http.resolveApiKey(),
    )

    is RealtimeTtsAuthorization.SingleUseToken -> RealtimeTtsCredentials(
        singleUseToken = token,
    )

    is RealtimeTtsAuthorization.TokenProvider -> {
        val token = provider.getToken()
        require(token.isNotBlank()) {
            "The ElevenLabs realtime token provider returned a blank token."
        }
        RealtimeTtsCredentials(singleUseToken = token)
    }
}
