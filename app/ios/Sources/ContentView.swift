import SwiftUI
import UIKit
import Shared // Kotlin framework from :shared (baseName "Shared")

/// Hosts the shared Compose UI (app/shared/src/iosMain/kotlin/com/shikkhasathi/shared/ui/MainViewController.kt).
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
