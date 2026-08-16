package dev.yveskalume.elevenlabs.tts

/** A complete generated audio response. */
public class Audio internal constructor(
    bytes: ByteArray,
    public val contentType: String?,
    public val requestId: String?,
) {
    public val bytes: ByteArray = bytes.copyOf()
}
