package dev.yveskalume.elevenlabs.tts

sealed interface RealtimeTtsEvent {
    data class Audio(
        val bytes: ByteArray,
        val alignment: RealtimeTtsAlignment? = null,
        val normalizedAlignment: RealtimeTtsAlignment? = null,
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

    data object Finished : RealtimeTtsEvent
}