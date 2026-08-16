package dev.yveskalume.elevenlabs

/** Configuration used to create an [ElevenLabs] client. */
public class ElevenLabsConfiguration {

    /**
     * Root URL for REST API calls.
     *
     * Override this for a compatible proxy or test server. The default targets the public
     * ElevenLabs API.
     */
    public var baseUrl: String = "https://api.elevenlabs.io"

    internal var apiKeyProvider: ApiKeyProvider? = null

    /**
     * Configures a static API key. This is intended for trusted environments.
     * Don't embed an ElevenLabs API key in a distributed Android or iOS app.
     *
     * @throws IllegalArgumentException if [value] is blank.
     */
    public fun apiKey(value: String) {
        require(value.isNotBlank()) { "The ElevenLabs API key cannot be blank." }
        apiKeyProvider = ApiKeyProvider { value }
    }

    /**
     * Configures a provider that is evaluated immediately before every authenticated request.
     *
     * This replaces any previously configured static key or provider.
     */
    public fun apiKey(provider: ApiKeyProvider) {
        apiKeyProvider = provider
    }

}
