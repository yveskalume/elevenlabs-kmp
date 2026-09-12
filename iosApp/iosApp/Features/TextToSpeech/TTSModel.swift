import ElevenLabs
import Foundation
import Observation

@MainActor
@Observable
final class TTSModel {
    var state = TTSState()

    private let apiKey: String
    private let audioPlayer: AudioPlayer
    private var client: ElevenLabs?
    private var streamCollector: AudioChunkCollector?
    private var realtimeSession: RealtimeTtsSession?
    private var realtimeCollector: RealtimeEventCollector?
    private var realtimeTask: Task<Void, Never>?
    private var realtimeCollectionTask: Task<Void, Never>?
    private var operationID = UUID()

    init(apiKey: String, audioPlayer: AudioPlayer) {
        self.apiKey = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        self.audioPlayer = audioPlayer
        loadVoices()
    }

    func loadVoices() {
        guard !apiKey.isEmpty, !state.isProcessing else {
            if apiKey.isEmpty {
                state.voices = .error(
                    message: "Add ELEVENLABS_API_KEY to Configuration/Secrets.xcconfig."
                )
            }
            return
        }

        state.voices = .loading
        let currentOperationID = beginOperation()
        let request = ListVoicesRequest(
            pageSize: 100,
            nextPageToken: nil,
            search: nil,
            voiceType: nil,
            category: nil,
            includeTotalCount: true
        )

        client?.voices.list(request: request) { [weak self] page, error in
            Task { @MainActor in
                guard let self, self.operationID == currentOperationID else { return }
                defer { self.finishOperation(id: currentOperationID) }

                if let error {
                    self.state.voices = .error(message: error.localizedDescription)
                    return
                }

                let voices = page?.voices ?? []
                guard !voices.isEmpty else {
                    self.state.voices = .error(message: "No voices were returned for this account.")
                    return
                }

                self.state.voices = .idle(data: voices)
                if self.state.selectedVoiceID == nil {
                    self.state.selectedVoiceID = voices.first?.id
                }
            }
        }
    }

    func createSpeech() {
        guard let voiceID = state.selectedVoiceID, state.canCreateSpeech else { return }

        audioPlayer.stop()
        switch state.mode {
        case .generate:
            generate(completeRequest(voiceID: voiceID))
        case .stream:
            stream(completeRequest(voiceID: voiceID))
        case .realtime:
            realtime(voiceID: voiceID, text: state.text)
        }
    }

    func stopPlayback() {
        audioPlayer.stop()
        operationID = UUID()
        cancelRealtimeSession()
        streamCollector = nil
        client?.close()
        client = nil
        state.isProcessing = false
    }

    func clearError() {
        state.errorMessage = nil
    }

    func close() {
        stopPlayback()
    }

    private func completeRequest(voiceID: String) -> TextToSpeechRequest {
        TextToSpeechRequest(
            voiceId: voiceID,
            text: state.text,
            modelId: nil,
            languageCode: nil,
            voiceSettings: nil,
            outputFormat: OutputFormat.companion.Mp3_44100_128,
            enableLogging: true
        )
    }

    private func generate(_ request: TextToSpeechRequest) {
        let currentOperationID = beginOperation()
        client?.textToSpeech.generate(request: request) { [weak self] audio, error in
            Task { @MainActor in
                guard let self, self.operationID == currentOperationID else { return }
                defer { self.finishOperation(id: currentOperationID) }

                if let error {
                    self.show(error)
                    return
                }
                guard let audio else {
                    self.state.errorMessage = "ElevenLabs returned no audio data."
                    return
                }

                do {
                    try self.audioPlayer.play(audio.bytes)
                } catch {
                    self.show(error)
                }
            }
        }
    }

    private func stream(_ request: TextToSpeechRequest) {
        let currentOperationID = beginOperation()
        guard let flow = client?.textToSpeech.stream(request: request) else {
            state.errorMessage = "Could not start the audio stream."
            finishOperation(id: currentOperationID)
            return
        }

        let collector = AudioChunkCollector()
        streamCollector = collector
        flow.collect(collector: collector) { [weak self, weak collector] error in
            Task { @MainActor in
                guard let self, self.operationID == currentOperationID else { return }
                defer { self.finishOperation(id: currentOperationID) }

                if let error {
                    self.show(error)
                    return
                }
                guard let collector, !collector.data.isEmpty else {
                    self.state.errorMessage = "The audio stream ended without data."
                    return
                }

                do {
                    try self.audioPlayer.play(collector.data)
                } catch {
                    self.show(error)
                }
            }
        }
    }

