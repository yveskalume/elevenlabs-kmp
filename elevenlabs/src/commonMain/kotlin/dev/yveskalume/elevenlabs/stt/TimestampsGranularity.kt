package dev.yveskalume.elevenlabs.stt

/**
 * Timestamp detail requested for a batch transcription.
 *
 * Construct this type with a service-supported value when a newer option is not yet exposed as
 * a companion constant.
 */
public data class TimestampsGranularity @Throws(IllegalArgumentException::class) constructor(
    public val value: String,
) {
    init {
        require(value.isNotBlank()) { "The timestamps granularity cannot be blank." }
    }

    public companion object {
        /** Do not request timestamped transcription units. */
        public val None: TimestampsGranularity = TimestampsGranularity("none")
        /** Request one timestamp range per word. */
        public val Word: TimestampsGranularity = TimestampsGranularity("word")
        /** Request one timestamp range per character. */
        public val Character: TimestampsGranularity = TimestampsGranularity("character")
    }
}
