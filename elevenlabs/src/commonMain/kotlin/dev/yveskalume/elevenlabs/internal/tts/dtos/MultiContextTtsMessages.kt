package dev.yveskalume.elevenlabs.internal.tts.dtos

import dev.yveskalume.elevenlabs.internal.http.JSON
import dev.yveskalume.elevenlabs.tts.MultiContextTtsContextOptions
import dev.yveskalume.elevenlabs.tts.MultiContextTtsEvent
import dev.yveskalume.elevenlabs.tts.RealtimeTtsAlignment
import dev.yveskalume.elevenlabs.tts.VoiceSettings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.io.encoding.Base64

internal sealed interface DecodedMultiContextTtsMessage {
    data class Event(val value: MultiContextTtsEvent) : DecodedMultiContextTtsMessage
    data class ConnectionError(val message: String) : DecodedMultiContextTtsMessage
    data object Unknown : DecodedMultiContextTtsMessage
}

internal object MultiContextTtsMessages {
    fun initialize(
        contextId: String,
        options: MultiContextTtsContextOptions,
    ): String = JSON.encodeToString(
        MultiContextTextDto(
            contextId = contextId,
            text = " ",
            voiceSettings = options.voiceSettings?.toDto(),
            generationConfig = options.generationConfig?.let {
                MultiContextGenerationConfigDto(it.chunkLengthSchedule)
            },
        ),
    )

    fun text(contextId: String, text: String, flush: Boolean = false): String =
        JSON.encodeToString(
            MultiContextTextDto(
                contextId = contextId,
                text = text,
                flush = flush.takeIf { it },
            ),
        )

    fun flush(contextId: String): String = JSON.encodeToString(
        MultiContextControlDto(contextId = contextId, flush = true),
    )

    fun keepAlive(contextId: String): String = JSON.encodeToString(
        MultiContextTextDto(contextId = contextId, text = ""),
    )

    fun closeContext(contextId: String): String = JSON.encodeToString(
        MultiContextControlDto(contextId = contextId, closeContext = true),
    )

    fun closeSocket(): String = JSON.encodeToString(
        MultiContextCloseSocketDto(closeSocket = true),
    )

    fun decode(value: String): DecodedMultiContextTtsMessage {
        val message = JSON.decodeFromString<MultiContextIncomingDto>(value)
        val contextId = message.contextId

        if (message.isFinal && contextId != null) {
            return DecodedMultiContextTtsMessage.Event(
                MultiContextTtsEvent.ContextFinished(contextId),
            )
        }

        if (message.audio != null && contextId != null) {
            return DecodedMultiContextTtsMessage.Event(
                MultiContextTtsEvent.Audio(
                    contextId = contextId,
                    bytes = Base64.decode(message.audio),
                    alignment = message.alignment?.toPublic(),
                    normalizedAlignment = message.normalizedAlignment?.toPublic(),
                ),
            )
        }

        val error = message.error?.findMessage()
            ?: message.detail?.findMessage()
            ?: message.message?.findMessage()
        return when {
            error == null -> DecodedMultiContextTtsMessage.Unknown
            contextId != null -> DecodedMultiContextTtsMessage.Event(
                MultiContextTtsEvent.ContextError(contextId, error),
            )
            else -> DecodedMultiContextTtsMessage.ConnectionError(error)
        }
    }

    private fun VoiceSettings.toDto() = TTSVoiceSettingsDto(
        stability = stability,
        similarityBoost = similarityBoost,
        style = style,
        useSpeakerBoost = useSpeakerBoost,
        speed = speed,
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
private data class MultiContextTextDto(
    @SerialName("context_id")
    val contextId: String,
    @SerialName("text")
    val text: String,
    @SerialName("flush")
    val flush: Boolean? = null,
    @SerialName("voice_settings")
    val voiceSettings: TTSVoiceSettingsDto? = null,
    @SerialName("generation_config")
    val generationConfig: MultiContextGenerationConfigDto? = null,
)

@Serializable
private data class MultiContextGenerationConfigDto(
    @SerialName("chunk_length_schedule")
    val chunkLengthSchedule: List<Int>,
)

@Serializable
private data class MultiContextControlDto(
    @SerialName("context_id")
    val contextId: String,
    @SerialName("flush")
    val flush: Boolean? = null,
    @SerialName("close_context")
    val closeContext: Boolean? = null,
)

@Serializable
private data class MultiContextCloseSocketDto(
    @SerialName("close_socket")
    val closeSocket: Boolean,
)

@Serializable
private data class MultiContextIncomingDto(
    @SerialName("context_id")
    val contextId: String? = null,
    @SerialName("audio")
    val audio: String? = null,
    @SerialName("alignment")
    val alignment: MultiContextAlignmentDto? = null,
    @SerialName("normalized_alignment")
    val normalizedAlignment: MultiContextAlignmentDto? = null,
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
private data class MultiContextAlignmentDto(
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
