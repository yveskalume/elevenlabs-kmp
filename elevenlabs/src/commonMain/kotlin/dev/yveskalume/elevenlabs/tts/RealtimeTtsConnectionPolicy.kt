package dev.yveskalume.elevenlabs.tts

/** Time limits applied to a realtime text-to-speech session. */
public class RealtimeTtsTimeouts private constructor(
    /** Maximum time allowed to establish a connection, in milliseconds. */
    public val connectTimeoutMillis: Long,
    /** Maximum time allowed for a session send operation, in milliseconds. */
    public val sendTimeoutMillis: Long,
    /** Maximum time [RealtimeTtsSession.finish] waits for server completion, in milliseconds. */
    public val finishTimeoutMillis: Long,
    /** Inactivity timeout sent to ElevenLabs, in seconds and from 1 through 180. */
    public val inactivityTimeoutSeconds: Int,
) {
    init {
        require(connectTimeoutMillis > 0) { "connectTimeoutMillis must be greater than 0." }
        require(sendTimeoutMillis > 0) { "sendTimeoutMillis must be greater than 0." }
        require(finishTimeoutMillis > 0) { "finishTimeoutMillis must be greater than 0." }
        require(inactivityTimeoutSeconds in 1..180) {
            "inactivityTimeoutSeconds must be between 1 and 180."
        }
    }

    /** Returns a builder with these values, to create a changed copy. */
    public fun toBuilder(): Builder = Builder().also {
        it.connectTimeoutMillis = connectTimeoutMillis
        it.sendTimeoutMillis = sendTimeoutMillis
        it.finishTimeoutMillis = finishTimeoutMillis
        it.inactivityTimeoutSeconds = inactivityTimeoutSeconds
    }

    /** Builds a [RealtimeTtsTimeouts]. Every value starts at its default. */
    public class Builder {
        /** Maximum time allowed to establish a connection, in milliseconds. */
        public var connectTimeoutMillis: Long = 10_000
        /** Maximum time allowed for a session send operation, in milliseconds. */
        public var sendTimeoutMillis: Long = 10_000
        /**
         * Maximum time [RealtimeTtsSession.finish] waits for server completion, in milliseconds.
         */
        public var finishTimeoutMillis: Long = 30_000
        /** Inactivity timeout sent to ElevenLabs, in seconds and from 1 through 180. */
        public var inactivityTimeoutSeconds: Int = 60

        /**
         * Creates the [RealtimeTtsTimeouts].
         *
         * @throws IllegalArgumentException if a value is invalid.
         */
        @Throws(IllegalArgumentException::class)
        public fun build(): RealtimeTtsTimeouts = RealtimeTtsTimeouts(
            connectTimeoutMillis,
            sendTimeoutMillis,
            finishTimeoutMillis,
            inactivityTimeoutSeconds,
        )
    }

    override fun equals(other: Any?): Boolean =
        other is RealtimeTtsTimeouts &&
            connectTimeoutMillis == other.connectTimeoutMillis &&
            sendTimeoutMillis == other.sendTimeoutMillis &&
            finishTimeoutMillis == other.finishTimeoutMillis &&
            inactivityTimeoutSeconds == other.inactivityTimeoutSeconds

    override fun hashCode(): Int {
        var result = connectTimeoutMillis.hashCode()
        result = 31 * result + sendTimeoutMillis.hashCode()
        result = 31 * result + finishTimeoutMillis.hashCode()
        result = 31 * result + inactivityTimeoutSeconds.hashCode()
        return result
    }

    override fun toString(): String =
        "RealtimeTtsTimeouts(connectTimeoutMillis=$connectTimeoutMillis, " +
            "sendTimeoutMillis=$sendTimeoutMillis, finishTimeoutMillis=$finishTimeoutMillis, " +
            "inactivityTimeoutSeconds=$inactivityTimeoutSeconds)"
}

/**
 * Creates a [RealtimeTtsTimeouts]. Set the values to change in [configure].
 *
 * @throws IllegalArgumentException if a value is invalid.
 */
