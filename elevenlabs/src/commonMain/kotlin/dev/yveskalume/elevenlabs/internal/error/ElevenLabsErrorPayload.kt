package dev.yveskalume.elevenlabs.internal.error

import dev.yveskalume.elevenlabs.error.ElevenLabsValidationError
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
internal data class ElevenLabsErrorPayload(
    @SerialName("detail")
    val detail: JsonElement? = null,
    @SerialName("message")
    val message: JsonElement? = null,
    @SerialName("error")
    val error: JsonElement? = null,
) {
    val parsedMessage: String?
        get() = detail.message()
            ?: message.message()
            ?: error.message()

    val parsedCode: String?
        get() = detail.code()
            ?: error.code()

    val validationErrors: List<ElevenLabsValidationError>
        get() = (detail as? JsonArray).orEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val message = item["msg"]?.primitiveContent() ?: return@mapNotNull null
            ElevenLabsValidationError(
                location = (item["loc"] as? JsonArray)
                    .orEmpty()
                    .mapNotNull { it.primitiveContent() },
                message = message,
                type = item["type"]?.primitiveContent(),
            )
        }

    private fun JsonElement?.message(): String? = when (this) {
        is JsonPrimitive -> contentOrNull
        is JsonObject -> this["message"]?.primitiveContent()
            ?: this["detail"]?.message()
            ?: this["error"]?.message()
        is JsonArray -> validationErrors.takeIf(List<*>::isNotEmpty)?.joinToString("; ") { error ->
            val field = error.location.joinToString(".").ifBlank { "request" }
            "$field: ${error.message}"
        }
        else -> null
    }

    private fun JsonElement?.code(): String? = (this as? JsonObject)?.let { value ->
        value["code"]?.primitiveContent() ?: value["status"]?.primitiveContent()
    }

    private fun JsonElement.primitiveContent(): String? =
        (this as? JsonPrimitive)?.jsonPrimitive?.contentOrNull
}
