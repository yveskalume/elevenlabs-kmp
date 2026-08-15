//
//  IOSSampleViewModel.swift
//  iosApp
//
//  Created by Yves Kalume on 15/08/2026.
//


import ElevenLabs
import SwiftUI

enum SynthesisMode: String, CaseIterable, Identifiable {
    case generate
    case stream
    case realtime

    var id: Self { self }
    var title: String { rawValue.capitalized }
}

@MainActor
final class SampleViewModel: ObservableObject {
    @Published var text = "Hello from the ElevenLabs Kotlin Multiplatform SDK."
    @Published var voices: [Voice] = []
    @Published var selectedVoiceID: String?
    @Published var feature = SampleFeature.textToSpeech
    @Published var mode = SynthesisMode.generate
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var partialTranscript = ""
    @Published var committedTranscript = ""
    @Published var isFinalizingTranscription = false

    private let audioPlayer = AudioPlayer()
    private let microphoneRecorder = MicrophoneRecorder()
    private let apiKey: String
    private var client: ElevenLabs?
    private var streamCollector: AudioChunkCollector?
    private var realtimeSession: RealtimeTtsSession?
    private var realtimeCollector: RealtimeEventCollector?
    private var realtimeTask: Task<Void, Never>?
    private var realtimeCollectionTask: Task<Void, Never>?
    private var sttSession: RealtimeSttSession?
    private var sttCollector: SttEventCollector?
    private var sttCollectionTask: Task<Void, Never>?
    private var microphoneTask: Task<Void, Never>?
    private var microphoneContinuation: AsyncStream<Data>.Continuation?
    private var operationID = UUID()

    init(apiKey: String) {
        self.apiKey = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        loadVoices()
    }

    var selectedVoiceName: String {
        voices.first(where: { $0.id == selectedVoiceID })?.name ?? "Select a voice"
    }

