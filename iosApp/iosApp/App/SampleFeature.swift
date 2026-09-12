enum SampleFeature: String, CaseIterable, Identifiable {
    case textToSpeech
    case speechToText

    var id: Self { self }

    var title: String {
        switch self {
        case .textToSpeech:
            "Text to speech"
        case .speechToText:
            "Speech to text"
        }
    }
}
