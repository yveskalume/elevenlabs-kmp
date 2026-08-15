package dev.yveskalume.elevenlabs.internal.stt.dtos

import dev.yveskalume.elevenlabs.stt.Transcription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SpeechToTextResponseDto(
    @SerialName("language_code")
    val languageCode: String? = null,
    @SerialName("language_probability")
    val languageProbability: Double? = null,
    @SerialName("text")
    val text: String = "",
    @SerialName("words")
    val words: List<TranscriptionWordDto> = emptyList(),
) {
    fun toPublic() = Transcription(
        languageCode = languageCode,
        languageProbability = languageProbability,
        text = text,
        words = words.map(TranscriptionWordDto::toPublic),
    )
}