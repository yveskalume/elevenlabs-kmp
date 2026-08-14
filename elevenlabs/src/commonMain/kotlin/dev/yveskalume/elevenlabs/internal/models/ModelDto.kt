package dev.yveskalume.elevenlabs.internal.models

import dev.yveskalume.elevenlabs.models.Model
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ModelDto(
    @SerialName("model_id") val id: String,
    @SerialName("name")
    val name: String,
    @SerialName("description")
    val description: String? = null,
    @SerialName("can_do_text_to_speech")
    val canDoTextToSpeech: Boolean = false,
    @SerialName("can_do_voice_conversion")
    val canDoVoiceConversion: Boolean = false,
    @SerialName("can_use_style")
    val canUseStyle: Boolean = false,
    @SerialName("can_use_speaker_boost")
    val canUseSpeakerBoost: Boolean = false,
    @SerialName("maximum_text_length_per_request")
    val maximumTextLengthPerRequest: Int? = null,
    @SerialName("languages")
    val languages: List<ModelLanguageDto> = emptyList(),
) {
    fun toPublic() = Model(
        id = id,
        name = name,
        description = description,
        canDoTextToSpeech = canDoTextToSpeech,
        canDoVoiceConversion = canDoVoiceConversion,
        canUseStyle = canUseStyle,
        canUseSpeakerBoost = canUseSpeakerBoost,
        maximumTextLengthPerRequest = maximumTextLengthPerRequest,
        languages = languages.map(ModelLanguageDto::toPublic),
    )
}
