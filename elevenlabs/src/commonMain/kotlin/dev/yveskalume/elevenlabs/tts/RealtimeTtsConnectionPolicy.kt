package dev.yveskalume.elevenlabs.tts

/** Time limits applied to a realtime text-to-speech session. */
public data class RealtimeTtsTimeouts(
    /** Maximum time allowed to establish a connection, in milliseconds. */
    val connectTimeoutMillis: Long = 10_000,
    /** Maximum time allowed for a session send operation, in milliseconds. */
    val sendTimeoutMillis: Long = 10_000,
    /** Maximum time [RealtimeTtsSession.finish] waits for server completion, in milliseconds. */
    val finishTimeoutMillis: Long = 30_000,
    /** Inactivity timeout sent to ElevenLabs, in seconds and from 1 through 180. */
    val inactivityTimeoutSeconds: Int = 60,
) {
    init {
        require(connectTimeoutMillis > 0) { "connectTimeoutMillis must be greater than 0." }
        require(sendTimeoutMillis > 0) { "sendTimeoutMillis must be greater than 0." }
        require(finishTimeoutMillis > 0) { "finishTimeoutMillis must be greater than 0." }
        require(inactivityTimeoutSeconds in 1..180) {
            "inactivityTimeoutSeconds must be between 1 and 180."
        }
    }
}

/** Keeps an otherwise idle ElevenLabs TTS WebSocket session open. */
public data class RealtimeTtsKeepAlive(
    /** Whether keepalive messages are sent. */
    val enabled: Boolean = true,
    /** Delay between keepalive messages, in milliseconds. */
    val intervalMillis: Long = 15_000,
) {
    init {
        require(intervalMillis > 0) { "intervalMillis must be greater than 0." }
    }
}

/** Controls if a realtime TTS connection may be opened again after a transient failure. */
public sealed interface RealtimeTtsReconnectPolicy {
    /** Never reconnect automatically. */
    public data object Never : RealtimeTtsReconnectPolicy

    /**
     * Reconnect only before the first audio chunk is received, when replaying buffered text
     * cannot duplicate audio already delivered to the caller.
     */
    public data class BeforeFirstAudio(
        /** Maximum number of reconnection attempts. */
        val maxAttempts: Int = 3,
        /** Delay before the first retry, in milliseconds. */
        val initialDelayMillis: Long = 500,
        /** Upper bound for exponential retry delays, in milliseconds. */
        val maxDelayMillis: Long = 5_000,
        /** Random proportional variation applied to retry delays, from 0 to 1. */
        val jitterFactor: Double = 0.2,
    ) : RealtimeTtsReconnectPolicy {
        init {
            require(maxAttempts > 0) { "maxAttempts must be greater than 0." }
            require(initialDelayMillis >= 0) { "initialDelayMillis cannot be negative." }
            require(maxDelayMillis >= initialDelayMillis) {
                "maxDelayMillis must be greater than or equal to initialDelayMillis."
            }
            require(jitterFactor in 0.0..1.0) { "jitterFactor must be between 0 and 1." }
        }
    }
}
