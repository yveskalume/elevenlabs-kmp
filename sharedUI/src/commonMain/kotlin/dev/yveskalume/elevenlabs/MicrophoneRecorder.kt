package dev.yveskalume.elevenlabs

import kotlinx.coroutines.flow.Flow

interface MicrophoneRecorder {
    val audio: Flow<ByteArray>

    /**
     * Starts capturing 16-bit mono PCM.
     *
     * With [voiceChat], capture keeps running while audio plays and uses the platform's voice
     * processing (echo cancellation, noise suppression) where available.
     */
    suspend fun start(sampleRate: Int, voiceChat: Boolean = false)
    fun stop()
    fun close()
}
