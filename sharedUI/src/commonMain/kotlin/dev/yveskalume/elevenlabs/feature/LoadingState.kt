package dev.yveskalume.elevenlabs.feature

sealed interface LoadingState<out T> {
    data class Idle<T>(val data: T) : LoadingState<T>
    data object Loading : LoadingState<Nothing>
    data class Error<T>(val message: String) : LoadingState<T>

    val dataOrNull: T?
        get() = (this as? Idle)?.data
}