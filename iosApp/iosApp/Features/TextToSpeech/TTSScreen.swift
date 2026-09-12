import SwiftUI

struct TTSScreen: View {
    @Bindable var model: TTSModel

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            VStack(alignment: .leading, spacing: 8) {
                Text("Text")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                TextEditor(text: $model.state.text)
                    .frame(minHeight: 120, maxHeight: 200)
                    .padding(8)
                    .scrollContentBackground(.hidden)
                    .background(.background, in: RoundedRectangle(cornerRadius: 12))
                    .overlay {
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(.separator, lineWidth: 1)
                    }
                    .disabled(model.state.isProcessing)
            }

            voicesContent

            if model.state.isProcessing {
                LoadingView(message: model.state.statusText)
            }

            if let errorMessage = model.state.errorMessage {
                ErrorView(
                    message: errorMessage,
                    actionTitle: "Dismiss",
                    action: model.clearError
                )
            }
        }
    }

    @ViewBuilder
    private var voicesContent: some View {
        switch model.state.voices {
        case .loading:
            LoadingView(message: "Loading voices…")
        case let .error(message):
            ErrorView(message: message, actionTitle: "Retry", action: model.loadVoices)
        case let .idle(voices):
            Menu {
                Picker("Voice", selection: $model.state.selectedVoiceID) {
                    ForEach(voices, id: \.id) { voice in
                        Text(voice.name).tag(Optional(voice.id))
                    }
                }
            } label: {
                HStack {
                    VStack(alignment: .leading, spacing: 3) {
                        Text("Voice")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Text(model.state.selectedVoiceName)
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
            .disabled(model.state.isProcessing)

            Text("Mode")
                .font(.headline)

            Picker("Mode", selection: $model.state.mode) {
                ForEach(TTSMode.allCases) { mode in
                    Text(mode.title).tag(mode)
                }
            }
            .pickerStyle(.segmented)
            .disabled(model.state.isProcessing)

            Button(
                model.state.isRealtimeActive ? "Stop realtime" : "Create speech",
                action: model.state.isRealtimeActive ? model.stopPlayback : model.createSpeech
            )
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .frame(maxWidth: .infinity)
            .disabled(!model.state.canCreateSpeech)

            Button("Stop playback", role: .destructive, action: model.stopPlayback)
                .buttonStyle(.borderedProminent)
                .tint(.red)
                .controlSize(.large)
                .frame(maxWidth: .infinity)
        }
    }
}

#Preview {
    TTSScreen(model: TTSModel(apiKey: "", audioPlayer: AudioPlayer()))
        .padding()
}
