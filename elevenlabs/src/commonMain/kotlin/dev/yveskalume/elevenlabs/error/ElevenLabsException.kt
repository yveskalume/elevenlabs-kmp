package dev.yveskalume.elevenlabs.error

/** One field-level validation failure returned by ElevenLabs. */
public data class ElevenLabsValidationError(
    /** Location components such as `body.text`. */
    public val location: List<String>,
    /** Human-readable validation message. */
    public val message: String,
    /** ElevenLabs' machine-readable validation type, when supplied. */
    public val type: String? = null,
)

/** Structured details associated with a failed ElevenLabs operation. */
public data class ElevenLabsErrorDetails(
    /** HTTP status code, or `null` when no HTTP response was received. */
    public val statusCode: Int?,
    /** Human-readable error suitable for logs or a fallback UI message. */
    public val message: String,
    /** ElevenLabs request identifier, useful when contacting support. */
    public val requestId: String? = null,
    /** Diagnostic response body, capped by the SDK and potentially containing sensitive information. */
    public val responseBody: String? = null,
    /** Machine-readable code reported by ElevenLabs, when present. */
    public val errorCode: String? = null,
    /** Field-level failures from ElevenLabs' `detail` array. */
    public val validationErrors: List<ElevenLabsValidationError> = emptyList(),
)

/**
 * Base exception for failures produced by the ElevenLabs SDK.
 *
 * Invalid arguments are reported as [IllegalArgumentException], and coroutine cancellation is
 * propagated unchanged. Use [kind] when application code needs stable classification without
 * matching every concrete subtype.
 */
public sealed class ElevenLabsException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /** Stable category intended for application-level handling. */
    public abstract val kind: ElevenLabsErrorKind

    /** Whether retrying the same operation may succeed without changing the request. */
    public val isRetryable: Boolean
        get() = when (kind) {
            ElevenLabsErrorKind.Network,
            ElevenLabsErrorKind.Timeout,
            ElevenLabsErrorKind.RateLimit,
            ElevenLabsErrorKind.Server -> true

            else -> false
        }

    /** The server response could not be decoded into the expected response type. */
    public class Serialization internal constructor(
        details: ElevenLabsErrorDetails,
        cause: Throwable,
    ) : ElevenLabsException(details.message, cause) {
        override val kind: ElevenLabsErrorKind = ElevenLabsErrorKind.Serialization
    }

    /**
     * Legacy generic HTTP error. New HTTP requests throw a typed [ApiException] instead.
     */
    @Deprecated("Use ApiException and inspect its kind or statusCode.", ReplaceWith("ApiException"))
    public class UnexpectedResponse internal constructor(
        public val details: ElevenLabsErrorDetails,
    ) : ElevenLabsException(details.message) {
        override val kind: ElevenLabsErrorKind = ElevenLabsErrorKind.Unknown
    }

    /**
     * @deprecated Use [RealtimeServerError] for WebSocket protocol failures and the specific
     * transport exception for network, timeout, or serialization failures.
     */
    @Deprecated("Use RealtimeServerError or a specific transport exception.")
    public class Realtime internal constructor(
        message: String,
        closeCode: Short? = null,
        responseBody: String? = null,
        cause: Throwable? = null,
    ) : RealtimeServerError(message, closeCode, responseBody, cause)
}

/** The request failed before an HTTP response was received. */
public class NetworkError internal constructor(
    cause: Throwable,
    message: String = "Network request failed. Check the connection.",
) : ElevenLabsException(message, cause) {
    override val kind: ElevenLabsErrorKind = ElevenLabsErrorKind.Network
}

/** The request or realtime operation exceeded a configured timeout. */
public class TimeoutError internal constructor(
    cause: Throwable? = null,
    message: String = "The request timed out.",
) : ElevenLabsException(message, cause) {
    override val kind: ElevenLabsErrorKind = ElevenLabsErrorKind.Timeout
}

/** A successful response or realtime message could not be decoded. */
public class SerializationError internal constructor(
    cause: Throwable,
    details: ElevenLabsErrorDetails? = null,
) : ElevenLabsException(details?.message ?: "Failed to decode the ElevenLabs response.", cause) {
    public val details: ElevenLabsErrorDetails? = details?.copy(
        responseBody = details.responseBody.truncateDiagnosticBody(),
    )
    override val kind: ElevenLabsErrorKind = ElevenLabsErrorKind.Serialization
}

/** A realtime WebSocket protocol error or unexpected server close. */
public open class RealtimeServerError internal constructor(
    message: String,
    /** WebSocket close code, when the server supplied one. */
    public val closeCode: Short? = null,
    /** Capped diagnostic server payload, which may contain sensitive information. */
    rawResponseBody: String? = null,
    cause: Throwable? = null,
) : ElevenLabsException(message, cause) {
    public val responseBody: String? = rawResponseBody.truncateDiagnosticBody()
    override val kind: ElevenLabsErrorKind = ElevenLabsErrorKind.Realtime
}

/** An unexpected SDK or transport failure that could not be classified more precisely. */
public class UnknownError internal constructor(
    cause: Throwable
) : ElevenLabsException("An unexpected ElevenLabs error occurred.", cause) {
    override val kind: ElevenLabsErrorKind = ElevenLabsErrorKind.Unknown
}

internal fun String?.truncateDiagnosticBody(): String? = this?.let { body ->
    if (body.length <= MAX_DIAGNOSTIC_BODY_LENGTH) body
    else body.take(MAX_DIAGNOSTIC_BODY_LENGTH) + "… [truncated]"
}

private const val MAX_DIAGNOSTIC_BODY_LENGTH = 16 * 1024
