package dev.yveskalume.elevenlabs.tts

import dev.yveskalume.elevenlabs.realtime.Alignment

/** Event emitted by a [RealtimeTtsSession]. */
public sealed interface RealtimeTtsEvent {
    /** A generated audio chunk */
    public data class Audio(
        /** Generated audio bytes owned by this event */
        public val bytes: ByteArray,
        /** Timing for the submitted text, when requested and available. */
        public val alignment: Alignment? = null,
        /** Timing for the normalized spoken text, when requested and available. */
        public val normalizedAlignment: Alignment? = null,
    ) : RealtimeTtsEvent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Audio

            if (!bytes.contentEquals(other.bytes)) return false
            if (alignment != other.alignment) return false
            if (normalizedAlignment != other.normalizedAlignment) return false

            return true
        }

        override fun hashCode(): Int {
            var result = bytes.contentHashCode()
            result = 31 * result + (alignment?.hashCode() ?: 0)
            result = 31 * result + (normalizedAlignment?.hashCode() ?: 0)
            return result
        }
    }

    /** The server has completed generation and no more events will be emitted. */
    public data object Finished : RealtimeTtsEvent
}
