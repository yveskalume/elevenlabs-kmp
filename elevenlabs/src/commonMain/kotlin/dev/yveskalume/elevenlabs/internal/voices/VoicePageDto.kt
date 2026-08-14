package dev.yveskalume.elevenlabs.internal.voices

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class VoicePageDto(
    @SerialName("voices")
    val voices: List<VoiceDto>,
    @SerialName("has_more") val hasMore: Boolean = false,
    @SerialName("next_page_token") val nextPageToken: String? = null,
    @SerialName("total_count") val totalCount: Int? = null,
)
