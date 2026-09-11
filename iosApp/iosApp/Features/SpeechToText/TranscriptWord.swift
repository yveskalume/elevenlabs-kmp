import Foundation

struct TranscriptWord: Identifiable, Equatable {
    let id: UUID
    var text: String
    var isVisible: Bool
}
