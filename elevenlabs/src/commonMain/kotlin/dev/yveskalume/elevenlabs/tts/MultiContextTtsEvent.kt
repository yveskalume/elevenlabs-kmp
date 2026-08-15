package dev.yveskalume.elevenlabs.tts

sealed interface MultiContextTtsEvent {
    val contextId: String

    data class Audio(
        override val contextId: String,
        val bytes: ByteArray,
        val alignment: RealtimeTtsAlignment? = null,
        val normalizedAlignment: RealtimeTtsAlignment? = null,
    ) : MultiContextTtsEvent {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Audio) return false
            return contextId == other.contextId &&
                bytes.contentEquals(other.bytes) &&
                alignment == other.alignment &&
                normalizedAlignment == other.normalizedAlignment
        }

        override fun hashCode(): Int {
            var result = contextId.hashCode()
            result = 31 * result + bytes.contentHashCode()
            result = 31 * result + (alignment?.hashCode() ?: 0)
            result = 31 * result + (normalizedAlignment?.hashCode() ?: 0)
            return result
        }
    }

    data class ContextFinished(
        override val contextId: String,
    ) : MultiContextTtsEvent

    data class ContextError(
        override val contextId: String,
        val message: String,
    ) : MultiContextTtsEvent
}
