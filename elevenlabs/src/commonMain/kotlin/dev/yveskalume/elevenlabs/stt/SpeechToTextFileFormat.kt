package dev.yveskalume.elevenlabs.stt

/** Format hint for a batch speech-to-text upload. */
public enum class SpeechToTextFileFormat(internal val value: String) {
    /** A standard encoded audio or video file whose format can be detected. */
    Other("other"),
    /** Raw signed 16-bit little-endian PCM sampled at 16 kHz. */
    PcmS16Le16("pcm_s16le_16"),
}
