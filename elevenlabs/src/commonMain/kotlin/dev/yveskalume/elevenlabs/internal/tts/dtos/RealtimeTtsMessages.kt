@file:OptIn(ExperimentalSerializationApi::class)

package dev.yveskalume.elevenlabs.internal.tts.dtos

import dev.yveskalume.elevenlabs.internal.error.realtimeErrorCode
import dev.yveskalume.elevenlabs.internal.http.JSON
import dev.yveskalume.elevenlabs.internal.realtime.hasUnrecognizedFields
import dev.yveskalume.elevenlabs.realtime.Alignment
import dev.yveskalume.elevenlabs.tts.RealtimeTtsEvent
import dev.yveskalume.elevenlabs.tts.RealtimeTtsOptions
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.io.encoding.Base64

internal sealed interface DecodedRealtimeTtsMessage {
    data class Event(val value: RealtimeTtsEvent) : DecodedRealtimeTtsMessage
    data class Error(val message: String, val errorCode: String? = null) : DecodedRealtimeTtsMessage

    /** A recognized message with nothing to deliver, such as an audio message without audio. */
    data object Ignored : DecodedRealtimeTtsMessage
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
        if (message.isFinal == true) {
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
        return when {
            error != null -> DecodedRealtimeTtsMessage.Error(error, realtimeErrorCode(value))
            hasUnrecognizedFields(value, KNOWN_FIELDS) ->
                DecodedRealtimeTtsMessage.Event(RealtimeTtsEvent.Unknown(value))
            else -> DecodedRealtimeTtsMessage.Ignored
        }
    }

    private val KNOWN_FIELDS = setOf(
        "audio", "alignment", "normalizedAlignment", "normalized_alignment", "isFinal", "is_final",
        "message", "error", "detail",
    )

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
    // ElevenLabs documents camelCase; snake_case is accepted too, as for multi-context messages.
    @SerialName("normalizedAlignment")
    @JsonNames("normalized_alignment")
    val normalizedAlignment: RealtimeTtsAlignmentDto? = null,
    @SerialName("isFinal")
    @JsonNames("is_final")
    val isFinal: Boolean? = null,
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
    @SerialName("charStartTimesMs")
    @JsonNames("char_start_times_ms")
    val characterStartTimesMs: List<Int> = emptyList(),
    // Same spellings as multi-context messages, where ElevenLabs has also sent "charsDurationsMs".
    @SerialName("charDurationsMs")
    @JsonNames("charsDurationsMs", "char_durations_ms")
    val characterDurationsMs: List<Int> = emptyList(),
) {
    fun toPublic() = Alignment(
        characters = characters,
        characterStartTimesMs = characterStartTimesMs,
        characterDurationsMs = characterDurationsMs,
    )
}
