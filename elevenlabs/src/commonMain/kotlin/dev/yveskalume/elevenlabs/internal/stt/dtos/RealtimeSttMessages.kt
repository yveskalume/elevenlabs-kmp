package dev.yveskalume.elevenlabs.internal.stt.dtos

import dev.yveskalume.elevenlabs.internal.http.JSON
import dev.yveskalume.elevenlabs.stt.RealtimeSttEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64

internal sealed interface DecodedRealtimeSttMessage {
    data class Event(val value: RealtimeSttEvent) : DecodedRealtimeSttMessage
    data class Error(val message: String) : DecodedRealtimeSttMessage
}

internal object RealtimeSttMessages {
    fun audio(bytes: ByteArray, commit: Boolean = false): String =
        JSON.encodeToString(
            RealtimeSttAudioDto(
                messageType = "input_audio_chunk",
                audioBase64 = Base64.encode(bytes),
                commit = commit.takeIf { it },
            ),
        )

    fun commit(): String = audio(ByteArray(0), commit = true)

    fun decode(rawMessage: String): DecodedRealtimeSttMessage {
        val dto = JSON.decodeFromString<RealtimeSttIncomingDto>(rawMessage)
        val type = dto.messageType
        if (type in ERROR_TYPES || type?.endsWith("_error") == true) {
            val fallback = runCatching {
                JSON.parseToJsonElement(rawMessage)
                    .jsonObject["message"]
                    ?.jsonPrimitive
                    ?.contentOrNull
            }.getOrNull()
            return DecodedRealtimeSttMessage.Error(
                dto.error ?: fallback ?: "ElevenLabs realtime STT returned $type.",
            )
        }

        val words = dto.words.map(TranscriptionWordDto::toPublic)
        val event = when (type) {
            "session_started" -> RealtimeSttEvent.SessionStarted(dto.sessionId.orEmpty())
            "partial_transcript" -> RealtimeSttEvent.PartialTranscript(dto.text)
            "final_transcript", "final_transcript_with_timestamps" ->
                RealtimeSttEvent.FinalTranscript(dto.text, words, dto.languageCode)
            "committed_transcript", "committed_transcript_with_timestamps" ->
                RealtimeSttEvent.CommittedTranscript(dto.text, words, dto.languageCode)
            else -> RealtimeSttEvent.Unknown(type, rawMessage)
        }
        return DecodedRealtimeSttMessage.Event(event)
    }

    private val ERROR_TYPES = setOf(
        "error",
        "auth_error",
        "quota_exceeded",
        "transcriber_error",
        "input_error",
        "invalid_request",
        "commit_throttled",
        "unaccepted_terms",
        "rate_limited",
        "queue_overflow",
        "resource_exhausted",
        "session_time_limit_exceeded",
        "chunk_size_exceeded",
        "insufficient_audio_activity",
    )
}



@Serializable
private data class RealtimeSttAudioDto(
    @SerialName("message_type")
    val messageType: String,
    @SerialName("audio_base_64")
    val audioBase64: String,
    @SerialName("commit")
    val commit: Boolean? = null,
)

@Serializable
private data class RealtimeSttIncomingDto(
    @SerialName("message_type")
    val messageType: String? = null,
    @SerialName("session_id")
    val sessionId: String? = null,
    @SerialName("text")
    val text: String = "",
    @SerialName("words")
    val words: List<TranscriptionWordDto> = emptyList(),
    @SerialName("language_code")
    val languageCode: String? = null,
    @SerialName("error")
    val error: String? = null,
)