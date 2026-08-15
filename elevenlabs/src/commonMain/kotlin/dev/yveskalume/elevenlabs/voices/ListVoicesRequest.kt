package dev.yveskalume.elevenlabs.voices

/** Filters and pagination controls for [VoicesApi.list]. */
public data class ListVoicesRequest(
    /** Maximum number of voices to return, from 1 through 100. */
    val pageSize: Int = 10,
    /** Opaque token returned by the previous [VoicePage]. */
    val nextPageToken: String? = null,
    /** Free-text search*/
    val search: String? = null,
    /** Voice-type filter accepted by ElevenLabs. */
    val voiceType: String? = null,
    /** Voice-category filter accepted by ElevenLabs. */
    val category: String? = null,
    /** Whether [VoicePage.totalCount] should be populated. */
    val includeTotalCount: Boolean = false,
) {
    init {
        require(pageSize in 1..100) { "pageSize must be between 1 and 100." }
    }
}
