package dev.yveskalume.elevenlabs.internal.tts.dtos

import dev.yveskalume.elevenlabs.internal.http.JSON
import dev.yveskalume.elevenlabs.tts.RealtimeTtsAlignment
import dev.yveskalume.elevenlabs.tts.RealtimeTtsEvent
import dev.yveskalume.elevenlabs.tts.RealtimeTtsOptions
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.io.encoding.Base64

internal sealed interface DecodedRealtimeTtsMessage {
    data class Event(val value: RealtimeTtsEvent) : DecodedRealtimeTtsMessage
    data class Error(val message: String) : DecodedRealtimeTtsMessage
    data object Unknown : DecodedRealtimeTtsMessage
}

internal object RealtimeTtsMessages {
    fun initialization(options: RealtimeTtsOptions): String =
        JSON.encodeToString(
            RealtimeTtsInitializationDto(
                text = " ",
                voiceSettings = options.voiceSettings?.let {
                    TTSVoiceSettingsDto(
                        stability = it.stability,
                        similarityBoost = it.similarityBoost,
                        style = it.style,
                        useSpeakerBoost = it.useSpeakerBoost,
                        speed = it.speed,
                    )
                },
                generationConfig = options.generationConfig?.let {
                    RealtimeTtsGenerationConfigDto(it.chunkLengthSchedule)
                },
            ),
        )

    fun text(text: String, flush: Boolean = false): String = JSON.encodeToString(
        RealtimeTtsTextDto(text = text, flush = flush.takeIf { it }),
    )

    fun finish(): String = JSON.encodeToString(RealtimeTtsTextDto(text = ""))

    fun decode(value: String): DecodedRealtimeTtsMessage {
        val message = JSON.decodeFromString<RealtimeTtsIncomingDto>(value)
        if (message.isFinal) {
            return DecodedRealtimeTtsMessage.Event(RealtimeTtsEvent.Finished)
        }

        message.audio?.let { encodedAudio ->
            return DecodedRealtimeTtsMessage.Event(
                RealtimeTtsEvent.Audio(
                    bytes = Base64.decode(encodedAudio),
                    alignment = message.alignment?.toPublic(),
                    normalizedAlignment = message.normalizedAlignment?.toPublic(),
                ),
            )
        }

        val error = message.error?.findMessage()
            ?: message.detail?.findMessage()
            ?: message.message?.findMessage()
        return if (error == null) {
            DecodedRealtimeTtsMessage.Unknown
        } else {
            DecodedRealtimeTtsMessage.Error(error)
        }
    }

    private fun JsonElement.findMessage(): String? = when (this) {
        is JsonPrimitive -> contentOrNull
        is JsonObject -> {
            this["message"]?.findMessage()
                ?: this["detail"]?.findMessage()
                ?: this["error"]?.findMessage()
        }
        else -> null
    }
}


@Serializable
private data class RealtimeTtsInitializationDto(
    @SerialName("text")
    val text: String,
    @SerialName("voice_settings")
    val voiceSettings: TTSVoiceSettingsDto? = null,
    @SerialName("generation_config")
    val generationConfig: RealtimeTtsGenerationConfigDto? = null,
)

@Serializable
private data class RealtimeTtsGenerationConfigDto(
    @SerialName("chunk_length_schedule")
    val chunkLengthSchedule: List<Int>,
)

@Serializable
private data class RealtimeTtsTextDto(
    @SerialName("text")
    val text: String,
    @SerialName("flush")
    val flush: Boolean? = null,
)

@Serializable
private data class RealtimeTtsIncomingDto(
    @SerialName("audio")
    val audio: String? = null,
    @SerialName("alignment")
    val alignment: RealtimeTtsAlignmentDto? = null,
    @SerialName("normalized_alignment")
    val normalizedAlignment: RealtimeTtsAlignmentDto? = null,
    @SerialName("is_final")
    val isFinal: Boolean = false,
    @SerialName("message")
    val message: JsonElement? = null,
    @SerialName("error")
    val error: JsonElement? = null,
    @SerialName("detail")
    val detail: JsonElement? = null,
)

@Serializable
private data class RealtimeTtsAlignmentDto(
    @SerialName("chars")
    val characters: List<String> = emptyList(),
    @SerialName("char_start_times_ms")
    val characterStartTimesMs: List<Int> = emptyList(),
    @SerialName("char_durations_ms")
    val characterDurationsMs: List<Int> = emptyList(),
) {
    fun toPublic() = RealtimeTtsAlignment(
        characters = characters,
        characterStartTimesMs = characterStartTimesMs,
        characterDurationsMs = characterDurationsMs,
    )
}
