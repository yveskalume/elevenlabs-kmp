package dev.yveskalume.elevenlabs.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal class AndroidAudioPlayer(context: Context) {
    private val cacheDirectory = context.applicationContext.cacheDir
    private var mediaPlayer: MediaPlayer? = null
    private var streamingPlayer: AudioTrack? = null
    private var audioFile: File? = null

    suspend fun play(audio: ByteArray) {
        withContext(Dispatchers.IO) {
            stop()
            val file = File.createTempFile("elevenlabs-sample-", ".mp3", cacheDirectory).apply {
                writeBytes(audio)
            }
            val player = MediaPlayer()
            mediaPlayer = player
            audioFile = file

            try {
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                player.setDataSource(file.absolutePath)
                player.setOnPreparedListener { prepared ->
                    if (mediaPlayer === prepared) prepared.start()
                }
                player.setOnCompletionListener { completed ->
                    releaseIfCurrent(completed)
                }
                player.setOnErrorListener { failed, _, _ ->
                    releaseIfCurrent(failed)
                    true
                }
                player.prepareAsync()
            } catch (throwable: Throwable) {
                releaseIfCurrent(player)
                throw throwable
            }
        }
    }

    suspend fun startStream(sampleRate: Int) {
        withContext(Dispatchers.IO) {
            stop()
            val minimumBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            check(minimumBufferSize > 0) { "Could not create a PCM playback buffer." }

            val player = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(minimumBufferSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            check(player.state == AudioTrack.STATE_INITIALIZED) {
                player.release()
                "Could not initialize realtime PCM playback."
            }
            streamingPlayer = player
            player.play()
        }
    }

    suspend fun writeStream(audio: ByteArray) {
        if (audio.isEmpty()) return
        withContext(Dispatchers.IO) {
            val player = streamingPlayer ?: return@withContext
            val written = player.write(audio, 0, audio.size, AudioTrack.WRITE_BLOCKING)
            check(written >= 0) { "Failed to write realtime PCM audio ($written)." }
        }
    }

    fun finishStream() {
        // AudioTrack remains active long enough to play its queued tail. It is released by stop(),
        // the next playback request, or close().
    }

    fun stop() {
        val activeStream = streamingPlayer
        streamingPlayer = null
        runCatching { activeStream?.pause() }
        runCatching { activeStream?.flush() }
        runCatching { activeStream?.stop() }
        runCatching { activeStream?.release() }

        val player = mediaPlayer
        mediaPlayer = null
        runCatching { player?.stop() }
        runCatching { player?.release() }
        deleteAudioFile()
    }

    fun close() {
        stop()
    }

    private fun releaseIfCurrent(player: MediaPlayer) {
        if (mediaPlayer !== player) return
        mediaPlayer = null
        runCatching { player.release() }
        deleteAudioFile()
    }

    private fun deleteAudioFile() {
        audioFile?.delete()
        audioFile = null
    }
}
