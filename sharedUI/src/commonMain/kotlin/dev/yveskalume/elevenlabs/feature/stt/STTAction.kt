package dev.yveskalume.elevenlabs.feature.stt

sealed interface STTAction {
    /** Start when idle, cancel while connecting, commit while listening. */
    data object ToggleListening : STTAction
    /** Drop the current session without waiting for a committed transcript. */
    data object Cancel : STTAction
    data object ClearError : STTAction
}
