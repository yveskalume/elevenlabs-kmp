package dev.yveskalume.elevenlabs.stt

/**
 * Encoding and sample rate of audio sent to a realtime transcription session.
 *
 * Construct this type with a service-supported value when a newer format is not yet exposed as
 * a companion constant.
 */
public data class RealtimeSttAudioFormat @Throws(IllegalArgumentException::class) constructor(
    public val value: String,
) {
    init {
        require(value.isNotBlank()) { "The audio format cannot be blank." }
    }

    public companion object {
        /** Signed 16-bit little-endian PCM at 8 kHz. */
        public val Pcm8000: RealtimeSttAudioFormat = RealtimeSttAudioFormat("pcm_8000")
        /** Signed 16-bit little-endian PCM at 16 kHz. */
        public val Pcm16000: RealtimeSttAudioFormat = RealtimeSttAudioFormat("pcm_16000")
        /** Signed 16-bit little-endian PCM at 22.05 kHz. */
        public val Pcm22050: RealtimeSttAudioFormat = RealtimeSttAudioFormat("pcm_22050")
        /** Signed 16-bit little-endian PCM at 24 kHz. */
        public val Pcm24000: RealtimeSttAudioFormat = RealtimeSttAudioFormat("pcm_24000")
        /** Signed 16-bit little-endian PCM at 44.1 kHz. */
        public val Pcm44100: RealtimeSttAudioFormat = RealtimeSttAudioFormat("pcm_44100")
        /** Signed 16-bit little-endian PCM at 48 kHz. */
        public val Pcm48000: RealtimeSttAudioFormat = RealtimeSttAudioFormat("pcm_48000")
        /** G.711 mu-law audio at 8 kHz. */
        public val Ulaw8000: RealtimeSttAudioFormat = RealtimeSttAudioFormat("ulaw_8000")
    }
}
