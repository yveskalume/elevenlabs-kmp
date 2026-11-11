package dev.yveskalume.elevenlabs.stt

/**
 * Determines when buffered realtime audio is committed for transcription.
 *
 * Construct this type with a service-supported value when a newer strategy is not yet exposed as
 * a companion constant.
 */
public data class RealtimeSttCommitStrategy @Throws(IllegalArgumentException::class) constructor(
    public val value: String,
) {
    init {
        require(value.isNotBlank()) { "The commit strategy cannot be blank." }
    }

    public companion object {
        /** Audio is committed only through [RealtimeSttSession.commit] or `sendAudio(..., commit = true)`. */
        public val Manual: RealtimeSttCommitStrategy = RealtimeSttCommitStrategy("manual")
        /** ElevenLabs commits audio when server-side voice activity detection identifies a boundary. */
        public val VoiceActivityDetection: RealtimeSttCommitStrategy = RealtimeSttCommitStrategy("vad")
        /** ElevenLabs commits audio when it predicts that the speaker has finished their turn. */
        public val TurnPrediction: RealtimeSttCommitStrategy = RealtimeSttCommitStrategy("turn_prediction")
    }
}
