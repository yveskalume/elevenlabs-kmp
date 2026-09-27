package dev.yveskalume.elevenlabs.feature

import kotlinx.coroutines.flow.StateFlow

internal interface LogicHandler<S, A> {
    val uiState: StateFlow<S>
    fun onAction(action: A)
}
