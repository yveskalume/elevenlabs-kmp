import AVFoundation
import ElevenLabs

@MainActor
final class IOSAudioPlayer: NSObject, AVAudioPlayerDelegate {
    private var player: AVAudioPlayer?

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

    func stop() {
        player?.stop()
        player = nil
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

    var errorDescription: String? {
        switch self {
        case .emptyAudio:
            return "ElevenLabs returned an empty audio response."
        case .playbackDidNotStart:
            return "iOS could not start audio playback."
        }
    }
}
