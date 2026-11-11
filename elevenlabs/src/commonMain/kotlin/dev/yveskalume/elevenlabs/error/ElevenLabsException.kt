package dev.yveskalume.elevenlabs.error

import dev.yveskalume.elevenlabs.internal.error.realtimeErrorKind

/** One field-level validation failure returned by ElevenLabs. */
public data class ElevenLabsValidationError(
    /** Location components such as `body.text`. */
    public val location: List<String>,
    /** Human-readable validation message. */
    public val message: String,
    /** ElevenLabs' machine-readable validation type, when supplied. */
    public val type: String? = null,
)

/**
 * Base exception for failures produced by the ElevenLabs SDK.
 *
 * Invalid arguments are reported as [IllegalArgumentException], and coroutine cancellation is
 * propagated unchanged. Use [kind] when application code needs stable classification: it covers
 * HTTP and realtime failures alike, while a concrete subtype only describes where a failure
 * happened.
 */
public sealed class ElevenLabsException(
    message: String,
    cause: Throwable? = null,
    /** HTTP status code, or `null` when the failure did not come from an HTTP response. */
    public val statusCode: Int? = null,
    /** ElevenLabs request identifier, useful when contacting support. */
    public val requestId: String? = null,
    /**
     * Machine-readable code reported by ElevenLabs, such as `quota_exceeded` or `auth_error`, when
     * present. Codes are defined by the service and may change; use them for logging and targeted
     * handling, with a fallback for unknown values.
     */
    public val errorCode: String? = null,
    responseBody: String? = null,
) : Exception(message, cause) {
    /** Stable category intended for application-level handling. */
    public abstract val kind: ElevenLabsErrorKind

    /** Diagnostic server payload, capped by the SDK and potentially containing sensitive information. */
    public val responseBody: String? = responseBody.truncateDiagnosticBody()

    /**
     * Whether retrying the same operation may succeed without changing the request.
     *
     * A retry repeats the operation: a timed-out generation may already have used credits, so
     * retrying it can bill the same text twice.
     */
    public val isRetryable: Boolean
        get() = when (kind) {
            ElevenLabsErrorKind.Network,
            ElevenLabsErrorKind.Timeout,
            ElevenLabsErrorKind.RateLimit,
            ElevenLabsErrorKind.Server -> true

            else -> false
        }

    /** The request failed before an HTTP response was received. */
    public class Network internal constructor(
        cause: Throwable,
        message: String = "Network request failed. Check the connection.",
    ) : ElevenLabsException(message, cause) {
        override val kind: ElevenLabsErrorKind = ElevenLabsErrorKind.Network
    }

    /** The request or realtime operation exceeded a configured timeout. */
    public class Timeout internal constructor(
        cause: Throwable? = null,
        message: String = "The request timed out.",
    ) : ElevenLabsException(message, cause) {
        override val kind: ElevenLabsErrorKind = ElevenLabsErrorKind.Timeout
    }

    /** A successful response or realtime message could not be decoded. */
    public class Serialization internal constructor(
        cause: Throwable,
        message: String = "Failed to decode the ElevenLabs response.",
        responseBody: String? = null,
    ) : ElevenLabsException(message, cause, responseBody = responseBody) {
        override val kind: ElevenLabsErrorKind = ElevenLabsErrorKind.Serialization
    }

    /** A realtime WebSocket protocol error or unexpected server close. */
    public class RealtimeServer internal constructor(
        message: String,
        /** WebSocket close code, when the server supplied one. */
        public val closeCode: Short? = null,
        responseBody: String? = null,
        cause: Throwable? = null,
        errorCode: String? = null,
    ) : ElevenLabsException(message, cause, errorCode = errorCode, responseBody = responseBody) {
        /**
         * The category of a recognized [errorCode], such as [ElevenLabsErrorKind.Quota] for
         * `quota_exceeded` or [ElevenLabsErrorKind.Authentication] for `auth_error`, so realtime
         * failures can be handled like HTTP ones. [ElevenLabsErrorKind.Realtime] when there is no
         * code or it is not recognized.
         */
        override val kind: ElevenLabsErrorKind = realtimeErrorKind(errorCode)
    }

    /**
     * No usable credentials could be obtained, so nothing was sent to ElevenLabs.
     *
     * No API key was configured, or a provider returned a blank value or threw. In the last case
     * [cause] holds what the provider threw. A provider that throws an [ElevenLabsException] has
     * that exception reported as is instead.
     */
    public class MissingCredentials internal constructor(
        message: String,
        cause: Throwable? = null,
    ) : ElevenLabsException(message, cause) {
        override val kind: ElevenLabsErrorKind = ElevenLabsErrorKind.Authentication
    }

    /** An unexpected SDK or transport failure that could not be classified more precisely. */
    public class Unknown internal constructor(
        cause: Throwable,
    ) : ElevenLabsException("An unexpected ElevenLabs error occurred.", cause) {
        override val kind: ElevenLabsErrorKind = ElevenLabsErrorKind.Unknown
    }
}

private fun String?.truncateDiagnosticBody(): String? = this?.let { body ->
    if (body.length <= MAX_DIAGNOSTIC_BODY_LENGTH) body
    else body.take(MAX_DIAGNOSTIC_BODY_LENGTH) + "… [truncated]"
}

private const val MAX_DIAGNOSTIC_BODY_LENGTH = 16 * 1024
