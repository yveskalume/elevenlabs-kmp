package dev.yveskalume.elevenlabs.internal.error

import dev.yveskalume.elevenlabs.error.ElevenLabsErrorKind
import dev.yveskalume.elevenlabs.internal.http.JSON
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Extracts the machine-readable code from a realtime error message, using the same conventions as
 * HTTP errors: a top-level `error_code` (text-to-dialogue, multi-context), or a `code`/`status`
 * inside an `error` or `detail` object (for example `{"detail":{"status":"quota_exceeded"}}`).
 * Returns `null` when the message has no code or is not a JSON object.
 */
internal fun realtimeErrorCode(rawMessage: String): String? {
    val message = runCatching { JSON.parseToJsonElement(rawMessage) }.getOrNull() as? JsonObject
        ?: return null
    return message["error_code"].text()
        ?: message["errorCode"].text()
        ?: message["error"].nestedCode()
        ?: message["detail"].nestedCode()
}

private fun JsonElement?.nestedCode(): String? = (this as? JsonObject)?.let { value ->
    value["code"].text() ?: value["error_code"].text() ?: value["status"].text()
}

private fun JsonElement?.text(): String? =
    (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

/**
 * Classifies a realtime error code with the same categories as HTTP errors. Codes come from the
 * realtime STT message types and the error codes ElevenLabs documents for its WebSocket APIs;
 * anything unrecognized stays [ElevenLabsErrorKind.Realtime].
 */
internal fun realtimeErrorKind(code: String?): ElevenLabsErrorKind =
    when (code?.lowercase()) {
        "auth_error", "authentication_error", "authentication_required", "invalid_api_key", "invalid_token", "unauthorized" ->
            ElevenLabsErrorKind.Authentication

        "quota_exceeded", "insufficient_credits", "payment_required" ->
            ElevenLabsErrorKind.Quota

        "unaccepted_terms", "permission_denied", "forbidden", "missing_permissions" ->
            ElevenLabsErrorKind.Permission

        "rate_limited", "rate_limit_exceeded", "commit_throttled", "too_many_concurrent_requests" ->
            ElevenLabsErrorKind.RateLimit

        "input_error", "invalid_request", "validation_error", "chunk_size_exceeded" ->
            ElevenLabsErrorKind.Validation

        "voice_not_found", "model_not_found", "not_found" ->
            ElevenLabsErrorKind.NotFound

        "transcriber_error", "internal_error", "server_error", "queue_overflow", "resource_exhausted" ->
            ElevenLabsErrorKind.Server

        else -> ElevenLabsErrorKind.Realtime
    }
