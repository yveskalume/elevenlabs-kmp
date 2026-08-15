package dev.yveskalume.elevenlabs.tts

/**
 * Audio encoding and sample-rate identifier accepted by ElevenLabs.
 *
 * Construct this type with a service-supported value when a newer format is not yet exposed as
 * a companion constant.
 */
public data class OutputFormat(public val value: String) {
    init {
        require(value.isNotBlank()) { "The output format cannot be blank." }
    }

    public companion object {
        /** MP3 at 44.1 kHz and 128 kbps. */
        public val Mp3_44100_128: OutputFormat = OutputFormat("mp3_44100_128")
        /** MP3 at 44.1 kHz and 192 kbps. */
        public val Mp3_44100_192: OutputFormat = OutputFormat("mp3_44100_192")
        /** Signed 16-bit little-endian PCM at 16 kHz. */
        public val Pcm_16000: OutputFormat = OutputFormat("pcm_16000")
        /** Signed 16-bit little-endian PCM at 24 kHz. */
        public val Pcm_24000: OutputFormat = OutputFormat("pcm_24000")
        /** Signed 16-bit little-endian PCM at 44.1 kHz. */
        public val Pcm_44100: OutputFormat = OutputFormat("pcm_44100")
        /** WAV container at 44.1 kHz. */
        public val Wav_44100: OutputFormat = OutputFormat("wav_44100")
        /** G.711 mu-law audio at 8 kHz. */
        public val Ulaw_8000: OutputFormat = OutputFormat("ulaw_8000")
    }
}
