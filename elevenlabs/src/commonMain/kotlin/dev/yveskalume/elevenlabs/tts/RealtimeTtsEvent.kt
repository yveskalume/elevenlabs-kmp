package dev.yveskalume.elevenlabs.tts

import dev.yveskalume.elevenlabs.realtime.Alignment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

/**
 * Event emitted by a [RealtimeTtsSession].
 *
 * Later SDK versions can add event types, so give a `when` over these events an `else` branch.
 */
public sealed interface RealtimeTtsEvent {
    /** A generated audio chunk */
    public data class Audio(
        /** Generated audio. The array belongs to the caller; the SDK never reads or changes it again. */
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

    /** A forward-compatible server message not recognized by this SDK version. */
    public data class Unknown(
        public val rawMessage: String,
    ) : RealtimeTtsEvent
}

/** Keeps only the audio of these events, for callers that don't need alignment. */
public fun Flow<RealtimeTtsEvent>.audioOnly(): Flow<AudioChunk> =
    mapNotNull { event -> (event as? RealtimeTtsEvent.Audio)?.let { AudioChunk(it.bytes) } }
