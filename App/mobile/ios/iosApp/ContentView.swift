import SwiftUI
import UIKit
import Shared // Kotlin framework from :shared (baseName "Shared")

/// Hosts the shared Compose UI (App/mobile/src/shared/iosMain/ui/MainViewController.kt).
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.keyboard) // Compose handles IME insets itself
    }
}
