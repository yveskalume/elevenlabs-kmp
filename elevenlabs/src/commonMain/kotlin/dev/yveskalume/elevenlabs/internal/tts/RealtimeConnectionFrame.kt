package dev.yveskalume.elevenlabs.internal.tts

internal sealed interface RealtimeConnectionFrame {
    data class Text(val value: String) : RealtimeConnectionFrame
    data class Closed(val code: Short?, val reason: String?) : RealtimeConnectionFrame
}