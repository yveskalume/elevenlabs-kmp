import ElevenLabs
import Foundation
import Observation

@MainActor
@Observable
final class STTModel {
    var state = STTState()

    private let apiKey: String
    private let microphoneRecorder: MicrophoneRecorder
    private var client: ElevenLabs?
    private var session: RealtimeSttSession?
    private var eventCollector: STTEventCollector?
    private var eventCollectionTask: Task<Void, Never>?
    private var microphoneTask: Task<Void, Never>?
    private var microphoneContinuation: AsyncStream<Data>.Continuation?
    private var operationID = UUID()

    init(apiKey: String, microphoneRecorder: MicrophoneRecorder) {
        self.apiKey = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        self.microphoneRecorder = microphoneRecorder
    }

    func startListening() {
        guard !state.isListening, !state.isFinalizing else { return }
        guard !apiKey.isEmpty else {
            state.errorMessage = "Add ELEVENLABS_API_KEY to Configuration/Secrets.xcconfig."
            return
        }

        Task { @MainActor [weak self] in
            guard let self else { return }
            let granted = await self.microphoneRecorder.requestPermission()
            guard granted else {
                self.state.errorMessage = "Microphone permission is required for live transcription."
                return
            }

            let currentOperationID = self.beginOperation()
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
                self.state.errorMessage = "Could not create the ElevenLabs client."
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

                self.session = session
                let collector = STTEventCollector(
                    onPartial: { [weak self] text in
                        self?.state.partialTranscript = text
                    },
                    onCommitted: { [weak self] text in
                        guard let self else { return }
                        self.state.committedTranscript = text
                        self.state.partialTranscript = ""
                        self.finishTranscription(id: currentOperationID)
                    }
                )
                self.eventCollector = collector
                self.eventCollectionTask = Task { @MainActor [weak self, weak collector] in
                    do {
                        guard let collector else { return }
                        try await session.events.collect(collector: collector)
                    } catch is CancellationError {
                        return
                    } catch {
                        guard let self, self.operationID == currentOperationID else { return }
                        self.show(error)
                        self.cancelSession()
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
                        self.cancelSession()
                        self.finishOperation(id: currentOperationID)
                    }
                }

                do {
                    try self.microphoneRecorder.start { data in
                        continuation.yield(data)
                    }
                } catch {
                    self.show(error)
                    self.cancelSession()
                    self.finishOperation(id: currentOperationID)
                }
            } catch {
                guard self.operationID == currentOperationID else { return }
                self.show(error)
                self.cancelSession()
                self.finishOperation(id: currentOperationID)
            }
        }
    }

    func stopListening(discardingResult: Bool = false) {
        guard state.isListening, !state.isFinalizing else { return }

        if discardingResult {
            operationID = UUID()
            cancelSession()
            client?.close()
            client = nil
            state.isListening = false
            return
        }

        state.isFinalizing = true
        microphoneRecorder.stop()
        microphoneContinuation?.finish()
        microphoneContinuation = nil
        let sender = microphoneTask
        microphoneTask = nil
        let session = session
        let currentOperationID = operationID

        Task { @MainActor [weak self] in
            await sender?.value
            do {
                try await session?.commit()
            } catch {
                guard let self, self.operationID == currentOperationID else { return }
                self.show(error)
                self.cancelSession()
                self.finishOperation(id: currentOperationID)
            }
        }
    }

    func clearError() {
        state.errorMessage = nil
    }

    func close() {
        operationID = UUID()
        cancelSession()
        client?.close()
        client = nil
        state.isListening = false
    }

    @discardableResult
    private func beginOperation() -> UUID {
        cancelSession()
        client?.close()
        operationID = UUID()
        state.errorMessage = nil
        state.partialTranscript = ""
        state.committedTranscript = ""
        state.isFinalizing = false
        state.isListening = true
        client = ElevenLabs { configuration in
            configuration.apiKey(value: self.apiKey)
        }
        return operationID
    }

    private func finishOperation(id: UUID) {
        guard operationID == id else { return }
        client?.close()
        client = nil
        state.isFinalizing = false
        state.isListening = false
    }

    private func finishTranscription(id: UUID) {
        guard operationID == id else { return }
        cancelSession()
        finishOperation(id: id)
    }

    private func cancelSession() {
        microphoneRecorder.stop()
        microphoneContinuation?.finish()
        microphoneContinuation = nil
        microphoneTask?.cancel()
        microphoneTask = nil
        eventCollectionTask?.cancel()
        eventCollectionTask = nil
        if let session {
            Task { try? await session.close() }
        }
        session = nil
        eventCollector = nil
        state.isFinalizing = false
    }

    private func show(_ error: Error) {
        state.errorMessage = error.localizedDescription
    }
}
