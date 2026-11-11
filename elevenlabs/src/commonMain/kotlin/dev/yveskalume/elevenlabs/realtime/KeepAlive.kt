package dev.yveskalume.elevenlabs.realtime

/** Keeps an otherwise idle realtime WebSocket session or context open. */
public data class KeepAlive @Throws(IllegalArgumentException::class) constructor(
    /** Whether keepalive messages are sent. */
    val enabled: Boolean = true,
    /** Delay between keepalive messages, in milliseconds. */
    val intervalMillis: Long = 15_000,
) {
    init {
        require(intervalMillis > 0) { "intervalMillis must be greater than 0." }
    }
}
