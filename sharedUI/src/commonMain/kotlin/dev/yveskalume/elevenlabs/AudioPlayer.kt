package dev.yveskalume.elevenlabs

interface AudioPlayer {
    suspend fun play(audio: ByteArray)

    suspend fun startStream(sampleRate: Int)

    suspend fun writeStream(audio: ByteArray)

    /**
     * How much of the streamed audio has been heard since [startStream], in milliseconds.
     *
     * Only audio actually played counts, so gaps while waiting for more audio don't advance it.
     * Used to follow along with the audio, for example to highlight the sentence being read.
     */
    fun streamPositionMillis(): Long

    fun finishStream()
    fun stop()
    fun close()
}
