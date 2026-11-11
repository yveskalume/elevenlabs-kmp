package dev.yveskalume.elevenlabs.tts

/**
 * An arbitrary chunk of a streaming audio response.
 *
 * A chunk is not guaranteed to contain a complete audio container or codec frame.
 */
public class AudioChunk internal constructor(
    /** Audio in this chunk. The array belongs to the caller; the SDK never reads or changes it again. */
    public val bytes: ByteArray,
)
