package dev.yveskalume.elevenlabs

import kotlinx.coroutines.flow.Flow

interface MicrophoneRecorder {
    val audio: Flow<ByteArray>

    suspend fun start(sampleRate: Int)
    fun stop()
    fun close()
}
