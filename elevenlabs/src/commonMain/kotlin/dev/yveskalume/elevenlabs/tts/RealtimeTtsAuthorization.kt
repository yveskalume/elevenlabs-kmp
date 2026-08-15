package dev.yveskalume.elevenlabs.tts

import dev.yveskalume.elevenlabs.internal.http.ElevenLabsHttpClient
import dev.yveskalume.elevenlabs.internal.tts.RealtimeTtsCredentials

sealed interface RealtimeTtsAuthorization {
    data object ConfiguredApiKey : RealtimeTtsAuthorization

    data class SingleUseToken(val token: String) : RealtimeTtsAuthorization {
        init {
            require(token.isNotBlank()) { "The ElevenLabs single-use token cannot be blank." }
        }
    }

    /** Resolves a new token for every realtime connection. Tokens are never cached by the SDK. */
    data class TokenProvider(
        val provider: RealtimeTokenProvider,
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