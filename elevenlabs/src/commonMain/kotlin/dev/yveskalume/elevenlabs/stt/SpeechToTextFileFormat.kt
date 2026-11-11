package dev.yveskalume.elevenlabs.stt

/**
 * Format hint for a batch speech-to-text upload.
 *
 * Construct this type with a service-supported value when a newer format is not yet exposed as
 * a companion constant.
 */
public data class SpeechToTextFileFormat @Throws(IllegalArgumentException::class) constructor(
    public val value: String,
) {
    init {
        require(value.isNotBlank()) { "The file format cannot be blank." }
    }

    public companion object {
        /** A standard encoded audio or video file whose format can be detected. */
        public val Other: SpeechToTextFileFormat = SpeechToTextFileFormat("other")
        /** Raw signed 16-bit little-endian PCM sampled at 16 kHz. */
        public val PcmS16Le16: SpeechToTextFileFormat = SpeechToTextFileFormat("pcm_s16le_16")
    }
}
