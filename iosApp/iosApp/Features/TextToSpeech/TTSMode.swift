enum TTSMode: String, CaseIterable, Identifiable {
    case generate
    case stream
    case realtime

    var id: Self { self }
    var title: String { rawValue.capitalized }
}
