import Foundation

enum AudioStreamError: LocalizedError {
    case invalidChunk

    var errorDescription: String? {
        "The audio stream returned an unexpected value."
    }
}
