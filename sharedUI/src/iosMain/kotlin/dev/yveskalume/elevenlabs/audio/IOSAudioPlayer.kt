@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package dev.yveskalume.elevenlabs.audio

import dev.yveskalume.elevenlabs.AudioPlayer
import kotlinx.cinterop.*
import platform.AVFAudio.*
import platform.Foundation.NSData
import platform.Foundation.create
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal class IOSAudioPlayer : AudioPlayer {
    private var player: AVAudioPlayer? = null
    private val engine = AVAudioEngine()
    private val node = AVAudioPlayerNode()
    private var format: AVAudioFormat? = null

    override suspend fun play(audio: ByteArray) {
        require(audio.isNotEmpty()) { "Audio is empty." }
        stop()
        activatePlayback()
        val data = audio.usePinned { NSData.create(bytes = it.addressOf(0), length = audio.size.toULong()) }
        val next = AVAudioPlayer(data = data, error = null)
        check(next.prepareToPlay() && next.play()) { "Could not start audio playback." }
        player = next
    }

    override suspend fun startStream(sampleRate: Int) {
        stop()
        activatePlayback()
        val next = AVAudioFormat(AVAudioPCMFormatFloat32, sampleRate.toDouble(), 1u, false)
        if (node.engine == null) engine.attachNode(node)
        engine.connect(node, engine.mainMixerNode, next)
        engine.prepare()
        check(engine.startAndReturnError(null)) { "Could not start playback engine." }
        node.play()
        format = next
    }

    override suspend fun writeStream(audio: ByteArray) {
        if (audio.isEmpty()) return
        require(audio.size % 2 == 0) { "Incomplete PCM sample." }
        val activeFormat = checkNotNull(format) { "Stream is not started." }
        val buffer = AVAudioPCMBuffer(activeFormat, (audio.size / 2).toUInt())
        buffer.frameLength = buffer.frameCapacity
        val samples = buffer.floatChannelData!![0]!!
        for (index in 0 until audio.size / 2) {
            val value = (audio[index * 2].toInt() and 0xff) or (audio[index * 2 + 1].toInt() shl 8)
            samples[index] = value.toShort().toFloat() / 32768f
        }
        // Await consumption to bound queued buffers; cancellation is handled by the owner's stop().
        suspendCancellableCoroutine<Unit> { continuation ->
            node.scheduleBuffer(buffer) { continuation.resume(Unit) }
        }
    }

    override fun finishStream() {
        // Queued PCM drains naturally. stop/close owns final engine disposal.
    }

    override fun stop() {
        player?.stop()
        player = null
        node.stop()
        engine.stop()
        engine.reset()
        if (node.engine != null) engine.disconnectNodeOutput(node)
        format = null
    }

    override fun close() = stop()

    private fun activatePlayback() {
        val session = AVAudioSession.sharedInstance()
        check(session.setCategory(AVAudioSessionCategoryPlayback, mode = AVAudioSessionModeSpokenAudio, options = 0u, error = null))
        check(session.setActive(true, error = null))
    }
}
