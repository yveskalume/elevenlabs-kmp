package dev.yveskalume.elevenlabs

interface AudioPlayer {
    suspend fun play(audio: ByteArray)

    /**
     * Starts PCM streaming playback.
     *
     * With [voiceChat], playback is configured to run while the microphone is recording
     * (full duplex, with the platform's echo cancellation where available), as in a live voice
     * conversation.
     */
    suspend fun startStream(sampleRate: Int, voiceChat: Boolean = false)

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
