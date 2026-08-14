package dev.yveskalume.elevenlabs.voices

data class VoicePage(
    val voices: List<Voice>,
    val hasMore: Boolean,
    val nextPageToken: String? = null,
    val totalCount: Int? = null,
)