import SwiftUI

struct SampleScreen: View {
    @Bindable var model: SampleModel

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("ElevenLabs Sample")
                        .font(.title.bold())

                    Picker("Feature", selection: $model.selectedFeature) {
                        ForEach(SampleFeature.allCases) { feature in
                            Text(feature.title).tag(feature)
                        }
                    }
                    .pickerStyle(.segmented)
                    .disabled(model.isBusy)
                    .onChange(of: model.selectedFeature) { previousFeature, _ in
                        model.didSelectFeature(replacing: previousFeature)
                    }

                    switch model.selectedFeature {
                    case .textToSpeech:
                        TTSScreen(model: model.tts)
                    case .speechToText:
                        STTScreen(model: model.stt)
                    }
                }
                .padding(20)
            }
            .background(Color(uiColor: .systemGroupedBackground))
            .toolbar(.hidden, for: .navigationBar)
        }
        .onDisappear(perform: model.close)
    }
}

#Preview {
    SampleScreen(
        model: SampleModel(
            apiKey: "",
            audioPlayer: AudioPlayer(),
            microphoneRecorder: MicrophoneRecorder()
        )
    )
}
