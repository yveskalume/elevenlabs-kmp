package dev.yveskalume.elevenlabs.internal.tts

import dev.yveskalume.elevenlabs.tts.TextToSpeechRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class TTSBody(
    @SerialName("text")
    val text: String,
    @SerialName("model_id")
    val modelId: String? = null,
    @SerialName("language_code")
    val languageCode: String? = null,
    @SerialName("voice_settings")
    val voiceSettings: TTSVoiceSettingsDto? = null,
)

internal fun TextToSpeechRequest.toBody() = TTSBody(
    text = text,
    modelId = modelId,
    languageCode = languageCode,
    voiceSettings = voiceSettings?.let {
        TTSVoiceSettingsDto(
            stability = it.stability,
            similarityBoost = it.similarityBoost,
            style = it.style,
            useSpeakerBoost = it.useSpeakerBoost,
            speed = it.speed,
        )
    },
)