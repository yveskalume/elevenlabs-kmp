import ElevenLabs
import SwiftUI

struct ContentView: View {
    @StateObject private var model = IOSSampleViewModel(apiKey: Self.apiKey)

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("ElevenLabs Player")
                        .font(.title.bold())

                    VStack(alignment: .leading, spacing: 8) {
                        Text("Text")
                            .font(.caption)
                            .foregroundStyle(.secondary)

                        TextEditor(text: $model.text)
                            .frame(minHeight: 120, maxHeight: 200)
                            .padding(8)
                            .scrollContentBackground(.hidden)
                            .background(.background, in: RoundedRectangle(cornerRadius: 12))
                            .overlay {
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(.separator, lineWidth: 1)
                            }
                            .disabled(model.isLoading)
                    }

                    if !model.voices.isEmpty {
                        Menu {
                            Picker("Voice", selection: $model.selectedVoiceID) {
                                ForEach(model.voices, id: \.id) { voice in
                                    Text(voice.name).tag(Optional(voice.id))
                                }
                            }
                        } label: {
                            HStack {
                                VStack(alignment: .leading, spacing: 3) {
                                    Text("Voice")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                    Text(model.selectedVoiceName)
                                        .foregroundStyle(.primary)
                                }
                                Spacer()
                                Image(systemName: "chevron.up.chevron.down")
                                    .foregroundStyle(.secondary)
                            }
                            .padding(.horizontal, 12)
                            .frame(minHeight: 56)
                            .background(.background, in: RoundedRectangle(cornerRadius: 12))
                            .overlay {
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(.separator, lineWidth: 1)
                            }
                        }
                        .disabled(model.isLoading)

                        Text("Mode")
                            .font(.headline)

                        Picker("Mode", selection: $model.mode) {
                            ForEach(SynthesisMode.allCases) { mode in
                                Text(mode.title).tag(mode)
                            }
                        }
                        .pickerStyle(.segmented)
                        .disabled(model.isLoading)

                        Button("Create speech", action: model.synthesize)
                            .buttonStyle(.borderedProminent)
                            .controlSize(.large)
                            .frame(maxWidth: .infinity)
                            .disabled(!model.canPlay)

                        Button("Stop playback", role: .destructive, action: model.stopPlayback)
                            .buttonStyle(.borderedProminent)
                            .tint(.red)
                            .controlSize(.large)
                            .frame(maxWidth: .infinity)
                            .disabled(model.isLoading)
                    }

                    Text("Status")
                        .font(.headline)

                    if model.isLoading {
                        ProgressView()
                            .frame(maxWidth: .infinity)
                    }

                    if let errorMessage = model.errorMessage {
                        Text(errorMessage)
                            .foregroundStyle(.red)

                        Button(model.voices.isEmpty ? "Retry loading voices" : "Dismiss") {
                            if model.voices.isEmpty {
                                model.loadVoices()
                            } else {
                                model.clearError()
                            }
                        }
                    } else if model.voices.isEmpty && !model.isLoading {
                        Button("Retry loading voices", action: model.loadVoices)
                    }
                }
                .padding(20)
            }
            .background(Color(.systemGroupedBackground))
            .toolbar(.hidden, for: .navigationBar)
        }
        .onDisappear(perform: model.close)
    }

    private static var apiKey: String {
        Bundle.main.object(forInfoDictionaryKey: "ElevenLabsAPIKey") as? String ?? ""
    }
}

private enum SynthesisMode: String, CaseIterable, Identifiable {
    case generate
    case stream

    var id: Self { self }
    var title: String { rawValue.capitalized }
}

@MainActor
private final class IOSSampleViewModel: ObservableObject {
    @Published var text = "Hello from the ElevenLabs Kotlin Multiplatform SDK."
    @Published var voices: [Voice] = []
    @Published var selectedVoiceID: String?
    @Published var mode = SynthesisMode.generate
    @Published var isLoading = false
    @Published var errorMessage: String?

    private let audioPlayer = IOSAudioPlayer()
    private let apiKey: String
    private var client: ElevenLabs?
    private var streamCollector: AudioChunkCollector?
    private var operationID = UUID()

    init(apiKey: String) {
        self.apiKey = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        loadVoices()
    }

    var selectedVoiceName: String {
        voices.first(where: { $0.id == selectedVoiceID })?.name ?? "Select a voice"
    }

    var canPlay: Bool {
        !isLoading
            && selectedVoiceID != nil
            && !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
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
        let request = TextToSpeechRequest(
            voiceId: voiceID,
            text: text,
            modelId: nil,
            languageCode: nil,
            voiceSettings: nil,
            outputFormat: OutputFormat.companion.Mp3_44100_128,
            enableLogging: true
        )

        switch mode {
        case .generate:
            generate(request)
        case .stream:
            stream(request)
        }
    }

    func stopPlayback() {
        audioPlayer.stop()
    }

    func clearError() {
        errorMessage = nil
    }

    func close() {
        audioPlayer.stop()
        operationID = UUID()
        client?.close()
        client = nil
        streamCollector = nil
        isLoading = false
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

    @discardableResult
    private func beginOperation() -> UUID {
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
        isLoading = false
    }

    private func show(_ error: Error) {
        errorMessage = error.localizedDescription
    }
}

private final class AudioChunkCollector: NSObject, Kotlinx_coroutines_coreFlowCollector, @unchecked Sendable {
    private(set) var data = Data()

    func emit(value: Any?, completionHandler: @escaping (Error?) -> Void) {
        guard let chunk = value as? AudioChunk else {
            completionHandler(AudioStreamError.invalidChunk)
            return
        }

        data.append(Data(kotlinBytes: chunk.bytes))
        completionHandler(nil)
    }
}

private enum AudioStreamError: LocalizedError {
    case invalidChunk

    var errorDescription: String? {
        "The audio stream returned an unexpected value."
    }
}

#Preview {
    ContentView()
}
