package dev.yveskalume.elevenlabs.stt

/** Determines when buffered realtime audio is committed for transcription. */
public enum class RealtimeSttCommitStrategy(internal val value: String) {
    /** Audio is committed only through [RealtimeSttSession.commit] or `sendAudio(..., commit = true)`. */
    Manual("manual"),
    /** ElevenLabs commits audio when server-side voice activity detection identifies a boundary. */
    VoiceActivityDetection("vad"),
}
