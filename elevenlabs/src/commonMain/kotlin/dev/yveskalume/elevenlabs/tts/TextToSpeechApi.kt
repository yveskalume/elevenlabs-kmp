package dev.yveskalume.elevenlabs.tts

import kotlinx.coroutines.flow.Flow

interface TextToSpeechApi {
    suspend fun generate(request: TextToSpeechRequest): Audio

    /**
     * Performs HTTP response streaming. The returned flow is cold and each collection starts a new request.
     * Cancelling collection closes the underlying response.
     */
    fun stream(request: TextToSpeechRequest): Flow<AudioChunk>
}