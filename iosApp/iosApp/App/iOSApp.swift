import SwiftUI

@main
struct iOSApp: App {
    @State private var model: SampleModel

    init() {
        let apiKey = Bundle.main.object(forInfoDictionaryKey: "ElevenLabsAPIKey") as? String ?? ""
        _model = State(
            initialValue: SampleModel(
                apiKey: apiKey,
                audioPlayer: AudioPlayer(),
                microphoneRecorder: MicrophoneRecorder()
            )
        )
    }

    var body: some Scene {
        WindowGroup {
            SampleScreen(model: model)
        }
    }
}
