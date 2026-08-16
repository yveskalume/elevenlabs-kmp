package dev.yveskalume.elevenlabs.voices

/** Discovers voices available to the configured ElevenLabs account. */
public interface VoicesApi {
    /**
     * Returns one page of voices matching [request].
     *
     * Pass [VoicePage.nextPageToken] into a subsequent request to continue pagination.
     *
     * @throws dev.yveskalume.elevenlabs.ElevenLabsException when the request fails.
     */
    public suspend fun list(request: ListVoicesRequest = ListVoicesRequest()): VoicePage

    /**
     * Returns the voice identified by [voiceId].
     *
     * @throws IllegalArgumentException if [voiceId] is blank.
     * @throws dev.yveskalume.elevenlabs.ElevenLabsException when the request fails.
     */
    public suspend fun get(voiceId: String): Voice
}
