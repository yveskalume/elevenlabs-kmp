@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.yveskalume.elevenlabs.audio

import dev.yveskalume.elevenlabs.MicrophoneRecorder
import kotlinx.cinterop.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import platform.AVFAudio.*
import platform.posix.memcpy
import kotlin.math.ceil

internal class IOSMicrophoneRecorder : MicrophoneRecorder {
    private val engine = AVAudioEngine()
    private var tapInstalled = false
    private var active = false
    private var chunks = Channel<ByteArray>(32)
    override val audio: Flow<ByteArray> get() = chunks.receiveAsFlow()

    override suspend fun start(sampleRate: Int) {
        val session = AVAudioSession.sharedInstance()
        check(session.setCategory(AVAudioSessionCategoryRecord, mode = AVAudioSessionModeMeasurement, options = 0u, error = null))
        check(session.setPreferredSampleRate(sampleRate.toDouble(), null))
        check(session.setActive(true, error = null))
        active = true
        val input = engine.inputNode
        val inputFormat = input.outputFormatForBus(0u)
        check(inputFormat.sampleRate > 0 && inputFormat.channelCount > 0u) { "Microphone is unavailable." }
        val outputFormat = AVAudioFormat(AVAudioPCMFormatInt16, sampleRate.toDouble(), 1u, true)
        val converter = AVAudioConverter(inputFormat, outputFormat)
        val output = chunks
        input.installTapOnBus(0u, 4096u, inputFormat) { buffer, _ ->
            if (buffer != null) {
                val capacity = ceil(buffer.frameLength.toDouble() * outputFormat.sampleRate / inputFormat.sampleRate).toUInt().coerceAtLeast(1u)
                val converted = AVAudioPCMBuffer(outputFormat, capacity)
                var supplied = false
                val status = converter.convertToBuffer(converted, error = null) { _, inputStatus ->
                    if (supplied) {
                        inputStatus?.pointed?.value = AVAudioConverterInputStatus_NoDataNow
                        null
                    } else {
                        supplied = true
                        inputStatus?.pointed?.value = AVAudioConverterInputStatus_HaveData
                        buffer
                    }
                }
                if (status == AVAudioConverterOutputStatus_Error) {
                    output.close(IllegalStateException("Microphone conversion failed."))
                } else if (converted.frameLength > 0u) {
                    val bytes = ByteArray(converted.frameLength.toInt() * 2)
                    bytes.usePinned { memcpy(it.addressOf(0), converted.int16ChannelData!![0], bytes.size.toULong()) }
                    if (output.trySend(bytes).isFailure) {
                        output.close(IllegalStateException("Microphone audio buffer overflow."))
                    }
                }
            }
        }
        tapInstalled = true
        engine.prepare()
        check(engine.startAndReturnError(null)) { "Could not start microphone." }
    }

    override fun stop() {
        if (!active) return
        active = false
        if (tapInstalled) engine.inputNode.removeTapOnBus(0u)
        tapInstalled = false
        engine.stop()
        engine.reset()
        chunks.close()
        chunks = Channel(32)
        AVAudioSession.sharedInstance().setActive(false, AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, null)
    }

    override fun close() {
        stop()
        chunks.close()
    }
}
