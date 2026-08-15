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
        streamingEngine.connect(
            streamingNode,
            to: streamingEngine.mainMixerNode,
            format: format
        )
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
        // Scheduled buffers remain owned by AVAudioPlayerNode and finish naturally. The engine is
        // released by stop(), the next playback request, or when the view model closes.
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

extension Data {
    init(kotlinBytes bytes: KotlinByteArray) {
        var buffer = [UInt8](repeating: 0, count: Int(bytes.size))
        for index in 0..<bytes.size {
            buffer[Int(index)] = UInt8(bitPattern: bytes.get(index: index))
        }
        self.init(buffer)
    }
}

private enum IOSAudioPlayerError: LocalizedError {
    case emptyAudio
    case playbackDidNotStart
    case invalidStreamFormat
    case invalidPcmChunk
    case streamNotStarted
    case couldNotCreatePcmBuffer

    var errorDescription: String? {
        switch self {
        case .emptyAudio:
            return "ElevenLabs returned an empty audio response."
        case .playbackDidNotStart:
            return "iOS could not start audio playback."
        case .invalidStreamFormat:
            return "iOS could not create the realtime PCM audio format."
        case .invalidPcmChunk:
            return "ElevenLabs returned an invalid realtime PCM chunk."
        case .streamNotStarted:
            return "Realtime audio playback has not started."
        case .couldNotCreatePcmBuffer:
            return "iOS could not create a realtime PCM audio buffer."
        }
    }
}
