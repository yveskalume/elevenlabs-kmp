import Foundation

enum IOSAudioPlayerError: LocalizedError {
    case emptyAudio
    case playbackDidNotStart
    case invalidStreamFormat
    case invalidPcmChunk
    case streamNotStarted
    case couldNotCreatePcmBuffer

    var errorDescription: String? {
        switch self {
        case .emptyAudio:
            "ElevenLabs returned an empty audio response."
        case .playbackDidNotStart:
            "iOS could not start audio playback."
        case .invalidStreamFormat:
            "iOS could not create the realtime PCM audio format."
        case .invalidPcmChunk:
            "ElevenLabs returned an invalid realtime PCM chunk."
        case .streamNotStarted:
            "Realtime audio playback has not started."
        case .couldNotCreatePcmBuffer:
            "iOS could not create a realtime PCM audio buffer."
        }
    }
}
