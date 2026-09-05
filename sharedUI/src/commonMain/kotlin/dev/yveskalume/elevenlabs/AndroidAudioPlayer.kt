package dev.yveskalume.elevenlabs

interface AndroidAudioPlayer {
    suspend fun play(audio: ByteArray)

    suspend fun startStream(sampleRate: Int)

    suspend fun writeStream(audio: ByteArray)
    fun finishStream()
    fun stop()
    fun close()
}