package dev.yveskalume.elevenlabs.stt

/** Event emitted by a [RealtimeSttSession]. */
public sealed interface RealtimeSttEvent {
    /** The server accepted the connection and assigned [sessionId]. */
    public data class SessionStarted(public val sessionId: String) : RealtimeSttEvent
    /** A provisional transcript that may be replaced by later events. */
    public data class PartialTranscript(public val text: String) : RealtimeSttEvent
    /** A finalized transcript segment that has not necessarily been committed. */
    public data class FinalTranscript(
        /** Transcript text. */
        public val text: String,
        /** Timestamped units, when requested. */
        public val words: List<TranscriptionWord> = emptyList(),
        /** Detected language code, when requested. */
        public val languageCode: String? = null,
    ) : RealtimeSttEvent
    /** A transcript segment produced after an audio commit. */
    public data class CommittedTranscript(
        /** Transcript text. */
        public val text: String,
        /** Timestamped units, when requested. */
        public val words: List<TranscriptionWord> = emptyList(),
        /** Detected language code, when requested. */
        public val languageCode: String? = null,
    ) : RealtimeSttEvent
    /** A forward-compatible server event not recognized by this SDK version. */
    public data class Unknown(
        public val messageType: String?,
        public val rawMessage: String,
    ) : RealtimeSttEvent
}
