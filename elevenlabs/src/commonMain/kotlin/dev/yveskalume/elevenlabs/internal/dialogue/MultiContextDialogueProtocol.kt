package dev.yveskalume.elevenlabs.internal.dialogue

import dev.yveskalume.elevenlabs.dialogue.DialogueEvent
import dev.yveskalume.elevenlabs.dialogue.DialogueLine
import dev.yveskalume.elevenlabs.dialogue.MultiContextDialogueContextOptions
import dev.yveskalume.elevenlabs.internal.error.realtimeErrorCode
import dev.yveskalume.elevenlabs.internal.http.JSON
import dev.yveskalume.elevenlabs.internal.realtime.MultiContextInbound
import dev.yveskalume.elevenlabs.internal.realtime.MultiContextProtocol
import dev.yveskalume.elevenlabs.tts.RealtimeTtsAlignment
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.io.encoding.Base64

/**
 * Wire format of `/v1/text-to-dialogue/multi-stream-input`.
 *
 * Every field is snake_case in both directions. Unlike text-to-speech, closing a context flushes
 * its remaining text, keepalive has its own flag, and every error closes the connection.
 */
internal object MultiContextDialogueProtocol : MultiContextProtocol<DialogueEvent> {
    override val finishedEvent: DialogueEvent = DialogueEvent.Finished

    /** The first message of a context registers its voices and settings. */
    fun register(
        contextId: String,
        voiceIds: List<String>,
        options: MultiContextDialogueContextOptions,
    ): String = JSON.encodeToString(
        DialogueClientDto(
            contextId = contextId,
            voices = voiceIds,
            voiceSettings = options.stability?.let { DialogueVoiceSettingsDto(stability = it) },
            pronunciationDictionaryLocators = options.pronunciationDictionaries
                .takeIf { it.isNotEmpty() }
                ?.map { DialoguePronunciationDictionaryDto(it.dictionaryId, it.versionId) },
        ),
    )

    fun lines(contextId: String, lines: List<DialogueLine>): String = JSON.encodeToString(
        DialogueClientDto(
            contextId = contextId,
            inputs = lines.map {
                DialogueInputDto(text = it.text, voiceId = it.voiceId, newTurn = it.newTurn.takeIf { turn -> turn })
            },
        ),
    )

    override fun flush(contextId: String): String =
        JSON.encodeToString(DialogueClientDto(contextId = contextId, flush = true))

    override fun keepAlive(contextId: String): String =
        JSON.encodeToString(DialogueClientDto(contextId = contextId, keepAlive = true))

    /** Closing a dialogue context also flushes it, so this is both "finish" and "close". */
    override fun closeContext(contextId: String): String =
        JSON.encodeToString(DialogueClientDto(contextId = contextId, closeContext = true))

    override fun closeSocket(): String = JSON.encodeToString(DialogueClientDto(closeSocket = true))

    override fun decode(raw: String): List<MultiContextInbound<DialogueEvent>> {
        val message = JSON.decodeFromString<DialogueServerDto>(raw)

        val error = message.message?.text() ?: message.error?.text()
        if (error != null) {
            // Errors close the whole connection, even when they name a context.
            val errorCode = message.errorCode?.text()
                ?: message.error?.text()?.takeIf { message.message != null }
                ?: realtimeErrorCode(raw)
            return listOf(MultiContextInbound.ConnectionError(error, errorCode))
        }

        val contextId = message.contextId ?: return emptyList()
        return buildList {
            message.audio?.takeIf { it.isNotEmpty() }?.let { audio ->
                add(
                    MultiContextInbound.Event(
                        contextId = contextId,
                        event = DialogueEvent.Audio(
                            bytes = Base64.decode(audio),
                            alignment = message.alignment?.toPublic(),
                        ),
                    ),
                )
            }
            if (message.isFinalAudioForTurn == true) {
                add(MultiContextInbound.Event(contextId, DialogueEvent.TurnFinished))
            }
            if (message.isFinal == true) add(MultiContextInbound.ContextFinished(contextId))
        }
    }

    private fun JsonElement.text(): String? =
        (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}

@Serializable
private data class DialogueClientDto(
    @SerialName("context_id")
    val contextId: String? = null,
    @SerialName("voices")
    val voices: List<String>? = null,
    @SerialName("voice_settings")
    val voiceSettings: DialogueVoiceSettingsDto? = null,
    @SerialName("pronunciation_dictionary_locators")
    val pronunciationDictionaryLocators: List<DialoguePronunciationDictionaryDto>? = null,
    @SerialName("inputs")
    val inputs: List<DialogueInputDto>? = null,
    @SerialName("flush")
    val flush: Boolean? = null,
    @SerialName("keep_alive")
    val keepAlive: Boolean? = null,
    @SerialName("close_context")
    val closeContext: Boolean? = null,
    @SerialName("close_socket")
    val closeSocket: Boolean? = null,
)

@Serializable
private data class DialogueVoiceSettingsDto(
    @SerialName("stability")
    val stability: Double,
)

@Serializable
private data class DialoguePronunciationDictionaryDto(
    @SerialName("pronunciation_dictionary_id")
    val dictionaryId: String,
    @SerialName("version_id")
    val versionId: String,
)

@Serializable
private data class DialogueInputDto(
    @SerialName("text")
    val text: String,
    @SerialName("voice_id")
    val voiceId: String,
    @SerialName("new_turn")
    val newTurn: Boolean? = null,
)

@Serializable
private data class DialogueServerDto(
    @SerialName("context_id")
    val contextId: String? = null,
    @SerialName("audio")
    val audio: String? = null,
    @SerialName("alignment")
    val alignment: DialogueAlignmentDto? = null,
    @SerialName("is_final_audio_for_turn")
    val isFinalAudioForTurn: Boolean? = null,
    @SerialName("is_final")
    val isFinal: Boolean? = null,
    /** Human-readable error description. */
    @SerialName("message")
    val message: JsonElement? = null,
    /** Machine-readable error identifier, such as `authentication_required`. */
    @SerialName("error")
    val error: JsonElement? = null,
    @SerialName("error_code")
    val errorCode: JsonElement? = null,
)

@Serializable
private data class DialogueAlignmentDto(
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
