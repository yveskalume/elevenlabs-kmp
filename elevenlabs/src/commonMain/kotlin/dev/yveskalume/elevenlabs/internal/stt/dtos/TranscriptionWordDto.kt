package dev.yveskalume.elevenlabs.internal.stt.dtos

import dev.yveskalume.elevenlabs.stt.TranscriptionWord
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class TranscriptionWordDto(
    @SerialName("text")
    val text: String = "",
    @SerialName("start")
    val start: Double? = null,
    @SerialName("end")
    val end: Double? = null,
    @SerialName("type")
    val type: String = "word",
    @SerialName("speaker_id")
    val speakerId: String? = null,
    @SerialName("logprob")
    val logprob: Double? = null,
    @SerialName("channel_index")
    val channelIndex: Int? = null,
) {
    fun toPublic() = TranscriptionWord(
        text = text,
        startSeconds = start,
        endSeconds = end,
        type = type,
        speakerId = speakerId,
        logProbability = logprob,
        channelIndex = channelIndex,
    )
}