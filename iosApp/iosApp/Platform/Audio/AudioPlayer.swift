import AVFoundation
import ElevenLabs

@MainActor
final class AudioPlayer: NSObject, AVAudioPlayerDelegate {
    private var player: AVAudioPlayer?
    private let streamingEngine = AVAudioEngine()
    private let streamingNode = AVAudioPlayerNode()
    private var streamingFormat: AVAudioFormat?

    func play(_ bytes: KotlinByteArray) throws {
        try play(Data(kotlinBytes: bytes))
    }

    func play(_ data: Data) throws {
        guard !data.isEmpty else {
            throw IOSAudioPlayerError.emptyAudio
        }

        stop()

        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.playback, mode: .spokenAudio)
        try session.setActive(true)

        let newPlayer = try AVAudioPlayer(data: data)
        newPlayer.delegate = self
        newPlayer.prepareToPlay()

        guard newPlayer.play() else {
            throw IOSAudioPlayerError.playbackDidNotStart
        }
        player = newPlayer
    }

    func startStream(sampleRate: Double) throws {
        stop()

        guard let format = AVAudioFormat(
            commonFormat: .pcmFormatInt16,
            sampleRate: sampleRate,
            channels: 1,
            interleaved: false
        ) else {
            throw IOSAudioPlayerError.invalidStreamFormat
        }

        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.playback, mode: .spokenAudio)
        try session.setActive(true)

        if streamingNode.engine == nil {
            streamingEngine.attach(streamingNode)
        }
        streamingEngine.connect(streamingNode, to: streamingEngine.mainMixerNode, format: format)
        streamingEngine.prepare()
        try streamingEngine.start()
        streamingNode.play()
        streamingFormat = format
    }

    func enqueueStream(_ bytes: KotlinByteArray) throws {
        try enqueueStream(Data(kotlinBytes: bytes))
    }

    func enqueueStream(_ data: Data) throws {
        guard !data.isEmpty else { return }
        guard data.count.isMultiple(of: MemoryLayout<Int16>.size) else {
            throw IOSAudioPlayerError.invalidPcmChunk
        }
        guard let format = streamingFormat, streamingEngine.isRunning else {
            throw IOSAudioPlayerError.streamNotStarted
        }

        let frameCount = AVAudioFrameCount(data.count / MemoryLayout<Int16>.size)
        guard
            let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: frameCount),
            let channel = buffer.int16ChannelData?.pointee
        else {
            throw IOSAudioPlayerError.couldNotCreatePcmBuffer
        }

        buffer.frameLength = frameCount
        data.withUnsafeBytes { source in
            guard let sourceAddress = source.baseAddress else { return }
            memcpy(channel, sourceAddress, data.count)
        }
        streamingNode.scheduleBuffer(buffer)
    }

    func finishStream() {
        // Scheduled buffers finish naturally; stop() owns engine cleanup.
    }

    func stop() {
        player?.stop()
        player = nil

        streamingNode.stop()
        streamingEngine.stop()
        streamingEngine.reset()
        if streamingNode.engine != nil {
            streamingEngine.disconnectNodeOutput(streamingNode)
        }
        streamingFormat = nil
    }

    nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        Task { @MainActor [weak self] in
            guard self?.player === player else { return }
            self?.player = nil
        }
    }
}
