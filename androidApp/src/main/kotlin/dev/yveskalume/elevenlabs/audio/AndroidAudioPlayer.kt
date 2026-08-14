package dev.yveskalume.elevenlabs.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal class AndroidAudioPlayer(context: Context) {
    private val cacheDirectory = context.applicationContext.cacheDir
    private var mediaPlayer: MediaPlayer? = null
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

    fun stop() {
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
