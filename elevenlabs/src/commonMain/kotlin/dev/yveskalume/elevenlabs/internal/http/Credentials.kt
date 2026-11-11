package dev.yveskalume.elevenlabs.internal.http

import dev.yveskalume.elevenlabs.Authorization
import dev.yveskalume.elevenlabs.error.ElevenLabsException
import kotlinx.coroutines.CancellationException

/** Credentials for one request or connection. Exactly one of the two is set. */
internal data class Credentials(
    val apiKey: String? = null,
    val singleUseToken: String? = null,
)

internal suspend fun Authorization.resolveCredentials(http: ElevenLabsHttpClient): Credentials =
    when (this) {
        Authorization.ConfiguredApiKey -> Credentials(apiKey = http.resolveApiKey())
        is Authorization.SingleUseToken -> Credentials(singleUseToken = token)
        is Authorization.TokenProvider -> Credentials(
            singleUseToken = resolveCredential("ElevenLabs single-use token provider") {
                provider.getToken()
            },
        )
    }

/** Runs a user-supplied credential [provider], keeping its failures inside the SDK's error model. */
internal suspend fun resolveCredential(
    providerName: String,
    provider: suspend () -> String,
): String {
    val credential = try {
        provider()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (exception: ElevenLabsException) {
        throw exception
    } catch (throwable: Throwable) {
        throw ElevenLabsException.MissingCredentials("The $providerName failed.", throwable)
    }
    if (credential.isBlank()) {
        throw ElevenLabsException.MissingCredentials("The $providerName returned a blank value.")
    }
    return credential
}
