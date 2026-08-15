package dev.yveskalume.elevenlabs.internal.stt

internal sealed interface RealtimeSttConnectionFrame {
    data class Text(val value: String) : RealtimeSttConnectionFrame
    data class Closed(val code: Short?, val reason: String?) : RealtimeSttConnectionFrame
}