    private func realtime(voiceID: String, text: String) {
        let currentOperationID = beginOperation()
        let options = RealtimeTtsOptions(
            modelId: "eleven_flash_v2_5",
            languageCode: nil,
            outputFormat: OutputFormat.companion.Pcm_24000,
            voiceSettings: nil,
            generationConfig: nil,
            enableLogging: true,
            syncAlignment: false,
            enableSsmlParsing: false,
            timeouts: RealtimeTtsTimeouts(
                connectTimeoutMillis: 10_000,
                sendTimeoutMillis: 10_000,
                finishTimeoutMillis: 30_000,
                inactivityTimeoutSeconds: 60
            ),
            keepAlive: RealtimeTtsKeepAlive(enabled: true, intervalMillis: 15_000),
            reconnectPolicy: RealtimeTtsReconnectPolicyNever.shared
        )

        client?.textToSpeech.openRealtimeSession(
            voiceId: voiceID,
            options: options,
            authorization: RealtimeTtsAuthorizationConfiguredApiKey.shared
        ) { [weak self] session, error in
            Task { @MainActor in
                guard let self, self.operationID == currentOperationID else {
                    try? await session?.close()
                    return
                }
                if let error {
                    self.show(error)
                    self.finishOperation(id: currentOperationID)
                    return
                }
                guard let session else {
                    self.state.errorMessage = "Could not open the realtime TTS session."
                    self.finishOperation(id: currentOperationID)
                    return
                }

                do {
                    try self.audioPlayer.startStream(sampleRate: 24_000)
                } catch {
                    try? await session.close()
                    self.show(error)
                    self.finishOperation(id: currentOperationID)
                    return
                }

                self.realtimeSession = session
                let collector = RealtimeEventCollector(
                    onAudio: { [weak self] bytes in
                        guard let self else { return }
                        try self.audioPlayer.enqueueStream(bytes)
                    },
                    onFinished: { [weak self] in
                        self?.audioPlayer.finishStream()
                    }
                )
                self.realtimeCollector = collector
                self.realtimeCollectionTask = Task { @MainActor [weak self, weak collector] in
                    do {
                        guard let collector else { return }
                        try await session.events.collect(collector: collector)
                    } catch is CancellationError {
                        return
                    } catch {
                        guard let self, self.operationID == currentOperationID else { return }
                        self.audioPlayer.stop()
                        self.show(error)
                    }
                    guard let self, self.operationID == currentOperationID else { return }
                    self.finishOperation(id: currentOperationID)
                }

                let chunks = Self.realtimeChunks(from: text)
                self.realtimeTask = Task { @MainActor [weak self] in
                    guard let self else { return }
                    do {
                        for (index, chunk) in chunks.enumerated() {
                            try Task.checkCancellation()
                            try await session.sendText(
                                text: chunk,
                                flush: index == chunks.indices.last
                            )
                            if index != chunks.indices.last {
                                try await Task.sleep(for: .milliseconds(80))
                            }
                        }
                        try await session.finish()
                    } catch is CancellationError {
                        // Explicit cancellation is cleaned up by stopPlayback().
                    } catch {
                        guard self.operationID == currentOperationID else { return }
                        self.audioPlayer.stop()
                        self.show(error)
                        try? await session.close()
                        self.finishOperation(id: currentOperationID)
                    }
                }
            }
        }
    }

    private static func realtimeChunks(from text: String) -> [String] {
        text.split(whereSeparator: { $0.isWhitespace }).map { "\($0) " }
    }

    @discardableResult
    private func beginOperation() -> UUID {
        cancelRealtimeSession()
        client?.close()
        streamCollector = nil
        operationID = UUID()
        state.errorMessage = nil
        state.isProcessing = true
        client = ElevenLabs { configuration in
            configuration.apiKey(value: self.apiKey)
        }
        return operationID
    }

    private func finishOperation(id: UUID) {
        guard operationID == id else { return }
        client?.close()
        client = nil
        streamCollector = nil
        realtimeTask?.cancel()
        realtimeTask = nil
        realtimeCollectionTask?.cancel()
        realtimeCollectionTask = nil
        realtimeSession = nil
        realtimeCollector = nil
        state.isProcessing = false
    }

    private func cancelRealtimeSession() {
        realtimeTask?.cancel()
        realtimeTask = nil
        realtimeCollectionTask?.cancel()
        realtimeCollectionTask = nil
        if let realtimeSession {
            Task { try? await realtimeSession.close() }
        }
        realtimeSession = nil
        realtimeCollector = nil
    }

    private func show(_ error: Error) {
        state.errorMessage = error.localizedDescription
    }
}
