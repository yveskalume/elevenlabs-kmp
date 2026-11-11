package dev.yveskalume.elevenlabs.internal.stt

import dev.yveskalume.elevenlabs.internal.http.ElevenLabsHttpClient
import dev.yveskalume.elevenlabs.internal.http.resolveCredential
import dev.yveskalume.elevenlabs.stt.SpeechToTextAuthorization

internal data class SpeechToTextCredentials(
    val apiKey: String? = null,
    val token: String? = null,
)

internal suspend fun SpeechToTextAuthorization.resolveCredentials(
    http: ElevenLabsHttpClient,
): SpeechToTextCredentials = when (this) {
    SpeechToTextAuthorization.ConfiguredApiKey -> SpeechToTextCredentials(
        apiKey = http.resolveApiKey(),
    )
    is SpeechToTextAuthorization.SingleUseToken -> SpeechToTextCredentials(token = token)
    is SpeechToTextAuthorization.TokenProvider -> SpeechToTextCredentials(
        token = resolveCredential("ElevenLabs speech-to-text token provider") {
            provider.getToken()
        },
    )
}
