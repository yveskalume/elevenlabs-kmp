package dev.yveskalume.elevenlabs.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class AndroidMicrophoneRecorder {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val chunks = MutableSharedFlow<ByteArray>(extraBufferCapacity = 32)
    private var recorder: AudioRecord? = null
    private var captureJob: Job? = null

    val audio: Flow<ByteArray> = chunks

    @SuppressLint("MissingPermission")
    suspend fun start(sampleRate: Int) = withContext(Dispatchers.IO) {
        stop()
        val minimumBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minimumBufferSize > 0) { "Could not create a microphone buffer." }

        val audioRecord = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(minimumBufferSize * 2)
            .build()
        check(audioRecord.state == AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            "Could not initialize microphone capture."
        }

        recorder = audioRecord
        audioRecord.startRecording()
        check(audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            audioRecord.release()
            recorder = null
            "Could not start microphone capture."
        }

        captureJob = scope.launch {
            val buffer = ByteArray(minimumBufferSize)
            while (isActive && recorder === audioRecord) {
                val count = audioRecord.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                when {
                    count > 0 -> chunks.emit(buffer.copyOf(count))
                    count == AudioRecord.ERROR_DEAD_OBJECT -> break
                    count < 0 -> error("Microphone capture failed ($count).")
                }
            }
        }
    }

    fun stop() {
        val activeRecorder = recorder
        recorder = null
        runCatching { activeRecorder?.stop() }
        captureJob?.cancel()
        captureJob = null
        runCatching { activeRecorder?.release() }
    }

    fun close() {
        stop()
        scope.cancel()
    }
}
