package dev.yveskalume.elevenlabs

data class ElevenLabsError(
    val statusCode: Int?,
    val message: String,
    val requestId: String? = null,
    val responseBody: String? = null,
)

sealed class ElevenLabsException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    class Serialization internal constructor(
        val error: ElevenLabsError,
        cause: Throwable,
    ) : ElevenLabsException(error.message, cause)

    class UnexpectedResponse internal constructor(
        error: ElevenLabsError,
    ) : ElevenLabsException(error.message)

    class Realtime internal constructor(
        message: String,
        val closeCode: Short? = null,
        val responseBody: String? = null,
        cause: Throwable? = null,
    ) : ElevenLabsException(message, cause)
}