@Throws(IllegalArgumentException::class)
public fun RealtimeTtsTimeouts(
    configure: RealtimeTtsTimeouts.Builder.() -> Unit = {},
): RealtimeTtsTimeouts = RealtimeTtsTimeouts.Builder().apply(configure).build()

/** Controls if a realtime TTS connection may be opened again after a transient failure. */
public sealed interface RealtimeTtsReconnectPolicy {
    /** Never reconnect automatically. */
    public data object Never : RealtimeTtsReconnectPolicy

    /**
     * Reconnect only before the first audio chunk is received, when replaying buffered text
     * cannot duplicate audio already delivered to the caller.
     */
    public class BeforeFirstAudio private constructor(
        /** Maximum number of reconnection attempts. */
        public val maxAttempts: Int,
        /** Delay before the first retry, in milliseconds. */
        public val initialDelayMillis: Long,
        /** Upper bound for exponential retry delays, in milliseconds. */
        public val maxDelayMillis: Long,
        /** Random proportional variation applied to retry delays, from 0 to 1. */
        public val jitterFactor: Double,
    ) : RealtimeTtsReconnectPolicy {
        init {
            require(maxAttempts > 0) { "maxAttempts must be greater than 0." }
            require(initialDelayMillis >= 0) { "initialDelayMillis cannot be negative." }
            require(maxDelayMillis >= initialDelayMillis) {
                "maxDelayMillis must be greater than or equal to initialDelayMillis."
            }
            require(jitterFactor in 0.0..1.0) { "jitterFactor must be between 0 and 1." }
        }

        /** Returns a builder with these values, to create a changed copy. */
        public fun toBuilder(): Builder = Builder().also {
            it.maxAttempts = maxAttempts
            it.initialDelayMillis = initialDelayMillis
            it.maxDelayMillis = maxDelayMillis
            it.jitterFactor = jitterFactor
        }

        /** Builds a [BeforeFirstAudio]. Every value starts at its default. */
        public class Builder {
            /** Maximum number of reconnection attempts. */
            public var maxAttempts: Int = 3
            /** Delay before the first retry, in milliseconds. */
            public var initialDelayMillis: Long = 500
            /** Upper bound for exponential retry delays, in milliseconds. */
            public var maxDelayMillis: Long = 5_000
            /** Random proportional variation applied to retry delays, from 0 to 1. */
            public var jitterFactor: Double = 0.2

            /**
             * Creates the [BeforeFirstAudio].
             *
             * @throws IllegalArgumentException if a value is invalid.
             */
            @Throws(IllegalArgumentException::class)
            public fun build(): BeforeFirstAudio = BeforeFirstAudio(
                maxAttempts,
                initialDelayMillis,
                maxDelayMillis,
                jitterFactor,
            )
        }

        override fun equals(other: Any?): Boolean =
            other is BeforeFirstAudio &&
                maxAttempts == other.maxAttempts &&
                initialDelayMillis == other.initialDelayMillis &&
                maxDelayMillis == other.maxDelayMillis &&
                jitterFactor == other.jitterFactor

        override fun hashCode(): Int {
            var result = maxAttempts.hashCode()
            result = 31 * result + initialDelayMillis.hashCode()
            result = 31 * result + maxDelayMillis.hashCode()
            result = 31 * result + jitterFactor.hashCode()
            return result
        }

        override fun toString(): String =
            "BeforeFirstAudio(maxAttempts=$maxAttempts, initialDelayMillis=$initialDelayMillis, " +
                "maxDelayMillis=$maxDelayMillis, jitterFactor=$jitterFactor)"

        public companion object {
            /**
             * Creates a [BeforeFirstAudio]. Set the values to change in [configure].
             *
             * @throws IllegalArgumentException if a value is invalid.
             */
            @Throws(IllegalArgumentException::class)
            public operator fun invoke(configure: Builder.() -> Unit = {}): BeforeFirstAudio =
                Builder().apply(configure).build()
        }
    }
}
