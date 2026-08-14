package dev.yveskalume.elevenlabs.internal.voices

import dev.yveskalume.elevenlabs.voices.Voice
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class VoiceDto(
    @SerialName("voice_id") val id: String,
    @SerialName("name")
    val name: String,
    @SerialName("category")
    val category: String? = null,
    @SerialName("description")
    val description: String? = null,
    @SerialName("preview_url") val previewUrl: String? = null,
    @SerialName("labels")
    val labels: Map<String, String> = emptyMap(),
) {
    fun toPublic() = Voice(
        id = id,
        name = name,
        category = category,
        description = description,
        previewUrl = previewUrl,
        labels = labels,
    )
}
