package dev.yveskalume.elevenlabs.models

/** Discovers models available to the configured ElevenLabs account. */
public interface ModelsApi {
    /**
     * Returns the models currently available to the account.
     *
     * @throws dev.yveskalume.elevenlabs.ElevenLabsException when the request fails.
     */
    public suspend fun list(): List<Model>
}
