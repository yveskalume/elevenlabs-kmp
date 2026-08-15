package dev.yveskalume.elevenlabs.stt

/** Encoding and sample rate of audio sent to a realtime transcription session. */
public enum class RealtimeSttAudioFormat(internal val value: String) {
    /** Signed 16-bit little-endian PCM at 8 kHz. */
    Pcm8000("pcm_8000"),
    /** Signed 16-bit little-endian PCM at 16 kHz. */
    Pcm16000("pcm_16000"),
    /** Signed 16-bit little-endian PCM at 22.05 kHz. */
    Pcm22050("pcm_22050"),
    /** Signed 16-bit little-endian PCM at 24 kHz. */
    Pcm24000("pcm_24000"),
    /** Signed 16-bit little-endian PCM at 44.1 kHz. */
    Pcm44100("pcm_44100"),
    /** Signed 16-bit little-endian PCM at 48 kHz. */
    Pcm48000("pcm_48000"),
    /** G.711 mu-law audio at 8 kHz. */
    Ulaw8000("ulaw_8000"),
}
