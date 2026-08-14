package dev.yveskalume.elevenlabs.internal.tts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class TTSVoiceSettingsDto(
    @SerialName("stability")
    val stability: Double? = null,
    @SerialName("similarity_boost")
    val similarityBoost: Double? = null,
    @SerialName("style")
    val style: Double? = null,
    @SerialName("use_speaker_boost")
    val useSpeakerBoost: Boolean? = null,
    @SerialName("speed")
    val speed: Double? = null,
)