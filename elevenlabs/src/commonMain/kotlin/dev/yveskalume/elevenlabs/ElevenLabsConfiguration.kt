package dev.yveskalume.elevenlabs

class ElevenLabsConfiguration {

    var baseUrl: String = "https://api.elevenlabs.io"

    internal var apiKeyProvider: ApiKeyProvider? = null

    /**
     * Configures a static key. This is intended for trusted environments.
     * Don't embed an ElevenLabs API key in a distributed Android or iOS app.
     */
    fun apiKey(value: String) {
        require(value.isNotBlank()) { "The ElevenLabs API key cannot be blank." }
        apiKeyProvider = ApiKeyProvider { value }
    }

    /** Configures a provider that is evaluated immediately before every request. */
    fun apiKey(provider: ApiKeyProvider) {
        apiKeyProvider = provider
    }

}

