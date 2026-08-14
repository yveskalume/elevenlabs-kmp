package dev.yveskalume.elevenlabs.internal.models

import dev.yveskalume.elevenlabs.models.ModelLanguage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ModelLanguageDto(
    @SerialName("language_id")
    val id: String,
    @SerialName("name")
    val name: String,
) {
    fun toPublic() = ModelLanguage(id = id, name = name)
}