package dev.yveskalume.elevenlabs.feature.multicontext

sealed interface MultiContextAction {
    data object LoadVoices : MultiContextAction
    data class SelectVoice(val voiceId: String) : MultiContextAction

    /** Starts reading from the current line. */
    data object Play : MultiContextAction

    /** Stops reading and closes the connection. */
    data object Stop : MultiContextAction

    /** Reads from [line], starting playback if needed. */
    data class JumpTo(val line: Int) : MultiContextAction
    data object Previous : MultiContextAction
    data object Next : MultiContextAction

    data class SetLookAhead(val paragraphs: Int) : MultiContextAction
    data object ClearError : MultiContextAction
}
