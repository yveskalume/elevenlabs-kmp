import ElevenLabs

struct TTSState {
    var text = "Hello from the ElevenLabs Kotlin Multiplatform SDK."
    var voices: LoadingState<[Voice]> = .loading
    var selectedVoiceID: String?
    var mode = TTSMode.generate
    var isProcessing = false
    var errorMessage: String?

    var selectedVoiceName: String {
        voices.dataOrNil?.first(where: { $0.id == selectedVoiceID })?.name ?? "Select a voice"
    }

    var canCreateSpeech: Bool {
        (!isProcessing || isRealtimeActive)
            && selectedVoiceID != nil
            && !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var isRealtimeActive: Bool {
        isProcessing && mode == .realtime
    }

    var statusText: String {
        mode == .realtime ? "Receiving realtime audio…" : "Creating speech…"
    }
}
