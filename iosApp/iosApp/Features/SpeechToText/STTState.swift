struct STTState {
    var partialTranscript = ""
    var committedTranscript = ""
    var isListening = false
    var isFinalizing = false
    var errorMessage: String?

    var displayedTranscript: String {
        if !partialTranscript.isEmpty {
            partialTranscript
        } else if !committedTranscript.isEmpty {
            committedTranscript
        } else {
            "Your speech will appear here"
        }
    }

    var hasTranscript: Bool {
        !partialTranscript.isEmpty || !committedTranscript.isEmpty
    }

    var statusText: String {
        isFinalizing ? "Finalizing transcript…" : "Listening to microphone…"
    }
}
