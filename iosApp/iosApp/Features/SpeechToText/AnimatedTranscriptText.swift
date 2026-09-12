import SwiftUI

struct AnimatedTranscriptText: View {
    let text: String

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
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
                            .offset(y: word.isVisible || reduceMotion ? 0 : 7)
                            .animation(
                                reduceMotion ? nil : .easeOut(duration: 0.18),
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
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(text.isEmpty ? "Your speech will appear here" : text)
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
            TranscriptWord(id: UUID(), text: $0, isVisible: reduceMotion)
        }
        words.append(contentsOf: added)
        guard !reduceMotion else { return }

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
