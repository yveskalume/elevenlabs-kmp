package dev.yveskalume.elevenlabs.error

/**
 * Base class for a non-successful HTTP response from ElevenLabs.
 *
 * [details] contains the complete structured response. [kind] provides a stable category for
 * application-level handling, while [statusCode] and [errorCode] remain available for precise
 * protocol-specific decisions.
 */
public sealed class ApiException(
    /** Complete parsed server error details. */
    public val details: ElevenLabsErrorDetails,
    override val kind: ElevenLabsErrorKind,
) : ElevenLabsException(details.message) {
    public val statusCode: Int = requireNotNull(details.statusCode)
    public val errorCode: String? = details.errorCode
    public val requestId: String? = details.requestId
    public val responseBody: String? = details.responseBody
    public val validationErrors: List<ElevenLabsValidationError> = details.validationErrors

    /** ElevenLabs rejected a malformed or invalid request with HTTP 400. */
    public class BadRequest internal constructor(error: ElevenLabsErrorDetails) :
        ApiException(
            details = error,
            kind = if (error.validationErrors.isNotEmpty()) ElevenLabsErrorKind.Validation
            else ElevenLabsErrorKind.Api,
        )

    /** Authentication is missing or invalid (HTTP 401). */
    public class Unauthorized internal constructor(error: ElevenLabsErrorDetails) :
        ApiException(error, ElevenLabsErrorKind.Authentication)

    /** The account has insufficient credits or quota (HTTP 402). */
    public class PaymentRequired internal constructor(error: ElevenLabsErrorDetails) :
        ApiException(error, ElevenLabsErrorKind.Quota)

    /** The credentials do not grant access to the requested resource (HTTP 403). */
    public class Forbidden internal constructor(error: ElevenLabsErrorDetails) :
        ApiException(error, ElevenLabsErrorKind.Permission)

    /** The requested ElevenLabs resource does not exist (HTTP 404). */
    public class NotFound internal constructor(error: ElevenLabsErrorDetails) :
        ApiException(error, ElevenLabsErrorKind.NotFound)

    /** ElevenLabs rejected one or more fields (HTTP 422). */
    public class UnprocessableEntity internal constructor(error: ElevenLabsErrorDetails) :
        ApiException(error, ElevenLabsErrorKind.Validation)

    /** Too many requests were made (HTTP 429). */
    public class RateLimitExceeded internal constructor(
        error: ElevenLabsErrorDetails,
        /** Delay from a numeric `Retry-After` header, in seconds, when present. */
        public val retryAfterSeconds: Long?,
    ) : ApiException(error, ElevenLabsErrorKind.RateLimit)

    /** ElevenLabs failed to process the request (HTTP 5xx). */
    public class ServerError internal constructor(error: ElevenLabsErrorDetails) :
        ApiException(error, ElevenLabsErrorKind.Server)

    /** A non-success HTTP status not covered by a more specific subtype. */
    public class UnknownHttpError internal constructor(error: ElevenLabsErrorDetails) :
        ApiException(error, ElevenLabsErrorKind.Unknown)
}