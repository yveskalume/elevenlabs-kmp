import SwiftUI

struct LoadingView: View {
    let message: String

    var body: some View {
        HStack(spacing: 8) {
            ProgressView()
            Text(message)
        }
        .frame(maxWidth: .infinity, alignment: .center)
        .accessibilityElement(children: .combine)
    }
}

#Preview {
    LoadingView(message: "Loading…")
        .padding()
}
