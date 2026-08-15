package dev.yveskalume.elevenlabs

/** Details associated with a failed ElevenLabs operation. */
public data class ElevenLabsError(
    val statusCode: Int?,
    val message: String,
    val requestId: String? = null,
    /** Raw response body, when one was received. It may contain sensitive information. */
    val responseBody: String? = null,
)


public sealed class ElevenLabsException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** The server response could not be decoded into the expected response type. */
    public class Serialization internal constructor(
        public val error: ElevenLabsError,
        cause: Throwable,
    ) : ElevenLabsException(error.message, cause)

    /** ElevenLabs returned a non-successful HTTP response. */
    public class UnexpectedResponse internal constructor(
        public val error: ElevenLabsError,
    ) : ElevenLabsException(error.message)

    /** A realtime WebSocket connection or session operation failed. */
    public class Realtime internal constructor(
        message: String,
        public val closeCode: Short? = null,
        public val responseBody: String? = null,
        cause: Throwable? = null,
    ) : ElevenLabsException(message, cause)
}
