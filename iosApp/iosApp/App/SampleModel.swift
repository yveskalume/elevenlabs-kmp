import Observation

@MainActor
@Observable
final class SampleModel {
    var selectedFeature = SampleFeature.textToSpeech

    let tts: TTSModel
    let stt: STTModel

    init(
        apiKey: String,
        audioPlayer: AudioPlayer,
        microphoneRecorder: MicrophoneRecorder
    ) {
        tts = TTSModel(apiKey: apiKey, audioPlayer: audioPlayer)
        stt = STTModel(apiKey: apiKey, microphoneRecorder: microphoneRecorder)
    }

    var isBusy: Bool {
        tts.state.isProcessing || stt.state.isListening || stt.state.isFinalizing
    }

    func didSelectFeature(replacing previousFeature: SampleFeature) {
        guard previousFeature != selectedFeature else { return }

        switch previousFeature {
        case .textToSpeech:
            tts.stopPlayback()
        case .speechToText:
            stt.stopListening(discardingResult: true)
        }
    }

    func close() {
        tts.close()
        stt.close()
    }
}