    var canPlay: Bool {
        (!isLoading || isRealtimeActive)
            && selectedVoiceID != nil
            && !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var isRealtimeActive: Bool {
        feature == .textToSpeech && isLoading && mode == .realtime
    }

    var isTranscribing: Bool {
        feature == .speechToText && isLoading
    }

    var displayedTranscript: String {
        if !partialTranscript.isEmpty { return partialTranscript }
        if !committedTranscript.isEmpty { return committedTranscript }
        return "Your speech will appear here"
    }

    var hasTranscript: Bool {
        !partialTranscript.isEmpty || !committedTranscript.isEmpty
    }

    var statusText: String {
        if feature == .speechToText {
            return isFinalizingTranscription
                ? "Finalizing transcript…"
                : "Listening to microphone…"
        }
        return mode == .realtime ? "Receiving realtime audio…" : "Creating speech…"
    }

    func loadVoices() {
        guard !apiKey.isEmpty, !isLoading else {
            if apiKey.isEmpty {
                errorMessage = "Add ELEVENLABS_API_KEY to Configuration/Secrets.xcconfig."
            }
            return
        }

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
                    self.show(error)
                    return
                }

                let loadedVoices = page?.voices ?? []
                self.voices = loadedVoices
                self.selectedVoiceID = loadedVoices.first?.id
                if loadedVoices.isEmpty {
                    self.errorMessage = "No voices were returned for this account."
                }
            }
        }
    }

    func synthesize() {
        guard let voiceID = selectedVoiceID, canPlay else { return }

        audioPlayer.stop()
        switch mode {
        case .generate:
            generate(completeRequest(voiceID: voiceID))
        case .stream:
            stream(completeRequest(voiceID: voiceID))
        case .realtime:
            realtime(voiceID: voiceID, text: text)
        }
    }

    func stopPlayback() {
        audioPlayer.stop()
        operationID = UUID()
        cancelRealtimeSession()
        cancelSttSession()
        streamCollector = nil
        client?.close()
        client = nil
        isLoading = false
    }

    func clearError() {
        errorMessage = nil
    }

    func close() {
        audioPlayer.stop()
        operationID = UUID()
        cancelRealtimeSession()
        cancelSttSession()
        client?.close()
        client = nil
        streamCollector = nil
        isLoading = false
    }

    func startTranscription() {
        guard !isLoading else { return }
        Task { @MainActor [weak self] in
            guard let self else { return }
            let granted = await self.microphoneRecorder.requestPermission()
            guard granted else {
                self.errorMessage = "Microphone permission is required for live transcription."
                return
            }
            guard self.feature == .speechToText else { return }

            let currentOperationID = self.beginOperation()
            self.partialTranscript = ""
            self.committedTranscript = ""
            self.isFinalizingTranscription = false
            let options = RealtimeSttOptions(
                modelId: "scribe_v2_realtime",
                audioFormat: RealtimeSttAudioFormat.pcm16000,
                languageCode: nil,
                secondaryLanguages: [],
                commitStrategy: RealtimeSttCommitStrategy.manual,
                includeTimestamps: true,
                includeLanguageDetection: true,
                keyterms: [],
                noVerbatim: false,
                enableLogging: true
            )

            guard let client = self.client else {
                self.errorMessage = "Could not create the ElevenLabs client."
                self.finishOperation(id: currentOperationID)
                return
            }
            do {
                let session = try await client.speechToText.openRealtimeSession(
                    options: options,
                    authorization: SpeechToTextAuthorizationConfiguredApiKey.shared
                )
                guard self.operationID == currentOperationID else {
                    try? await session.close()
                    return
                }

                self.sttSession = session
                let collector = SttEventCollector(
                    onPartial: { [weak self] text in
                        self?.partialTranscript = text
                    },
                    onCommitted: { [weak self] text in
                        guard let self else { return }
                        self.committedTranscript = text
                        self.partialTranscript = ""
                        self.finishTranscription(id: currentOperationID)
                    }
                )
                self.sttCollector = collector
                self.sttCollectionTask = Task { @MainActor [weak self, weak collector] in
                    do {
                        guard let collector else { return }
                        try await session.events.collect(collector: collector)
                    } catch is CancellationError {
                        return
                    } catch {
                        guard let self, self.operationID == currentOperationID else { return }
                        self.show(error)
                        self.cancelSttSession()
                        self.finishOperation(id: currentOperationID)
                    }
                }

                let (audioStream, continuation) = AsyncStream<Data>.makeStream()
                self.microphoneContinuation = continuation
                self.microphoneTask = Task { @MainActor [weak self] in
                    do {
                        for await chunk in audioStream {
                            try Task.checkCancellation()
                            try await session.sendAudio(
                                audio: chunk.kotlinByteArray(),
                                commit: false
                            )
                        }
                    } catch is CancellationError {
                        return
                    } catch {
                        guard let self, self.operationID == currentOperationID else { return }
                        self.show(error)
                        self.cancelSttSession()
                        self.finishOperation(id: currentOperationID)
                    }
                }

                do {
                    try self.microphoneRecorder.start { data in
                        continuation.yield(data)
                    }
                } catch {
                    self.show(error)
                    self.cancelSttSession()
                    self.finishOperation(id: currentOperationID)
                }
            } catch {
                guard self.operationID == currentOperationID else { return }
                self.show(error)
                self.cancelSttSession()
                self.finishOperation(id: currentOperationID)
            }
        }
    }

    func stopTranscription() {
        guard isTranscribing, !isFinalizingTranscription else { return }
        isFinalizingTranscription = true
        microphoneRecorder.stop()
        microphoneContinuation?.finish()
        microphoneContinuation = nil
        let sender = microphoneTask
        microphoneTask = nil
        let session = sttSession
        let currentOperationID = operationID

        Task { @MainActor [weak self] in
            await sender?.value
            do {
                try await session?.commit()
            } catch {
                guard let self, self.operationID == currentOperationID else { return }
                self.show(error)
                self.cancelSttSession()
                self.finishOperation(id: currentOperationID)
            }
        }
    }

    private func completeRequest(voiceID: String) -> TextToSpeechRequest {
        TextToSpeechRequest(
            voiceId: voiceID,
            text: text,
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
                    self.errorMessage = "ElevenLabs returned no audio data."
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
            errorMessage = "Could not start the audio stream."
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
                    self.errorMessage = "The audio stream ended without data."
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
            keepAlive: RealtimeTtsKeepAlive(
                enabled: true,
                intervalMillis: 15_000
            ),
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
                    self.errorMessage = "Could not open the realtime TTS session."
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
                        // stopPlayback() owns cleanup for an explicitly cancelled operation.
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
        cancelSttSession()
        client?.close()
        streamCollector = nil
        operationID = UUID()
        errorMessage = nil
        isLoading = true
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
        isFinalizingTranscription = false
        isLoading = false
    }

    private func finishTranscription(id: UUID) {
        guard operationID == id else { return }
        cancelSttSession()
        finishOperation(id: id)
    }

    private func cancelRealtimeSession() {
        realtimeTask?.cancel()
        realtimeTask = nil
        realtimeCollectionTask?.cancel()
        realtimeCollectionTask = nil
        if let session = realtimeSession {
            Task { try? await session.close() }
        }
        realtimeSession = nil
        realtimeCollector = nil
    }

    private func cancelSttSession() {
        microphoneRecorder.stop()
        microphoneContinuation?.finish()
        microphoneContinuation = nil
        microphoneTask?.cancel()
        microphoneTask = nil
        sttCollectionTask?.cancel()
        sttCollectionTask = nil
        if let session = sttSession {
            Task { try? await session.close() }
        }
        sttSession = nil
        sttCollector = nil
        isFinalizingTranscription = false
    }

    private func show(_ error: Error) {
        errorMessage = error.localizedDescription
    }
}
