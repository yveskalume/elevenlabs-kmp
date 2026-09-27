import SwiftUI
import SharedUI

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeScreen().ignoresSafeArea()
        }
    }
}

private struct ComposeScreen: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let apiKey = Bundle.main.object(forInfoDictionaryKey: "ElevenLabsAPIKey") as? String ?? ""
        return MainViewControllerKt.MainViewController(apiKey: apiKey)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
