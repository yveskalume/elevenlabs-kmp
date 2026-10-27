@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class,
    kotlin.concurrent.atomics.ExperimentalAtomicApi::class,
)

package dev.yveskalume.elevenlabs.audio

import dev.yveskalume.elevenlabs.AudioPlayer
import kotlinx.cinterop.*
import platform.AVFAudio.*
import platform.Foundation.NSData
import platform.Foundation.create
import kotlinx.coroutines.CompletableDeferred
import kotlin.concurrent.atomics.AtomicLong
import kotlin.time.TimeSource

internal class IOSAudioPlayer : AudioPlayer {
    private var player: AVAudioPlayer? = null
    private val engine = AVAudioEngine()
    private val node = AVAudioPlayerNode()
    private var format: AVAudioFormat? = null

    // Playback clock. Completion handlers run on an audio thread, hence the atomics. A stream's
    // epoch lets handlers of buffers dropped by stop() be ignored.
    private val clock = TimeSource.Monotonic.markNow()
    private val epoch = AtomicLong(0)
    private val playedFrames = AtomicLong(0)
    private val lastPlayedAtNanos = AtomicLong(0)
    private val queued = ArrayDeque<QueuedBuffer>()
    private var scheduledFrames = 0L
    private var previousBuffer: CompletableDeferred<Unit>? = null

    /** A buffer handed to the player node: where it ends in the stream and when it was queued. */
    private class QueuedBuffer(val endFrame: Long, val queuedAtNanos: Long)

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
        playedFrames.store(0)
        lastPlayedAtNanos.store(0)
        queued.clear()
        scheduledFrames = 0
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
        val frames = (audio.size / 2).toLong()
        val bufferEpoch = epoch.load()
        val playedBack = CompletableDeferred<Unit>()
        scheduledFrames += frames
        queued.addLast(QueuedBuffer(scheduledFrames, nowNanos()))
        node.scheduleBuffer(buffer, completionCallbackType = AVAudioPlayerNodeCompletionDataPlayedBack) { _ ->
            if (epoch.load() == bufferEpoch) {
                playedFrames.addAndFetch(frames)
                lastPlayedAtNanos.store(nowNanos())
            }
            playedBack.complete(Unit)
        }
        // Keep one buffer queued behind the playing one, so playback has no gaps between chunks
        // while memory stays bounded. Cancellation is handled by the owner's stop().
        previousBuffer?.await()
        previousBuffer = playedBack
    }

    /**
     * Frames of finished buffers, plus the time spent in the buffer playing now. The node's own
     * sample time keeps running while it waits for audio, so it can't be used directly.
     */
    override fun streamPositionMillis(): Long {
        val activeFormat = format ?: return 0
        val played = playedFrames.load()
        while (queued.firstOrNull()?.let { it.endFrame <= played } == true) queued.removeFirst()
        val playing = queued.firstOrNull()
        val frames = if (playing == null) {
            played
        } else {
            val startedAt = maxOf(lastPlayedAtNanos.load(), playing.queuedAtNanos)
            val elapsed = (nowNanos() - startedAt) * activeFormat.sampleRate / 1_000_000_000
            played + minOf(elapsed.toLong(), playing.endFrame - played)
        }
        return (frames * 1_000 / activeFormat.sampleRate).toLong()
    }

    override fun finishStream() {
        // Queued PCM drains naturally. stop/close owns final engine disposal.
    }

    override fun stop() {
        epoch.addAndFetch(1)
        previousBuffer = null
        player?.stop()
        player = null
        node.stop()
        engine.stop()
        engine.reset()
        if (node.engine != null) engine.disconnectNodeOutput(node)
        format = null
    }

    override fun close() = stop()

    private fun nowNanos(): Long = clock.elapsedNow().inWholeNanoseconds

    private fun activatePlayback() {
        val session = AVAudioSession.sharedInstance()
        check(session.setCategory(AVAudioSessionCategoryPlayback, mode = AVAudioSessionModeSpokenAudio, options = 0u, error = null))
        check(session.setActive(true, error = null))
    }
}
