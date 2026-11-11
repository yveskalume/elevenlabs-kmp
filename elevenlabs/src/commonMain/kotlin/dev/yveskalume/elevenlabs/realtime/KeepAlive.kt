package dev.yveskalume.elevenlabs.realtime

/** Keeps an otherwise idle realtime WebSocket session or context open. */
public class KeepAlive private constructor(
    /** Whether keepalive messages are sent. */
    public val enabled: Boolean,
    /** Delay between keepalive messages, in milliseconds. */
    public val intervalMillis: Long,
) {
    init {
        require(intervalMillis > 0) { "intervalMillis must be greater than 0." }
    }

    /** Returns a builder with these values, to create a changed copy. */
    public fun toBuilder(): Builder = Builder().also {
        it.enabled = enabled
        it.intervalMillis = intervalMillis
    }

    /** Builds a [KeepAlive]. Every value starts at its default. */
    public class Builder {
        /** Whether keepalive messages are sent. */
        public var enabled: Boolean = true
        /** Delay between keepalive messages, in milliseconds. */
        public var intervalMillis: Long = 15_000

        /**
         * Creates the [KeepAlive].
         *
         * @throws IllegalArgumentException if a value is invalid.
         */
        @Throws(IllegalArgumentException::class)
        public fun build(): KeepAlive = KeepAlive(enabled, intervalMillis)
    }

    override fun equals(other: Any?): Boolean =
        other is KeepAlive &&
            enabled == other.enabled &&
            intervalMillis == other.intervalMillis

    override fun hashCode(): Int {
        var result = enabled.hashCode()
        result = 31 * result + intervalMillis.hashCode()
        return result
    }

    override fun toString(): String = "KeepAlive(enabled=$enabled, intervalMillis=$intervalMillis)"
}

/**
 * Creates a [KeepAlive]. Set the values to change in [configure].
 *
 * @throws IllegalArgumentException if a value is invalid.
 */
@Throws(IllegalArgumentException::class)
public fun KeepAlive(
    configure: KeepAlive.Builder.() -> Unit = {},
): KeepAlive = KeepAlive.Builder().apply(configure).build()
