package dev.yveskalume.elevenlabs.tts

/** Optional controls that tune the generated voice. */
public class VoiceSettings private constructor(
    /** Voice consistency from 0 to 1, or `null` to use the service default. */
    public val stability: Double?,
    /** Similarity to the source voice from 0 to 1. */
    public val similarityBoost: Double?,
    /** Style exaggeration accepted by the selected model; must not be negative. */
    public val style: Double?,
    /** Whether to enable speaker similarity boost. */
    public val useSpeakerBoost: Boolean?,
    /** Speech-speed multiplier; must be greater than zero. */
    public val speed: Double?,
) {
    init {
        require(stability == null || stability in 0.0..1.0) { "stability must be between 0 and 1." }
        require(similarityBoost == null || similarityBoost in 0.0..1.0) {
            "similarityBoost must be between 0 and 1."
        }
        require(style == null || style >= 0.0) { "style cannot be negative." }
        require(speed == null || speed > 0.0) { "speed must be greater than 0." }
    }

    /** Returns a builder with these values, to create a changed copy. */
    public fun toBuilder(): Builder = Builder().also {
        it.stability = stability
        it.similarityBoost = similarityBoost
        it.style = style
        it.useSpeakerBoost = useSpeakerBoost
        it.speed = speed
    }

    /** Builds a [VoiceSettings]. Every value starts at its default. */
    public class Builder {
        /** Voice consistency from 0 to 1, or `null` to use the service default. */
        public var stability: Double? = null
        /** Similarity to the source voice from 0 to 1. */
        public var similarityBoost: Double? = null
        /** Style exaggeration accepted by the selected model; must not be negative. */
        public var style: Double? = null
        /** Whether to enable speaker similarity boost. */
        public var useSpeakerBoost: Boolean? = null
        /** Speech-speed multiplier; must be greater than zero. */
        public var speed: Double? = null

        /**
         * Creates the [VoiceSettings].
         *
         * @throws IllegalArgumentException if a value is invalid.
         */
        @Throws(IllegalArgumentException::class)
        public fun build(): VoiceSettings = VoiceSettings(
            stability,
            similarityBoost,
            style,
            useSpeakerBoost,
            speed,
        )
    }

    override fun equals(other: Any?): Boolean =
        other is VoiceSettings &&
            stability == other.stability &&
            similarityBoost == other.similarityBoost &&
            style == other.style &&
            useSpeakerBoost == other.useSpeakerBoost &&
            speed == other.speed

    override fun hashCode(): Int {
        var result = stability.hashCode()
        result = 31 * result + similarityBoost.hashCode()
        result = 31 * result + style.hashCode()
        result = 31 * result + useSpeakerBoost.hashCode()
        result = 31 * result + speed.hashCode()
        return result
    }

    override fun toString(): String =
        "VoiceSettings(stability=$stability, similarityBoost=$similarityBoost, style=$style, " +
            "useSpeakerBoost=$useSpeakerBoost, speed=$speed)"
}

/**
 * Creates a [VoiceSettings]. Set the values to change in [configure].
 *
 * @throws IllegalArgumentException if a value is invalid.
 */
@Throws(IllegalArgumentException::class)
public fun VoiceSettings(
    configure: VoiceSettings.Builder.() -> Unit = {},
): VoiceSettings = VoiceSettings.Builder().apply(configure).build()
