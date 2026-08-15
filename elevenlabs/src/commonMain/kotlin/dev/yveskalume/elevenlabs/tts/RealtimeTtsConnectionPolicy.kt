package dev.yveskalume.elevenlabs.tts

/** Time limits applied to a realtime text-to-speech session. */
data class RealtimeTtsTimeouts(
    val connectTimeoutMillis: Long = 10_000,
    val sendTimeoutMillis: Long = 10_000,
    val finishTimeoutMillis: Long = 30_000,
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
data class RealtimeTtsKeepAlive(
    val enabled: Boolean = true,
    val intervalMillis: Long = 15_000,
) {
    init {
        require(intervalMillis > 0) { "intervalMillis must be greater than 0." }
    }
}

/** Controls if a realtime TTS connection may be opened again after a transient failure. */
sealed interface RealtimeTtsReconnectPolicy {
    /** Never reconnect automatically. */
    data object Never : RealtimeTtsReconnectPolicy

    /**
     * Reconnect only before the first audio chunk is received, when replaying buffered text
     * cannot duplicate audio already delivered to the caller.
     */
    data class BeforeFirstAudio(
        val maxAttempts: Int = 3,
        val initialDelayMillis: Long = 500,
        val maxDelayMillis: Long = 5_000,
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
