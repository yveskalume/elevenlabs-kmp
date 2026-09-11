import Foundation

enum IOSMicrophoneRecorderError: LocalizedError {
    case unavailableInput
    case invalidFormat

    var errorDescription: String? {
        switch self {
        case .unavailableInput:
            "No microphone input is available."
        case .invalidFormat:
            "iOS could not create the 16 kHz microphone format."
        }
    }
}
