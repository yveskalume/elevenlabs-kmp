package dev.yveskalume.elevenlabs.internal.models

import dev.yveskalume.elevenlabs.models.Model
import dev.yveskalume.elevenlabs.models.ModelId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

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
    init {
        // Checked while decoding, so a bad payload becomes a serialization failure instead of the
        // IllegalArgumentException that ModelId(id) would throw from toPublic().
        if (id.isBlank()) throw SerializationException("model_id cannot be blank.")
    }

    fun toPublic() = Model(
        id = ModelId(id),
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
