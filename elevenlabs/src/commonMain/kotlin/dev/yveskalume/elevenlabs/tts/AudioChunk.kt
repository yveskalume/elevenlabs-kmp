package dev.yveskalume.elevenlabs.tts

/**
 * An arbitrary chunk of a streaming audio response.
 *
 * A chunk is not guaranteed to contain a complete audio container or codec frame.
 */
public class AudioChunk internal constructor(bytes: ByteArray) {
    public val bytes: ByteArray = bytes.copyOf()
}
