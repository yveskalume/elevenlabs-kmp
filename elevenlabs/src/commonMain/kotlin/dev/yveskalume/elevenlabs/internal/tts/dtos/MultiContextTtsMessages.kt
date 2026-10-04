@file:OptIn(ExperimentalSerializationApi::class)

package dev.yveskalume.elevenlabs.internal.tts.dtos

import dev.yveskalume.elevenlabs.internal.error.realtimeErrorCode
import dev.yveskalume.elevenlabs.internal.http.JSON
import dev.yveskalume.elevenlabs.internal.realtime.MultiContextInbound
import dev.yveskalume.elevenlabs.internal.realtime.MultiContextProtocol
import dev.yveskalume.elevenlabs.tts.MultiContextTtsContextOptions
import dev.yveskalume.elevenlabs.tts.RealtimeTtsAlignment
import dev.yveskalume.elevenlabs.tts.RealtimeTtsEvent
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.io.encoding.Base64

/**
 * Wire format of `/v1/text-to-speech/{voice_id}/multi-stream-input`.
 *
 * Client messages use `context_id`. ElevenLabs documents server messages with both `contextId`
 * and `context_id` (and camelCase or snake_case alignment fields), so decoding accepts either.
 */
internal object MultiContextTtsProtocol : MultiContextProtocol<RealtimeTtsEvent> {
    override val finishedEvent: RealtimeTtsEvent = RealtimeTtsEvent.Finished

    /** The first message creates the context. A single space is the protocol's no-op text. */
    fun initialize(contextId: String, options: MultiContextTtsContextOptions): String =
        JSON.encodeToString(
            MultiContextTextDto(
                contextId = contextId,
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
                    MultiContextGenerationConfigDto(it.chunkLengthSchedule)
                },
                pronunciationDictionaryLocators = options.pronunciationDictionaries
                    .takeIf { it.isNotEmpty() }
                    ?.map { PronunciationDictionaryLocatorDto(it.dictionaryId, it.versionId) },
            ),
        )

    fun text(contextId: String, text: String, flush: Boolean): String = JSON.encodeToString(
        MultiContextTextDto(contextId = contextId, text = text, flush = flush.takeIf { it }),
    )

    override fun flush(contextId: String): String = JSON.encodeToString(
        MultiContextTextDto(contextId = contextId, text = "", flush = true),
    )

    /** An empty text resets the context's inactivity timer without generating audio. */
    override fun keepAlive(contextId: String): String = JSON.encodeToString(
        MultiContextTextDto(contextId = contextId, text = ""),
    )

    override fun closeContext(contextId: String): String = JSON.encodeToString(
        MultiContextCloseContextDto(contextId = contextId, closeContext = true),
    )

    override fun closeSocket(): String = JSON.encodeToString(
        MultiContextCloseSocketDto(closeSocket = true),
    )

    override fun decode(raw: String): List<MultiContextInbound<RealtimeTtsEvent>> {
        val message = JSON.decodeFromString<MultiContextIncomingDto>(raw)
        val contextId = message.contextId
        val error = message.error?.findMessage()
            ?: message.detail?.findMessage()
            ?: message.message?.findMessage()

        if (error != null) {
            val errorCode = realtimeErrorCode(raw)
            return listOf(
                if (contextId == null) {
                    MultiContextInbound.ConnectionError(error, errorCode)
                } else {
                    MultiContextInbound.ContextError(contextId, error, errorCode)
                },
            )
        }
        if (contextId == null) return emptyList()

        return buildList {
            message.audio?.takeIf { it.isNotEmpty() }?.let { audio ->
                add(
                    MultiContextInbound.Event(
                        contextId = contextId,
                        event = RealtimeTtsEvent.Audio(
                            bytes = Base64.decode(audio),
                            alignment = message.alignment?.toPublic(),
                            normalizedAlignment = message.normalizedAlignment?.toPublic(),
                        ),
                    ),
                )
            }
            if (message.isFinal == true) add(MultiContextInbound.ContextFinished(contextId))
        }
    }

    private fun JsonElement.findMessage(): String? = when (this) {
        is JsonPrimitive -> contentOrNull
        is JsonObject -> this["message"]?.findMessage()
            ?: this["detail"]?.findMessage()
            ?: this["error"]?.findMessage()

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
    @SerialName("pronunciation_dictionary_locators")
    val pronunciationDictionaryLocators: List<PronunciationDictionaryLocatorDto>? = null,
)

@Serializable
private data class MultiContextGenerationConfigDto(
    @SerialName("chunk_length_schedule")
    val chunkLengthSchedule: List<Int>,
)

@Serializable
private data class PronunciationDictionaryLocatorDto(
    @SerialName("pronunciation_dictionary_id")
    val dictionaryId: String,
    @SerialName("version_id")
    val versionId: String,
)

@Serializable
private data class MultiContextCloseContextDto(
    @SerialName("context_id")
    val contextId: String,
    @SerialName("close_context")
    val closeContext: Boolean,
)

@Serializable
private data class MultiContextCloseSocketDto(
    @SerialName("close_socket")
    val closeSocket: Boolean,
)

@Serializable
private data class MultiContextIncomingDto(
    @SerialName("contextId")
    @JsonNames("context_id")
    val contextId: String? = null,
    @SerialName("audio")
    val audio: String? = null,
    @SerialName("alignment")
    val alignment: MultiContextAlignmentDto? = null,
    @SerialName("normalizedAlignment")
    @JsonNames("normalized_alignment")
    val normalizedAlignment: MultiContextAlignmentDto? = null,
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
private data class MultiContextAlignmentDto(
    @SerialName("chars")
    val characters: List<String> = emptyList(),
    @SerialName("charStartTimesMs")
    @JsonNames("char_start_times_ms")
    val characterStartTimesMs: List<Int> = emptyList(),
    @SerialName("charsDurationsMs")
    @JsonNames("charDurationsMs", "char_durations_ms")
    val characterDurationsMs: List<Int> = emptyList(),
) {
    fun toPublic() = RealtimeTtsAlignment(
        characters = characters,
        characterStartTimesMs = characterStartTimesMs,
        characterDurationsMs = characterDurationsMs,
    )
}
