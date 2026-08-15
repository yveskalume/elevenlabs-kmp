package dev.yveskalume.elevenlabs.stt

enum class RealtimeSttCommitStrategy(internal val value: String) {
    Manual("manual"),
    VoiceActivityDetection("vad"),
}