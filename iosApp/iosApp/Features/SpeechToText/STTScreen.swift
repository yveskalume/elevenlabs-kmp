import SwiftUI

struct STTScreen: View {
    @Bindable var model: STTModel

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Button(
                buttonTitle,
                action: {
                    if model.state.isListening {
                        model.stopListening()
                    } else {
                        model.startListening()
                    }
                }
            )
            .buttonStyle(.borderedProminent)
            .tint(model.state.isListening ? .red : .accentColor)
            .controlSize(.large)
            .frame(maxWidth: .infinity)
            .disabled(model.state.isFinalizing)

            Text("Live transcript")
                .font(.headline)

            AnimatedTranscriptText(
                text: model.state.hasTranscript ? model.state.displayedTranscript : ""
            )
            .frame(maxWidth: .infinity, minHeight: 120, alignment: .topLeading)

            if model.state.isListening {
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

    private var buttonTitle: String {
        if model.state.isFinalizing {
            "Finishing transcription…"
        } else if model.state.isListening {
            "Stop and transcribe"
        } else {
            "Start listening"
        }
    }
}

#Preview {
    STTScreen(model: STTModel(apiKey: "", microphoneRecorder: MicrophoneRecorder()))
        .padding()
}
