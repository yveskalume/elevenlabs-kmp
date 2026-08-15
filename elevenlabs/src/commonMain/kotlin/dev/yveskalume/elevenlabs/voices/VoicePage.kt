package dev.yveskalume.elevenlabs.voices

/** One page returned by [VoicesApi.list]. */
public data class VoicePage(
    /** Voices in this page. */
    val voices: List<Voice>,
    /** Whether another page is available. */
    val hasMore: Boolean,
    /** Opaque token to pass to [ListVoicesRequest.nextPageToken]. */
    val nextPageToken: String? = null,
    /** Total number of matches when requested, otherwise `null`. */
    val totalCount: Int? = null,
)
