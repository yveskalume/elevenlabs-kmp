import ElevenLabs
import SwiftUI

struct ContentView: View {
    @StateObject private var model = SampleViewModel(apiKey: Self.apiKey)

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("ElevenLabs Sample")
                        .font(.title.bold())

                    Picker("Feature", selection: $model.feature) {
                        ForEach(SampleFeature.allCases) { feature in
                            Text(feature.title).tag(feature)
                        }
                    }
                    .pickerStyle(.segmented)
                    .disabled(model.isLoading)

                    if model.feature == .textToSpeech {
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

                            Button(
                                model.isRealtimeActive ? "Stop realtime" : "Create speech",
                                action: model.isRealtimeActive ? model.stopPlayback : model.synthesize
                            )
                                .buttonStyle(.borderedProminent)
                                .controlSize(.large)
                                .frame(maxWidth: .infinity)
                                .disabled(!model.canPlay)

                            Button("Stop playback", role: .destructive, action: model.stopPlayback)
                                .buttonStyle(.borderedProminent)
                                .tint(.red)
                                .controlSize(.large)
                                .frame(maxWidth: .infinity)
                        }
                    } else {
                        Button(
                            model.isFinalizingTranscription
                                ? "Finishing transcription…"
                                : model.isTranscribing
                                    ? "Stop and transcribe"
                                    : "Start listening",
                            action: model.isTranscribing
                                ? model.stopTranscription
                                : model.startTranscription
                        )
                            .buttonStyle(.borderedProminent)
                            .tint(model.isTranscribing ? .red : .accentColor)
                            .controlSize(.large)
                            .frame(maxWidth: .infinity)
                            .disabled(model.isFinalizingTranscription)

                        Text("Live transcript")
                            .font(.headline)

                        AnimatedTranscriptText(
                            text: model.hasTranscript ? model.displayedTranscript : ""
                        )
                        .frame(maxWidth: .infinity, minHeight: 120, alignment: .topLeading)
                    }

                    Text("Status")
                        .font(.headline)

                    if model.isLoading {
                        HStack(spacing: 8) {
                            ProgressView()
                            Text(model.statusText)
                        }
                        .frame(maxWidth: .infinity)
                    }

                    if let errorMessage = model.errorMessage {
                        Text(errorMessage)
                            .foregroundStyle(.red)

                        Button(
                            model.feature == .textToSpeech && model.voices.isEmpty
                                ? "Retry loading voices"
                                : "Dismiss"
                        ) {
                            if model.feature == .textToSpeech && model.voices.isEmpty {
                                model.loadVoices()
                            } else {
                                model.clearError()
                            }
                        }
                    } else if model.feature == .textToSpeech
                                && model.voices.isEmpty
                                && !model.isLoading {
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





private struct TranscriptWord: Identifiable, Equatable {
    let id: UUID
    var text: String
    var isVisible: Bool
}

private struct AnimatedTranscriptText: View {
    let text: String

    @State private var words: [TranscriptWord] = []
    @State private var revealTask: Task<Void, Never>?

    var body: some View {
        Group {
            if words.isEmpty {
                Text("Your speech will appear here")
                    .foregroundStyle(.secondary)
            } else {
                TranscriptFlowLayout(horizontalSpacing: 4, verticalSpacing: 5) {
                    ForEach(words) { word in
                        Text(word.text)
                            .foregroundStyle(.primary)
                            .opacity(word.isVisible ? 1 : 0)
                            .offset(y: word.isVisible ? 0 : 7)
                            .animation(
                                .easeOut(duration: 0.18),
                                value: word.isVisible
                            )
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .topLeading)
        .onAppear { updateWords(for: text) }
        .onChange(of: text) { _, newText in updateWords(for: newText) }
        .onDisappear { revealTask?.cancel() }
    }

    private func updateWords(for transcript: String) {
        revealTask?.cancel()
        for index in words.indices {
            words[index].isVisible = true
        }

        let incoming = transcript
            .split(whereSeparator: { $0.isWhitespace })
            .map(String.init)
        let sharedWordCount = min(words.count, incoming.count)
        for index in 0..<sharedWordCount where words[index].text != incoming[index] {
            words[index].text = incoming[index]
            words[index].isVisible = true
        }

        if incoming.count < words.count {
            words.removeSubrange(incoming.count...)
        }
        let added = incoming.dropFirst(words.count).map {
            TranscriptWord(id: UUID(), text: $0, isVisible: false)
        }
        words.append(contentsOf: added)
        let addedIDs = added.map(\.id)

        revealTask = Task { @MainActor in
            for id in addedIDs {
                guard !Task.isCancelled else { return }
                try? await Task.sleep(for: .milliseconds(45))
                guard !Task.isCancelled,
                      let index = words.firstIndex(where: { $0.id == id }) else { continue }
                words[index].isVisible = true
            }
        }
    }
}

private struct TranscriptFlowLayout: Layout {
    let horizontalSpacing: CGFloat
    let verticalSpacing: CGFloat

    func sizeThatFits(
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) -> CGSize {
        let layout = positions(proposal: proposal, subviews: subviews)
        return layout.size
    }

    func placeSubviews(
        in bounds: CGRect,
        proposal: ProposedViewSize,
        subviews: Subviews,
        cache: inout ()
    ) {
        let layout = positions(
            proposal: ProposedViewSize(width: bounds.width, height: proposal.height),
            subviews: subviews
        )
        for (index, point) in layout.points.enumerated() {
            subviews[index].place(
                at: CGPoint(x: bounds.minX + point.x, y: bounds.minY + point.y),
                anchor: .topLeading,
                proposal: .unspecified
            )
        }
    }

    private func positions(
        proposal: ProposedViewSize,
        subviews: Subviews
    ) -> (size: CGSize, points: [CGPoint]) {
        let availableWidth = proposal.width ?? .infinity
        var points: [CGPoint] = []
        var x: CGFloat = 0
        var y: CGFloat = 0
        var rowHeight: CGFloat = 0
        var contentWidth: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x > 0, x + size.width > availableWidth {
                x = 0
                y += rowHeight + verticalSpacing
                rowHeight = 0
            }
            points.append(CGPoint(x: x, y: y))
            x += size.width + horizontalSpacing
            rowHeight = max(rowHeight, size.height)
            contentWidth = max(contentWidth, x - horizontalSpacing)
        }

        return (
            CGSize(
                width: proposal.width ?? contentWidth,
                height: subviews.isEmpty ? 0 : y + rowHeight
            ),
            points
        )
    }
}







#Preview {
    ContentView()
}
