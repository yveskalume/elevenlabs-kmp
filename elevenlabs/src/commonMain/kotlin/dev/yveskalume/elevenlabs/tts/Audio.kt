package dev.yveskalume.elevenlabs.tts

/** A complete generated audio response. */
public class Audio internal constructor(
    /** Generated audio. The array belongs to the caller; the SDK never reads or changes it again. */
    public val bytes: ByteArray,
    public val contentType: String?,
    public val requestId: String?,
)
